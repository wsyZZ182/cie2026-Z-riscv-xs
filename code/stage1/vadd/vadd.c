#include <stdio.h>

int a[4] __attribute__((aligned(16))) = {1, 2, 3, 4};
int b[4] __attribute__((aligned(16))) = {5, 6, 7, 8};
int c[4] __attribute__((aligned(16))) = {0, 0, 0, 0};
int expected[4] = {6, 8, 10, 12};

int main() {
    int vl;
    asm volatile(
        "vsetvli %0, x0, e32, m1\n"
        "vle32.v  v0, (%1)\n"
        "vle32.v  v1, (%2)\n"
        "vadd.vv  v2, v0, v1\n"
        "vse32.v  v2, (%3)\n"
        : "=r"(vl)
        : "r"(a), "r"(b), "r"(c)
        : "v0", "v1", "v2", "memory"
    );
    int pass = 1;
    for (int i = 0; i < 4; i++)
        if (c[i] != expected[i]) pass = 0;
    if (pass) {
        printf("vadd test PASS! vl=%d\n", vl);
        for (int i = 0; i < 4; i++)
            printf("  c[%d] = %d (expected %d)\n", i, c[i], expected[i]);
        return 0;
    } else {
        printf("vadd test FAIL!\n");
        for (int i = 0; i < 4; i++)
            printf("  c[%d] = %d (expected %d)\n", i, c[i], expected[i]);
        return 1;
    }
}