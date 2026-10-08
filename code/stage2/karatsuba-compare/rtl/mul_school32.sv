// ============================================================================
// mul_school32.sv —— 普通"学校法"位分解 32×32（4 个 16 位基元）
//
// a = a1·2^16 + a0, b = b1·2^16 + b0
//   z11 = a1*b1（复用 MulKar16，与 Kar32 的高半口径一致，保证公平）
//   z10 = a1*b0, z01 = a0*b1（17 位：高半符号扩展 + 低半零扩展非负）
//   z00 = a0*b0（非负）
// 结果 = z11·2^32 + (z10+z01)·2^16 + z00
//
// 构成：1×Kar16 + 3×Base17（对照 Kar32 的 1×Kar16 + 1×Base17 + 1×Base18）。
// ============================================================================
`ifndef MUL_SCHOOL32_SV
`define MUL_SCHOOL32_SV

module MulSchool32 (
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
  wire signed [16:0] a1x = {a1[15], a1};      // 符号扩展
  wire signed [16:0] b1x = {b1[15], b1};

  wire signed [31:0] z11;  // a1*b1
  wire signed [33:0] z10;  // a1*b0
  wire signed [33:0] z01;  // a0*b1
  wire signed [33:0] z00;  // a0*b0 (non-neg)

  MulKar16     m11(.a(a1),  .b(b1),  .p(z11));
  MulBase #(17) m10(.a(a1x), .b(b0s), .p(z10));
  MulBase #(17) m01(.a(a0s), .b(b1x), .p(z01));
  MulBase #(17) m00(.a(a0s), .b(b0s), .p(z00));

  wire signed [65:0] t1 = z11;                // 权重 2^32
  wire signed [65:0] t2 = z10 + z01;          // 权重 2^16
  wire signed [65:0] t0 = z00;                // 权重 1
  wire signed [65:0] acc = (t1 <<< 32) + (t2 <<< 16) + t0;

  assign p = acc[63:0];
endmodule

`endif
