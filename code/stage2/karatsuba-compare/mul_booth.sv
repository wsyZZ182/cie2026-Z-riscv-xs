// ============================================================================
// mul_booth.sv —— Radix-4 Booth 有符号乘法（参数化 W，纯组合）
//
// 对应队友 VDotCore.BoothMultiplier：
//   部分积组数 NG = ceil((W+1)/2)；b 扩展为 {sign,b,1'b0}；
//   每个 3-bit 窗口选择 0/±a/±2a，负项取反后 +1 完成补码取负，
//   全部部分积符号扩展后按组号算术左移 2i 位累加，取低 2W 位。
// ============================================================================
`ifndef MUL_BOOTH_SV
`define MUL_BOOTH_SV

module MulBooth #(parameter int W = 16) (
  input  wire signed [W-1:0]    a,
  input  wire signed [W-1:0]    b,
  output logic signed [2*W-1:0] p
);
  localparam int NG   = (W + 1) / 2;   // 8→4, 16→8, 32→16
  localparam int WW   = 2 * W + 2;     // 中间符号扩展位宽

  wire [W+1:0]          bExt  = {b[W-1], b, 1'b0};
  wire signed [WW-1:0]  aWide = a;                 // 符号扩展
  wire signed [WW:0]    a2    = aWide <<< 1;       // 2a

  always @(*) begin
    logic signed [2*W+6:0] acc;
    acc = 0;
    for (int i = 0; i < NG; i++) begin
      logic [2:0]          bits;
      logic signed [WW:0]  raw;
      logic                isNeg;
      bits  = bExt[2*i+2 -: 3];
      isNeg = (bits == 3'b100) || (bits == 3'b101) || (bits == 3'b110);
      case (bits)
        3'b000: raw = '0;
        3'b001: raw = aWide;
        3'b010: raw = aWide;
        3'b011: raw = a2;
        3'b100: raw = ~a2;
        3'b101: raw = ~aWide;
        3'b110: raw = ~aWide;
        3'b111: raw = '0;
      endcase
      acc += (raw + (isNeg ? signed'(1) : signed'(0))) <<< (2 * i);
    end
    p = acc[2*W-1:0];
  end
endmodule

`endif
