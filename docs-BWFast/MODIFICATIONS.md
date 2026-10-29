# MODIFICATIONS — BWFast Batch24 frozen single-round run

Date: 2026-10-28 (Asia/Shanghai)
Operator: batch monitor / troubleshooter

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
