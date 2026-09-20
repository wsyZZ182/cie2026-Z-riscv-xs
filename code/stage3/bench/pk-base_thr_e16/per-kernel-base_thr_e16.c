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

static void load_data_e16(void) {
  static int16_t a[8] = {1,2,3,4,5,6,7,8};
  static int16_t b[8] = {1,1,1,1,1,1,1,1};
  int vl;
  asm volatile("vsetvli %0, x0, e16, m1" : "=r"(vl));
  asm volatile("vle16.v v0, (%0)" :: "r"(a));
  asm volatile("vle16.v v1, (%0)" :: "r"(b));
}
static uint64_t base_thr_e16(int n) {
  int vl, i; uint64_t s, e;
  load_data_e16();
  asm volatile("vsetvli %0, x0, e32, m1" : "=r"(vl));
  asm volatile("vxor.vv v4, v0, v0"); asm volatile("vxor.vv v5, v0, v0");
  asm volatile("vxor.vv v6, v0, v0"); asm volatile("vxor.vv v7, v0, v0");
  asm volatile("vsetvli %0, x0, e16, m1" : "=r"(vl));
  s = rdcycle();
  for (i = 0; i < n; i += 4) {
    asm volatile("vsetvli %0, x0, e16, m1" : "=r"(vl));
    asm volatile("vwmul.vv v2, v0, v1");
    asm volatile("vsetvli %0, x0, e32, m2" : "=r"(vl));
    asm volatile("vwredsum.vs v4, v2, v4");
    asm volatile("vsetvli %0, x0, e16, m1" : "=r"(vl));
    asm volatile("vwmul.vv v2, v0, v1");
    asm volatile("vsetvli %0, x0, e32, m2" : "=r"(vl));
    asm volatile("vwredsum.vs v5, v2, v5");
    asm volatile("vsetvli %0, x0, e16, m1" : "=r"(vl));
    asm volatile("vwmul.vv v2, v0, v1");
    asm volatile("vsetvli %0, x0, e32, m2" : "=r"(vl));
    asm volatile("vwredsum.vs v6, v2, v6");
    asm volatile("vsetvli %0, x0, e16, m1" : "=r"(vl));
    asm volatile("vwmul.vv v2, v0, v1");
    asm volatile("vsetvli %0, x0, e32, m2" : "=r"(vl));
    asm volatile("vwredsum.vs v7, v2, v7");
  }
  e = rdcycle();
  asm volatile("vsetvli %0, x0, e32, m1" : "=r"(vl));
  uint64_t v = 0; asm volatile("vmv.x.s %0, v4" : "=r"(v)); g_vres = v;
  return e - s;
}

int main(void) {
  int Ns[3] = {10, 100, 500};
  int k, n; uint64_t c;
  enable_vs();
  printf("base_thr_e16 start\n");
  for (k = 0; k < 3; k++) {
    n = Ns[k];
    c = run3(base_thr_e16, n);
    printf("base_thr_e16,%d,%lu,%lu\n", n, (unsigned long)c, (unsigned long)(c / (uint64_t)n));
  }
  printf("base_thr_e16 done\n");
  return 0;
}
