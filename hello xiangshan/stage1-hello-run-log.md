# 阶段一 · 环境部署验证（1.1）执行日志

- 执行时间：2026-09-04 22:1x（北京时间）
- 执行环境：WSL（用户 xiangshan，`/home/xiangshan/projects/xs-env`）
- 操作人：由 AI 助手在用户 WSL 中后台执行

## 执行命令

```bash
source ~/projects/xs-env/env.sh
cd ~/projects/xs-env/NEMU
make riscv64-xs_defconfig
make -j
./build/riscv64-nemu-interpreter -b ~/projects/xs-env/nexus-am/apps/hello/build/hello-riscv64-xs.bin
```

## 1. 加载环境变量（env.sh）

```
SET XS_PROJECT_ROOT: /home/xiangshan/projects/xs-env
SET NOOP_HOME (XiangShan RTL Home): /home/xiangshan/projects/xs-env/XiangShan
SET NEMU_HOME: /home/xiangshan/projects/xs-env/NEMU
SET AM_HOME: /home/xiangshan/projects/xs-env/nexus-am
SET DRAMSIM3_HOME: /home/xiangshan/projects/xs-env/DRAMsim3
```

## 2. 编译 NEMU（make riscv64-xs_defconfig + make -j）

编译过程正常完成，最终生成可执行文件：

```
+ g++ /home/xiangshan/projects/xs-env/NEMU/build/riscv64-nemu-interpreter
```

> 编译期间出现 2 条无害警告（不影响本次构建与运行）：
> - `resource/gcpt_restore` 与 `resource/nanopb` 子模块 clone 超时（GitHub 网络问题）。这两个为可选组件，阶段三如需 NEMU 的 checkpoint / 序列化功能再补拉。

## 3. 运行 hello 程序（NEMU）

```
Hello, XiangShan，I am Tang, IP address
[src/isa/riscv64/init.c:220,init_isa] NEMU will start from pc 0x80000000
[src/device/io/port-io.c:35,add_pio_map_with_diff] Add port-io map 'uartlite' at 00000000000003f8, 0x0000000000000404
[src/device/io/port-io.c:35,add_pio_map_with_diff] Add port-io map 'screen' at 0000000000000100, 0x0000000000000107
[src/device/io/port-io.c:35,add_pio_map_with_diff] Add port-io map 'keyboard' at 0000000000000060, 0x0000000000000063
[src/device/sdcard.c:137,init_sdcard] Can not find sdcard image:
[src/monitor/image_loader.c:204,load_img] Loading image (checkpoint/bare metal app/bbl) form cmdline: /home/xiangshan/projects/xs-env/nexus-am/apps/hello/build/hello-riscv64-xs.bin
[src/monitor/image_loader.c:260,load_img] Read 5416 bytes from file /home/xiangshan/projects/xs-env/nexus-am/apps/hello/build/hello-riscv64-xs.bin to 0x0x100000000
[src/monitor/monitor.c:60,welcome] Debug: OFF
[src/monitor/monitor.c:65,welcome] Build time: 22:14:29, Sep  4 2026
Welcome to riscv64-NEMU!
For help, type "help"
[/home/xiangshan/projects/xs-env/NEMU/src/isa/riscv64/include/../instr/special.h:38,execute] nemu_trap case 0
[src/cpu/cpu-exec.c:902,cpu_exec] nemu: HIT GOOD TRAP at pc = 0x000000008000014c
[src/cpu/cpu-exec.c:908,cpu_exec] trap code:0
[src/cpu/cpu-exec.c:154,monitor_statistic] host time spent = 346 us
[src/cpu/cpu-exec.c:156,monitor_statistic] total guest instructions = 1050
[src/cpu/cpu-exec.c:157,monitor_statistic] vst count = 0, vst unit count = 0, vst unit optimized count = 0
[src/cpu/cpu-exec.c:160,monitor_statistic] simulation frequency = 3034682 instr/s
[src/utils/state.c:30,is_exit_status_bad] NEMU exit with good state: 2, halt ret: 0
```

## 结果判定

- ✅ 打印输出：`Hello, XiangShan，I am Tang, IP address`（对应官方要求 `hello xiangshan, I am xxx, IP address`）
- ✅ 模拟器正常结束：`nemu: HIT GOOD TRAP`，`trap code:0`，`good state: 2, halt ret: 0`
- ✅ 阶段一 1.1「环境部署验证」完成

## 后续可复用命令

```bash
# 运行 hello（快速验证）
cd ~/projects/xs-env/NEMU && ./build/riscv64-nemu-interpreter -b ~/projects/xs-env/nexus-am/apps/hello/build/hello-riscv64-xs.bin

# 编译香山 emu（阶段 1.3 出波形用，耗时较长）
cd ~/projects/xs-env/XiangShan && make emu EMU_TRACE=1 -j32
```
