// ============================================================================
// mul_school16.sv —— 普通"学校法"位分解 16×16（4 个基元，Karatsuba 的对照）
//
// a = a1·2^8 + a0, b = b1·2^8 + b0
//   a1/b1 为 8 位有符号高半；a0/b0 为 8 位位模式（数值非负）。
// 结果 = a1b1·2^16 + (a1b0 + a0b1)·2^8 + a0b0
//
// 关键：低位 a0/b0 先零扩展成 9 位非负有符号数再进基元，避免把无符号
// 部分积当有符号而错误符号扩展（队友 Karatsuba 当年的失败点）。
// 共 4 个叶子乘法器（1 个 8×8 + 3 个 9×9）。
// ============================================================================
`ifndef MUL_SCHOOL16_SV
`define MUL_SCHOOL16_SV

module MulSchool16 (
  input  wire signed [15:0] a,
  input  wire signed [15:0] b,
  output wire signed [31:0] p
);
  wire signed [7:0] a1 = a[15:8];
  wire signed [7:0] b1 = b[15:8];
  wire [7:0]        a0 = a[7:0];
  wire [7:0]        b0 = b[7:0];

  wire signed [8:0] a0s = {1'b0, a0};   // 零扩展，非负
  wire signed [8:0] b0s = {1'b0, b0};
  wire signed [8:0] a1x = {a1[7], a1};  // 符号扩展到 9 位
  wire signed [8:0] b1x = {b1[7], b1};

  wire signed [15:0] z11;  // a1*b1 (signed)
  wire signed [17:0] z10;  // a1*b0
  wire signed [17:0] z01;  // a0*b1
  wire signed [17:0] z00;  // a0*b0 (non-neg)

  MulBase #(8) m11(.a(a1),  .b(b1),  .p(z11));
  MulBase #(9) m10(.a(a1x), .b(b0s), .p(z10));
  MulBase #(9) m01(.a(a0s), .b(b1x), .p(z01));
  MulBase #(9) m00(.a(a0s), .b(b0s), .p(z00));

  wire signed [33:0] t1 = z11;                 // 权重 2^16
  wire signed [33:0] t2 = z10 + z01;           // 权重 2^8
  wire signed [33:0] t0 = z00;                 // 权重 1
  wire signed [33:0] acc = (t1 <<< 16) + (t2 <<< 8) + t0;

  assign p = acc[31:0];
endmodule

`endif
