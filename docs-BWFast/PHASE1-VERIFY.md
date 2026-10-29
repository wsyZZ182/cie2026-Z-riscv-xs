# Phase 1 验证报告：K07 接入香山整核（B1 路线）

Date: 2026-10-29 (Asia/Shanghai)
Goal: 整模块嵌入 K07（BWFast 24 配置之一，S02+S03+S04+S06+S07）到香山 VDotFu，
E8 走 K07、E16/E32 回退香山原生，整核 DiffTest 验证。

## 1. 代码修改（XiangShan/yunsuan/.../VectorALU/VDotFu.scala）
| # | 修改 | 说明 |
|---|------|------|
| 1 | import `_root_.vdot.BWFastTop` | 规避 vdot 子包歧义（编译 ambiguous） |
| 2 | 实例化 BWFastTop + 输出分流 | E8(sew=0)→K07；E16/E32→原 core.io.result；vxsat e8→false |
| 3 | K07 输入接当前指令 io.in.bits | 时序修复（见 §3），vs1/vs2 即时按 vl/vm/mask 清零，mask 全使能 |

## 2. 构建链（工具版本）
- Chisel 6.6.0 / Scala 2.13.15 / mill 0.12.3 / GraalVM JDK21 / firtool 1.62.1（香山 make 链）
- 顺序：`mill yunsuan.compile` → `make verilog`（XSTop.sv，1901 个 .sv）→ `make sim-verilog`（SimTop.sv 1.16MB）→ `make emu`
- 新 emu：SHA256 `40eb84aa957d924db25384cab36290fce2ec40bf5a722f134cd90c4dbe76ab8e`，构建 2026-10-29 19:37→21:14

## 3. 时序 bug 与修复
- 现象：首轮 DiffTest `v2_low different at pc=0x0080000180, right=0x88, wrong=0x00`（instrCnt=650 ABORT）
- 根因：VDotFu 锁存值（vs1_r/vsew）为 RegEnable，**fire 沿才更新**——fire 拍读取仍是上一条指令数据；
  K07 在 fire 沿捕获输入 → 捕获到上一条指令 → 结果错位全零
- 修复：K07 输入全部改接当前指令原始输入 io.in.bits（vs1/vs2 即时 act 清零、vdOld/sew 取当前指令），
  valid 判定用当前指令 vdType；原版 VDotCore 纯组合（S2 采样时锁存值已更新）不受影响
- RTL 验证：VDotFu.sv `io_in_valid = io_in_valid & io_in_bits_vdType[1:0]==2'h0`；
  `vs1 = {act_43 ? io_in_bits_vs1[127:120] : 8'h0, ...}`（当前指令 + 即时清零）

## 4. 整核 DiffTest 结果（700 用例 vdot-test）
```
e8 正数   : c0=136    (expect 136)
e8 负数   : c0=-136   (expect -136)
e8 边界   : c0=2032   (expect 2032)   ← Booth 时代首个失败用例
e8 vl=4   : c0=10     (expect 10)
e16 正数  : c0=36     (expect 36)
e32 正数  : c0=10     (expect 10)
e32 混合  : c0=-128   (expect -128)
e16 负数  : c0=-36    (expect -36)
e8 最小值 : c0=-2048  (expect -2048)
e32 边界  : c0=2147483647 (expect 2147483647)
e8 vl=1   : c0=5      (expect 5)
e8 vl=0   : c0=0x11223344 (expect 0x11223344)   ← 非活跃元素保留旧 vd
e32 溢出  : c0=0x80000000 vxsat_csr=1 (expect 0x80000000; csr=1 表示链路未接)
vdot.vv test done.
Core-0 instrCnt = 17209, cycleCnt = 64909, IPC = 0.265125
Host time spent: 545196ms
```
- DiffTest 结论：**无任何 different/ABORT**（grep 计数仅来自 "this will be different from cycleCnt" 日志文本）
- e32 溢出 vxsat_csr=1：K07 不产生 vxsat，NEMU MinimalConfig 明确不写 vcsr/vxsat（nemu-vdot 注释），口径一致

## 5. 环境与稳定性
- WSL 实例构建期间多次重启（~20-40 min 周期；宿主 63.5GB RAM 空闲 43GB，排除 OOM）
- 对策：setsid 脱离会话 + 日志写持久路径 /home/gingk + 四段独立续传
- DiffTest 运行：545 秒 / 64913 周期（单核仿真，NEMU 参考模型对照）

## 6. 文件清单
- 源码修改：`XiangShan/yunsuan/src/main/scala/yunsuan/vector/VectorALU/VDotFu.scala`（+ .bak-k07-phase1）
- Phase 0 接入：`XiangShan/yunsuan/src/main/scala/vdot/{BWFast.scala, BatchConfig.scala}`（默认 cfg=K07）
- 生成 RTL：`XiangShan/build/rtl/{XSTop.sv, SimTop.sv, VDotFu.sv, BWFastTop.sv, SharedBWFrontend.sv}`
- 日志：WSL /home/gingk/{p1_compile,p1_verilog,p1_simverilog,p1_emu,p1_difftest}.log

## 7. 后续（未做）
- Phase 2：E16 四拍慢单元（VIAlu/VIPU 慢单元机制）未启动
- 3.3 延迟加速比报告重做（K07 整核数据 vs RVV 基线组合）
- 2.1 设计文档 K07 章节更新；第三阶段 PPT + 波形/性能录屏
