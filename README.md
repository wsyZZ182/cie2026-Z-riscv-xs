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

- **团队名称**：Z
- **参赛单位**：南京大学
- **指导老师**：鲁金铭（助理教授）
- **成员**：王殊一、汪子扬、陈颖旸
- **联系邮箱**：251510002@smail.nju.edu.cn

## 📁 项目结构

```
cie2026-Z-riscv-xs/
├── code/
│   ├── stage1/vadd/                # 阶段1：vadd 向量加法应用
│   ├── stage2/vdot-rtl/            # 阶段2：Chisel RTL（VDotCore Booth 修复版 + 数据通路
│   │                               #   Instructions→VecDecoder→VIPU→VIAlu→VDotFu→(BWFastTop|VDotCore)）
│   ├── stage2/vdot-test/           # 阶段2.3：功能测试（13 用例：正/负/边界/vl/溢出）
│   ├── stage2/karatsuba-compare/   # 阶段2.4 探索：4 种乘法器（Native/Booth/School/Karatsuba）SV 实现
│   └── stage3/
│       ├── nemu-vdot/              # 阶段3.1：NEMU 扩展（decode.h + vcompute.h + .patch）
│       ├── vdot-baseline/          # 阶段3.2：基线程序（标准 RVV 组合实现同 13 用例）
│       └── bench/                  # 阶段3.3：性能测试 per-kernel 源码（12 个 .c + Makefile）
├── archive/BWFast_20261030/src-BWFast/                     # K07 源码（BWFast.scala / BatchConfig.scala / CounterProof.scala）
├── archive/BWFast_20261030/rtl-BWFast/                     # K07 生成 RTL（BWFastTop.sv / SharedBWFrontend.sv）
├── archive/BWFast_20261030/synth-BWFast/                   # K07 Yosys 通用门级综合（结构指标，无工艺库）
├── archive/BWFast_20261030/sim-BWFast/                     # K07 模块级仿真（20 seeds 回归）
├── archive/BWFast_20261030/results-BWFast/                 # K07 结果（result.json：comb 11454 / reg 621 / depth 65）
├── docs/
│   ├── stage1/                     # 1.3 向量加法执行过程文档（.docx）
│   ├── stage2/                     # 2.1 设计文档（v1.4，K07 方案已并入 §7–§13）/ 2.2 RTL 说明 /
│   │                               #   2.3 功能验证报告 / 2.4 Karatsuba 乘法器算法对比
│   └── stage3/                     # 3.1 NEMU 扩展与协同仿真 / 3.2 基线功能 / 3.3 加速比与时序面积
│                                   #   旧版迭代记录归档于 archive/BWFast_20261030/docs-stage3-legacy/
├── archive/BWFast_20261030/docs-BWFast/                    # K07 文档（README / MODIFICATIONS / PHASE1-VERIFY）
├── logs/                           # 各阶段运行日志
├── screenshots/stage3/             # commit trace 写回证据截图
├── videos/stage1/                  # 阶段1 hello 运行录屏
└── README.md
```

> 说明：源码/开发改动（香山工作区）与交付材料（本仓库）分区管理；原始 `.vcd` 波形（451~463MB）经 `.gitignore` 排除；`*.log` 不入库（保留必要 .gitkeep 占位）。

## ✅ 三阶段完成情况

### 阶段一：环境部署与验证（入门）
- ✅ 完成香山开发环境（xs-env）部署，输出 `hello xiangshan, I am <团队标识>, <IP>`
- ✅ 完整部署与操作日志
- ✅ 基于仿真波形分析向量加法指令在香山中的执行过程

### 阶段二：设计与实现（中等）
- ✅ `vdot.vv` 设计文档（v1.4：指令语义、微架构、数据通路、**§15 K07 最终方案**）
- ✅ 原版 `vdot.vv` RTL 实现（Chisel，VDotCore Booth 修复版，700 用例整核 DiffTest 通过）
- ✅ **K07 高速点积单元**（BWFast 24 配置之一）：模块级验证（20 seeds + SAT 证明）+ 整核接入（Phase 0/1）
- ✅ **BWFast 版整核 DiffTest：700 用例全部通过**（E8 走 K07 / E16/E32 回退原生，17209 条指令全提交，无 mismatch/ABORT）
- ✅ **交付版双模式 K07 整核 DiffTest：13/13 PASS**（E8 单笔 + E16 两笔，17209 指令 / 63027 周期，零失配，`HIT GOOD TRAP`；见 [`3.1`](docs/stage3/3.1-NEMU-vdot-指令扩展与协同仿真验证.md)）
- ✅ 正数 / 负数 / 边界值（127 / -128 / 0 / 溢出累加 / vl=0/1/4）测试
- ✅ 探索性补充：Native / Booth / Schoolbook / Karatsuba 四种乘法器面积-延迟对比（`docs/stage2/2.4`）
- ✅ **48 版本候选综合比较**（`archive/BWFast_20261030/synth-BWFast`）：统一 Yosys 通用门级综合，K07 胜出；**最终交付为双模式 `K07.sv`**（面积 671.18 µm²、reg→reg 动态 2128.0 ps，见 [`3.3`](docs/stage3/3.3-延迟加速比分析.md)）
- ✅ Phase 2（E16 接入整核）：**已用两笔方案实现**（非遗留的四拍慢单元）——第 1 笔算元素 0–3，第 2 笔算元素 4–7 并回灌首笔结果，单次架构写回；`VIAlu`/`VipuCfg` 未改，面积 +106.49 µm²（+2.56%），详见 [`3.1`](docs/stage3/3.1-NEMU-vdot-指令扩展与协同仿真验证.md)

### 阶段三：验证与优化（高难度）
- ✅ 为 NEMU 模拟器新增 `vdot.vv` 指令支持（不写 vxsat/vcsr，与 RTL 口径一致）
- ✅ NEMU 与 RTL 协同仿真（Difftest）验证一致性（整核 700 用例通过）
- ✅ 用已有向量指令组合实现相同功能作为 Baseline（vmul/vwmul + vredsum 组合，13 用例一致）
- ⬜ **延迟/吞吐加速比分析（K07 版）待重做**：现有 3.3 报告为 Booth 修复前**原版 vdot** 数据（延迟打平、吞吐 4×），K07 版需在整核数据通路重测（roi_cycles 尚未在整核测量）

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
  - **E8**：16 对 8 位元素点积，BW8 部分积生成器 + **组内 carry-save 两行 + 四组等权合并进计数树**（S02 7:3 / S03 15:4 精确压缩 + S04 有界前瞻调度），单拍、II=1；**替代原 Booth 8×8 乘法器**。
  - **E16**：K07 拆两笔完成 8 对——第 1 笔算元素 0–3（低 64 位、`mask[3:0]`、`vdOld=old_vd`），第 2 笔算元素 4–7（高 64 位下移、`mask[7:4]`、`vdOld=第 1 笔结果`），仅第 2 笔写回；两笔都落在原 `CertainLatency(2)` 窗口内，`VIAlu`/`VipuCfg` 无需改动。
  - **E32**：整核中**回退香山原生乘法器路径**（§7）；K07 无 e32 模式。
  - **饱和/vxsat**：K07 不产生 vxsat、不实现饱和（模 2³²）；与 NEMU vdot（不写 vcsr）口径一致。
  - 结构指标（Yosys 通用门级，无工艺库）：comb 11,454 / reg 621 / 逻辑深度 65。
- **接入方式**：在香山向量执行单元注册自定义 opcode（`VipuType.vdot_vv`），`VDotFu.scala` 整模块嵌入 `BWFastTop`（K07），E8/E16 走 K07（E16 为两笔）、E32 走原 `VDotCore`；K07 输入取**当前指令原始输入**（关键时序修复：规避 fire 沿捕获上一条指令锁存值的错位）。
- **设计文档**：详见 [`docs/stage2/2.1-vdot-vv-设计文档.md`](docs/stage2/2.1-vdot-vv-设计文档.md)（§15）与 [`archive/BWFast_20261030/docs-BWFast/PHASE1-VERIFY.md`](archive/BWFast_20261030/docs-BWFast/PHASE1-VERIFY.md)。

## 🧪 测试与验证

| 测试类别 | 用例 | 结果 |
|---|---|---|
| 正数点积 | 全正数输入 | ✅ 通过 |
| 负数点积 | 含负数输入（验证符号扩展） | ✅ 通过 |
| 边界值 | 127 / -128 / 0 / 溢出累加 / vl=0/1/4 | ✅ 通过 |
| 整核协同仿真（BWFast 版） | NEMU vs RTL 逐指令比对（Difftest，700 用例） | ✅ 一致（17209 指令全提交，无 mismatch/ABORT） |
| 整核协同仿真（交付版 K07） | NEMU vs RTL，13 项（e8/e16/e32 正负数、边界、vl、溢出） | ✅ 13/13，17209 指令 / 63027 周期，零失配 |
| K07 模块级 | 20 seeds RTL 仿真 + 7/15/31 计数器 SAT 证明 | ✅ 通过（PASS_RTL_GENERIC） |
| 48 版综合比较 | 统一 Yosys 通用门级综合 | ✅ K07 胜出（结果归档 archive/BWFast_20261030/synth-BWFast/） |

详细测试报告见 [`docs/stage2/2.3-功能验证报告.md`](docs/stage2/2.3-功能验证报告.md)、[`archive/BWFast_20261030/docs-BWFast/PHASE1-VERIFY.md`](archive/BWFast_20261030/docs-BWFast/PHASE1-VERIFY.md)。

## 📊 性能分析（口径说明）

> **重要口径**：下表为 Booth 修复前**原版 vdot**（VDotCore）在 MinimalConfig 下的测量结果（N=10/100/500 三组独立程序"周期/op"斜率，run3 取最小）。**K07 版整核延迟/吞吐加速比尚未重测**（3.3 报告重做进行中）——K07 版数据出来后本表将更新，现有数值不代表 K07 性能。

| 场景 | Baseline（周期/op） | vdot 原版（周期/op） | 加速比 |
|---|---|---|---|
| 延迟·依赖链（e8/e16/e32） | 7.0 ~ 7.1 | 7.0 | ≈1.0×（打平，均受写回等待支配） |
| 吞吐·背靠背（e8/e16） | 7.1 | 1.78 | **≈4.0×** |
| 吞吐·背靠背（e32） | 2.7 | 1.78 | **≈1.5×** |

> 写回证据：commit trace 显示 vdot 单指令单次写回 v2（data 每次 +0x88=136），基线需 vwmul+vwredsum 两条指令两次写回。完整测量方法见 [`docs/stage3/3.3-延迟加速比分析.md`](docs/stage3/3.3-延迟加速比分析.md)。

## 📝 关键提交记录

| 提交 | 内容 |
|---|---|
| `153efe7` | 修复原版 VDotCore Booth 乘法器符号补偿（1.S(1.W)→1.S(2.W)），700 用例整核 DiffTest 通过 |
| `3837dde` | 同步 K07 设计六目录（rtl/src/synth/sim/results/docs） |
| `61e7c8c` | Phase 1 K07 整核接入记录——700 用例 DiffTest 通过（含时序修复根因） |
| `4b8513c` | 2.1 设计文档 v1.4（新增 §15 K07 方案 + §16 待办） |

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
