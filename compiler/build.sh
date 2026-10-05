#!/usr/bin/env bash
# Full build: hand-written (JFlex) compiler + JFlex/CUP front end.
#   ./compiler/build.sh
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec bash "$ROOT/compiler/build_cup.sh"
