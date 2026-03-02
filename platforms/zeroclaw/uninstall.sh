#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../../scripts/lib.sh"

echo "=== Removing ZeroClaw Platform ==="
echo ""

step() {
    echo ""
    echo -e "${BOLD}[$1/3] $2${NC}"
    echo "----------------------------------------"
}

step 1 "ZeroClaw binary"
if [ -f "$PREFIX/bin/zeroclaw" ]; then
    if pgrep -f "zeroclaw" &>/dev/null; then
        pkill -f "zeroclaw" || true
        echo -e "${GREEN}[OK]${NC}   Stopped running ZeroClaw"
    fi
    rm -f "$PREFIX/bin/zeroclaw"
    echo -e "${GREEN}[OK]${NC}   Removed zeroclaw binary"
else
    echo -e "${YELLOW}[SKIP]${NC} zeroclaw binary not found"
fi

step 2 "ZeroClaw data"
if [ -d "$HOME/.zeroclaw" ]; then
    reply=""
    read -rp "Remove ZeroClaw data directory (~/.zeroclaw)? [y/N] " reply < /dev/tty
    if [[ "$reply" =~ ^[Yy]$ ]]; then
        rm -rf "$HOME/.zeroclaw"
        echo -e "${GREEN}[OK]${NC}   Removed ~/.zeroclaw"
    else
        echo -e "${YELLOW}[KEEP]${NC} Keeping ~/.zeroclaw"
    fi
else
    echo -e "${YELLOW}[SKIP]${NC} ~/.zeroclaw not found"
fi

step 3 "ZeroClaw temporary files"
if [ -d "${PREFIX:-}/tmp/zeroclaw" ]; then
    rm -rf "${PREFIX:-}/tmp/zeroclaw"
    echo -e "${GREEN}[OK]${NC}   Removed ${PREFIX:-}/tmp/zeroclaw"
else
    echo -e "${YELLOW}[SKIP]${NC} ${PREFIX:-}/tmp/zeroclaw not found"
fi
