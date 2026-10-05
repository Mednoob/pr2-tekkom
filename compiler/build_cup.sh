#!/usr/bin/env bash
# Build the JFlex + CUP front end (and the shared VM / hand-written compiler).
#   ./compiler/build_cup.sh
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/compiler/src"
CUP="$ROOT/compiler/cup"
GEN="$ROOT/build/cupgen"
OUT="$ROOT/build/classes"

JFLEX="$ROOT/compiler/lib/jflex-full-1.9.1.jar"
CUPJAR="$ROOT/compiler/lib/java-cup-11b.jar"

if [ ! -f "$JFLEX" ]; then
    echo "JFlex jar not found: $JFLEX" >&2
    exit 1
fi
if [ ! -f "$CUPJAR" ]; then
    echo "CUP jar not found: $CUPJAR" >&2
    exit 1
fi

mkdir -p "$GEN" "$OUT"

echo "[1/4] generating the JFlex lexer for the hand-written compiler ..."
java -jar "$JFLEX" -d "$SRC" "$SRC/Lexer.flex" >/dev/null

echo "[2/4] generating the JFlex lexer for CUP ..."
java -jar "$JFLEX" -d "$GEN" "$CUP/Yylex.flex" >/dev/null

echo "[3/4] generating the CUP parser ..."
java -cp "$CUPJAR" java_cup.Main \
    -destdir "$GEN" -parser CupParser -symbols CupSym \
    "$CUP/Parser.cup" >/dev/null

echo "[4/4] compiling Java sources ..."
javac -cp "$CUPJAR" -d "$OUT" \
    "$SRC"/*.java "$CUP"/*.java "$GEN"/*.java

echo "Build OK -> $OUT"
echo "Run:  java -cp $OUT CupCompiler <source.src>"
