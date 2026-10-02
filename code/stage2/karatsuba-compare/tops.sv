// ============================================================================
// tops.sv —— 参数化 Native/Booth 在 16/32 位下的明确顶层封装
//   Schoolbook/Karatsuba 已是固定位宽模块，直接作为顶层；
//   Native/Booth 为参数化模块，通过这里的 wrapper 固定位宽后综合。
// ============================================================================
`ifndef TOPS_SV
`define TOPS_SV

module TopNative16 (
  input  wire signed [15:0] a, b,
  output wire signed [31:0] p
);
  MulNative #(16) dut(.a(a), .b(b), .p(p));
endmodule

module TopBooth16 (
  input  wire signed [15:0] a, b,
  output wire signed [31:0] p
);
  MulBooth #(16) dut(.a(a), .b(b), .p(p));
endmodule

module TopNative32 (
  input  wire signed [31:0] a, b,
  output wire signed [63:0] p
);
  MulNative #(32) dut(.a(a), .b(b), .p(p));
endmodule

module TopBooth32 (
  input  wire signed [31:0] a, b,
  output wire signed [63:0] p
);
  MulBooth #(32) dut(.a(a), .b(b), .p(p));
endmodule

`endif
