#!/usr/bin/env bash
set -uo pipefail
B=/home/xiangshan/k07_dual_xs_difftest_20261106/build9
BIN=/home/xiangshan/projects/xs-env/nexus-am/apps/k07-wave/build/k07-wave-riscv64-xs.bin
REF=/home/xiangshan/projects/xs-env/NEMU/build/riscv64-nemu-interpreter-so
OUT=/home/xiangshan/k07_wave_20261108
cd "$B" || exit 1
./emu -i "$BIN" --diff="$REF" --dump-wave-full --wave-path="$OUT/vdot_win.vcd" \
  --log-begin=2690 --log-end=2745 > "$OUT/dump3.log" 2>&1
echo "exit=$?"
ls -lh "$OUT/vdot_win.vcd" 2>/dev/null
echo "--- 时间范围 ---"
grep -a -m1 '^#' "$OUT/vdot_win.vcd"
tail -c 200000 "$OUT/vdot_win.vcd" | grep -a '^#' | tail -1
