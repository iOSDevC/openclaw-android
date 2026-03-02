#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../../scripts/lib.sh"

echo ""
echo -e "${BOLD}Platform Components${NC}"

if command -v zeroclaw &>/dev/null; then
    zc_status="stopped"
    if pgrep -f "zeroclaw" &>/dev/null; then
        zc_status="running"
    fi
    echo "  ZeroClaw:    $(zeroclaw --version 2>/dev/null || echo 'installed') ($zc_status)"
else
    echo -e "  ZeroClaw:    ${RED}not installed${NC}"
fi

echo ""
echo -e "${BOLD}Disk${NC}"
if [ -d "$PROJECT_DIR" ]; then
    echo "  ~/.openclaw-android:  $(du -sh "$PROJECT_DIR" 2>/dev/null | cut -f1)"
fi
if [ -d "$HOME/.zeroclaw" ]; then
    echo "  ~/.zeroclaw:          $(du -sh "$HOME/.zeroclaw" 2>/dev/null | cut -f1)"
fi
AVAIL_MB=$(df "${PREFIX:-/}" 2>/dev/null | awk 'NR==2 {print int($4/1024)}') || true
echo "  Available:            ${AVAIL_MB:-unknown}MB"
