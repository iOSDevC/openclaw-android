---
layout: default
title: OpenClaw on Android
---

# OpenClaw on Android

This repository can publish a small GitHub Pages site from the `docs/` folder.

## Languages

- [English README](../README.md)
- [README en Español](../README.es.md)
- [GitHub Pages setup guide](./github-pages/)
- [Guia de GitHub Pages en Espanol](./github-pages/index.es)

## Install

For this repository, the GitHub Pages install endpoint is:

```bash
curl -sL https://iosdevc.github.io/openclaw-android/install | bash && source ~/.bashrc
```

## Update

If `oa` is not available yet, you can also run the update wrapper from GitHub Pages:

```bash
curl -sL https://iosdevc.github.io/openclaw-android/update | bash && source ~/.bashrc
```

## Publish Your Own Fork

If you publish a fork with GitHub Pages, replace the owner and repository in the URL:

```text
https://<owner>.github.io/<repo>/install
https://<owner>.github.io/<repo>/update
```

If your repository itself is named `<owner>.github.io`, the project path is omitted:

```text
https://<owner>.github.io/install
https://<owner>.github.io/update
```

## Docs

- [GitHub Pages setup guide](./github-pages/)
- [Guia de GitHub Pages en Espanol](./github-pages/index.es)
- [README](../README.md)
- [README en Espanol](../README.es.md)
- [Keeping Processes Alive](./disable-phantom-process-killer.md)
- [Mantener procesos activos](./disable-phantom-process-killer.es.md)
- [Guia SSH de Termux](./termux-ssh-guide.es.md)
- [Troubleshooting](./troubleshooting.md)
- [Solucion de problemas](./troubleshooting.es.md)
