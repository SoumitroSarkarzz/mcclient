#!/bin/sh
set -eu
cd "$(dirname "$0")"
if ! python3 -c 'import sys, tkinter; assert sys.version_info >= (3, 11)' 2>/dev/null; then
    echo 'Install Python 3.11+ with Tk from https://www.python.org/downloads/macos/ first.'
    printf 'Press Enter to close.'
    read -r response
    exit 1
fi
if [ ! -x .venv/bin/python ]; then
    python3 -m venv .venv
fi
.venv/bin/python -m pip install .
exec .venv/bin/mcprofiles
