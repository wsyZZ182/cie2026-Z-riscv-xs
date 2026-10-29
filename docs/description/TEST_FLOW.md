# 测试流程设计（尚未实施）

## 执行与证据规则

由L3运行、L1验收；L2提供待测源码与接口，不能自行批准自己的结果。所有新运行写唯一 runs/<run_id>/，原包及历史build只读。阶段分别保存状态，失败保留日志并阻止依赖阶段；允许其他独立候选继续，不把失败隐藏在最终汇总中。

每次记录run_id、task_id、candidate、主仓/子仓commit（缺失为null并给源码hash）、源码包/RTL/脚本/测试/库/SDC SHA256、工具路径/版本、OS、cwd、完整命令、开始结束时间（Asia/Shanghai及UTC）、退出码、seed、样本/周期/接受/输出/取消数、日志/网表/报告路径及hash。结果值缺失用null，禁止填0或估算补齐。临时目录使用可写路径，失败清理不覆盖证据。

## 阶段与验收

| 阶段 | 执行内容 | PASS条件/证据 |
|---|---|---|
| T0环境 | 工具版本、依赖缓存、源版本、完整库与约束 | 工具实际可执行；缺工具BLOCKED；WSL权限错误不等于没安装 |
| T1编译 | 固定Scala/Chisel/sbt/JDK，elaborate真实顶层 | 原始编译/展开日志、退出0、RTL hash；无黑盒/未驱动/意外锁存 |
| T2单元 | BW8四模式；3:2/4:2/7:3/15:4；CPA、偏置 | BW8 4×256×256穷举；7/15真值表穷举；31位随机+形式；独立整数参考 |
| T3模块RTL | Verilator/Icarus scoreboard、四模式E8/E16 | 定向+固定20 seeds，零错误，所有必需覆盖命中；日志有明确PASS及实际计数 |
| T4等价/结构 | 新旧语义/周期、计数器形式、结构清单 | 全输入组合证明或注明有界范围；超时非PASS；实际4×4 BW8及共享前端、CPA类型、各counter计数、寄存器统计 |
| T5综合 | 同库同约束逐候选映射 | 完整综合日志/脚本/网表；无未映射算术/黑盒；面积来自库；通用门映射单列 |
| T6 STA | 完整E8、E16、控制路径setup/hold | 单位/角/负载/输入slew明确，约束覆盖合格，报告解析无错误；所有要求角WNS≥0、TNS=0且hold无违例才能宣称该条件收敛 |
| T7预测器 | 用已映射实际单元逐弧标定与独立验证 | 拓扑合法/无环/无浮空，corner切换真实生效；记录最大/均值/分位误差、排序一致性、关键路径；10%为目标非保证 |
| T8整核 | 香山/yunsuan/NEMU/Difftest接入 | 固定指令/分支版本，指令语义、vl/vstart/vm/mask/tail、异常/flush回归；独立模块PASS不能替代 |

T2/T3边界：零、全一、最小负数、最大正数、交替位、累加回绕、全部mask关/开、单lane、稀疏mask；E16低字节始终无符号，高字节依操作数符号，覆盖LL/HL/LH/HH权重。测试同时含随机与可人工计算的反例。

协议scoreboard仅在valid&&ready接受；flush优先级按冻结状态机定义，覆盖每个E16状态及输出背压时flush、连续E8、E8/E16交错、reset、tag环绕、结果顺序与不丢不重。背压输出保持稳定。无背压实测E8 L=1/II=1，E16 L/II按状态机单独记录；接受数=输出数+取消数+在途数。不能仅比较最终数值。

T6不得固定真实mode/state/mask掩盖路径，不增false path/multicycle掩盖违例。报告起终点和各寄存器边界、时钟不确定性、I/O delay、setup/hold、WNS/TNS、未约束路径和面积。ASAP7缩放、time/capacitance单位及TT/SS标称条件从库读取；SEQ/INVBUF缺失不得靠假单元补齐。组合虚拟时钟分析与真实寄存器路径分别标记；未布局布线的STA不叫物理签核。

## 自动收集器拟实现接口

输入冻结 manifest.json（variants、seeds、工具、库、SDC、顶层、超时与资源限制）；输出每阶段 result.json、汇总 results.json/summary.csv 和 review.md。执行顺序T0→T1→T2/T3/T4→T5→T6；T7/T8依赖对应资产。阶段状态由退出码+工具成功标志+计数/覆盖+报告有效性共同决定，保留工具原始诊断。

需实现并验证：编译失败、仿真FAIL但退出0、零测试、形式timeout、映射失败、STA未约束/负slack、报告缺字段、重复运行和源文件变化时缓存失效。缓存键含源码、工具、脚本、测试、库/SDC哈希；只能复用精确匹配结果。运行中断标INTERRUPTED，不能沿用旧PASS。

当前vdotgen/__main__.py已有experiment --eda入口；flow.py可保存部分日志和hash，但尚未覆盖Chisel构建、完整状态协议、全工具版本、覆盖汇总、稳健报告解析、统一最终manifest。批准后扩展，当前未修改。

## 命令依据（设计参考，当前未执行）

本地生成器README已有 python -m unittest discover -s tests -v 与 python -m vdotgen experiment ... --eda 入口。BWFast历史包有 run_batch_wsl.sh、sbt compile、runMain vdot.EmitBatch 和 sim_main.cpp；原runner可能重建结果目录，恢复后必须先改为唯一输出目录，再运行。STA_DRIVER仅为历史预留入口，尚不能直接作为已完成STA流程。正式命令必须在P00解析实际路径后写入版本化脚本，禁止执行未替换占位符。

## K16 本轮增量验证范围（2026-10-29）

用户要求节省额度，本轮不做24候选全回归。K16先编译与smoke，再覆盖四符号、E16全部16种低四位mask、边界和累加回绕，以及至少3个固定随机种子的混合E8/E16、背压、flush、tag、reset与连续吞吐测试。E8/E16均目标L=1/无背压II=1。这是增量功能验收范围，不宣称完成上表正式20-seed全回归、形式证明、整核DiffTest或STA。若出现失败，保留反例并针对修复回归。

E16新模块契约是低四对有效，高四对不属于其计算范围；不据此改变整核八项ISA。测试须覆盖高半输入扰动不影响四对结果，并明确接口使用约束。
