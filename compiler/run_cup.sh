#!/usr/bin/env bash
# Compile and run one program with the JFlex + CUP front end.
#   ./compiler/run_cup.sh tests/good/src/07_factorial.src [input-file]
#
# Generated code goes to the "pcode_cup" directory next to "src".
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLS="$ROOT/build/classes"
CUPJAR="$ROOT/compiler/lib/java-cup-11b.jar"
SRC="$1"
IN="$2"

if [ -z "$SRC" ]; then
    echo "usage: $0 <source.src> [input-file]" >&2
    exit 2
fi

SRCDIR="$(dirname "$SRC")"
if [ "$(basename "$SRCDIR")" = "src" ]; then
    OUTDIR="$(dirname "$SRCDIR")/pcode_cup"
else
    OUTDIR="$SRCDIR"
fi
mkdir -p "$OUTDIR"
PCODE="$OUTDIR/$(basename "${SRC%.*}").pcode"

java -cp "$CLS:$CUPJAR" CupCompiler "$SRC" -o "$PCODE"

if [ -n "$IN" ]; then
    java -cp "$CLS" VM "$PCODE" < "$IN"
else
    java -cp "$CLS" VM "$PCODE"
fi
