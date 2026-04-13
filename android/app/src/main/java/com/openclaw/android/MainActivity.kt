package com.openclaw.android

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.openclaw.android.databinding.ActivityMainBinding
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalViewClient
import java.io.ByteArrayInputStream
import java.io.File

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val DEFAULT_TEXT_SIZE = 32
        private const val MIN_TEXT_SIZE = 8
        private const val MAX_TEXT_SIZE = 32
        private const val KEYBOARD_SHOW_DELAY_MS = 200L
        private const val TAB_NAME_TEXT_SIZE = 12f
        private const val TAB_CLOSE_TEXT_SIZE = 14f
        private const val TAB_ADD_TEXT_SIZE = 18f
        private const val TAB_MARGIN_DP = 2
        private const val TAB_HPAD_DP = 10
        private const val TAB_VPAD_DP = 4
        private const val TAB_CLOSE_PAD_DP = 6
        private const val TAB_ADD_PAD_DP = 12
        private const val INDICATOR_HEIGHT_DP = 2
        private const val INPUT_MODE_TYPE_NULL = 1
        private const val ADVANCED_MODE_DURATION_MS = 5 * 60 * 1000L
        private const val TRUSTED_WEB_SCHEME = "https"
        private const val TRUSTED_WEB_HOST = "app.openclaw.local"
    }

    private lateinit var binding: ActivityMainBinding

    lateinit var sessionManager: TerminalSessionManager
    lateinit var bootstrapManager: BootstrapManager
    lateinit var eventBridge: EventBridge
    private lateinit var privilegeManager: PrivilegeManager
    private lateinit var biometricHelper: BiometricHelper
    private lateinit var jsBridge: JsBridge

    private var currentTextSize = DEFAULT_TEXT_SIZE
    private var ctrlDown = false
    private var altDown = false
    private var isTrustedWebContentActive = false
    private val terminalSessionClient = OpenClawSessionClient()
    private val terminalViewClient = OpenClawViewClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val privilegeChangeListener: (Boolean) -> Unit = { privileged ->
        runOnUiThread {
            if (privileged) {
                applyPrivilegedWebViewMode(binding.webView)
            } else {
                applySafeWebViewDefaults(binding.webView)
            }
            updateAdvancedModeBanner()
            emitPrivilegeStatus()
        }
    }
    private val advancedModeTicker =
        object : Runnable {
            override fun run() {
                updateAdvancedModeBanner()
                if (privilegeManager.isPrivileged()) {
                    emitPrivilegeStatus()
                    mainHandler.postDelayed(this, 1_000L)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bootstrapManager = BootstrapManager(this)
        privilegeManager = PrivilegeManager()
        biometricHelper = BiometricHelper(this)
        eventBridge = EventBridge(binding.webView)
        sessionManager = TerminalSessionManager(this, terminalSessionClient, eventBridge)
        jsBridge = JsBridge(this, sessionManager, bootstrapManager, eventBridge, privilegeManager)
        privilegeManager.addListener(privilegeChangeListener)

        setupTerminalView()
        setupWebView()
        setupExtraKeys()
        setupAdvancedModeBanner()
        sessionManager.onSessionsChanged = { updateSessionTabs() }
        startService(Intent(this, OpenClawService::class.java))

        val isInstalled = bootstrapManager.isInstalled()
        AppLogger.i(TAG, "Bootstrap installed: $isInstalled, needsPostSetup: ${bootstrapManager.needsPostSetup()}")

        // Sync www assets and check for APK version upgrade
        if (isInstalled) {
            val prefs = getSharedPreferences("openclaw", 0)
            val savedVersionCode = prefs.getInt("versionCode", 0)
            val currentVersionCode = packageManager.getPackageInfo(packageName, 0).versionCode
            // Always sync www from assets to pick up UI updates
            bootstrapManager.syncWwwFromAssets()
            // Ensure oa CLI is installed (network, run in background)
            Thread { bootstrapManager.installOaCli() }.start()
            if (currentVersionCode > savedVersionCode) {
                AppLogger.i(TAG, "APK version upgrade detected: $savedVersionCode -> $currentVersionCode")
                bootstrapManager.applyScriptUpdate()
                prefs.edit().putInt("versionCode", currentVersionCode).apply()
            }
        }
        if (isInstalled) {
            showTerminal()
            val session = createOrReuseTerminalSession()
            if (bootstrapManager.needsPostSetup()) {
                AppLogger.i(TAG, "Running post-setup script in terminal")
                runPostSetupScript(session)
            } else if (intent?.getBooleanExtra("from_boot", false) == true) {
                val platformFile = java.io.File(bootstrapManager.homeDir, ".openclaw-android/.platform")
                val platformId = if (platformFile.exists()) platformFile.readText().trim() else "openclaw"
                AppLogger.i(TAG, "Boot launch \u2014 auto-starting $platformId gateway")
                binding.terminalView.post {
                    session.write("$platformId gateway\n")
                }
            }
        }
        // else: WebView shows setup UI, user triggers startSetup via JsBridge

        applySafeWebViewDefaults(binding.webView)
        updateAdvancedModeBanner()
    }

    // --- Terminal setup ---

    private fun setupTerminalView() {
        binding.terminalView.setTerminalViewClient(terminalViewClient)
        binding.terminalView.setTextSize(currentTextSize)
    }

    // --- WebView setup ---

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        binding.webView.apply {
            clearCache(true)
            applySafeWebViewDefaults(this)
            addJavascriptInterface(jsBridge, "OpenClaw")
            webViewClient =
                object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val currentRequest = request ?: return false
                        val uri = currentRequest.url
                        if (!currentRequest.isForMainFrame) return false
                        if (isTrustedWebUri(uri)) return false

                        revokePrivilege("Advanced Mode was disabled because the app left its trusted origin.")
                        openExternalUri(uri.toString())
                        return true
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): WebResourceResponse? {
                        val currentRequest = request ?: return null
                        val uri = currentRequest.url
                        if (!isTrustedWebUri(uri)) return super.shouldInterceptRequest(view, request)
                        return try {
                            buildTrustedWebResponse(uri)
                        } catch (e: SecurityException) {
                            AppLogger.w(TAG, "Blocked trusted content request: ${e.message}")
                            WebResourceResponse(
                                "text/plain",
                                "utf-8",
                                403,
                                "Forbidden",
                                emptyMap(),
                                ByteArrayInputStream(ByteArray(0)),
                            )
                        }
                    }

                    override fun onPageFinished(
                        view: WebView?,
                        url: String?,
                    ) {
                        super.onPageFinished(view, url)
                        val uri = url?.let(android.net.Uri::parse)
                        isTrustedWebContentActive = uri != null && isTrustedWebUri(uri)
                        if (!isTrustedWebContentActive) {
                            revokePrivilege("Advanced Mode was disabled because the page origin changed.")
                        }
                        AppLogger.i(TAG, "WebView page loaded: $url")
                        emitPrivilegeStatus()
                    }
                }
            webChromeClient =
                object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        consoleMessage?.let {
                            AppLogger.d("WebViewJS", "${it.sourceId()}:${it.lineNumber()} ${it.message()}")
                        }
                        return true
                    }
                }
        }

        val url = trustedWebUrl()
        isTrustedWebContentActive = true
        AppLogger.i(TAG, "Loading WebView URL: $url")
        binding.webView.loadUrl(url)
    }

    fun reloadWebView() {
        binding.webView.reload()
    }

    fun isTrustedWebContentActive(): Boolean = isTrustedWebContentActive

    fun getPrivilegeStatus(): Map<String, Any?> =
        mapOf(
            "privileged" to privilegeManager.isPrivileged(),
            "remainingMs" to privilegeManager.remainingMs(),
            "expiresAtEpochMs" to privilegeManager.expiresAtEpochMillis(),
        )

    fun requestAdvancedMode() {
        runOnUiThread {
            AlertDialog
                .Builder(this)
                .setTitle("Enable Advanced Mode?")
                .setMessage(
                    "Advanced Mode temporarily unlocks terminal command injection, command execution, " +
                        "tool installs, and update actions for 5 minutes.\n\n" +
                        "It automatically turns off when the timer expires, the app goes to the background, " +
                        "or trusted WebView content changes.",
                ).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Continue") { _, _ ->
                    biometricHelper.authenticateForAdvancedMode(
                        onSuccess = {
                            privilegeManager.grant(ADVANCED_MODE_DURATION_MS)
                            showSecurityMessage("Advanced Mode is active for 5 minutes.")
                        },
                        onFailure = { message ->
                            showSecurityMessage(message)
                            emitPrivilegeStatus()
                        },
                    )
                }.show()
        }
    }

    fun disableAdvancedMode() {
        revokePrivilege("Advanced Mode disabled.")
    }

    fun openSetupTerminal() {
        runOnUiThread {
            val session = createOrReuseTerminalSession()
            showTerminal()
            if (bootstrapManager.needsPostSetup()) {
                AppLogger.i(TAG, "Launching post-setup script from setup completion flow")
                runPostSetupScript(session)
            }
        }
    }

    fun confirmPrivilegedCommand(
        command: String,
        onApproved: () -> Unit,
    ) {
        if (!privilegeManager.isPrivileged()) {
            showSecurityMessage("Enable Advanced Mode before running commands from the WebView.")
            emitPrivilegeStatus()
            return
        }

        runOnUiThread {
            AlertDialog
                .Builder(this)
                .setTitle("Confirm command")
                .setMessage(
                    "Review this command before sending it to the terminal:\n\n$command\n\n" +
                        "Commands from the WebView can modify files, install software, or exfiltrate data.",
                ).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Run") { _, _ ->
                    if (CommandRunner.requiresDoubleConfirmation(command)) {
                        showDangerousCommandConfirmation(command, onApproved)
                    } else {
                        onApproved()
                    }
                }.show()
        }
    }

    fun showSecurityMessage(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            revokePrivilege("Advanced Mode disabled because the app went to the background.")
        }
    }

    override fun onDestroy() {
        privilegeManager.removeListener(privilegeChangeListener)
        mainHandler.removeCallbacks(advancedModeTicker)
        super.onDestroy()
    }

    private fun setupAdvancedModeBanner() {
        binding.advancedModeDisableButton.setOnClickListener {
            disableAdvancedMode()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun applySafeWebViewDefaults(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun applyPrivilegedWebViewMode(webView: WebView) {
        if (!privilegeManager.isPrivileged()) {
            applySafeWebViewDefaults(webView)
            return
        }
        applySafeWebViewDefaults(webView)
    }

    private fun updateAdvancedModeBanner() {
        if (!::privilegeManager.isInitialized || !privilegeManager.isPrivileged()) {
            binding.advancedModeBanner.visibility = View.GONE
            mainHandler.removeCallbacks(advancedModeTicker)
            return
        }

        binding.advancedModeBanner.visibility = View.VISIBLE
        binding.advancedModeBannerText.text =
            "Advanced mode active \u00b7 expires in ${formatDuration(privilegeManager.remainingMs())}"
        mainHandler.removeCallbacks(advancedModeTicker)
        mainHandler.postDelayed(advancedModeTicker, 1_000L)
    }

    private fun emitPrivilegeStatus() {
        eventBridge.emit("privilege_status", getPrivilegeStatus())
    }

    private fun revokePrivilege(message: String) {
        val wasPrivileged = privilegeManager.isPrivileged()
        privilegeManager.revoke()
        if (wasPrivileged) {
            showSecurityMessage(message)
        }
    }

    private fun showDangerousCommandConfirmation(
        command: String,
        onApproved: () -> Unit,
    ) {
        AlertDialog
            .Builder(this)
            .setTitle("Confirm high-risk command")
            .setMessage(
                "This command matches a dangerous pattern and needs one more confirmation:\n\n$command\n\n" +
                    "Patterns like rm, chmod, or curl | sh can permanently change the environment.",
            ).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Run anyway") { _, _ -> onApproved() }
            .show()
    }

    private fun trustedWebUrl(): String = "$TRUSTED_WEB_SCHEME://$TRUSTED_WEB_HOST/index.html"

    private fun isTrustedWebUri(uri: android.net.Uri): Boolean =
        uri.scheme == TRUSTED_WEB_SCHEME && uri.host == TRUSTED_WEB_HOST

    private fun buildTrustedWebResponse(uri: android.net.Uri): WebResourceResponse {
        val relativePath = normalizeTrustedPath(uri.path)
        val mimeType = mimeTypeFor(relativePath)
        val stream = openTrustedPath(relativePath)
        return WebResourceResponse(mimeType, "utf-8", stream)
    }

    private fun normalizeTrustedPath(path: String?): String {
        val normalized =
            path
                ?.removePrefix("/")
                ?.ifBlank { "index.html" }
                ?: "index.html"
        if (normalized.contains("..")) {
            throw SecurityException("Blocked path traversal in trusted WebView request.")
        }
        return normalized
    }

    private fun openTrustedPath(relativePath: String): java.io.InputStream {
        val localFile = File(bootstrapManager.wwwDir, relativePath)
        if (bootstrapManager.wwwDir.resolve("index.html").exists()) {
            val canonicalRoot = bootstrapManager.wwwDir.canonicalFile
            val canonicalFile = localFile.canonicalFile
            val allowedPrefix = canonicalRoot.path + File.separator
            if (
                canonicalFile.path == canonicalRoot.path ||
                canonicalFile.path.startsWith(allowedPrefix)
            ) {
                if (canonicalFile.exists() && canonicalFile.isFile) {
                    return canonicalFile.inputStream()
                }
            }
        }

        return try {
            assets.open("www/$relativePath")
        } catch (_: Exception) {
            ByteArrayInputStream(ByteArray(0))
        }
    }

    private fun mimeTypeFor(relativePath: String): String =
        MimeTypeMap
            .getSingleton()
            .getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(relativePath))
            ?: when {
                relativePath.endsWith(".js") -> "text/javascript"
                relativePath.endsWith(".css") -> "text/css"
                relativePath.endsWith(".svg") -> "image/svg+xml"
                relativePath.endsWith(".json") -> "application/json"
                else -> "text/html"
            }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs.coerceAtLeast(0L) / 1_000L).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    private fun openExternalUri(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            showSecurityMessage("No application is available to open that link.")
        }
    }

    private fun createOrReuseTerminalSession(): TerminalSession =
        sessionManager.activeSession ?: sessionManager.createSession()

    private fun runPostSetupScript(session: TerminalSession) {
        val script = bootstrapManager.postSetupScript.absolutePath
        binding.terminalView.post {
            session.write("bash $script\n")
        }
    }

    // --- View switching ---

    fun showTerminal() {
        runOnUiThread {
            binding.webView.visibility = View.GONE
            binding.terminalContainer.visibility = View.VISIBLE
            binding.terminalView.requestFocus()
            updateSessionTabs()
            // Delay keyboard show — view must be focused and laid out first
            binding.terminalView.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.terminalView, InputMethodManager.SHOW_IMPLICIT)
            }, KEYBOARD_SHOW_DELAY_MS)
        }
    }

    fun showWebView() {
        runOnUiThread {
            binding.terminalContainer.visibility = View.GONE
            binding.webView.visibility = View.VISIBLE
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (binding.terminalContainer.visibility == View.VISIBLE) {
            showWebView()
        } else if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // --- Extra Keys ---

    private val pressedAlpha = 0.5f
    private val normalAlpha = 1.0f

    @SuppressLint("ClickableViewAccessibility")
    private fun setupExtraKeys() {
        // Key code buttons — send key event on touch, never steal focus
        val keyMap =
            mapOf(
                R.id.btnEsc to KeyEvent.KEYCODE_ESCAPE,
                R.id.btnTab to KeyEvent.KEYCODE_TAB,
                R.id.btnHome to KeyEvent.KEYCODE_MOVE_HOME,
                R.id.btnEnd to KeyEvent.KEYCODE_MOVE_END,
                R.id.btnUp to KeyEvent.KEYCODE_DPAD_UP,
                R.id.btnDown to KeyEvent.KEYCODE_DPAD_DOWN,
                R.id.btnLeft to KeyEvent.KEYCODE_DPAD_LEFT,
                R.id.btnRight to KeyEvent.KEYCODE_DPAD_RIGHT,
            )
        for ((btnId, keyCode) in keyMap) {
            setupExtraKeyTouch(findViewById(btnId)) { sendExtraKey(keyCode) }
        }

        // Character keys
        setupExtraKeyTouch(findViewById(R.id.btnDash)) { sessionManager.activeSession?.write("-") }
        setupExtraKeyTouch(findViewById(R.id.btnPipe)) { sessionManager.activeSession?.write("|") }
        setupExtraKeyTouch(findViewById(R.id.btnPaste)) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val text =
                clipboard.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(this)
                    ?.toString()
            if (!text.isNullOrEmpty()) sessionManager.activeSession?.write(text)
        }

        // Modifier toggles — stay pressed until next key or toggled off
        setupModifierTouch(findViewById(R.id.btnCtrl)) {
            ctrlDown = !ctrlDown
            ctrlDown
        }
        setupModifierTouch(findViewById(R.id.btnAlt)) {
            altDown = !altDown
            altDown
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupExtraKeyTouch(
        btn: Button,
        action: () -> Unit,
    ) {
        btn.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.alpha = pressedAlpha
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = normalAlpha
                    if (event.action == MotionEvent.ACTION_UP) action()
                }
            }
            true // consume — never let focus leave TerminalView
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupModifierTouch(
        btn: Button,
        toggle: () -> Boolean,
    ) {
        btn.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.alpha = pressedAlpha
                MotionEvent.ACTION_UP -> {
                    val active = toggle()
                    updateModifierButton(v as Button, active)
                    v.alpha = normalAlpha
                }
                MotionEvent.ACTION_CANCEL -> v.alpha = normalAlpha
            }
            true
        }
    }

    private fun sendExtraKey(keyCode: Int) {
        var metaState = 0
        if (ctrlDown) metaState = metaState or (KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
        if (altDown) metaState = metaState or (KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON)

        val ev = KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, metaState)
        binding.terminalView.onKeyDown(keyCode, ev)

        // Auto-deactivate modifiers after use
        if (ctrlDown) {
            ctrlDown = false
            updateModifierButton(findViewById(R.id.btnCtrl), false)
        }
        if (altDown) {
            altDown = false
            updateModifierButton(findViewById(R.id.btnAlt), false)
        }
    }

    private fun updateModifierButton(
        button: Button,
        active: Boolean,
    ) {
        val bgColor = if (active) R.color.extraKeyActive else R.color.extraKeyDefault
        val txtColor = if (active) R.color.extraKeyActiveText else R.color.extraKeyText
        button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, bgColor))
        button.setTextColor(ContextCompat.getColor(this, txtColor))
    }

    // --- Session tab bar ---

    private fun updateSessionTabs() {
        val tabsLayout = binding.tabsLayout
        tabsLayout.removeAllViews()

        val sessions = sessionManager.getSessionsInfo()
        val density = resources.displayMetrics.density

        for (info in sessions) {
            val tabWrapper = createSessionTab(info, density)
            tabsLayout.addView(tabWrapper)
            if (info["active"] as Boolean) {
                binding.sessionTabBar.post {
                    binding.sessionTabBar.smoothScrollTo(tabWrapper.left, 0)
                }
            }
        }

        tabsLayout.addView(createAddButton(density))
    }

    private fun createSessionTab(
        info: Map<String, Any>,
        density: Float,
    ): LinearLayout {
        val id = info["id"] as String
        val name = info["name"] as String
        val active = info["active"] as Boolean
        val finished = info["finished"] as Boolean

        val tabWrapper =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    LinearLayout
                        .LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.MATCH_PARENT,
                        ).apply {
                            marginEnd = (TAB_MARGIN_DP * density).toInt()
                        }
                val bgColor =
                    if (active) {
                        R.color.tabActiveBackground
                    } else {
                        R.color.tabInactiveBackground
                    }
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, bgColor))
                isFocusable = false
                isFocusableInTouchMode = false
            }

        val tabContent = createTabContent(name, active, finished, id, density)
        val indicator = createTabIndicator(active, density)

        tabWrapper.addView(tabContent)
        tabWrapper.addView(indicator)
        tabWrapper.setOnClickListener {
            sessionManager.switchSession(id)
            binding.terminalView.requestFocus()
        }

        return tabWrapper
    }

    private fun createTabContent(
        name: String,
        active: Boolean,
        finished: Boolean,
        id: String,
        density: Float,
    ): LinearLayout {
        val tabContent =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val hPad = (TAB_HPAD_DP * density).toInt()
                val vPad = (TAB_VPAD_DP * density).toInt()
                setPadding(hPad, vPad, (TAB_CLOSE_PAD_DP * density).toInt(), vPad)
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        0,
                        1f,
                    )
                isFocusable = false
                isFocusableInTouchMode = false
            }

        val nameView =
            TextView(this).apply {
                text = name
                textSize = TAB_NAME_TEXT_SIZE
                val textColor =
                    when {
                        finished -> R.color.tabTextFinished
                        active -> R.color.tabTextPrimary
                        else -> R.color.tabTextSecondary
                    }
                setTextColor(ContextCompat.getColor(this@MainActivity, textColor))
                if (finished) setTypeface(typeface, Typeface.ITALIC)
                isSingleLine = true
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
            }

        val closeView =
            TextView(this).apply {
                text = "\u00D7"
                textSize = TAB_CLOSE_TEXT_SIZE
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.tabTextSecondary))
                setPadding((TAB_CLOSE_PAD_DP * density).toInt(), 0, 0, 0)
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                isFocusable = false
                isFocusableInTouchMode = false
                setOnClickListener { closeSessionFromTab(id) }
            }

        tabContent.addView(nameView)
        tabContent.addView(closeView)
        return tabContent
    }

    private fun createTabIndicator(
        active: Boolean,
        density: Float,
    ): View =
        View(this).apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (INDICATOR_HEIGHT_DP * density).toInt(),
                )
            val color = if (active) R.color.tabAccent else android.R.color.transparent
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, color))
        }

    private fun createAddButton(density: Float): TextView =
        TextView(this).apply {
            text = "+"
            textSize = TAB_ADD_TEXT_SIZE
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.tabTextSecondary))
            val pad = (TAB_ADD_PAD_DP * density).toInt()
            setPadding(pad, 0, pad, 0)
            gravity = Gravity.CENTER
            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                )
            isFocusable = false
            isFocusableInTouchMode = false
            setOnClickListener {
                sessionManager.createSession()
                binding.terminalView.requestFocus()
            }
        }

    private fun closeSessionFromTab(handleId: String) {
        if (sessionManager.sessionCount <= 1) {
            // Create new session first, then close the old one
            sessionManager.createSession()
        }
        sessionManager.closeSession(handleId)
        binding.terminalView.requestFocus()
    }

    // --- Terminal session callbacks ---

    private inner class OpenClawSessionClient : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            binding.terminalView.onScreenUpdated()
        }

        override fun onTitleChanged(changedSession: TerminalSession) {
            // Update tab bar when title changes
            runOnUiThread { updateSessionTabs() }
            // title changes propagated via EventBridge
        }

        override fun onSessionFinished(finishedSession: TerminalSession) {
            sessionManager.onSessionFinished(finishedSession)
        }

        override fun onCopyTextToClipboard(
            session: TerminalSession,
            text: String,
        ) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("OpenClaw", text))
        }

        override fun onPasteTextFromClipboard(session: TerminalSession?) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.text ?: return
            session?.write(text.toString())
        }

        override fun onBell(session: TerminalSession) = Unit

        override fun onColorsChanged(session: TerminalSession) = Unit

        override fun onTerminalCursorStateChange(state: Boolean) = Unit

        override fun setTerminalShellPid(
            session: TerminalSession,
            pid: Int,
        ) = Unit

        override fun getTerminalCursorStyle(): Int = 0

        override fun logError(
            tag: String,
            message: String,
        ) {
            AppLogger.e(tag, message)
        }

        override fun logWarn(
            tag: String,
            message: String,
        ) {
            AppLogger.w(tag, message)
        }

        override fun logInfo(
            tag: String,
            message: String,
        ) {
            AppLogger.i(tag, message)
        }

        override fun logDebug(
            tag: String,
            message: String,
        ) {
            AppLogger.d(tag, message)
        }

        override fun logVerbose(
            tag: String,
            message: String,
        ) {
            AppLogger.v(tag, message)
        }

        override fun logStackTraceWithMessage(
            tag: String,
            message: String,
            e: Exception,
        ) {
            AppLogger.e(tag, message, e)
        }

        override fun logStackTrace(
            tag: String,
            e: Exception,
        ) {
            AppLogger.e(tag, "Exception", e)
        }
    }

    // --- Terminal view callbacks ---

    @Suppress("TooManyFunctions") // Interface implementation requires all methods
    private inner class OpenClawViewClient : TerminalViewClient {
        override fun onScale(scale: Float): Float {
            val currentSize = currentTextSize
            val newSize = if (scale > 1f) currentSize + 1 else currentSize - 1
            val clamped = newSize.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)
            currentTextSize = clamped
            binding.terminalView.setTextSize(clamped)
            return scale
        }

        override fun onSingleTapUp(e: MotionEvent) {
            // Toggle soft keyboard on tap (same as Termux)
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
        }

        override fun shouldBackButtonBeMappedToEscape(): Boolean = false

        override fun shouldEnforceCharBasedInput(): Boolean = true

        override fun getInputMode(): Int = INPUT_MODE_TYPE_NULL

        override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

        override fun isTerminalViewSelected(): Boolean = binding.terminalContainer.visibility == View.VISIBLE

        override fun copyModeChanged(copyMode: Boolean) = Unit

        override fun onKeyDown(
            keyCode: Int,
            e: KeyEvent,
            session: TerminalSession,
        ): Boolean = false

        override fun onKeyUp(
            keyCode: Int,
            e: KeyEvent,
        ): Boolean = false

        override fun onLongPress(event: MotionEvent): Boolean = false

        override fun readControlKey(): Boolean {
            val v = ctrlDown
            if (v) {
                ctrlDown = false
                runOnUiThread { updateModifierButton(findViewById(R.id.btnCtrl), false) }
            }
            return v
        }

        override fun readAltKey(): Boolean {
            val v = altDown
            if (v) {
                altDown = false
                runOnUiThread { updateModifierButton(findViewById(R.id.btnAlt), false) }
            }
            return v
        }

        override fun readShiftKey(): Boolean = false

        override fun readFnKey(): Boolean = false

        override fun onCodePoint(
            codePoint: Int,
            ctrlDown: Boolean,
            session: TerminalSession,
        ): Boolean = false

        override fun onEmulatorSet() = Unit

        override fun logError(
            tag: String,
            message: String,
        ) {
            AppLogger.e(tag, message)
        }

        override fun logWarn(
            tag: String,
            message: String,
        ) {
            AppLogger.w(tag, message)
        }

        override fun logInfo(
            tag: String,
            message: String,
        ) {
            AppLogger.i(tag, message)
        }

        override fun logDebug(
            tag: String,
            message: String,
        ) {
            AppLogger.d(tag, message)
        }

        override fun logVerbose(
            tag: String,
            message: String,
        ) {
            AppLogger.v(tag, message)
        }

        override fun logStackTraceWithMessage(
            tag: String,
            message: String,
            e: Exception,
        ) {
            AppLogger.e(tag, message, e)
        }

        override fun logStackTrace(
            tag: String,
            e: Exception,
        ) {
            AppLogger.e(tag, "Exception", e)
        }
    }
}
