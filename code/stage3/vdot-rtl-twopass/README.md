# stage3 / vdot-rtl-twopass

K07 `vdot.vv` 的 **E16 两笔实现**源码副本，对应文档 `docs/stage3/3.9-K07-E16两笔实现.md`。

## 与 `code/stage2/vdot-rtl/VDotFu.scala` 的关系

| 项 | stage2（单笔） | stage3（两笔，本目录） |
|---|---|---|
| 文件 | `VDotFu.scala` 164 行，SHA256 `018da2fd…` | `VDotFu.scala` 229 行 |
| E8 | K07 单笔，16 对 | 同左，未改 |
| E16 | K07 单笔，**只算低 64 位的 4 对**（语义缺口） | K07 **两笔**：低 4 对 + 高 4 对，8 对完整 |
| E32 | VDotCore | 同左，未改 |
| `VIAlu` / `VipuCfg` | latency = 2 | **不变**，仍为 `CertainLatency(2)` |

两笔版只改 `VDotFu.scala` 一个文件。`VIAlu.scala`、`VipuCfg`、`K07.sv` 全部保持原样。

## 两笔怎么排进 2 拍

`VipuCfg.latency = CertainLatency(2)`，`VIAlu` 在 fire 后第 2 拍用 `opcodeS2` 选通数据。

| 拍 | K07 请求 | K07 输出 | 说明 |
|---|---|---|---|
| `t0`（fire 沿） | 第 1 笔：元素 0..3，`vdOld = old_vd[31:0]` | — | 同时锁存第 2 笔操作数到 S1 |
| `t1` | 第 2 笔：元素 4..7，`vdOld = ` **K07 当前输出** | 第 1 笔结果 | 组合直连，不打拍 |
| `t2` | — | 第 2 笔结果 | 正是 `opcodeS2` 采样沿 |

`vdOld` 的加法在 K07 的 fused accumulator 内完成，所以第 2 笔输出 = `sum(0..7) + old_vd`，与 e8 的 `sum(0..15) + old_vd` 同构。

## 约束

K07 每拍只接受一个请求，E16 的第 2 笔占用 fire 后第 1 拍。理论上 E16 后面紧跟另一条 `vdot` 会争用端口，代码里用断言暴露：

```scala
assert(!pass2Conflict,
  "VDotFu: e16 pass-2 request slot collides with a new vdot request ...")
```

实测（背靠背压力测试，两条 `vdot` 的 PC 差 4 字节）断言从未触发：香山 vipu 的实际发射节奏会插入间隔。该断言是防御性检查。

## 验证

- 13 项整核 DiffTest：**13/13 PASS**，`HIT GOOD TRAP`，`instrCnt = 17209 / cycleCnt = 63027`
- E16 正数 36（原 10）、E16 负数 −36（原 −10）
- 背靠背 3 项 PASS
- 面积增量 **+106.49 µm²**（整块 VDotFu 4152.92 → 4259.42 µm²，**+2.56%**；ASAP7 RVT TT 实测）

完整证据：`xs_cycle_calibration/runs/k07_e16_twopass_20261107/`（24 项含 SHA256SUMS）。

## 尚未回流生产树

`/home/xiangshan/projects/xs-env/XiangShan` 仍是单笔版（`018da2fd…`）。本目录是隔离副本
`/home/xiangshan/k07_dual_xs_difftest_20261106/XiangShan` 验证通过的版本，回流需要另行确认。
