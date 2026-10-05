#!/usr/bin/env bash
# Compile and run one program.
#   ./compiler/run.sh tests/good/src/hello.src [input-file]
#
# The generated machine code (.pcode) is placed in the "pcode" directory
# that is a sibling of the source's "src" directory, so sources and
# generated code stay separated:
#
#   tests/good/src/x.src   ->  tests/good/pcode/x.pcode
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLS="$ROOT/build/classes"
SRC="$1"
IN="$2"

if [ -z "$SRC" ]; then
    echo "usage: $0 <source.src> [input-file]" >&2
    exit 2
fi

SRCDIR="$(dirname "$SRC")"
if [ "$(basename "$SRCDIR")" = "src" ]; then
    OUTDIR="$(dirname "$SRCDIR")/pcode"
else
    OUTDIR="$SRCDIR"
fi
mkdir -p "$OUTDIR"
PCODE="$OUTDIR/$(basename "${SRC%.*}").pcode"

java -cp "$CLS" Compiler "$SRC" -o "$PCODE"

if [ -n "$IN" ]; then
    java -cp "$CLS" VM "$PCODE" < "$IN"
else
    java -cp "$CLS" VM "$PCODE"
fi
