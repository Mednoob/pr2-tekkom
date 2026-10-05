#!/usr/bin/env bash
# Compile and run every good program and compile every bad program,
# printing a transcript to stdout.
#
# Sources live in  tests/<kind>/src/*.src
# Generated code in tests/<kind>/pcode/*.pcode
#
#   ./tests/run_all.sh            # transcript
#   ./tests/run_all.sh > out.txt  # save transcript
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLS="$ROOT/build/classes"
PASS=0
FAIL=0
cd "$ROOT"

if [ ! -d "$CLS" ]; then
    echo "Building compiler first ..."
    bash compiler/build.sh
fi

# Map tests/good/src/x.src -> tests/good/pcode/x.pcode
pcode_of() {
    echo "$(dirname "$(dirname "$1")")/pcode/$(basename "${1%.src}").pcode"
}

run_good() {
    local f="$1"
    local base
    base="$(basename "$f")"
    local pcode
    pcode="$(pcode_of "$f")"
    mkdir -p "$(dirname "$pcode")"
    echo "---------------------------------------------------------------"
    echo "PROGRAM : $base"
    echo "---------------------------------------------------------------"
    echo "\$ cat $f"
    cat "$f"
    echo
    echo "\$ java Compiler $f -o $pcode"
    if ! java -cp "$CLS" Compiler "$f" -o "$pcode"; then
        echo "  >>> COMPILATION FAILED"
        FAIL=$((FAIL + 1))
        return
    fi
    echo "\$ java VM $pcode"
    if [ -f "${f%.src}.in" ]; then
        java -cp "$CLS" VM "$pcode" < "${f%.src}.in"
    else
        java -cp "$CLS" VM "$pcode"
    fi
    PASS=$((PASS + 1))
}

run_bad() {
    local f="$1"
    local base
    base="$(basename "$f")"
    echo "---------------------------------------------------------------"
    echo "PROGRAM : $base   (expected to be rejected)"
    echo "---------------------------------------------------------------"
    echo "\$ cat $f"
    cat "$f"
    echo
    echo "\$ java Compiler $f"
    if java -cp "$CLS" Compiler "$f" -o /dev/null 2>&1; then
        echo "  >>> NO ERROR DETECTED (unexpected)"
        FAIL=$((FAIL + 1))
    else
        PASS=$((PASS + 1))
    fi
}

echo "###############################################################"
echo "#  Compiler Engineering - variation test suite                #"
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
