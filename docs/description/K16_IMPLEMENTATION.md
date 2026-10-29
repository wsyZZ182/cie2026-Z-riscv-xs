# K16：K07 E16 四对固定拍数独立模块

2026-10-29，K16-DEV。状态：NEEDS_REVIEW（源码已交独立测试，编译/RTL测试结果以测试报告为准）。

## 范围与来源

根据本轮用户明确批准，在 `implementation/k07_e16x4/` 新增独立模块。旧 K07、原始 48/24 版归档、整核、NEMU 和旧实验产物均未修改。恢复原始 `BWFast.scala`、`BatchConfig.scala`、`CounterProof.scala`、`build.sbt`、`project/build.properties` 后，原样保留这些文件；仅新建 `src/main/scala/vdot/K07E16x4Top.scala`。

输入归档 `C:/Users/陈颖旸/Downloads/BWFast_BATCH_RESULTS.zip` SHA256：`8e85a156480a51ce20aee3220502bca6f7ebfb83433a4df73e24edf8800f6fb0`。逐文件原始哈希见 `implementation/k07_e16x4/BASELINE_SHA256.json`。新增源码 SHA256：`adb2845e442a36e5a99b9f02b87a81adbd766cfdb0d70c4c8a02533e581b4796`。

## 冻结接口

新类 `vdot.K07E16x4Top`；生成入口 `vdot.EmitK07E16x4`。原 `BWFastTop`、`EmitBatch`、`EmitBWFast` 不变。

沿用 `FastRequest`：128 位 vs1/vs2、32 位 vdOld、a_signed/b_signed、2 位 sew、16 位 mask、8 位 tag；响应为 32 位 result 与原样 tag。沿用 Decoupled、clock/reset 和 flush。

| sew | 本模块契约 |
|---|---|
| 0 | 16 对 E8，mask[15:0]，与原 E8 算术行为相同 |
| 1 | 仅低 4 对 E16，即向量位[63:0]与mask[3:0]；其他向量位和mask[15:4]忽略 |
| 2/3 | 返回 vdOld，保留原行为 |

E16 是新独立模块四项契约，不是原八项指令的等价替换。特别是 mask[7:4] 不属于新 E16 输入有效范围。四种符号组合均有效，累加结果模 2^32；此接口没有 vxsat 或完整香山向量结果。

E8/E16 均在输入握手沿写输出寄存器，L=1；下游持续 ready 时可每拍接收，II=1。已有响应受背压时，result/tag/valid 保持；同沿消费旧响应及接收新请求时，新响应覆盖寄存器。flush 组合屏蔽 in.ready/out.valid，并在时钟沿清 valid，取消已驻留响应。reset 同样清 valid，未要求无效状态的数据寄存器值。

## 结构与算术

仅实例化一个原 `SharedBWFrontend`，`second` 连接常量 false。其原始源码含 4 组 × 4 BW8，elaboration 对 generatorCount=16 有 require 和 RESOURCE_SUMMARY。E8 与 E16 共用这些乘法资源。

E16 四组分别形成 LL、HL、LH、HH；高字节按请求符号扩展，低字节无符号。取各组两条 carry-save 行，分别整体左移 0/8/8/16 后截取低 32 位，与 vdOld 共同进入 heap 压缩，最后 KS CPA。行在组内已经按权重对齐，不再次移 carry 行，不对每条行单独符号扩展。K07 的 S06 常量补偿及其组归约原样保留。移位与截断作用于模 2^32 表示，因此可在各组内部先做模归约。

生成入口固定 `S02,S03,S04,S06,S07`，顶层 require 精确 K07 配置；没有第三行 Q3，也没有 E16 saved/p0/p1/acc/state 寄存器。新增顶层只有响应的 40 位数据/tag 寄存器和 1 位 valid；以实际生成 RTL 检查为准。E8 与 E16 的末级压缩/CPA 各有一条，模式选择在响应寄存器前；共享的是 BW8 前端。

## 构建与验证交接

正式构建配置：Scala 2.13.17、Chisel 7.2.0，版本不改。工作目录为 WSL 对应的 `implementation/k07_e16x4` 路径，正式生成命令：

```sh
sbt 'runMain vdot.EmitK07E16x4 /absolute/new/run/generated'
```

此命令是构建入口说明，不表示已执行成功。输出必须位于新的独立 runs 目录。环境只读盘点找到 Ubuntu-22.04、`/opt/graalvm-jdk-21/bin/java`、Mill 0.12.3 缓存、firtool 1.62.1；未找到精确版本 Scala/Chisel 或 sbt 缓存。测试 Agent 接管离线依赖盘点、必要兼容构建与 RTL 验证；已有缓存版本如用于兼容构建，须单独记录实际版本与限制，不改正式依赖、不安装下载。

独立测试需覆盖：四符号、掩码、边界与随机数、E8/E16交替、sew2/3、输入高位忽略、输出背压稳定、同时consume/replace、flush及reset取消、tag顺序、持续吞吐和周期延迟；实证单前端/16 BW8和无第二批状态。源码完成不代表测试 PASS。

STA、映射与整核集成均 NOT_RUN。L=1 是周期结构契约，不能据此声称能满足旧 333.333 ps 条件；E16 组合路径及同时保留两条末级归约的面积风险仍需测量。
