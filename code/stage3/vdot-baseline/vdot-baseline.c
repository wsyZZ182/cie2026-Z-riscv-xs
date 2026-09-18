#include <am.h>
#include <klib.h>

// ============================================================================
// vdot 基线功能测试（阶段 3.2）
//
// 基线 = 标准 RVV 指令组合实现与 vdot.vv 相同的点积功能：
//   e8  : vle8.v + vwmul.vv(e8->e16) + vwredsum.vs(e16->e32)   （宽乘+宽归约，正确 32 位）
//   e16 : vle16.v + vwmul.vv(e16->e32) + vredsum.vs(e32)
//   e32 : vle32.v + vmul.vv(e32) + vredsum.vs(e32)             （乘积 64 位高位截断，用例值不越界）
//
// 与 vdot 单指令相比，基线需要 3~4 条指令且结果经中间寄存器/多次 vsetvli。
// 13 用例与 vdot-test 完全一致（同输入、同期望），供 3.3 延迟加速比对比。
//
// 关键点：
//   1. vwmul.vv 是 widening 乘法：e8x8->e16（LMUL=2），e16x16->e32（LMUL=2）
//   2. vwredsum.vs 是 widening 归约：E16 向量 -> E32 标量（累加 vs1 旧值）
//   3. 累加器 v4 先 vmv.s.x 清零；vl=0 时不执行归约，v4 保持预置值（对应 vdot vl=0 语义）
//   4. vse32.v 前必须 vsetvli e32（EEW 与 SEW 匹配）
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

// e8 基线：vle8 + vwmul(e8->e16) + vwredsum(e16->e32)
static void base_e8(int8_t *a, int8_t *b, int n, int32_t *c) {
  int vl;
  asm volatile("vsetvli %0, %1, e8, m1" : "=r"(vl) : "r"(n));
  asm volatile("vle8.v v0, (%0)" :: "r"(a));
  asm volatile("vle8.v v1, (%0)" :: "r"(b));
  asm volatile("vwmul.vv v2, v0, v1");                          // 8x8 -> 16 宽乘，v2/v3 组
  asm volatile("vsetvli %0, x0, e32, m1" : "=r"(vl));           // e32 下清累加器
  asm volatile("vmv.s.x v4, x0");
  asm volatile("vsetvli %0, %1, e16, m2" : "=r"(vl) : "r"(n));  // vl=n（不能 x0：否则归约垃圾尾部元素）
  asm volatile("vwredsum.vs v4, v2, v4");                       // E16 归约 -> E32 标量（只归约前 n 个）
  asm volatile("vsetvli %0, x0, e32, m1" : "=r"(vl));
  asm volatile("vse32.v v4, (%0)" :: "r"(c));
}

// e16 基线：vle16 + vwmul(e16->e32) + vredsum(e32)
static void base_e16(int16_t *a, int16_t *b, int n, int32_t *c) {
  int vl;
  asm volatile("vsetvli %0, %1, e16, m1" : "=r"(vl) : "r"(n));
  asm volatile("vle16.v v0, (%0)" :: "r"(a));
  asm volatile("vle16.v v1, (%0)" :: "r"(b));
  asm volatile("vwmul.vv v2, v0, v1");                          // 16x16 -> 32 宽乘，v2/v3 组
  asm volatile("vsetvli %0, x0, e32, m1" : "=r"(vl));
  asm volatile("vmv.s.x v4, x0");
  asm volatile("vsetvli %0, %1, e32, m2" : "=r"(vl) : "r"(n));  // vl=n（不能 x0：否则归约垃圾尾部元素）
  asm volatile("vredsum.vs v4, v2, v4");                        // E32 归约（只归约前 n 个）
  asm volatile("vsetvli %0, x0, e32, m1" : "=r"(vl));
  asm volatile("vse32.v v4, (%0)" :: "r"(c));
}

// e32 基线：vle32 + vmul(e32) + vredsum(e32)
static void base_e32(int32_t *a, int32_t *b, int n, int32_t *c) {
  int vl;
  asm volatile("vsetvli %0, %1, e32, m1" : "=r"(vl) : "r"(n));
  asm volatile("vle32.v v0, (%0)" :: "r"(a));
  asm volatile("vle32.v v1, (%0)" :: "r"(b));
  asm volatile("vmul.vv v2, v0, v1");                           // 32x32 -> 低 32 位（用例不越界）
  asm volatile("vmv.s.x v4, x0");
  asm volatile("vredsum.vs v4, v2, v4");                        // E32 归约
  asm volatile("vse32.v v4, (%0)" :: "r"(c));
}

int main() {
  int32_t c[4] = {0};
  int i;

  enable_vs();
  printf("vdot-baseline start\n");

  // ---- 1. e8 正数：1..16 × 1s -> 136 ----
  {
    int8_t a[16], b[16];
    for (i = 0; i < 16; i++) { a[i] = i + 1; b[i] = 1; }
    base_e8(a, b, 16, c);
    printf("e8 正数   : c0=%d (expect 136)\n", c[0]);
  }

  // ---- 2. e8 负数：-1..-16 × 1s -> -136 ----
  {
    int8_t a[16], b[16];
    for (i = 0; i < 16; i++) { a[i] = -(i + 1); b[i] = 1; }
    base_e8(a, b, 16, c);
    printf("e8 负数   : c0=%d (expect -136)\n", c[0]);
  }

  // ---- 3. e8 边界：127×16 × 1s -> 2032 ----
  {
    int8_t a[16], b[16];
    for (i = 0; i < 16; i++) { a[i] = 127; b[i] = 1; }
    base_e8(a, b, 16, c);
    printf("e8 边界   : c0=%d (expect 2032)\n", c[0]);
  }

  // ---- 4. e8 vl=4 裁剪：1,2,3,4 × 1s -> 10 ----
  {
    int8_t a[16], b[16];
    for (i = 0; i < 16; i++) { a[i] = (i < 4) ? (i + 1) : 0; b[i] = 1; }
    base_e8(a, b, 4, c);
    printf("e8 vl=4   : c0=%d (expect 10)\n", c[0]);
  }

  // ---- 5. e16：1..8 × 1s -> 36 ----
  {
    int16_t a[8], b[8];
    for (i = 0; i < 8; i++) { a[i] = i + 1; b[i] = 1; }
    base_e16(a, b, 8, c);
    printf("e16 正数  : c0=%d (expect 36)\n", c[0]);
  }

  // ---- 6. e32：1..4 × 1s -> 10 ----
  {
    int32_t a[4], b[4];
    for (i = 0; i < 4; i++) { a[i] = i + 1; b[i] = 1; }
    base_e32(a, b, 4, c);
    printf("e32 正数  : c0=%d (expect 10)\n", c[0]);
  }

  // ---- 7. e32 混合：(-3,4,-5,6) × (2,-7,8,-9) -> -128 ----
  {
    int32_t a[4] = {-3, 4, -5, 6}, b[4] = {2, -7, 8, -9};
    base_e32(a, b, 4, c);
    printf("e32 混合  : c0=%d (expect -128)\n", c[0]);
  }

  // ---- 8. e16 负数：-1..-8 × 1s -> -36 ----
  {
    int16_t a[8], b[8];
    for (i = 0; i < 8; i++) { a[i] = -(i + 1); b[i] = 1; }
    base_e16(a, b, 8, c);
    printf("e16 负数  : c0=%d (expect -36)\n", c[0]);
  }

  // ---- 9. e8 最小值边界：-128×16 × 1s -> -2048 ----
  {
    int8_t a[16], b[16];
    for (i = 0; i < 16; i++) { a[i] = -128; b[i] = 1; }
    base_e8(a, b, 16, c);
    printf("e8 最小值 : c0=%d (expect -2048)\n", c[0]);
  }

  // ---- 10. e32 边界：2^31-1 元素（64 位乘积高位不丢）----
  {
    int32_t a[4] = {2147483647, 0, 0, 0}, b[4] = {1, 0, 0, 0};
    base_e32(a, b, 4, c);
    printf("e32 边界  : c0=%d (expect 2147483647)\n", c[0]);
  }

  // ---- 11. e8 vl=1：只取 1 个元素 ----
  {
    int8_t a[16], b[16];
    for (i = 0; i < 16; i++) { a[i] = i + 5; b[i] = 1; }   // 首元素 5
    base_e8(a, b, 1, c);
    printf("e8 vl=1   : c0=%d (expect 5)\n", c[0]);
  }

  // ---- 12. e8 vl=0：不执行，累加器保持预置值（对应 vdot vd 保持）----
  {
    int32_t sentinel[4] = {0x11223344, 0, 0, 0};
    int vll;
    asm volatile("vsetvli %0, %1, e32, m1" : "=r"(vll) : "r"(4));
    asm volatile("vle32.v v4, (%0)" :: "r"(sentinel));          // 累加器 = 0x11223344
    asm volatile("vsetvli %0, %1, e8, m1" : "=r"(vll) : "r"(0)); // vl=0
    asm volatile("vle8.v v0, (%0)" :: "r"(sentinel));           // 加载任意（不参与）
    asm volatile("vwmul.vv v2, v0, v0");                        // 乘积（不归约）
    asm volatile("vwredsum.vs v4, v2, v4");                     // vl=0 不执行，v4 保持
    asm volatile("vsetvli %0, %1, e32, m1" : "=r"(vll) : "r"(4));
    asm volatile("vse32.v v4, (%0)" :: "r"(c));
    printf("e8 vl=0   : c0=0x%08X (expect 0x11223344)\n", (unsigned)c[0]);
  }

  // ---- 13. e32 溢出：累加器=0x7FFFFFFF + dot=1 -> 0x80000000 ----
  {
    int32_t a[4] = {1, 0, 0, 0}, b[4] = {1, 0, 0, 0};
    int32_t big[4] = {0x7FFFFFFF, 0, 0, 0};
    int vll;
    asm volatile("vsetvli %0, %1, e32, m1" : "=r"(vll) : "r"(4));
    asm volatile("vle32.v v4, (%0)" :: "r"(big));               // 累加器 = 0x7FFFFFFF
    asm volatile("vle32.v v0, (%0)" :: "r"(a));
    asm volatile("vle32.v v1, (%0)" :: "r"(b));
    asm volatile("vmul.vv v2, v0, v1");                         // 1×1=1
    asm volatile("vredsum.vs v4, v2, v4");                      // 0x7FFFFFFF + 1 -> 0x80000000
    asm volatile("vse32.v v4, (%0)" :: "r"(c));
    printf("e32 溢出  : c0=0x%08X (expect 0x80000000)\n", (unsigned)c[0]);
  }

  printf("vdot-baseline all cases done\n");
  return 0;
}
