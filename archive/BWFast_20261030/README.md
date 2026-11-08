# BWFast 历史实现归档（2026-10）

> 归档时间：2026-11-06。来源仓库 commit `cca38addc4ffa09c7e2ae6acc489b4ca8e6e4aef`（main）。
> 本目录是 2026-10 那套 BWFast（K07 前身）实现的完整快照，**不再是交付版本**。

## 为什么归档

项目最终版本已确定为队友交付的双模式 `K07.sv`：

| 项 | 值 |
|---|---|
| 路径 | `01_K07/rtl/K07.sv`（交接包 v2，`code/stage2/K07_TEAM_HANDOFF_20261106_v2.zip`） |
| SHA256 | `b854caedd3d9847cd7f6ea569bfb4439f4ad73e0aa185449aaf82bb15ac0b0a8` |
| 面积 | 671.18 µm²（K07Reg 含壳 819.37 µm²） |
| 时序（reg→reg） | 动态 2128.0 ps / E8 1759.5 ps / E16 1737.0 ps |

口径与对比见仓库根 `docs/stage3/3.3-延迟加速比分析.md`。本目录 `rtl-BWFast/BWFastTop.sv` 是 10 月窄 E8 版的生成物，已被取代；整核 Chisel 源码（`VDotFu.scala`）当前仍实例化 `BWFastTop`，迁移到 `K07.sv` 属于整核集成工作，不在本归档范围。

## 内容

| 子目录 | 文件数 | 说明 |
|---|---:|---|
| `src-BWFast/` | 3 + 2 | Chisel 源码（BWFast.scala / BatchConfig.scala / CounterProof.scala）+ 归档补齐的构建脚手架 |
| `rtl-BWFast/` | 3 | 生成 RTL（BWFastTop.sv / SharedBWFrontend.sv / filelist.f） |
| `synth-BWFast/` | 6 | Yosys 通用门级综合产物（netlist.json 6.8 MB 为最大项） |
| `sim-BWFast/` | 22 | 模块级仿真（sim_build.log + sim_seed1..20.log + .gitkeep） |
| `results-BWFast/` | 3 | config.json / elaboration.log / result.json |
| `docs-BWFast/` | 3 | README / MODIFICATIONS / PHASE1-VERIFY |

原始 6 目录合计 **40 个文件**。其中 23 个 `*.log` 按仓库 `.gitignore` 的 `*.log` 规则**不进入 git 版本库**，只存在于本目录与 tar 包；因此 `git ls-files` 只显示 17 个。完整清单见 `SHA256SUMS`（含补齐件共 42 项）。

> 另有 1 份后续归档文档放在本目录根：`K07_交接包修正_20261106.md`（2026-11-06，K07 交接包三处修正说明，原先在 `docs/description/`）。它**不属于上表 6 个目录的 40 个文件**，也不在 `SHA256SUMS` 清单内。

## 构建脚手架（补齐件，非原始提交内容）

`src-BWFast/` 原始只有 3 个 `.scala`，没有 `build.sbt` / `project/`，属裸源码目录，无法直接编译。归档时补齐：

- `build.sbt`：Scala 2.13.17 / Chisel 7.2.0 / sbt 1.10.7，与 `docs-BWFast/README.md` 记录的版本一致；额外加 `Compile / unmanagedSourceDirectories := Seq(baseDirectory.value)`，因为源文件在目录根（`package vdot`），非标准 sbt 布局
- `project/build.properties`：`sbt.version=1.10.7`

来源：`~/K07_E8_PRIORITY/k07_e8_priority/shared/`（同版本 Chisel 工程）。**该脚手架未实测编译**（需要 Maven 缓存或网络），首次使用请保留完整日志。

重建（在 `src-BWFast/` 下）：

    java -jar $HOME/toolchain/sbt-launch.jar compile
    # 或 PATH 里有 sbt 时直接：sbt compile
    # 生成 SV 需 firtool，例如：
    # firtool --format=sv --split-verilog -o ../rtl-rebuild $(find target -name "*.fir")

## 恢复 / 单独存放

仓库外另有一份 tar 包：`~/archive_BWFast_20261030.tar.zst`。

    tar -I zstd -xf ~/archive_BWFast_20261030.tar.zst -C /tmp/bwfast_restore

## 已知过时点（保留历史原样，未修改原数值）

1. `results-BWFast/result.json` 的 `sta_status` 是旧版通用综合口径；K07 的实际时序/面积见仓库根 `docs/stage3/3.4`–`3.7`。
2. `docs-BWFast/README.md` 第 9 行「本环境没有 sbt / Verilator / Yosys」已不适用，文首已加补记。
3. 仓库根 `README.md:81` 的「K07 胜出并定为最终设计」是 2026-10 的结论；最终交付版为 `K07.sv`。