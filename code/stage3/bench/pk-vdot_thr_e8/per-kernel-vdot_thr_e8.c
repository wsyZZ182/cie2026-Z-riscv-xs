#include <am.h>
#include <klib.h>
#include <stdint.h>
#define VDOT_V2 0x12103157u
#define VDOT_V3 0x121031D7u
#define VDOT_V4 0x12103257u
#define VDOT_V5 0x121032D7u
volatile uint64_t g_vres = 0;
static inline uint64_t rdcycle(void) { uint64_t c; asm volatile("rdcycle %0" : "=r"(c)); return c; }
static uint64_t run3(uint64_t (*fn)(int), int n) {
  uint64_t best = (uint64_t)-1; int r;
  for (r = 0; r < 3; r++) { uint64_t v = fn(n); if (v < best) best = v; }
  return best;
}
static void enable_vs(void) {
  asm volatile("csrr t0, mstatus\nli t1, 0x600\nor t0, t0, t1\ncsrw mstatus, t0\n" ::: "t0","t1");
}

static void load_data_e8(void) {
  static int8_t a[16] = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16};
  static int8_t b[16] = {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1};
  int vl;
  asm volatile("vsetvli %0, x0, e8, m1" : "=r"(vl));
  asm volatile("vle8.v v0, (%0)" :: "r"(a));
  asm volatile("vle8.v v1, (%0)" :: "r"(b));
}
static uint64_t vdot_thr_e8(int n) {
  int vl, i; uint64_t s, e;
  load_data_e8();
  asm volatile("vsetvli %0, x0, e32, m1" : "=r"(vl));
  asm volatile("vxor.vv v2, v0, v0"); asm volatile("vxor.vv v3, v0, v0");
  asm volatile("vxor.vv v4, v0, v0"); asm volatile("vxor.vv v5, v0, v0");
  asm volatile("vsetvli %0, x0, e8, m1" : "=r"(vl));
  s = rdcycle();
  for (i = 0; i < n; i += 4) {
    asm volatile(".word %0" :: "i"(VDOT_V2));
    asm volatile(".word %0" :: "i"(VDOT_V3));
    asm volatile(".word %0" :: "i"(VDOT_V4));
    asm volatile(".word %0" :: "i"(VDOT_V5));
  }
  e = rdcycle();
  asm volatile("vsetvli %0, x0, e32, m1" : "=r"(vl));
  uint64_t v = 0; asm volatile("vmv.x.s %0, v2" : "=r"(v)); g_vres = v;
  return e - s;
}

int main(void) {
  int Ns[3] = {10, 100, 500};
  int k, n; uint64_t c;
  enable_vs();
  printf("vdot_thr_e8 start\n");
  for (k = 0; k < 3; k++) {
    n = Ns[k];
    c = run3(vdot_thr_e8, n);
    printf("vdot_thr_e8,%d,%lu,%lu\n", n, (unsigned long)c, (unsigned long)(c / (uint64_t)n));
  }
  printf("vdot_thr_e8 done\n");
  return 0;
}
