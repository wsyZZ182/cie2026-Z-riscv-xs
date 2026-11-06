# 当前条件与审计结论（2026-10-29）

本轮完成只读源码/历史包检查、历史对话恢复和规划文档建设；未执行功能测试、编译、综合或STA，未修改核心RTL/生成器/旧产物。下一步等待用户批准。详见 TASK_PLAN.md 与 TEST_FLOW.md。

> **2026-11-06 更新：本节状态已过时。** 此后已完成：13 项整核 DiffTest（GOOD TRAP，17209 instr，0 失配）、6 次全核 RTL 生成、多轮 ASAP7 映射与 STA、K07 双模式时序测量。当前状态见 `K07_交接包修正_20261106.md` 与 `docs/stage3/3.7-K07双模式时序修正.md`。

## 已确认的三条工程线

| 工程 | 实际可读资产 | 已知状态 |
|---|---|---|
| 当前目录 | vdotgen、tests、六个build/experiments变体 | 独立Python→Verilog/Chisel组合生成器；非完整香山Git仓库 |
| BWFast/K07 | Downloads/BWFast_BATCH_RESULTS.zip、BWFast_First_Design.zip | 24配置源码和历史日志已找到；Scala2.13.17/Chisel7.2.0；尚未在本轮恢复运行 |
| 旧48版 | Downloads/vdot_chisel_48.zip、vdot_synth_48_deliverables.zip | 667/377个归档成员，已记录哈希与清单；未逐版本复审 |
| ASAP7预测器 | Downloads/asap7_deliverable.tar.gz | 14个文件；预测器静态检查确认固定延迟、corner参数未用于计算；原报告承认映射失败、缺库、无OpenSTA |

完整路径、文件SHA256、成员名与选读内容保存在 audit-20261029/archive-inventory.json。压缩包只读打开，未覆盖或解压到工程。另找到下载目录中的指令设计文档与项目技术记录，可用于恢复实际接入位置。

## 历史结果的真实证据及边界

本地六变体每个manifest记录2200组Boolean DAG检查；其源哈希均与当前生成器相符，manifest所列产物哈希全部匹配。当前生成器SHA256为 0c9bf6d8f20346fe797b17edaf219dfe7bb7a01500baa7447ee699ae07d64a6e。RTL仿真/形式/Chisel仍not_run，综合/STA为skipped。历史聊天工具输出有14项Python测试OK；本轮没有重跑。

BWFast历史summary有24行PASS_RTL_GENERIC。K07 result.json记录263812 checks、11454组合单元、621寄存器位、65层，STA=NOT_RUN_NO_LIBRARY。原始20份sim_seed日志均可读取，含RESULT: PASS、errors=0及覆盖信息；原始综合synth.log/stat/ltp和工具版本也已保存。数字属于历史归档运行，不能称作本轮复现；65层是通用网表最长路径深度，不是65ps，也不保证就是独立E8路径。

> **2026-11-06 更新：`sta_status` 已不再是 NOT_RUN。** K07（K07 配置，`BWFast.scala` 哈希 `ccd16edf…`）在统一 ASAP7 流程下实测 **2694.5 ps / 150.1 FO4 / 1086 µm²**（最慢路径起点 `state[1]`，属 E16 状态机控制路径）。`depth: 65` 仍只是抽象门深度，不可与 ps 混用。

历史K07 RTL SHA256：6860564cb5097b417eecc8467036290878c7dd1ac09d4c967d91eec3cf58799d（归档结果记录，正式复现还需关联实际RTL文件重算）。日志环境路径为 /home/gingk/bwfast_batch24/bw_batch24；版本日志Yosys0.58、Verilator5.048、Oracle GraalVM21.0.12。另一历史ASAP7叙述中的Yosys0.70/Java17不可混为同一环境。

ASAP7的150/430/920/3225ps、demo830ps与comparison.csv中的K07 69000不作为真实STA证据。原包comparison标记generic_synth_only/asap7_est/pending；predictor不读取真实逐弧数据库，connections未参与计算、缺失输入默认零到达、demo计数器引脚数不符合名称。未运行预测器；这些结论来自源码静态阅读。原包缺SEQ/INVBUF、ABC错误8B及无STA是其报告承认的限制。

## 当前工具条件

实际路径盘点：Python3.12.10、Git、wsl.exe存在；Windows PATH未找到java/sbt/mill/verilator/iverilog/vvp/yosys/sta。WSL列举返回4294967295和Wsl/E_ACCESSDENIED，因此发行版及内部工具状态未知，不能写成“WSL没有安装工具”。本轮未提权、未安装。原始结果见 audit-20261029/environment.json。

现有ASAP7部分库文件在归档中，不能称完全没有库；缺的是可验收的完整映射/时序链路、完整库和已冻结约束。真实香山工艺库/SDC、主仓及yunsuan/NEMU/Difftest确切提交尚未验证；当前目录git status明确报非Git仓库。

下载目录《XiangShan项目技术记录.md》提供历史位置 /home/wwzzyy/cie2026-Z-riscv-xs、remote git@github.com:wsyZZ182/cie2026-Z-riscv-xs.git 及旧提交6a136aa线索。这些是文档记载，尚未核实对应本机WSL或当前分支，不能要求用户从零提供已找到的信息。

《2.1-vdot-vv-设计文档.md》指向 yunsuan/vector/vdot/VDotCore.scala 与 VDotFu.scala，描述原整核128位vd输出、vxsat、vl/vstart/vm/ta及E32路径；BWFast只有32位result/tag和模累加，sew=2/3返回旧值。直接替换会缺少语义，需冻结新版ISA与封装。旧文档中的溢出判定及“两拍可收敛”也只是历史描述，不能替代宽和范围证明或STA。

## 已知需求与待核对内容

历史目标是昆明湖V2 VDot，四模式BW、四组16个BW8、E8无背压L=1/II=1、E16复用共享前端、mask/flush/背压/结果顺序；BWFast为32位模累加，独立FastRequest/FastResponse不自动等于正式香山接口。E16两批运算与四拍控制可同时成立，需按状态机逐拍核对，不能直接判作冲突。

已读取11个相关聊天的工具可返回页面，快照均报告hasMore=false，内容有历史引用/附件未内联以及较长工具输出截断；不声称取得所有原始聊天和附件。历史索引在 audit-20261029/HISTORY.md。旧48版、24版、15/24进度、N00～N15及ASAP7五版本标定应按来源版本区分。新批次具体选择和正式ISA饱和/输出宽度等仍需结合实际源码冻结。

建议批准P00～P03：恢复独立历史工程与可执行环境→复现K07→补齐库并打通综合/STA→标定和修正预测器。待基础证据可靠后再实施N00～N15，不凭已有估算淘汰候选。当前等待批准是用户原始要求，不是工具自动审批拒绝。

## 最新状态：K16 实施已获批准

2026-10-29 用户明确批准K07四对E16固定拍数通路，旧文中的“等待批准”对本工作包已被取代。已成功访问Ubuntu-22.04，发现 /home/cyy/xs-env/XiangShan 与 yunsuan，mill/verilator位于/usr/local/bin；Java与构建依赖由测试Agent继续核查。开发及测试已派发；此刻尚无本轮运行PASS证据。保持原源码及历史产物，独立实现，不修改香山主体。

## 本机整核只读定位（2026-10-29）

WSL Ubuntu-22.04 的 /home/cyy/xs-env/XiangShan HEAD=16ae9ddcda54fc9a2fddffad73174cf793ac7814；其 yunsuan HEAD=cadd3c2f43096e253d61296b33ac697be8354e29。已读取 yunsuan/src/main/scala/yunsuan/vector/VectorALU/VIAlu.scala：ValidIO输入/输出，validS1=RegNext(in.valid)，out.valid=GatedValidRegNext(validS1)，当前输出只选择Reduction/Mask/vmvxs，没有VDot。此次查找也未找到VDotFu.scala或BWFast.scala。因此本机此检出不能视为用户历史中“原VDot已经接入”的版本；不在该版本上擅自接入或改变调度。独立K16模块实现不受此限制，整核接入需另定位已集成版本。

## K16 L1 源码审查

2026-10-29：已独立读取新增顶层及原前端。新顶层固定K07配置（无Q3），因此消费s/c两行与原设计一致；单SharedBWFrontend、second=false；低四对位权与旧值融合；输出寄存器支持同沿消费/替换，flush优先清valid。原五个恢复文件逐一重算哈希全部匹配，见 audit-20261029/k16-source-review.json。此结论为源码审查，不替代编译/RTL仿真。构建依赖核查及独立测试资产准备仍在进行。


2026-10-29 用户最新指令：仅交改好的代码，后续整合/编译交其他Agent。本轮停止新增验证工作；源码交付 implementation/K07_E16x4_source.zip。开发完成，源码审查完成；离线兼容展开由测试Agent报告成功，功能/STA未验收。

## ASAP小批次完成（2026-10-29）

A0～A3已交付K16_ASAP_VARIANTS_20261029.zip。L1已读取候选调度/CPA源码、测试汇总及原始日志：四款兼容编译和每款3seed功能回归通过，共91652接受事务；范围内PASS，不代表形式证明、正式Chisel7.2构建或STA。粗估A1 Dadda+KS相对A0 E8约-3.4%、E16约-3.8%，保留为后续候选而不取代基线；A2面积代理略低但慢，A3当前模型较慢。真实映射/STA NOT_RUN。详细来源、方法、限制见K16_ASAP_ITERATION.md。
