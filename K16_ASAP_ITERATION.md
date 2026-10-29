# K16 ASAP7 小范围迭代

2026-10-29。交付状态：NEEDS_REVIEW；四款源码、离线兼容编译和本轮RTL回归完成。推荐保留 **A1 Dadda + KS** 为下一次实际映射/STA的优先候选，保留A0对照；粗估优势只有约3%～4%，不能当作已实现的时序改善。未改香山主体或历史源码，没有安装工具。

## 冻结范围和决策

用户授权小范围压缩树/最终加法器迭代，L1确认只做A0～A3。独立目录 `implementation/k16_asap_variants/` 是 `implementation/k07_e16x4/` 的可独立构建副本。A0走原始 `FastLogic.reduce`、原始chooseSize/fastCount和KS；其余仅改变归约调度与最终CPA。两行均已对齐，carry行不再额外移位；模2^32不变。

一个SharedBWFrontend、4组×4 BW8，S06符号补偿与S07路由不变；E8十六对、E16低四对、四符号、L=1/无背压II=1、背压/flush/tag契约不变。E16四对仍不是旧八项ISA替换。

初稿A1只强制3:2，发现原A0在本K16的1257次调度已全部选3。初稿A0/A1 RTL SHA256均为 `1e0b12148b6ef382449f9c34bfab513c50dbeb8df05174b0acf3f346b64d8b61`，因此不能声称强制FA就会改善。L1随后批准A1～A3共用明确Dadda高度序列2,3,4,6,9,13,19,28…，逆序逐级归约；FA减少本列两位，必要HA减少本列一位，carry加入下一列。每级只消费旧输入，传入carry只计目标高度、不在本级再压缩；同列旧输入按原到达代理值取最早位。最终两行。此更改没有增加候选数量，详见 `matrix_revision.json`。

## 修正数据来源

输入 `C:/Users/陈颖旸/Downloads/asap7_deliverable_v2_20261029.zip`，SHA256 `3c8f32d1a7204f2d4ea9e5d013ad0ed4ee79fdec12fa3774b1f8920b9e930dd1`。仅选读说明、清单、CSV和TT/SS NLDM JSON，没有全包展开或重跑原包工具。202单元/角、5167弧/角；TT=0.70V/25°C，SS=0.63V/100°C。成员哈希在 `evaluation.json`，小型原始报告/CSV已留 `input_evidence/`。

原包TT/20ps/5.76fF STA记录：3:2=85.080ps；FA7=153.264ps、GuoLi7=160.396ps；FA15=236.920ps、GuoLi15=292.845ps。这些是原归档记录，不是本轮重跑，说明不应先验迷信大计数器。原包tree16是16操作数加法树，不能当点积乘法路径。**明确排除**仍混在新版内的 `timing_database/compressor_results.json`（150/430/920/3225旧常数）。原包4.01%均值/18.09%最大误差只来自原9电路288点，不是本候选置信范围。没有继承“理想线不影响相对排名”的断言。

## 同条件粗估方法与结果

范围为**BW部分积位到归约+CPA的组合子路径**，输入部分积位到达均设零；不包含输入路由、部分积/符号位生成、最终模式选择、寄存器或时钟约束。排程使用原源码t=3/4等门层数代理，时延计算另做，不把层数当ps。

Python构建对应Scala的Boolean DAG，A0完整重现原chooseSize、reference/prefix/排序网择优规则、parallelTail；A1～A3重现Dadda逐级FA/HA，CPA分别KS/BK/显式双carry四位CSLA。仅统计最终根可达节点，不计未选中候选逻辑。每个XOR/AND/OR指定一个真实ASAP7 x2单元；扇出负载为接收引脚电容之和，最终每位附5.76fF，逐输入弧取rise/fall及when分支最大表值。**所有内部弧固定20ps输入slew**，没有做动态slew传播；MUX保守展开为AND/OR/XOR，与综合优化后的MUX/AOI等不等价。常量没有折叠；负载在表外会钳位，本次输出锥门未出现高负载钳位。数值是未映射图固定单元赋值代理，不能解释为实际模块延迟、STA或置信范围。

以下是TT、零额外线电容代理，延迟单位仅为该模型ps；两模式分别计算可达锥，面积列用双模式锥并集避免重复计共享前端。

| 款 | 组合 | E8代理ps/相对A0 | E16代理ps/相对A0 | 并集原语门数 | 固定单元面积代理µm² |
|---|---|---:|---:|---:|---:|
| A0 | 原K16调度 + KS | 1268 (+0.0%) | 1206 (+0.0%) | 7530 | 863.7 |
| A1 | Dadda FA/HA + KS | 1225 (-3.4%) | 1160 (-3.8%) | 7540 | 865.2 |
| A2 | Dadda FA/HA + Brent–Kung | 1363 (+7.5%) | 1288 (+6.8%) | 7108 | 827.4 |
| A3 | Dadda FA/HA + 4位块CSLA | 1599 (+26.1%) | 1525 (+26.4%) | 7416 | 863.4 |

这不是映射后真实面积，连BW位生成及控制也未计入；不能与历史综合面积直接比较。A2保留了较少前缀节点，面积代理比A0低约4%，时延代理更差；A3的四位块线性选择链在此模型不利，真实MUX映射可能改写结论。

TT/SS和每sink附0或0.5fF的四组敏感性条件下，两模式顺序均为 A1 < A0 < A2 < A3。0.5fF只是自选扰动，不是提取RC；无法保证布线后排序不变。保留所有CSV和完整机器可读字段，拒绝以小幅代理优势宣布时钟收敛。

## 本轮验证

| 层次 | 状态 | 证据/边界 |
|---|---|---|
| 算术模型 | PASS | seed=20261029，每款4组×256守恒、256 E8、256 E16、256 CPA，共7168比较；CPA含12项全进位/边界向量 |
| 兼容Scala编译和RTL生成 | PASS | 实际Scala2.13.15/Chisel6.6.0、GraalVM21、firtool1.62.1；正式build保持2.13.17/7.2.0 |
| 结构检查 | PASS（有限） | 四款RESOURCE_SUMMARY=16BW8；模型/Scala每级层数一致；协议顶层与基线逐字相同 |
| RTL功能回归 | PASS | 每款3seed，复用独立标量参考/周期记分板；见下文 |
| 正式Chisel7.2构建 | NOT_RUN | 没有安装或下载正式版本依赖 |
| 形式证明 | NOT_RUN | 模型抽样不是形式证明 |
| 综合/库映射/STA/布线/整核 | NOT_RUN | 本轮仅粗评和功能验证 |

复用 `testing/k16/sim_main.cpp`。每款相同3seed（1/20261029/3735928559），每seed11440周期，接受7635/7660/7618项，共22913个L1检查；E16 sign×mask为64/64，sew×sign为16/16；覆盖高半输入忽略、E8/E16交替、持续II1、消费/替换、背压稳定、flush/reset取消、tag和旧值回绕。四款合计91652个已接受事务，原始日志逐款留存。此处PASS仅属于本轮测试范围，仍交L1独立验收。

首个内联WSL循环因命令行引号导致空变量而失败，日志 `simulate.log` 和 `simulation_status.txt` 原样保留；改用保存脚本后四款最终运行成功，失败未计PASS。没有删除失败记录。

## 复用和交付

在WSL以工程根为工作目录，预建一个**新**run目录：

```sh
bash implementation/k16_asap_variants/compile.sh /absolute/new/run
bash implementation/k16_asap_variants/simulate_variants.sh /absolute/new/run
# A0可同样调用已有 testing/k16/simulate.sh /absolute/new/run/A0
```

正式依赖可用时，候选入口为 `sbt 'runMain vdot.EmitAsapVariant A1 /absolute/new/run/generated'`。离线兼容入口只使用已有缓存，不联网安装。`evaluate.py` 的OUT固定本轮目录，复现时先修改OUT到新目录，勿覆盖本轮记录。`prepare.py` 只用于恢复拷贝，最终选择器实现保存在AsapVariant.scala，不是自动优化搜索。

交付包包含独立源码、报告、源哈希/RTL、算术模型与数据来源、原始编译/仿真日志和复用的测试文件；不打包大体积原ASAP7库或中间C++构建产物。参考数据库由原zip和成员SHA256追踪。下一步只需A0/A1统一映射/约束下确认能否保留3%～4%的代理优势；本轮没有扩为全候选优化。
