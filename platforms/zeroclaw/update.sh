#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../../scripts/lib.sh"

echo "=== Updating ZeroClaw Platform ==="
echo ""

GITHUB_REPO="AidanPark/zeroclaw"
ARCH=$(uname -m)
case "$ARCH" in
    aarch64) ARCH_LABEL="arm64" ;;
    x86_64)  ARCH_LABEL="amd64" ;;
    *)       echo -e "${RED}[FAIL]${NC} Unsupported architecture: $ARCH"; exit 1 ;;
esac

CURRENT_VER=$(zeroclaw --version 2>/dev/null || echo "")
LATEST_TAG=$(curl -sfL "https://api.github.com/repos/$GITHUB_REPO/releases/latest" | grep '"tag_name"' | sed 's/.*"tag_name": "//;s/".*//' || echo "")

if [ -z "$LATEST_TAG" ]; then
    echo -e "${YELLOW}[WARN]${NC} Could not check latest version"
    return 0 2>/dev/null || exit 0
fi

if [ -n "$CURRENT_VER" ] && [ "$CURRENT_VER" = "$LATEST_TAG" ]; then
    echo -e "${GREEN}[OK]${NC}   ZeroClaw $CURRENT_VER is already the latest"
else
    echo "Updating ZeroClaw... ($CURRENT_VER → $LATEST_TAG)"
    DOWNLOAD_URL="https://github.com/$GITHUB_REPO/releases/download/$LATEST_TAG/zeroclaw-linux-$ARCH_LABEL"
    if curl -sfL "$DOWNLOAD_URL" -o "$PREFIX/bin/zeroclaw"; then
        chmod +x "$PREFIX/bin/zeroclaw"
        echo -e "${GREEN}[OK]${NC}   ZeroClaw updated to $LATEST_TAG"
    else
        echo -e "${YELLOW}[WARN]${NC} Update failed (non-critical)"
    fi
fi
