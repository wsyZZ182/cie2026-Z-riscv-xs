# BWFast / K07 历史实现归档（2026-10）

> 归档时间：2026-11-06。来源仓库 commit `cca38addc4ffa09c7e2ae6acc489b4ca8e6e4aef`（main）。
> 本目录是 2026-10 那套 BWFast（K07 前身）实现的完整快照，**不再是交付版本**。
> 2026-11-08 按 `docs/stage2/2.1-vdot-vv-设计文档.md` 的修订记录（v1.0–v1.6）重组为版本目录。

## 为什么归档

项目最终版本已确定为双模式 `K07.sv`：

| 项 | 值 |
|---|---|
| 路径 | `01_K07/rtl/K07.sv`（交接包 v2，`code/stage2/K07_TEAM_HANDOFF_20261106_v2.zip`） |
| SHA256 | `b854caedd3d9847cd7f6ea569bfb4439f4ad73e0aa185449aaf82bb15ac0b0a8` |
| 面积 | 671.18 µm²（K07Reg 含壳 819.37 µm²） |
| 时序（reg→reg） | 动态 2128.0 ps / E8 1759.5 ps / E16 1737.0 ps |

口径与对比见仓库根 `docs/stage3/3.3-延迟加速比分析.md`。本目录 `v1.4/rtl/BWFastTop.sv` 是 10 月窄 E8 版的生成物，已被取代。

## 版本对照

| 版本 | 日期 | 主要内容 | 本目录归档 |
|---|---|---|---|
| v1.0 | 2026-09-11 | 初稿 | — |
| v1.1 | 2026-09-12 | 编码 funct3 修正、乘法器改型、接入 VIPU（9 处） | — |
| v1.2 | 2026-09-14 | Fused ReduceAcc、vxsat 语义修正、13 用例 | — |
| v1.3 | 2026-10-09 | Karatsuba 对比结论、面积优化方向调整 | — |
| **v1.4** | 2026-10-29 | K07 整模块嵌入（48 版综合 → K07 胜出） | `v1.4/` |
| **v1.4 定稿** | 2026-11-07 | E16 两笔、整核 13/13、第 15 章并入 §7–§13 | `v1.4-final/` |
| v1.5 | 2026-11-08 | 触发器口径拆分、背靠背明细、证据缺口 | — |
| v1.6 | 2026-11-08 | 并入 2.2b 窄 E8 对照件 | — |

- **v1.0–v1.3** 的实现（`VDotCore`）在 `code/stage2/`，不在本归档范围。
- **v1.5 / v1.6** 为设计文档修订，无独立归档文件。

## 内容

| 目录 | 内容 |
|---|---|
| `v1.4/src/` | K07 Chisel 源码：`BWFast.scala`、`BatchConfig.scala`、`CounterProof.scala`、`build.sbt`、`project/build.properties` |
| `v1.4/rtl/` | 生成 RTL：`BWFastTop.sv`、`SharedBWFrontend.sv`、`filelist.f` |
| `v1.4/synth/` | Yosys 通用门级综合产物：`netlist.v`、`netlist.json`（6.8 MB）、`stat.txt`、`synth.ys`、`ltp.txt` |
| `v1.4/results/` | `config.json`、`result.json`（K07：`PASS_RTL_GENERIC`，11,454 组合单元 / 621 寄存器位 / 深度 65） |
| `v1.4/docs/` | 实验说明（`README.md`）、批处理记录（`MODIFICATIONS.md`） |
| `v1.4-final/PHASE1-VERIFY.md` | Phase 1 整核 DiffTest（700 用例；E8 走 K07，E16/E32 回退 `VDotCore`） |
| `v1.4-final/K07_交接包修正_20261106.md` | K07 交接包三处修正说明（2026-11-06） |
| `v1.4-final/K07_交接包修正_20261106.md` | K07 交接包三处修正说明（2026-11-06，原在 `docs/description/`） |

原始 6 目录合计 40 个文件。其中 23 个 `*.log` 按 `.gitignore` 的 `*.log` 规则**不进入 git 版本库**，只存在于本目录与 tar 包；完整清单见 `SHA256SUMS`。

## 构建脚手架（补齐件，非原始提交内容）

来源：`~/K07_E8_PRIORITY/k07_e8_priority/shared/`（同版本 Chisel 工程）。**该脚手架未实测编译**（需要 Maven 缓存或网络），首次使用请保留完整日志。

重建（在 `v1.4/src/` 下）：

    java -jar $HOME/toolchain/sbt-launch.jar compile
    # 或 PATH 里有 sbt 时直接：sbt compile
    # 生成 SV 需 firtool，例如：
    # firtool --format=sv --split-verilog -o ../rtl-rebuild $(find target -name "*.fir")

## 恢复 / 单独存放

仓库外另有一份 tar 包：`~/archive_BWFast_20261030.tar.zst`。

    tar -I zstd -xf ~/archive_BWFast_20261030.tar.zst -C /tmp/bwfast_restore

## 已知过时点（保留历史原样，未修改原数值）

1. `v1.4/results/result.json` 的 `sta_status` 是旧版通用综合口径；K07 的实际时序/面积见仓库根 `docs/stage3/3.3-延迟加速比分析.md` §一。
2. `v1.4/docs/README.md` 第 9 行「本环境没有 sbt / Verilator / Yosys」已不适用，文首已加补记。
3. 仓库根 `README.md` 的「K07 胜出并定为最终设计」是 2026-10 的结论；最终交付版为 `K07.sv`。

## 与交付文档的关系

| 内容 | 位置 |
|---|---|
| 交付版时序、面积、加速比 | `docs/stage3/3.3-延迟加速比分析.md` §一 |
| E16 两笔接入与整核 13/13 | `docs/stage3/3.1-NEMU-vdot-指令扩展与协同仿真验证.md` §3.4 |
| 设计演进（v1.0–v1.6） | `docs/stage2/2.1-vdot-vv-设计文档.md` 修订记录 |
