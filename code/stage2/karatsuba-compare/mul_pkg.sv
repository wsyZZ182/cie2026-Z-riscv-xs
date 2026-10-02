// ============================================================================
// mul_pkg.sv —— 统一叶子基元 & 原生乘法
//
// MulBase   : 参数化有符号 W×W 原生小乘法，作为 Karatsuba / schoolbook 的
//             统一"叶子基元"，保证不同分解方案的对比公平。
// MulNative : 整体直接 a*b（综合器/Verilator 原生），对应队友 Chisel 原生 '*'。
// ============================================================================
`ifndef MUL_PKG_SV
`define MUL_PKG_SV

module MulBase #(parameter int W = 8) (
  input  wire signed [W-1:0]   a,
  input  wire signed [W-1:0]   b,
  output wire signed [2*W-1:0] p
);
  assign p = a * b;
endmodule

module MulNative #(parameter int W = 16) (
  input  wire signed [W-1:0]   a,
  input  wire signed [W-1:0]   b,
  output wire signed [2*W-1:0] p
);
  assign p = a * b;
endmodule

`endif
