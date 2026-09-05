# 阶段一 · 环境部署验证（1.1）工作日志

- 执行时间：2026-09-05
- 执行环境：WSL Ubuntu-22.04（用户 xiangshan，`/home/xiangshan/projects/xs-env`）
- 队伍名称：Z
- 赛题：2026 CIE 全国 RISC-V 高水平创新及应用大赛 — 面向边缘AI大语言模型推理的RISC-V自定义vdot指令设计（基于香山昆明湖V2）

## 一、环境变量加载

```bash
cd ~/projects/xs-env && source env.sh
```

输出：
```
SET XS_PROJECT_ROOT: /home/xiangshan/projects/xs-env
SET NOOP_HOME (XiangShan RTL Home): /home/xiangshan/projects/xs-env/XiangShan
SET NEMU_HOME: /home/xiangshan/projects/xs-env/NEMU
SET AM_HOME: /home/xiangshan/projects/xs-env/nexus-am
SET DRAMSIM3_HOME: /home/xiangshan/projects/xs-env/DRAMsim3
```

> 注：已将 `source ~/projects/xs-env/env.sh` 写入 `~/.bashrc`，每次打开终端自动加载。

## 二、hello.c 修改

文件路径：`~/projects/xs-env/nexus-am/apps/hello/hello.c`

修改内容（队伍标识为 Z）：
```c
#include <klib.h>

int main()
{
    printf("Hello, XiangShan，I am Z, IP address\n");
    return 0;
}
```

对应赛题阶段一 1.1 要求：输出 `hello xiangshan, I am xxx, IP address`。

## 三、编译 hello 程序

```bash
cd ~/projects/xs-env/nexus-am/apps/hello
make ARCH=riscv64-xs -j8
```

编译输出（关键部分）：
```
# Building hello [riscv64-xs] with AM_HOME {/home/xiangshan/projects/xs-env/nexus-am}
# Building lib-klib [riscv64-xs]
# Building lib-am [riscv64-xs]
# Creating binary image [riscv64-xs]
+ LD -> build/hello-riscv64-xs.elf
+ OBJCOPY -> build/hello-riscv64-xs.bin
```

产物：`build/hello-riscv64-xs.bin`

## 四、编译 NEMU 模拟器

```bash
cd ~/projects/xs-env/NEMU
make riscv64-xs_defconfig
make -j
```

编译成功，生成可执行文件：`build/riscv64-nemu-interpreter`

> 编译期间出现 2 条无害警告：`resource/gcpt_restore` 与 `resource/nanopb` 子模块 clone 超时（GitHub 网络问题），不影响本次构建与运行。

## 五、运行 hello 程序（NEMU）

```bash
cd ~/projects/xs-env/NEMU
./build/riscv64-nemu-interpreter -b ~/projects/xs-env/nexus-am/apps/hello/build/hello-riscv64-xs.bin
```

运行输出（关键部分）：
```
Hello, XiangShan，I am Z, IP address
[src/isa/riscv64/init.c:220,init_isa] NEMU will start from pc 0x80000000
[src/monitor/image_loader.c:204,load_img] Loading image ... hello-riscv64-xs.bin
Welcome to riscv64-NEMU!
[src/cpu/cpu-exec.c:902,cpu_exec] nemu: HIT GOOD TRAP at pc = 0x000000008000014c
[src/cpu/cpu-exec.c:908,cpu_exec] trap code:0
[src/cpu/cpu-exec.c:154,monitor_statistic] host time spent = 346 us
[src/cpu/cpu-exec.c:156,monitor_statistic] total guest instructions = 1050
[src/utils/state.c:30,is_exit_status_bad] NEMU exit with good state: 2, halt ret: 0
```

## 六、结果判定

- ✅ 打印输出：`Hello, XiangShan，I am Z, IP address`（对应赛题要求，队伍标识 Z）
- ✅ 模拟器正常结束：`nemu: HIT GOOD TRAP`，`trap code:0`，`good state: 2, halt ret: 0`
- ✅ 阶段一 1.1「环境部署验证」完成

## 七、遇到的问题与解决

| 问题 | 原因 | 解决 |
|---|---|---|
| `NEMU_HOME= is not a NEMU repo` | 新终端未 source env.sh，环境变量为空 | `source ~/projects/xs-env/env.sh`，并写入 ~/.bashrc 持久化 |
| `./build/riscv64-nemu-interpreter: No such file or directory` | NEMU 未编译 | `make riscv64-xs_defconfig && make -j` |
| WSL 里 git push GitHub 超时 | WSL 网络 TCP 连 GitHub 443 端口被干扰 | 配置 Windows 端口转发 + WSL 代理（socks5h://192.168.192.1:1081） |
| git push 每次要输 token | HTTPS 方式需要认证 | 配置 SSH Key，切换 remote 为 SSH 方式，实现免密提交 |

## 八、后续可复用命令

```bash
# 运行 hello（快速验证）
cd ~/projects/xs-env/NEMU && ./build/riscv64-nemu-interpreter -b ~/projects/xs-env/nexus-am/apps/hello/build/hello-riscv64-xs.bin

# 编译香山 emu（阶段 1.3 出波形用，耗时较长）
cd ~/projects/xs-env/XiangShan && make emu EMU_TRACE=1 -j32
```
