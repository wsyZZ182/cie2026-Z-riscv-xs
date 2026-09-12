#include <am.h>
#include <klib.h>

// ============================================================================
// vdot.vv 功能测试（阶段 2.3：正数 / 负数 / 边界值 / 多精度 / vl 裁剪）
//
// vdot.vv vd, vs2, vs1, vm 为自定义指令，汇编器不识别，故用 .word 直接编码：
//   funct6=000100 + vm=1 + vs2=v1 + vs1=v0 + funct3=100 + vd=v2 + opcode=1010111
//   = 0b000100_1_00001_00000_100_00010_1010111 = 0x12104157
//
// 香山 MinimalConfig 已验证约束：
//   1. vsetvli 必须最先执行（vtype 未初始化时任何向量算术指令非法 cause=2）
//   2. 清 v2 用 vxor.vv v2,v0,v0（vd≠源；vmv.v.i 与 vd==源 均非法）
//   3. vdot 结果在 vd 低 32 位（VDotCore 固定放第一个元素槽）
//   4. e8/e16 下 vse32.v 非法（EEW≠SEW），须先 vsetvli e32 再 vse32.v
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
  asm volatile("vsetvli %0, %1, e8, m1" : "=r"(vl) : "r"(n));  // vsetvli 最先
  asm volatile("vxor.vv v2, v0, v0");                          // 清 v2
  asm volatile("vle8.v v0, (%0)" :: "r"(a));
  asm volatile("vle8.v v1, (%0)" :: "r"(b));
  asm volatile(".word 0x12103157" ::: "memory");               // vdot.vv v2, v1, v0（funct3=011 修正编码）
  asm volatile("vsetvli %0, %1, e32, m1" : "=r"(vl) : "r"(4)); // 切 e32 后 vse32
  asm volatile("vse32.v v2, (%0)" :: "r"(c));
}

static void run_e16(int16_t *a, int16_t *b, int n, int32_t *c) {
  int vl;
  asm volatile("vsetvli %0, %1, e16, m1" : "=r"(vl) : "r"(n));
  asm volatile("vxor.vv v2, v0, v0");
  asm volatile("vle16.v v0, (%0)" :: "r"(a));
  asm volatile("vle16.v v1, (%0)" :: "r"(b));
  asm volatile(".word 0x12103157" ::: "memory");               // vdot.vv v2, v1, v0（funct3=011 修正编码）
  asm volatile("vsetvli %0, %1, e32, m1" : "=r"(vl) : "r"(4));
  asm volatile("vse32.v v2, (%0)" :: "r"(c));
}

static void run_e32(int32_t *a, int32_t *b, int n, int32_t *c) {
  int vl;
  asm volatile("vsetvli %0, %1, e32, m1" : "=r"(vl) : "r"(n));
  asm volatile("vxor.vv v2, v0, v0");
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

  printf("vdot.vv test done.\n");
  return 0;
}
