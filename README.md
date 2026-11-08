# 面向边缘 AI 大语言模型推理的 RISC-V 自定义 vdot 指令设计

> 基于「香山」昆明湖 V2 处理器架构
>
> **2026 CIE 全国 RISC-V 高水平创新及应用大赛（应用方向）· CIE-香山社区-RISC-V 应用创新赛道**

---

## 📋 项目简介

点积（Dot Product）是深度学习模型的核心计算内核，在推荐系统、大语言模型（LLM）等 AI 应用中通常占据总计算量的 60%–80%。本项目在国产开源高性能 RISC-V 处理器「香山」昆明湖 V2 平台上，**新增一条自定义向量点积指令 `vdot.vv`（SEW=8, VLEN=128）**，通过整模块嵌入独立设计的高速点积单元 **K07**（BWFast 24 配置之一，计数树压缩方案），在香山向量执行单元（VIPU/VIAlu）中以极小面积开销换取点积吞吐提升，满足边缘 LLM 推理对高性能、低功耗、低成本的需求。

**设计演进**：初版基于香山原生 VDotCore（Radix-4 Booth 8×8 乘法器 + CSA 归约）实现并修复 Booth 符号补偿 bug；随后在独立模块工程中构建 **48 个候选实现**（Booth/Karatsuba/CSA 计数树/字节级流水等），统一综合比较后选定 **K07**（S02+S03+S04+S06+S07）作为最终 vdot 加速单元，**E8 单拍走 K07 计数树点积（II=1），E16 由 K07 拆两笔完成 8 对，E32 回退香山原生乘法器路径**，并完成交付版双模式整核 DiffTest 13 项（13/13 PASS）与 BWFast 版 700 用例回归。

## 🏆 赛题信息

| 项目 | 内容 |
|---|---|
| 大赛 | 第二届 CIE 全国 RISC-V 高水平创新及应用大赛 |
| 方向 | RISC-V 高水平应用方向 |
| 赛道 | CIE-香山社区-RISC-V 应用创新赛道 |
| 赛题 | 面向边缘 AI 大语言模型推理的 RISC-V 自定义 vdot 指令设计 |
| 目标平台 | 香山昆明湖 V2 处理器 |
| 官方赛题页 | https://rv.cie.org.cn/direction?id=8 |

## 👥 团队信息

- **团队名称**：宇宙超级无敌暴龙战士
- **参赛单位**：南京大学
- **指导老师**：鲁金铭（助理教授）
- **成员**：王殊一、汪子扬、陈颖旸
- **联系邮箱**：251510002@smail.nju.edu.cn

## 📁 项目结构

```
cie2026-Z-riscv-xs/
├── code/
│   ├── stage1/vadd/                       # 阶段1：vadd 向量加法应用
│   ├── stage2/
│   │   ├── vdot-rtl/                      # Chisel RTL（VDotCore Booth 修复版 + 数据通路
│   │   │                                  #   Instructions→VecDecoder→VIPU→VIAlu→VDotFu→(BWFastTop|VDotCore)）
│   │   ├── vdot/                          # vdot 早期工程快照
│   │   ├── vdot-test/                     # 阶段2.3：13 用例功能测试（正/负/边界/vl/溢出）
│   │   ├── karatsuba-compare/             # 阶段2.4 探索：4 种乘法器（Native/Booth/Schoolbook/Karatsuba）SV 实现
│   │   ├── K07_TEAM_HANDOFF_20261106_v2.zip   # 队友交付：双模式 K07.sv 交接包 v2
│   │   └── vdot-rtl-v1.0-booth.tar.gz     # v1.0 Booth 版历史快照
│   └── stage3/
│       ├── nemu-vdot/                     # 阶段3.1：NEMU 扩展（decode.h + vcompute.h + .patch）
│       ├── vdot-baseline/                 # 阶段3.2：基线程序（标准 RVV 组合实现同 13 用例）
│       ├── bench/                         # 阶段3.3：per-kernel 周期测速源码（12 个 .c + Makefile）
│       └── vdot-rtl-twopass/              # 交付版两笔实现（VDotFu.scala + K07_blackbox.scala）
├── docs/
│   ├── stage1/                            # 1.3 向量加法执行过程文档（.docx）
│   ├── stage2/                            # 2.1 设计文档（v1.7，K07 已并入 §7–§13）/ 2.2 RTL 实现说明 /
│   │                                      #   2.3 功能验证报告 / 2.4 Karatsuba 乘法器算法对比
│   └── stage3/                            # 3.1 NEMU 扩展与协同仿真 / 3.2 基线功能 / 3.3 延迟与吞吐加速比
├── archive/BWFast_20261030/               # BWFast（K07 前身）快照，按 2.1 修订记录版本分区
│   ├── README.md                          # 归档说明（版本对照表 / 40 文件口径 / 已知过时点）
│   ├── SHA256SUMS                         # 快照校验清单
│   ├── v1.4/                              # v1.4：K07 整模块嵌入
│   │   ├── src/                           # K07 源码（BWFast.scala / BatchConfig.scala / CounterProof.scala + build.sbt）
│   │   ├── rtl/                           # K07 生成 RTL（BWFastTop.sv / SharedBWFrontend.sv / filelist.f）
│   │   ├── synth/                         # Yosys 通用门级综合 + 结构指标（comb 11454 / reg 621 / depth 65）
│   │   ├── results/                       # config.json / result.json
│   │   └── docs/                          # K07 文档（README / MODIFICATIONS）
│   └── v1.4-final/                        # v1.4 定稿：Phase 1 整核验证
│       ├── PHASE1-VERIFY.md               # 700 用例整核 DiffTest 记录
│       └── K07_交接包修正_20261106.md     # 交接包三处修正说明
├── logs/                                  # 各阶段运行日志
├── screenshots/stage3/                    # commit trace 写回证据截图
├── videos/
│   ├── stage1/                            # 阶段1 hello 运行录屏（附网盘链接说明）
│   └── stage3/                            # 阶段3 波形仿真与性能分析录屏（4:00，含分镜与复现脚本 tools/）
└── README.md
```

> 说明：源码/开发改动（香山工作区）与交付材料（本仓库）分区管理；原始 `.vcd` 波形（451~463MB）经 `.gitignore` 排除；`*.log` 不入库（保留必要 `.gitkeep` 占位）。大文件（波形/录屏）的网盘链接**直接写在引用它的文档或录屏说明里**，不另设集中资产目录。

## ✅ 三阶段完成情况

### 阶段一：环境部署与验证（入门）
- ✅ 完成香山开发环境（xs-env）部署，输出 `hello xiangshan, I am 宇宙超级无敌暴龙战士, <IP>`（阶段一录屏录制时的标识为 `Z`，队名后续改为现名）
- ✅ 完整部署与操作日志
- ✅ 基于仿真波形分析向量加法指令在香山中的执行过程

### 阶段二：设计与实现（中等）
- ✅ `vdot.vv` 设计文档（**v1.7**：指令语义、微架构、数据通路；K07 最终方案已按环节并入 §7–§13，见 §7.1b 结构、§7.1c 末端 CPA、§7.3b E16 两笔）
- ✅ 原版 `vdot.vv` RTL 实现（Chisel，VDotCore Booth 修复版，700 用例整核 DiffTest 通过）
- ✅ **K07 高速点积单元**（BWFast 24 配置之一）：模块级验证（19 例 × 20 seed = **380/380**）+ 整核接入（Phase 0/1）
- ✅ **BWFast 版整核 DiffTest：700 用例全部通过**（E8 走 K07 / E16/E32 回退原生，17209 条指令全提交，无 mismatch/ABORT）
- ✅ **交付版双模式 K07 整核 DiffTest：13/13 PASS**（E8 单笔 + E16 两笔，17209 指令 / 63027 周期，零失配，`HIT GOOD TRAP`；见 [`2.1` §13.4](docs/stage2/2.1-vdot-vv-设计文档.md)、[`3.1`](docs/stage3/3.1-NEMU-vdot-指令扩展与协同仿真验证.md)）
- ✅ 正数 / 负数 / 边界值（127 / -128 / 0 / 溢出累加 / vl=0/1/4）测试
- ✅ 探索性补充：Native / Booth / Schoolbook / Karatsuba 四种乘法器面积-延迟对比（`docs/stage2/2.4`）
- ✅ **48 版本候选综合比较**（`archive/BWFast_20261030/v1.4/synth`）：统一 Yosys 通用门级综合，K07 胜出；**最终交付为双模式 `K07.sv`**（面积 671.18 µm²、reg→reg 动态 2128.0 ps，见 [`3.3`](docs/stage3/3.3-延迟加速比分析.md)）
- ✅ Phase 2（E16 接入整核）：**已用两笔方案实现**（非遗留的四拍慢单元）——第 1 笔算元素 0–3，第 2 笔算元素 4–7 并回灌首笔结果，单次架构写回；`VIAlu`/`VipuCfg` 未改，面积 +106.49 µm²（+2.56%），详见 [`2.1` §7.3b](docs/stage2/2.1-vdot-vv-设计文档.md)、[`3.1` §三](docs/stage3/3.1-NEMU-vdot-指令扩展与协同仿真验证.md)

### 阶段三：验证与优化（高难度）
- ✅ 为 NEMU 模拟器新增 `vdot.vv` 指令支持（不写 vxsat/vcsr，与 RTL 口径一致）
- ✅ NEMU 与 RTL 协同仿真（Difftest）验证一致性（整核 700 用例通过）
- ✅ 用已有向量指令组合实现相同功能作为 Baseline（`vwmul.vv` + `vwredsum.vs` 组合，13 用例一致，`instrCnt 16521 / cycleCnt 60656`）
- ✅ 延迟/吞吐加速比分析（`3.3`）：已分两个互不可乘的口径给出——**路径口径**（交付版 K07 在 ASAP7 物理流程 + STA，动态 2128.0 ps vs 原生 `vwmul` 4885.7 ps → 2.30×）；**周期口径**（MinimalConfig + `rdcycle`，1.78 vs 7.1 周期/op → 3.99×）

## 🛠 技术栈

| 类别 | 工具/技术 |
|---|---|
| 硬件描述 | Chisel / Scala（香山原生 + BWFast 独立模块） |
| 构建工具 | Mill / sbt |
| 仿真器 | Verilator（香山仿真程序 `emu`） |
| 参考模型 | NEMU（RISC-V 解释型指令集模拟器） |
| 协同验证 | Difftest（RTL 与 NEMU 差分测试） |
| 综合 | Yosys（通用门级结构指标；无工艺库，不换算物理面积/频率/功耗） |
| 裸机运行时 | AM（nexus-am），用于编译测试 workload |
| 波形查看 | gtkwave |
| 开发环境 | xs-env（Ubuntu 22.04，≥32GB 内存） |
| 版本管理 | Git / GitHub |

## 🔧 vdot.vv 关键设计说明

- **指令语义**：`vdot.vv vd, vs2, vs1`（VLEN=128，SEW=8/16/32）——
  `vd[31:0] = sext(vd_old[31:0]) + Σ sext(vs1[i]) × sext(vs2[i])`，
  vd 高 96 位写 0；要纯 Σ 先把 vd 初值清零。
- **指令编码**：funct6=`000100` + funct3=`011` + opcode=`1010111`（示例 `0x12103157` = vdot.vv v2,v0,v1）。funct3=011 为 OPMVV 区空闲组合，避开与 `vminu.vx` 的冲突。
- **最终微架构（K07，v1.4 起）**：
  - **E8**：16 对 8 位元素点积，16 个共享 **BW8** 部分积生成器 + **组内 carry-save 两行 + 四组等权合并进 3:2 全加器计数树**（S04 有界两步前瞻调度在 3/7/15 中选宽，但实测 **1257 次压缩全部为 3:2**，S02 7:3 / S03 15:4 两条高阶路径**从未被选中**，见 [`2.1` §7.1b](docs/stage2/2.1-vdot-vv-设计文档.md) 复核注记），**S06 高位符号补偿常量 + S07 三来源字节路由**参与部分积生成，末端 **20+12 分裂前缀 CPA**（`SplitPrefix20x12`），单拍、II=1；**替代原 Booth 8×8 乘法器**。
  - **E16**：K07 拆两笔完成 8 对——第 1 笔算元素 0–3（低 64 位、`mask[3:0]`、`vdOld=old_vd`），第 2 笔算元素 4–7（高 64 位下移、`mask[7:4]`、`vdOld=第 1 笔结果`），仅第 2 笔写回；两笔都落在原 `CertainLatency(2)` 窗口内，`VIAlu`/`VipuCfg` 无需改动。
  - **E32**：整核中**回退香山原生乘法器路径**（§7）；K07 无 e32 模式。
  - **饱和/vxsat**：K07 不产生 vxsat、不实现饱和（模 2³²）；与 NEMU vdot（不写 vcsr）口径一致。
  - 结构指标（Yosys 通用门级，无工艺库）：comb 11,454 / reg 621 / 逻辑深度 65。
- **接入方式**：在香山向量执行单元注册自定义 opcode（`VipuType.vdot_vv`），`VDotFu.scala` 整模块嵌入 `BWFastTop`（K07），E8/E16 走 K07（E16 为两笔）、E32 走原 `VDotCore`；K07 输入取**当前指令原始输入**（关键时序修复：规避 fire 沿捕获上一条指令锁存值的错位）。
- **设计文档**：详见 [`docs/stage2/2.1-vdot-vv-设计文档.md`](docs/stage2/2.1-vdot-vv-设计文档.md)（v1.7；K07 结构 §7.1b、末端 20+12 分裂前缀 §7.1c、E16 两笔 §7.3b）、[`docs/stage2/2.2-vdot-RTL-实现说明.md`](docs/stage2/2.2-vdot-RTL-实现说明.md)（§2.3 接入九处、§3.7 窄 E8 对照件）与 [`archive/BWFast_20261030/v1.4-final/PHASE1-VERIFY.md`](archive/BWFast_20261030/v1.4-final/PHASE1-VERIFY.md)（Phase 1 历史记录）。

## 🧪 测试与验证

| 测试类别 | 用例 | 结果 |
|---|---|---|
| 正数点积 | 全正数输入 | ✅ 通过 |
| 负数点积 | 含负数输入（验证符号扩展） | ✅ 通过 |
| 边界值 | 127 / -128 / 0 / 溢出累加 / vl=0/1/4 | ✅ 通过 |
| 整核协同仿真（BWFast 版） | NEMU vs RTL 逐指令比对（Difftest，700 用例） | ✅ 一致（17209 指令全提交，无 mismatch/ABORT） |
| 整核协同仿真（交付版 K07） | NEMU vs RTL，13 项（e8/e16/e32 正负数、边界、vl、溢出） | ✅ 13/13，17209 指令 / 63027 周期，零失配 |
| K07 模块级 | 19 例 × 20 seed = **380/380** RTL 仿真；7/15/31 计数器 SAT 证明 | ✅ 全部通过（`2.3` §2.4） |
| 48 版综合比较 | 统一 Yosys 通用门级综合 | ✅ K07 胜出（结果归档 archive/BWFast_20261030/v1.4/synth/） |

详细测试报告见 [`docs/stage2/2.3-功能验证报告.md`](docs/stage2/2.3-功能验证报告.md)、[`archive/BWFast_20261030/v1.4-final/PHASE1-VERIFY.md`](archive/BWFast_20261030/v1.4-final/PHASE1-VERIFY.md)。

## 📊 性能分析（两个不可相乘的口径）

> **口径说明**（与 `3.3` 文首一致）：本节的「周期」与「路径」回答的是两个不同问题——**要做几拍** vs **一拍能装多快**，两者不得相乘，也不得用手工扣除等待周期的方式换算。
>
> **周期口径**：MinimalConfig RTL 仿真 + `rdcycle` 实测，N=10/100/500 三组独立程序取「周期/op」斜率（run3 取最小）。
> **路径口径**：ASAP7 RVT TT 综合 + OpenROAD 物理流程 + STA，基线同流程、同角、同综合策略，边界统一 reg-Q→reg-D。
>
> `3.3` 已声明：正式测速口径须在 **`KunminghuV2Config`**（集成最终双模式 K07）下重测，**该项尚未执行**；下表周期数据取自 MinimalConfig。

**① 周期口径：拍数**

| 场景 | Baseline（周期/op） | vdot（周期/op） | 比值 |
|---|---|---|---|
| 延迟·依赖链（e8/e16/e32） | 7.0 ~ 7.1 | 7.0 | **≈1.0×（打平，均受写回等待支配）** |
| 吞吐·背靠背（e8/e16） | 7.1 | 1.78 | **3.99×** |
| 吞吐·背靠背（e32） | 2.7 | 1.78 | **1.52×** |

**② 路径口径：单拍时延（交付版双模式 `K07.sv`）**

| 模式 | 交付版 K07（reg→reg） | FO4 | 原生基线 | 比值 |
|---|---:|---:|---|---:|
| 动态 | 2128.0 ps | 118.5 | `vwmul`（VIMacU）4885.7 ps | **2.30×** |
| E8 | 1759.5 ps | 98.0 | 同上 | 2.78× |
| E16 | 1737.0 ps | 96.7 | `vwredsum`（Reduction）2637.7 ps | 1.52× |

> **这两条不能互相替代**：路径比只说明「一拍装得下、且不比基线最慢一级更慢」；加速来自拍数（基线 3~4 条指令 → vdot 1 条）。用路径比冒充加速比会把 2.30× 说成性能提升 2.3 倍，口径错。
>
> **不能宣称「单拍成立」**：动态 2128.0 ps 对应约 **470 MHz**；若昆明湖 V2 目标 2–3 GHz，K07 与原生一样都必须流水化。最慢路径起点是 `q_e16`（模式选择控制路径，非 E16 数据通路），固定 E8/E16 后分别降到 1759.5 / 1737.0 ps——代价来自「要支持两种模式」本身。
>
> 写回证据：commit trace 显示 vdot 单指令单次写回 v2（data 每次 +0x88=136），基线需 vwmul + vwredsum 两条指令两次写回。完整方法见 [`docs/stage3/3.3-延迟加速比分析.md`](docs/stage3/3.3-延迟加速比分析.md)。

## 📝 关键提交记录

| 提交 | 内容 |
|---|---|
| `153efe7` | 修复 VDotCore Booth 乘法器符号补偿（`1.S(1.W)`→`1.S(2.W)`），700 用例整核 DiffTest 通过 |
| `3837dde` | 同步 K07 设计六目录（rtl/src/synth/sim/results/docs） |
| `61e7c8c` | Phase 1：K07 整核接入，700 用例 DiffTest 通过（含时序修复根因） |
| `7e7b5bf` | 同步 README 与说明文档到 E16 两笔实现（13/13 PASS） |
| `2fa6c65` | 2.1 将 K07 按环节并入 §7–§13，撤销 §15/§16 |
| `fd52845` | 2.3 以最终版验证数据替换主体，并入后删除 2.3b |
| `f4263b9` | 2.2b 的窄 E8 内容并入 2.1/2.2，删除 2.2b |
| `ebb8f6b` | 新增阶段三波形仿真与性能分析录屏（`videos/stage3/`） |
| `f667ae1` | 删除 `assets` 目录，大文件链接改为直接写在引用它的文档里 |
| `7e47f5c` | 把 K07 交接包修正说明移入 BWFast 归档，删除 `docs/description` |

> `153efe7` / `3837dde` / `61e7c8c` 是工程节点提交；其后为文档与仓库收敛。完整历史见 `git log`。

## 📦 提交说明

本作品按大赛要求通过 GitHub 加密提交流程提交：

1. Fork `OpenXiangShan/XiangShanLab`
2. 创建公有作品仓库（本仓库）
3. 填写 `my_submission.txt`，运行 `submit.sh` 加密生成 `submission.asc`
4. 推送至 fork 仓库的 `01_参赛选手提交区/<团队名>/` 并发起 PR

## 📄 许可证

本项目采用 [MulanPSL-2.0](http://license.coscl.org.cn/MulanPSL2)（木兰宽松许可证第 2 版）开源，与香山处理器保持一致。

## 📚 参考资料

- [香山官方文档](https://docs.xiangshan.cc/)
- [香山昆明湖 V2R2 设计文档](https://docs.xiangshan.cc/projects/design)
- [香山前端开发环境](https://docs.xiangshan.cc/zh-cn/latest/tools/xsenv/)
- [香山开发仓库 xs-env](https://github.com/OpenXiangShan/xs-env)
- [香山研习仓库 XiangShanLab](https://github.com/OpenXiangShan/XiangShanLab)
- [香山运算单元 yunsuan](https://github.com/OpenXiangShan/yunsuan)
- [RISC-V "V" 向量扩展规范](https://docs.riscv.org/reference/isa/v20250508/unpriv/v-st-ext.html)
- [大赛官方赛题页](https://rv.cie.org.cn/direction?id=8)

---

*本仓库为 2026 CIE 全国 RISC-V 高水平创新及应用大赛参赛作品，知识产权归参赛团队所有。*
