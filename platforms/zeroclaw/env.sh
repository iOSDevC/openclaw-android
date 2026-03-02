#!/usr/bin/env bash
# env.sh — ZeroClaw platform environment variables
# Called by setup-env.sh; stdout is inserted into .bashrc block.
# ZeroClaw is a standalone binary — minimal env needed.

cat << 'EOF'
export ZEROCLAW_HOME="$HOME/.zeroclaw"
EOF
