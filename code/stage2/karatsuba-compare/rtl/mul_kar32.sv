// ============================================================================
// mul_kar32.sv —— Karatsuba 位分解 32×32 有符号乘法（两层递归）
//
// a = a1·2^16 + a0, b = b1·2^16 + b0
//   z1 = a1*b1            ：复用 MulKar16（16 位 Karatsuba，体现两层递归）
//   z0 = a0*b0            ：低位零扩展 17 位非负 → MulBase#17
//   zm = (a1+a0)(b1+b0)   ："和变宽"，和为 18 位有符号 → MulBase#18
//   z2 = zm - z1 - z0     ：交叉项，可能为负，按补码保留符号
// 结果 = z1·2^32 + z2·2^16 + z0
//
// 构成：1×Kar16 + 1×Base17 + 1×Base18（对照 School32 的 1×Kar16 + 3×Base17）。
// ============================================================================
`ifndef MUL_KAR32_SV
`define MUL_KAR32_SV

module MulKar32 (
  input  wire signed [31:0] a,
  input  wire signed [31:0] b,
  output wire signed [63:0] p
);
  wire signed [15:0] a1 = a[31:16];
  wire signed [15:0] b1 = b[31:16];
  wire [15:0]        a0 = a[15:0];
  wire [15:0]        b0 = b[15:0];

  wire signed [16:0] a0s = {1'b0, a0};        // 零扩展，非负
  wire signed [16:0] b0s = {1'b0, b0};
  wire signed [17:0] sa  = a1 + a0s;          // 范围 -32768..98302 → 18 位
  wire signed [17:0] sb  = b1 + b0s;

  wire signed [31:0] z1;   // a1*b1（Karatsuba16）
  wire signed [33:0] z0;   // a0*b0 (non-neg)
  wire signed [35:0] zm;   // sa*sb

  MulKar16   m1 (.a(a1),  .b(b1),  .p(z1));
  MulBase #(17) m0 (.a(a0s), .b(b0s), .p(z0));
  MulBase #(18) mm (.a(sa),  .b(sb),  .p(zm));

  wire signed [37:0] z2 = zm - z1 - z0;        // 交叉项，有符号

  wire signed [65:0] t1 = z1;                 // 权重 2^32
  wire signed [65:0] t2 = z2;                 // 权重 2^16
  wire signed [65:0] t0 = z0;                 // 权重 1
  wire signed [65:0] acc = (t1 <<< 32) + (t2 <<< 16) + t0;

  assign p = acc[63:0];
endmodule

`endif
