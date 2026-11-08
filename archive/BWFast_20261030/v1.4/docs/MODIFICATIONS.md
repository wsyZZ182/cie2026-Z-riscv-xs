# MODIFICATIONS — BWFast Batch24 frozen single-round run

Date: 2026-10-28 (Asia/Shanghai)

## Verdict: NO code, script, RTL, or configuration modification was required.

The frozen 24-config batch ran end-to-end on the pre-provisioned toolchain with
zero source edits. No compile fix, no tool-compat workaround, no algorithm /
interface / pipeline-stage / compute-semantics change was needed.

### Why no fix was needed (environment verified as-is)
- java   : /opt/graalvm-jdk-21/bin/java (GraalVM 21.0.12 LTS) — ok
- sbt    : /home/gingk/toolchain/sbt-launch.jar (1.10.7), `compile` success (27 s)
- firtool: /home/gingk/.cache/llvm-firtool/1.62.1/bin/firtool (Chisel 7.2.0 auto-resolve)
- yosys  : /home/gingk/miniconda3/bin/yosys (0.58, built-in ABC 1.01) — ok
- verilator: /usr/local/bin/verilator (5.048) — ok
- g++/make: /usr/bin (g++ 11.4, make 4.3) — ok

Before/after of every project file: IDENTICAL (see batch_results/source_hashes.json,
unchanged from launch; no entry in this file means no edit).

### Operational note (NOT a project modification)
The very first launch used `nohup ... &` detached from the launching WSL session;
when that wsl client returned, WSL2 idled the VM and reaped the child (run_batch_all.log
was 0 bytes, pid gone). Re-launching was done by holding the wsl client in the
foreground from the Windows side (`wsl bash .../09_run_batch.sh`, client kept attached).
No file under bw_batch24 was changed to work around this; it is purely how the long
batch process was held alive. Per rules, batch_results was not resumed/overwritten —
the prior empty launch produced no batch_results, so no old-results backup was needed.

### Result
- counters 7/15/31 Yosys SAT proofs: all PASS (unit_proofs.json)
- 24/24 variants: PASS_RTL_GENERIC (20 seeds each, generic-gate synth + ltp)
- No FAILED / BLOCKED_* variants. No algorithm-level issue to flag.
- Generic-only metrics reported (comb_cells / reg_bits / ltp length); no library,
  no physical delay / frequency / power conversion.

## 2026-10-29 Phase 0：BWFast 接入香山 yunsuan（B1 路线第一步）
- 文件：XiangShan/yunsuan/src/main/scala/vdot/{BWFast.scala, BatchConfig.scala}（从 bwfast_batch24 复制）
- 修改1：BatchConfig.scala 默认 BatchContext.cfg 由 B 改为 K07（此前默认 B=空配置，非 K07 加速语义）
- 修改2：BWFast.scala firtoolOpts 移除 verification 相关选项（--disable-layers/--disable-opt=Verification；香山 firtool 1.62.1 无该层概念，且 K07 模块无断言/assume，选项无意义）
- 验证：mill yunsuan.compile 通过（Chisel 6.6.0 / Scala 2.13.15 / mill 0.12.3 / GraalVM JDK21）
- 验证：ChiselStage --target firrtl elaboration 成功（BWFastTop.fir.mlir 1.3MB，K07 计数树调度正常，1 个非致命 W003）
- 验证：firtool 1.62.1 lowering 成功（326KB SystemVerilog，0.6s）
- 环境差异：vdot.EmitBatch（ChiselStage stdin 管道方式）在香山 firtool 1.62.1 下不可用（Stream closed，Chisel 6.6.0+1.62.1 管道交互问题）；Phase 1 走香山 make verilog（--dump-fir + --firtool-opt），不受影响
- 待办：Phase 1 VDotFu 嵌入 BWFastTop（E8 走 K07 / E16 E32 回退香山原生）

## 2026-10-29 Phase 1：K07 接入香山整核（B1 路线第二步，整核 DiffTest 通过）
- 文件：XiangShan/yunsuan/src/main/scala/yunsuan/vector/VectorALU/VDotFu.scala（备份 VDotFu.scala.bak-k07-phase1）
- 修改1：import 改 `_root_.vdot.BWFastTop`（规避与 yunsuan.vector._ 的 vdot 子包歧义，编译 ambiguous 修复）
- 修改2：实例化 BWFastTop；E8（sew=0）走 K07 单拍路径，E16/E32 回退香山原生 VDotCore；out.ready 恒真（II=1）
- 修改3（时序修复，关键）：K07 输入改接**当前指令原始输入 io.in.bits**（vs1/vs2 即时按 vl/vm/mask 清零、vdOld/sew 取当前指令），valid 判定 `io.in.bits.vdType(1,0)===0`
  - 根因：锁存值 vs1_r/vsew 在 fire 拍仍是上一条指令（Reg 沿才更新），原实现接锁存值导致 K07 在 fire 沿捕获到上一条指令数据 → 结果错位全零（首轮 DiffTest `v2_low 0x00 vs 0x88`）
  - 原版 VDotCore 纯组合、S2（fire+1）采样时锁存值已更新，故不受影响；K07 是沿捕获，必须直接取当前指令输入
- 重建链：mill yunsuan.compile → make verilog（XSTop.sv）→ make sim-verilog（SimTop.sv 1.16MB）→ make emu（新 emu，SHA256 见 PHASE1-VERIFY.md）
- 整核 DiffTest：700 用例 vdot-test **全部通过**（17209 条指令全提交，64913 周期，无 mismatch / 无 ABORT）
  - e8 正 136 / e8 负 -136 / e8 边界 **2032**（Booth 时代首个失败用例，现通过）/ e8 vl=4 10 / vl=1 5 / vl=0 0x11223344
  - e16 正 36 / e16 负 -36；e32 正 10 / 混合 -128 / 边界 2147483647 / 溢出 0x80000000（vxsat_csr=1 为已知"链路未接"，NEMU 一致口径不写 vcsr）
- 环境备注：WSL 实例构建期间多次重启（~20-40 min 周期，宿主内存充足非 OOM），改 setsid 脱离会话 + 日志写持久路径 + 分段续传（compile/verilog/sim-verilog/emu 四段）
