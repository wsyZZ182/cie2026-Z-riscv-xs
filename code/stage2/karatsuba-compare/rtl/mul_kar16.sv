// ============================================================================
// mul_kar16.sv —— Karatsuba 位分解 16×16 有符号乘法（3 个乘法代 4 个）
//
// a = a1·2^8 + a0, b = b1·2^8 + b0
//   z1 = a1*b1                       （8×8 有符号）
//   z0 = a0*b0                       （低位零扩展，9×9 非负）
//   zm = (a1+a0)(b1+b0)              （"和变宽"：和为 10 位有符号 → 10×10）
//   z2 = zm - z1 - z0 = a1b0 + a0b1  （交叉项，可能为负，按补码保留符号）
// 结果 = z1·2^16 + z2·2^8 + z0
//
// 相比 schoolbook 少 1 个乘法器；代价是中间乘法器更宽（10×10）。
// 三大易错点均已处理：和变宽 / 交叉项为负 / 低位零扩展（高半负权由
// a1,b1 的有符号处理 + 低位零扩展修正承担）。
// ============================================================================
`ifndef MUL_KAR16_SV
`define MUL_KAR16_SV

module MulKar16 (
  input  wire signed [15:0] a,
  input  wire signed [15:0] b,
  output wire signed [31:0] p
);
  wire signed [7:0] a1 = a[15:8];
  wire signed [7:0] b1 = b[15:8];
  wire [7:0]        a0 = a[7:0];
  wire [7:0]        b0 = b[7:0];

  wire signed [8:0] a0s = {1'b0, a0};          // 零扩展，非负
  wire signed [8:0] b0s = {1'b0, b0};
  wire signed [9:0] sa  = a1 + a0s;            // 范围 -128..382 → 10 位
  wire signed [9:0] sb  = b1 + b0s;

  wire signed [15:0] z1;   // a1*b1
  wire signed [17:0] z0;   // a0*b0 (non-neg)
  wire signed [19:0] zm;   // sa*sb

  MulBase #(8)  m1 (.a(a1),  .b(b1),  .p(z1));
  MulBase #(9)  m0 (.a(a0s), .b(b0s), .p(z0));
  MulBase #(10) mm (.a(sa),  .b(sb),  .p(zm));

  wire signed [21:0] z2 = zm - z1 - z0;         // 交叉项，有符号

  wire signed [33:0] t1 = z1;                  // 权重 2^16
  wire signed [33:0] t2 = z2;                  // 权重 2^8
  wire signed [33:0] t0 = z0;                  // 权重 1
  wire signed [33:0] acc = (t1 <<< 16) + (t2 <<< 8) + t0;

  assign p = acc[31:0];
endmodule

`endif
