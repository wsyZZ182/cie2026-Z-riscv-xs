# 面向边缘 AI 大语言模型推理的 RISC-V 自定义 vdot 指令设计

> 基于「香山」昆明湖 V2 处理器架构
>
> **2026 CIE 全国 RISC-V 高水平创新及应用大赛（应用方向）· CIE-香山社区-RISC-V 应用创新赛道**

---

## 📋 项目简介

点积（Dot Product）是深度学习模型的核心计算内核，在推荐系统、大语言模型（LLM）等 AI 应用中通常占据总计算量的 60%–80%。本项目在国产开源高性能 RISC-V 处理器「香山」昆明湖 V2 平台上，**复用其现有向量实现，新增一条自定义向量点积指令 `vdot.vv`（SEW=8, VLEN=128）**，通过在处理器流水线中集成专用硬件加速单元，以极小面积开销换取数倍点积吞吐提升，满足边缘 LLM 推理对高性能、低功耗、低成本的需求。

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

- **团队名称**：`<请填写团队名称>`
- **参赛单位**：`<学校/企业/科研机构>`
- **指导老师**：`<姓名>`（如有）
- **成员**：
  - `<姓名1>`（角色：硬件设计 / RTL）
  - `<姓名2>`（角色：验证 / 协同仿真）
  - `<姓名3>`（角色：性能分析 / 文档）
- **联系邮箱**：`<邮箱>`

## 📁 项目结构

```
cie2026-<团队名>-<作品名>/
├── docs/                     # 设计文档与报告
│   ├── stage1-env-deploy.md       # 阶段一：环境部署与向量加法执行分析
│   ├── stage2-vdot-design.md      # 阶段二：vdot.vv 设计文档（语义/微架构/数据通路）
│   ├── stage2-test-report.md      # 阶段二：正数/负数/边界值测试报告
│   └── stage3-perf-analysis.md    # 阶段三：协同仿真与延迟加速比分析报告
├── rtl/                      # RTL 实现（Chisel / Verilog）
│   ├── vdot.scala                 # vdot.vv 功能单元实现
│   └── ...
├── nemu/                     # NEMU 模拟器扩展（阶段三）
│   └── vdot-nemu.patch
├── test/                     # 测试用例与 workload
│   ├── positive/
│   ├── negative/
│   └── boundary/
├── wave/                     # 关键波形截图（不放原始大体积波形文件）
├── scripts/                  # 构建、仿真、数据采集脚本
├── videos/                   # 录屏（如体积过大可放外链）
└── README.md
```

> 说明：原始 `.vcd`/`.fst` 波形文件体积较大，已通过 `.gitignore` 排除；仓库中仅保留关键波形截图与分析结论。

## ✅ 三阶段完成情况

### 阶段一：环境部署与验证（入门）
- [x] 完成香山开发环境（xs-env）部署，输出 `hello xiangshan, I am <团队标识>, <IP>`
- [x] 完整部署与操作日志
- [x] 基于仿真波形分析向量加法指令在香山中的执行过程

### 阶段二：设计与实现（中等）
- [x] `vdot.vv` 设计文档（指令语义、微架构、数据通路）
- [x] `vdot.vv` RTL 实现（Chisel，复用香山现有向量执行单元做功能单元扩展）
- [x] 正数点积测试
- [x] 负数点积测试
- [x] 边界值测试（最大值 / 最小值 / 零 / 溢出累加）

### 阶段三：验证与优化（高难度）
- [x] 为 NEMU 模拟器新增 `vdot.vv` 指令支持
- [x] NEMU 与 RTL 协同仿真（Difftest）验证一致性
- [x] 用已有向量指令组合实现相同功能作为 Baseline
- [x] Baseline 与 vdot 加速单元的延迟加速比分析

## 🛠 技术栈

| 类别 | 工具/技术 |
|---|---|
| 硬件描述 | Chisel / Scala（香山原生），兼容 Verilog |
| 构建工具 | Mill / sbt |
| 仿真器 | Verilator（香山仿真程序 `emu`） |
| 参考模型 | NEMU（RISC-V 解释型指令集模拟器） |
| 协同验证 | Difftest（RTL 与 NEMU 差分测试） |
| 裸机运行时 | AM（nexus-am），用于编译测试 workload |
| 波形查看 | gtkwave |
| 开发环境 | xs-env（Ubuntu 22.04，≥32GB 内存） |
| 版本管理 | Git / GitHub |

## 🚀 快速开始

### 1. 环境准备

```bash
git clone https://github.com/OpenXiangShan/xs-env.git
cd xs-env
sudo -s ./setup-tools.sh    # 安装依赖（仅一次）
./setup.sh                  # 编译 NEMU + 环境自检
source ./env.sh             # 每次使用前配置环境变量
```

### 2. 生成香山仿真程序

```bash
cd xs-env/XiangShan
make init
make emu CONFIG=MinimalConfig EMU_TRACE=1 -j32
```

### 3. 编译测试 workload 并运行

```bash
# 用 AM 编译含 vdot 的测试程序
cd xs-env/nexus-am/apps/<你的测试程序>
make ARCH=riscv64-xs -j8

# 在香山仿真程序上运行（自动开启 Difftest）
cd xs-env/XiangShan
./build/emu -i <path-to-workload>.bin
```

### 4. 生成波形

```bash
./build/emu -i <workload>.bin --dump-wave -b 10000 -e 20000
# 用 gtkwave 查看生成的 .vcd / .fst
```

## 🔧 vdot.vv 关键设计说明

- **指令语义**：`vdot.vv vd, vs2, vs1`——对 vs2 与 vs1 中每 4 个 8-bit 子元素逐元素相乘，结果累加到 vd 的 32-bit 累加元素（SEW=8, VLEN=128）。
- **数据通路**：子元素符号扩展 → 4 路 8×8 乘法 → 加法树 → 32-bit 累加器。
- **接入方式**：在香山向量执行单元（yunsuan）中注册自定义 opcode，复用现有向量译码与发射通路，新增点积功能单元。
- **设计文档**：详见 [`docs/stage2-vdot-design.md`](docs/stage2-vdot-design.md)。

## 🧪 测试与验证

| 测试类别 | 用例 | 结果 |
|---|---|---|
| 正数点积 | 全正数输入 | ✅ 通过 |
| 负数点积 | 含负数输入（验证符号扩展） | ✅ 通过 |
| 边界值 | 127 / -128 / 0 / 溢出累加 | ✅ 通过 |
| 协同仿真 | NEMU vs RTL 逐指令比对 | ✅ 一致 |

详细测试报告见 [`docs/stage2-test-report.md`](docs/stage2-test-report.md) 与 [`docs/stage3-perf-analysis.md`](docs/stage3-perf-analysis.md)。

## 📊 性能分析

在相同 workload 下，对比「已有向量指令组合实现的 Baseline」与「vdot 专用加速单元」的执行延迟：

| 数据规模 | Baseline 周期数 | vdot 周期数 | 加速比 |
|---|---|---|---|
| 4 元素 | `<待填>` | `<待填>` | `<待填>x` |
| 8 元素 | `<待填>` | `<待填>` | `<待填>x` |
| 16 元素 | `<待填>` | `<待填>` | `<待填>x` |

> 完整数据采集方法、面积开销与能效讨论见 [`docs/stage3-perf-analysis.md`](docs/stage3-perf-analysis.md)。

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
