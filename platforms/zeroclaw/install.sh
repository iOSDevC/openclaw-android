#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/scripts/lib.sh"

echo "=== Installing ZeroClaw Platform ==="
echo ""

# ZeroClaw is distributed as a single Go binary from GitHub releases.
GITHUB_REPO="AidanPark/zeroclaw"
ARCH=$(uname -m)
case "$ARCH" in
    aarch64) ARCH_LABEL="arm64" ;;
    x86_64)  ARCH_LABEL="amd64" ;;
    *)       echo -e "${RED}[FAIL]${NC} Unsupported architecture: $ARCH"; exit 1 ;;
esac

echo "Fetching latest ZeroClaw release..."
LATEST_TAG=$(curl -sfL "https://api.github.com/repos/$GITHUB_REPO/releases/latest" | grep '"tag_name"' | sed 's/.*"tag_name": "//;s/".*//')
if [ -z "$LATEST_TAG" ]; then
    echo -e "${RED}[FAIL]${NC} Could not determine latest version"
    exit 1
fi

DOWNLOAD_URL="https://github.com/$GITHUB_REPO/releases/download/$LATEST_TAG/zeroclaw-linux-$ARCH_LABEL"
echo "Downloading ZeroClaw $LATEST_TAG for linux-$ARCH_LABEL..."
echo "  (This may take a moment depending on network speed)"

mkdir -p "$PREFIX/bin"
if curl -sfL "$DOWNLOAD_URL" -o "$PREFIX/bin/zeroclaw"; then
    chmod +x "$PREFIX/bin/zeroclaw"
    echo -e "${GREEN}[OK]${NC}   ZeroClaw $LATEST_TAG installed"
else
    echo -e "${RED}[FAIL]${NC} Download failed"
    exit 1
fi

mkdir -p "$HOME/.zeroclaw"
echo -e "${GREEN}[OK]${NC}   Created data directory ~/.zeroclaw"
