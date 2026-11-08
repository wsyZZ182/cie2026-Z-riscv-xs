#include <am.h>
#include <klib.h>

// ============================================================================
// 波形录制专用：只跑一条 e8 和一条 e16，前后留 nop 便于在 GTKWave 里观察。
//   e8 : 16 对 int8，期望 136
//   e16: 8  对 int16，K07 每笔 4 对 → 两笔，期望 36
// ============================================================================
static void enable_vs(void) {
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

int main() {
  int8_t  a8[16], b8[16];
  int16_t a16[8], b16[8];
  int32_t c8[4] = {0}, c16[4] = {0};
  int i;

  enable_vs();
  for (i = 0; i < 16; i++) { a8[i] = i + 1; b8[i] = 1; }
  for (i = 0; i < 8; i++)  { a16[i] = i + 1; b16[i] = 1; }

  // ---------------- E8：16 对，单笔 ----------------
  asm volatile("vsetvli t0, %0, e32, m1" :: "r"(4) : "t0");
  asm volatile("vxor.vv v2, v0, v0");                 // vdOld = 0
  asm volatile("vsetvli t0, %0, e8, m1" :: "r"(16) : "t0");
  asm volatile("vle8.v v0, (%0)" :: "r"(a8));
  asm volatile("vle8.v v1, (%0)" :: "r"(b8));
  asm volatile("nop; nop; nop; nop");
  asm volatile(".word 0x12103157");                   // vdot.vv v2, v1, v0
  asm volatile("nop; nop; nop; nop; nop; nop");
  asm volatile("vsetvli t0, %0, e32, m1" :: "r"(4) : "t0");
  asm volatile("vse32.v v2, (%0)" :: "r"(c8));

  // ---------------- E16：8 对，两笔 ----------------
  asm volatile("vsetvli t0, %0, e32, m1" :: "r"(4) : "t0");
  asm volatile("vxor.vv v2, v0, v0");                 // vdOld = 0
  asm volatile("vsetvli t0, %0, e16, m1" :: "r"(8) : "t0");
  asm volatile("vle16.v v0, (%0)" :: "r"(a16));
  asm volatile("vle16.v v1, (%0)" :: "r"(b16));
  asm volatile("nop; nop; nop; nop");
  asm volatile(".word 0x12103157");                   // vdot.vv v2, v1, v0（第 1 笔 + 第 2 笔）
  asm volatile("nop; nop; nop; nop; nop; nop");
  asm volatile("vsetvli t0, %0, e32, m1" :: "r"(4) : "t0");
  asm volatile("vse32.v v2, (%0)" :: "r"(c16));

  printf("e8  = %d (expect 136)\n", c8[0]);
  printf("e16 = %d (expect 36)\n", c16[0]);
  printf("wave test done.\n");
  return 0;
}
