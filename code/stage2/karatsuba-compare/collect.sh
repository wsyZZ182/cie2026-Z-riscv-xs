#!/bin/bash
# ============================================================================
# collect.sh —— 采集 8 个乘法器的算法级面积与延迟（yosys/abc 原生、可复现）
#   面积：完整 synth(默认 abc 内置库) 后各类 generic 门计数（外部加权为等效门）
#   延迟：synth -noabc 写 BLIF，abc print_stats 给出 AIG 关键路径深度 lev
# 所有设计使用相同源集合与命令，保证公平。
# ============================================================================
cd /root/karatsuba-compare || exit 1
SRC="rtl/mul_pkg.sv rtl/mul_booth.sv rtl/mul_school16.sv rtl/mul_kar16.sv rtl/mul_school32.sv rtl/mul_kar32.sv rtl/tops.sv"
TOPS="TopNative16 TopBooth16 MulSchool16 MulKar16 TopNative32 TopBooth32 MulSchool32 MulKar32"
for TOP in $TOPS; do
  echo "########## $TOP ##########"
  echo "-- gates(full synth) --"
  yosys -p "read_verilog -sv $SRC; synth -top $TOP -flatten; stat" 2>&1 | grep -E '^[[:space:]]+\$_'
  echo "-- aig lev --"
  yosys -p "read_verilog -sv $SRC; synth -top $TOP -flatten -noabc; write_blif /tmp/$TOP.blif" >/dev/null 2>&1
  yosys-abc -c "read_blif /tmp/$TOP.blif; print_stats" 2>&1 | grep -oE 'lev = [0-9]+'
done
echo "COLLECT_DONE"
