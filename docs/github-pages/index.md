---
layout: default
title: GitHub Pages Setup
---

# GitHub Pages Setup

[Ver esta guia en Espanol](./index.es)

This repository already contains a `docs/` folder that can be published as a GitHub Pages site.

## Recommended Setup

GitHub's documentation says a repository Pages site can publish from either the repository root or the `/docs` folder on a branch. For this project, publish from `main` and `/docs`.

Official references:

- [Creating a GitHub Pages site](https://docs.github.com/en/pages/getting-started-with-github-pages/creating-a-github-pages-site)
- [Configuring a publishing source for your GitHub Pages site](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site)

## Steps

1. Push this repository to GitHub.
2. Open the repository on GitHub.
3. Go to `Settings` > `Pages`.
4. Under `Build and deployment`, set `Source` to `Deploy from a branch`.
5. Select branch `main`.
6. Select folder `/docs`.
7. Save and wait for the first deployment to finish.

GitHub Pages looks for an entry file at the top level of the publishing source. This repository provides `docs/index.md`, which becomes the home page for the site.

## Resulting URLs

For a project site, the URLs will look like this:

```text
https://<owner>.github.io/<repo>/
https://<owner>.github.io/<repo>/install
https://<owner>.github.io/<repo>/update
```

For this repository, the expected URLs are:

```text
https://iosdevc.github.io/openclaw-android/
https://iosdevc.github.io/openclaw-android/install
https://iosdevc.github.io/openclaw-android/update
```

## Files Used By The Site

- `docs/index.md`: landing page
- `docs/install`: install endpoint for `curl | bash`
- `docs/update`: update endpoint for `curl | bash`
- `docs/github-pages/index.md`: this guide

## Maintenance Notes

- `docs/install` must stay identical to `bootstrap.sh`
- `docs/update` must stay identical to `update.sh`
- `.github/workflows/code-quality.yml` now checks both pairs in CI

## Notes For Forks

If you fork this project and publish your own Pages site, update README examples if you want copy-paste commands to point to your fork instead of `iosdevc.github.io/openclaw-android`.
