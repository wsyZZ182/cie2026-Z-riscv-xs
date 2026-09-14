#include <am.h>
#include <klib.h>

// ============================================================================
// vdot.vv 功能测试（阶段 2.3：正数 / 负数 / 边界值 / 多精度 / vl 裁剪 / 溢出）
//
// vdot.vv vd, vs2, vs1, vm 为自定义指令，汇编器不识别，故用 .word 直接编码：
//   funct6=000100 + vm=1 + vs2=v1 + vs1=v0 + funct3=011 + vd=v2 + opcode=1010111
//   = 0b000100_1_00001_00000_011_00010_1010111 = 0x12103157
//   （初版 0x12104157 funct3=100 与 RVV 标准 vminu.vx 冲突，已修正）
//
// 香山 MinimalConfig 已验证约束：
//   1. vsetvli 必须最先执行（vtype 未初始化时任何向量算术指令非法 cause=2）
//   2. 清 v2 必须"先 e32 vl=4 再 vxor"：vxor 按 vl 只清前 vl 个元素，
//      vl=1 时低 32 位清不干净（残留旧值参与累加，曾致 vl=1 用例结果 0x7FFFFF05）
//   3. vdot 结果在 vd 低 32 位（VDotCore 固定放第一个元素槽，slotMask 低 32 位）
//   4. e8/e16 下 vse32.v 非法（EEW≠SEW），须先 vsetvli e32 再 vse32.v
//   5. vxsat 语义（Fused ReduceAcc 优化版）：结果超出 32 位有符号范围置位
//
// 用例清单（13）：
//   1-7  原始用例（e8 正/负/边界/vl=4、e16 正、e32 正/混合）
//   8    e16 负数（-1..-8 → -36）
//   9    e8 最小值边界（-128×16 → -2048）
//   10   e32 边界（2^31-1 元素，64 位乘积高位不丢）
//   11   e8 vl=1（部分归约最小 vl）
//   12   e8 vl=0（不执行，vd 保持预置值）
//   13   e32 溢出（vd=0x7FFFFFFF + 1 → 0x80000000，vxsat=1）
// ============================================================================
static void enable_vs(void) {
  // mstatus.VS = 01 (Initial), 位 10:9；AM 启动代码未设置，香山对 VS=Off 抛向量非法指令
  asm volatile(
    "csrr t0, mstatus\n"
    "li   t1, 0x600\n"
    "or   t0, t0, t1\n"
    "csrw mstatus, t0\n"
    :
    :
    : "t0", "t1"
  );
}

static void run_e8(int8_t *a, int8_t *b, int n, int32_t *c) {
  int vl;
  asm volatile("vsetvli %0, %1, e32, m1" : "=r"(vl) : "r"(4)); // 先全宽（vl=4, e32）
  asm volatile("vxor.vv v2, v0, v0");                          // 清 v2 全部 128 位
  asm volatile("vsetvli %0, %1, e8, m1" : "=r"(vl) : "r"(n));  // 再切目标档位
  asm volatile("vle8.v v0, (%0)" :: "r"(a));
  asm volatile("vle8.v v1, (%0)" :: "r"(b));
  asm volatile(".word 0x12103157" ::: "memory");               // vdot.vv v2, v1, v0（funct3=011 修正编码）
  asm volatile("vsetvli %0, %1, e32, m1" : "=r"(vl) : "r"(4)); // 切 e32 后 vse32
  asm volatile("vse32.v v2, (%0)" :: "r"(c));
}

static void run_e16(int16_t *a, int16_t *b, int n, int32_t *c) {
  int vl;
  asm volatile("vsetvli %0, %1, e32, m1" : "=r"(vl) : "r"(4)); // 先全宽
  asm volatile("vxor.vv v2, v0, v0");                          // 清 v2 全部 128 位
  asm volatile("vsetvli %0, %1, e16, m1" : "=r"(vl) : "r"(n)); // 再切目标档位
  asm volatile("vle16.v v0, (%0)" :: "r"(a));
  asm volatile("vle16.v v1, (%0)" :: "r"(b));
  asm volatile(".word 0x12103157" ::: "memory");               // vdot.vv v2, v1, v0（funct3=011 修正编码）
  asm volatile("vsetvli %0, %1, e32, m1" : "=r"(vl) : "r"(4));
  asm volatile("vse32.v v2, (%0)" :: "r"(c));
}

static void run_e32(int32_t *a, int32_t *b, int n, int32_t *c) {
  int vl;
  asm volatile("vsetvli %0, %1, e32, m1" : "=r"(vl) : "r"(4)); // 先全宽
  asm volatile("vxor.vv v2, v0, v0");                          // 清 v2 全部 128 位
  asm volatile("vsetvli %0, %1, e32, m1" : "=r"(vl) : "r"(n)); // 再切目标 vl
  asm volatile("vle32.v v0, (%0)" :: "r"(a));
  asm volatile("vle32.v v1, (%0)" :: "r"(b));
  asm volatile(".word 0x12103157" ::: "memory");               // vdot.vv v2, v1, v0（funct3=011 修正编码）
  asm volatile("vse32.v v2, (%0)" :: "r"(c));
}

int main() {
  int32_t c[4] = {0};
  int i;

  enable_vs();
  printf("vdot-test start\n");

  // ---- 1. e8 正数：1..16 × 1s -> 136 ----
  {
    int8_t a[16], b[16];
    for (i = 0; i < 16; i++) { a[i] = i + 1; b[i] = 1; }
    run_e8(a, b, 16, c);
    printf("e8 正数   : c0=%d (expect 136)\n", c[0]);
  }

  // ---- 2. e8 负数：-1..-16 × 1s -> -136 ----
  {
    int8_t a[16], b[16];
    for (i = 0; i < 16; i++) { a[i] = -(i + 1); b[i] = 1; }
    run_e8(a, b, 16, c);
    printf("e8 负数   : c0=%d (expect -136)\n", c[0]);
  }

  // ---- 3. e8 边界：127×16 × 1s -> 2032 ----
  {
    int8_t a[16], b[16];
    for (i = 0; i < 16; i++) { a[i] = 127; b[i] = 1; }
    run_e8(a, b, 16, c);
    printf("e8 边界   : c0=%d (expect 2032)\n", c[0]);
  }

  // ---- 4. e8 vl=4 裁剪：1,2,3,4 × 1s -> 10 ----
  {
    int8_t a[16], b[16];
    for (i = 0; i < 16; i++) { a[i] = (i < 4) ? (i + 1) : 0; b[i] = 1; }
    run_e8(a, b, 4, c);
    printf("e8 vl=4   : c0=%d (expect 10)\n", c[0]);
  }

  // ---- 5. e16：1..8 × 1s -> 36 ----
  {
    int16_t a[8], b[8];
    for (i = 0; i < 8; i++) { a[i] = i + 1; b[i] = 1; }
    run_e16(a, b, 8, c);
    printf("e16 正数  : c0=%d (expect 36)\n", c[0]);
  }

  // ---- 6. e32：1..4 × 1s -> 10 ----
  {
    int32_t a[4], b[4];
    for (i = 0; i < 4; i++) { a[i] = i + 1; b[i] = 1; }
    run_e32(a, b, 4, c);
    printf("e32 正数  : c0=%d (expect 10)\n", c[0]);
  }

  // ---- 7. e32 混合：(-3,4,-5,6) × (2,-7,8,-9) -> -128 ----
  {
    int32_t a[4] = {-3, 4, -5, 6}, b[4] = {2, -7, 8, -9};
    run_e32(a, b, 4, c);
    printf("e32 混合  : c0=%d (expect -128)\n", c[0]);
  }

  // ---- 8. e16 负数：-1..-8 × 1s -> -36（e16 符号扩展路径独立验证）----
  {
    int16_t a[8], b[8];
    for (i = 0; i < 8; i++) { a[i] = -(i + 1); b[i] = 1; }
    run_e16(a, b, 8, c);
    printf("e16 负数  : c0=%d (expect -36)\n", c[0]);
  }

  // ---- 9. e8 最小值边界：-128×16 × 1s -> -2048 ----
  {
    int8_t a[16], b[16];
    for (i = 0; i < 16; i++) { a[i] = -128; b[i] = 1; }
    run_e8(a, b, 16, c);
    printf("e8 最小值 : c0=%d (expect -2048)\n", c[0]);
  }

  // ---- 10. e32 边界：2^31-1 元素（64 位乘积高位不丢）----
  {
    int32_t a[4] = {2147483647, 0, 0, 0}, b[4] = {1, 0, 0, 0};
    run_e32(a, b, 4, c);
    printf("e32 边界  : c0=%d (expect 2147483647)\n", c[0]);
  }

  // ---- 11. e8 vl=1：只取 1 个元素 ----
  {
    int8_t a[16], b[16];
    for (i = 0; i < 16; i++) { a[i] = i + 5; b[i] = 1; }   // 首元素 5
    run_e8(a, b, 1, c);
    printf("e8 vl=1   : c0=%d (expect 5)\n", c[0]);
  }

  // ---- 12. e8 vl=0：不执行，vd 保持预置值 ----
  {
    int32_t sentinel[4] = {0x11223344, 0, 0, 0};
    int vll;
    asm volatile("vsetvli %0, %1, e32, m1" : "=r"(vll) : "r"(4));
    asm volatile("vle32.v v2, (%0)" :: "r"(sentinel));          // vd = 0x11223344
    asm volatile("vsetvli %0, %1, e8, m1" : "=r"(vll) : "r"(0)); // vl=0
    asm volatile(".word 0x12103157" ::: "memory");               // vdot 不执行（vstart<vl 为假）
    asm volatile("vsetvli %0, %1, e32, m1" : "=r"(vll) : "r"(4));
    asm volatile("vse32.v v2, (%0)" :: "r"(c));
    printf("e8 vl=0   : c0=0x%08X (expect 0x11223344)\n", (unsigned)c[0]);
  }

  // ---- 13. e32 溢出：vd=0x7FFFFFFF + dot=1 -> 0x80000000，vxsat=1 ----
  // vxsat CSR 读回验证：MinimalConfig 下香山 vxsat 写回链路被注释（VPUSubModule），
  // 标准 RVV 饱和指令同样不写 CSR。此处先 csrw 预置 1，若 vdot 不写 CSR 则读到 1；
  // 若读到 0 说明链路存在且被写 0（检测 bug）。vxsat 硬件信号以 RTL 波形为准。
  {
    int32_t a[4] = {1, 0, 0, 0}, b[4] = {1, 0, 0, 0};
    int32_t big[4] = {0x7FFFFFFF, 0, 0, 0};
    uint64_t vsat = 0;
    int vll;
    asm volatile("vsetvli %0, %1, e32, m1" : "=r"(vll) : "r"(4));
    asm volatile("vle32.v v2, (%0)" :: "r"(big));               // vd = 0x7FFFFFFF
    asm volatile("vle32.v v0, (%0)" :: "r"(a));
    asm volatile("vle32.v v1, (%0)" :: "r"(b));
    asm volatile("csrw vxsat, %0" :: "r"((uint64_t)1));          // 预置 vxsat CSR = 1
    asm volatile(".word 0x12103157" ::: "memory");               // 0x7FFFFFFF + 1 溢出
    asm volatile("csrr %0, vxsat" : "=r"(vsat));                 // 读 vxsat CSR
    asm volatile("vse32.v v2, (%0)" :: "r"(c));
    printf("e32 溢出  : c0=0x%08X vxsat_csr=%d (expect 0x80000000; csr=1 表示链路未接)\n",
           (unsigned)c[0], (int)vsat);
  }

  printf("vdot.vv test done.\n");
  return 0;
}
