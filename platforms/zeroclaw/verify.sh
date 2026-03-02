#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/scripts/lib.sh"

PASS=0
FAIL=0
WARN=0

check_pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
    PASS=$((PASS + 1))
}

check_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    FAIL=$((FAIL + 1))
}

check_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
    WARN=$((WARN + 1))
}

echo "=== ZeroClaw Platform Verification ==="
echo ""

if command -v zeroclaw &>/dev/null; then
    ZC_VER=$(zeroclaw --version 2>/dev/null || true)
    if [ -n "$ZC_VER" ]; then
        check_pass "zeroclaw $ZC_VER"
    else
        check_fail "zeroclaw found but --version failed"
    fi
else
    check_fail "zeroclaw command not found"
fi

if [ -d "$HOME/.zeroclaw" ]; then
    check_pass "Directory $HOME/.zeroclaw exists"
else
    check_fail "Directory $HOME/.zeroclaw missing"
fi

echo ""
echo "==============================="
echo -e "  Results: ${GREEN}$PASS passed${NC}, ${RED}$FAIL failed${NC}, ${YELLOW}$WARN warnings${NC}"
echo "==============================="

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
exit 0
