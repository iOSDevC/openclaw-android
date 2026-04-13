import { useEffect, useState } from 'react'
import { bridge } from '../lib/bridge'
import { useRoute } from '../lib/router'
import { useNativeEvent } from '../lib/useNativeEvent'
import { t, getLocale, setLocale, availableLocales } from '../i18n'

interface MenuItem {
  icon: string
  label: string
  desc: string
  route: string
  badge?: boolean
}

function getMenu(): MenuItem[] {
  return [
    { icon: '📱', label: t('settings_platforms'), desc: t('settings_platforms_desc'), route: '/settings/platforms' },
    { icon: '🔄', label: t('settings_updates'), desc: t('settings_updates_desc'), route: '/settings/updates', badge: false },
    { icon: '⚡', label: t('settings_keep_alive'), desc: t('settings_keep_alive_desc'), route: '/settings/keep-alive' },
    { icon: '💾', label: t('settings_storage'), desc: t('settings_storage_desc'), route: '/settings/storage' },
    { icon: 'ℹ️', label: t('settings_about'), desc: t('settings_about_desc'), route: '/settings/about' },
  ]
}

export function Settings() {
  const { navigate } = useRoute()
  const [privilegeStatus, setPrivilegeStatus] = useState<{ privileged?: boolean; remainingMs?: number }>({})

  useEffect(() => {
    const status = bridge.callJson<{ privileged?: boolean; remainingMs?: number }>('getPrivilegeStatus')
    if (status) setPrivilegeStatus(status)
  }, [])

  useNativeEvent('privilege_status', (data) => {
    setPrivilegeStatus((data ?? {}) as { privileged?: boolean; remainingMs?: number })
  })

  const countdown = formatRemainingTime(privilegeStatus.remainingMs || 0)

  return (
    <div className="page">
      <div className="page-title" style={{ marginBottom: 24 }}>{t('settings_title')}</div>
      <div className="card" style={{ marginBottom: 16 }}>
        <div className="card-row" style={{ alignItems: 'flex-start' }}>
          <span className="card-icon">🔐</span>
          <div className="card-content">
            <div className="card-label">Advanced Mode</div>
            <div className="card-desc">
              {privilegeStatus.privileged
                ? `Privileged features are unlocked for ${countdown}. They turn off automatically when the app backgrounds or the timer expires.`
                : 'Secure mode is active. Command injection, shell execution, installs, and updates stay locked until you authenticate.'}
            </div>
          </div>
          {privilegeStatus.privileged ? (
            <button className="btn btn-danger btn-small" onClick={() => bridge.call('disableAdvancedMode')}>
              Disable
            </button>
          ) : (
            <button className="btn btn-primary btn-small" onClick={() => bridge.call('requestAdvancedMode')}>
              Enable
            </button>
          )}
        </div>
      </div>

      {/* Language selector */}
      <div className="card" style={{ marginBottom: 16 }}>
        <div className="card-row">
          <span className="card-icon">🌐</span>
          <div className="card-content">
            <div className="card-label">Language</div>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            {availableLocales.map(loc => (
              <button
                key={loc.code}
                className={`btn ${getLocale() === loc.code ? 'btn-primary' : ''}`}
                style={{ padding: '4px 12px', fontSize: 13 }}
                onClick={() => { setLocale(loc.code); window.location.reload() }}
              >
                {loc.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {getMenu().map(item => (
        <div key={item.route} className="card" onClick={() => navigate(item.route)}>
          <div className="card-row">
            <span className="card-icon">{item.icon}</span>
            <div className="card-content">
              <div className="card-label">{item.label}</div>
              <div className="card-desc">{item.desc}</div>
            </div>
            {item.badge && <span className="card-badge" />}
            <span className="card-chevron">›</span>
          </div>
        </div>
      ))}
    </div>
  )
}

function formatRemainingTime(remainingMs: number) {
  const totalSeconds = Math.max(0, Math.floor(remainingMs / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}
