#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# replay.sh - menunjukkan proses kompilasi dan eksekusi untuk keperluan
#             perekaman video (screen recording).
#
# Pemakaian:
#     ./tests/replay.sh            # jeda 1.2 detik antar langkah
#     PAUSE=0 ./tests/replay.sh    # tanpa jeda (untuk pipe/redirection)
# ---------------------------------------------------------------------------
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLS="$ROOT/build/classes"
DELAY="${PAUSE:-1.2}"

pause() {
    if [ "$DELAY" != "0" ]; then sleep "$DELAY"; fi
}

banner() {
    echo
    echo "==============================================================="
    echo "  $1"
    echo "==============================================================="
    echo
}

cd "$ROOT"
mkdir -p tests/good/pcode

banner "1. MEMBANGUN KOMPILATOR (JFlex + javac)"
bash compiler/build.sh
pause

banner "2. CONTOH PROGRAM BENAR: deret & aritmetika"
echo "\$ cat tests/good/src/01_arithmetic.src"
cat tests/good/src/01_arithmetic.src
pause
echo
echo "\$ ./compiler/run.sh tests/good/src/01_arithmetic.src"
./compiler/run.sh tests/good/src/01_arithmetic.src
pause

banner "3. KODE MESIN YANG DIHASILKAN"
java -cp "$CLS" Compiler tests/good/src/01_arithmetic.src -o tests/good/pcode/01_arithmetic.pcode -d
pause

banner "4. CONTOH PROGRAM BENAR: rekursi (faktorial)"
echo "\$ ./compiler/run.sh tests/good/src/08_recursion.src"
./compiler/run.sh tests/good/src/08_recursion.src
pause

banner "5. CONTOH PROGRAM BENAR: prosedur & scope bersarang"
echo "\$ ./compiler/run.sh tests/good/src/20_nested_proc.src"
./compiler/run.sh tests/good/src/20_nested_proc.src
pause

banner "6. CONTOH PROGRAM BENAR: masukan dari papan ketik (get)"
echo "\$ echo 42 | ./compiler/run.sh tests/good/src/12_input.src /dev/stdin"
echo 42 | ./compiler/run.sh tests/good/src/12_input.src /dev/stdin
pause

banner "7. DETEKSI KESALAHAN: identifier tak terdeklarasi"
echo "\$ cat tests/bad/src/01_undeclared.src"
cat tests/bad/src/01_undeclared.src
pause
echo
echo "\$ java Compiler tests/bad/src/01_undeclared.src"
java -cp "$CLS" Compiler tests/bad/src/01_undeclared.src -o /tmp/x.pcode || true
pause

banner "8. DETEKSI KESALAHAN: ketidaksesuaian tipe"
echo "\$ cat tests/bad/src/03_assign_type.src"
cat tests/bad/src/03_assign_type.src
pause
echo
echo "\$ java Compiler tests/bad/src/03_assign_type.src"
java -cp "$CLS" Compiler tests/bad/src/03_assign_type.src -o /tmp/x.pcode || true
pause

banner "9. DETEKSI KESALAHAN: jumlah argumen salah"
echo "\$ cat tests/bad/src/11_arg_count.src"
cat tests/bad/src/11_arg_count.src
pause
echo
echo "\$ java Compiler tests/bad/src/11_arg_count.src"
java -cp "$CLS" Compiler tests/bad/src/11_arg_count.src -o /tmp/x.pcode || true
pause

banner "10. FRONT-END JFlex + CUP (parser LALR)"
echo "\$ ./compiler/run_cup.sh tests/good/src/06_array.src"
./compiler/run_cup.sh tests/good/src/06_array.src
pause

banner "SELESAI - seluruh 50 uji dapat dijalankan dengan ./tests/run_all.sh"
echo "          (dan ./tests/run_cup.sh untuk front-end JFlex + CUP)"
