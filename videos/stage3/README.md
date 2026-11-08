# 阶段三 · 波形仿真与性能分析录屏

对应赛题第三阶段要求：「基于波形仿真和性能分析」录屏（3–5 分钟）。

| 项 | 值 |
|---|---|
| 文件 | `k07_wave_perf.mp4` |
| 时长 | 4:00 |
| 分辨率 | 1920×1080 / 15 fps |
| 大小 | 1.7 MB |
| 内容 | 0:00–2:00 波形仿真；2:00–4:00 性能数据 |

---

## 一、分镜

### 第一段：波形仿真（0:00–2:00）

| 时间 | 画面 | 讲解要点 |
|---|---|---|
| 0:00–1:00 | GTKWave 打开 `vdot.vv` 的 VCD；信号组 `k07E8Mode` / `k07E16Mode` / `k07Fire` / `pass2Fire` / `validS1` / `vsewS2` / `io_in_bits_e16` / `io_out_bits_result` / `k07E8ResultS2` / `vs1HiS1` / `vs2HiS1` | Verilator 生成 VCD，GTKWave 查看执行过程；`k07Fire` 两个脉冲分别对应 e8 与 e16；`io_out_bits_result` 由 `0x88` 变为 `0x24` |
| 1:00–2:00 | 放大到 e16 窗口（`t = 5532–5569`） | `k07Fire` 发第 1 笔（元素 0–3）；下一拍 `pass2Fire` 发第 2 笔（元素 4–7）；`vs1HiS1` 暂存第 2 笔操作数；两笔合成 `0x24`（36），单次写回 |

### 第二段：性能数据（2:00–4:00）

| 时间 | 内容 |
|---|---|
| 2:00–2:20 | 封面 |
| 2:20–2:40 | 周期口径：vdot 1.78 vs 基线 7.1 周期/op → **3.99×** |
| 2:40–3:00 | 路径口径：K07 2128.0 ps vs 原生 `vwmul` 4885.7 ps → **2.30×** |
| 3:00–3:20 | 分模式时序：E8 1759.5 / E16 1737.0 / 动态 2128.0 ps |
| 3:20–3:40 | 面积：K07 671.18 µm²，整块两笔 +106.49 µm²（+2.56%） |
| 3:40–4:00 | 口径边界：两个口径不可相乘；正式口径待 `KunminghuV2Config` 重测 |

---

## 二、波形从哪来

**不是示意图，是真实仿真数据。**

| 步骤 | 做法 |
|---|---|
| 1 | Verilator 加 `--trace` 重新编译 emu（`build9`），其余参数与功能验证版完全一致 |
| 2 | 跑专用测试程序 `tools/vdot-wave.c`：e8 一条 + e16 一条，前后留 `nop` 便于观察 |
| 3 | `--dump-wave-full --log-begin=2690 --log-end=2745` 只 dump 目标窗口，得 59 MB VCD |
| 4 | GTKWave 3.3.100（Windows 版）+ `tools/load_win.tcl` 按完整层次路径加载信号 |

波形时间戳满足 `t = 2 × cycleCnt`，因此 `cycle 2690–2745` 对应 `t = 5484–5595`。

信号完整路径前缀：

```
TOP.SimTop.l_soc.core_with_l2.core.backend.inner_vfExuBlock.exus_1.Vipu.vialu.vDotAlu
```

---

## 三、关键波形证据

```
t=5514  k07Fire=1                      ← e8 单笔，io_out_bits_result = 0x88 (136)
t=5544  k07Fire=1, k07E16Mode=1        ← e16 第 1 笔（元素 0–3）
t=5546  pass2Fire=1, k07Fire=0         ← e16 第 2 笔（元素 4–7），隔 1 拍
t=5548  pass2Fire=0                    ← 两笔完成，io_out_bits_result = 0x24 (36)
```

`vs1HiS1 = 0x0008000700060005` 即第 2 笔的操作数（8,7,6,5），是「首笔结果回灌 `vdOld`」的直接证据。

两笔的间隔恰好 1 个时钟周期，与 `VipuCfg.latency = 2` 的窗口对齐——这正是 `VIAlu` 不需要改动的原因。

---

## 四、复现

```bash
# 1. 编译带 trace 的 emu（约 12 分钟）
verilator ... --trace ... -o build9/emu

# 2. 编译波形测试程序
cd $AM_HOME/apps/k07-wave && make ARCH=riscv64-xs

# 3. dump 波形（全景 / e16 窄窗）
./emu -i k07-wave-riscv64-xs.bin --diff=<nemu-so> \
      --dump-wave-full --wave-path=vdot_win.vcd \
      --log-begin=2690 --log-end=2745

# 4. GTKWave 加载
gtkwave -S videos/stage3/tools/load_win.tcl
```

完整脚本见 `tools/`：

| 文件 | 用途 |
|---|---|
| `vdot-wave.c` | 波形专用测试程序（e8 + e16 各一条） |
| `load_win.tcl` | GTKWave 自动加载 + 加信号（全景窗口） |
| `load_e16.tcl` | 同上，e16 窄窗 |
| `dump_win.sh` | dump 全景窗口（cycle 2690–2745） |
| `dump_e16.sh` | dump e16 窄窗（cycle 2714–2732） |

---

## 五、相关文档

| 内容 | 位置 |
|---|---|
| 波形对应的功能验证（13 项 DiffTest、E16 两笔接入） | `docs/stage3/3.1-NEMU-vdot-指令扩展与协同仿真验证.md` §3.4 |
| 时序与加速比（路径口径 / 周期口径） | `docs/stage3/3.3-延迟加速比分析.md` §一、§二 |
| 面积口径 | 同上 §二（1.7 面积） |
