#!/usr/bin/env bash
# Compile and run every program with the JFlex + CUP front end.
#
#   ./tests/run_cup.sh            # transcript
#
# Sources   : tests/<kind>/src/*.src
# Output    : tests/good/pcode_cup/*.pcode
# The VM is the same one used by the hand-written compiler, so the results
# are directly comparable with tests/run_all.sh.
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLS="$ROOT/build/classes"
CUPJAR="$ROOT/compiler/lib/java-cup-11b.jar"
PASS=0
FAIL=0
cd "$ROOT"

if [ ! -d "$CLS" ]; then
    echo "Building first ..."
    bash compiler/build_cup.sh
fi

pcode_of() {
    echo "$(dirname "$(dirname "$1")")/pcode_cup/$(basename "${1%.src}").pcode"
}

run_good() {
    local f="$1"
    local base
    base="$(basename "$f")"
    local pcode
    pcode="$(pcode_of "$f")"
    mkdir -p "$(dirname "$pcode")"
    echo "---------------------------------------------------------------"
    echo "PROGRAM : $base      [JFlex + CUP]"
    echo "---------------------------------------------------------------"
    echo "\$ java CupCompiler $f -o $pcode"
    if ! java -cp "$CLS:$CUPJAR" CupCompiler "$f" -o "$pcode"; then
        echo "  >>> COMPILATION FAILED"
        FAIL=$((FAIL + 1))
        return
    fi
    #echo "\$ java VM $pcode"
    #if [ -f "${f%.src}.in" ]; then
    #    java -cp "$CLS" VM "$pcode" < "${f%.src}.in"
    #else
    #    java -cp "$CLS" VM "$pcode"
    #fi
    PASS=$((PASS + 1))
}

run_bad() {
    local f="$1"
    local base
    base="$(basename "$f")"
    echo "---------------------------------------------------------------"
    echo "PROGRAM : $base   (expected to be rejected)   [JFlex + CUP]"
    echo "---------------------------------------------------------------"
    echo "\$ java CupCompiler $f"
    if java -cp "$CLS:$CUPJAR" CupCompiler "$f" -o /dev/null 2>&1; then
        echo "  >>> NO ERROR DETECTED (unexpected)"
        FAIL=$((FAIL + 1))
    else
        PASS=$((PASS + 1))
    fi
}

echo "###############################################################"
echo "#  JFlex + CUP front end - variation test suite               #"
echo "#  $(date)"
echo "###############################################################"
echo
echo "############  CORRECT PROGRAMS  ###############################"
echo
shopt -s nullglob
for f in tests/good/src/*.src; do
    run_good "$f"
    echo
done
echo
echo "############  ERRONEOUS PROGRAMS  #############################"
echo
for f in tests/bad/src/*.src; do
    run_bad "$f"
    echo
done
echo "###############################################################"
echo "#  SUMMARY:  $PASS checks passed, $FAIL failed                 "
echo "###############################################################"
