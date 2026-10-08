// ============================================================================
// tb_mul16.sv —— 16 位有符号乘法器自检 testbench（时钟同步 stimulus）
//   同时例化 Native / Booth / Schoolbook / Karatsuba 四个组合 DUT，
//   在时钟沿采样（输入已稳定整拍），参考模型用 64 位 longint 精确相乘；
//   覆盖 45×45 边界笛卡尔积 + 2 万组随机（含负数），分别统计错误数。
// ============================================================================
`timescale 1ns/1ps
module tb_mul16;
  localparam int NEDGE = 45*45;   // 2025 组边界
  localparam int NRAND = 20000;
  localparam int TOTAL = NEDGE + NRAND;

  logic clk;
  logic signed [15:0] a, b;
  wire signed [31:0] p_nat, p_booth, p_school, p_kar;

  MulNative #(16) u_nat   (.a(a), .b(b), .p(p_nat));
  MulBooth  #(16) u_booth (.a(a), .b(b), .p(p_booth));
  MulSchool16     u_school(.a(a), .b(b), .p(p_school));
  MulKar16        u_kar   (.a(a), .b(b), .p(p_kar));

  // 时钟
  initial begin clk = 0; forever #5 clk = ~clk; end

  // 64 位精确参考
  function automatic logic signed [31:0] refmul(input logic signed [15:0] x, y);
    longint rx, ry;
    rx = x; ry = y;
    refmul = (rx * ry) & 64'hFFFFFFFF;
  endfunction

  // 45 个边界/典型值
  function automatic logic signed [15:0] edgeval(input int i);
    case (i)
       0: edgeval = 0;      1: edgeval = 1;      2: edgeval = -1;
       3: edgeval = 2;      4: edgeval = -2;     5: edgeval = 3;
       6: edgeval = -3;     7: edgeval = 4;      8: edgeval = -4;
       9: edgeval = 7;     10: edgeval = -7;    11: edgeval = 8;
      12: edgeval = -8;    13: edgeval = 15;    14: edgeval = -15;
      15: edgeval = 16;    16: edgeval = -16;   17: edgeval = 30;
      18: edgeval = -30;   19: edgeval = 100;   20: edgeval = -100;
      21: edgeval = 127;   22: edgeval = -128;  23: edgeval = 128;
      24: edgeval = -129;  25: edgeval = 255;   26: edgeval = -255;
      27: edgeval = 256;   28: edgeval = -256;  29: edgeval = 257;
      30: edgeval = -257;  31: edgeval = 511;   32: edgeval = -511;
      33: edgeval = 512;   34: edgeval = -512;  35: edgeval = 1000;
      36: edgeval = -1000; 37: edgeval = 1023;  38: edgeval = -1023;
      39: edgeval = 1024;  40: edgeval = -1024; 41: edgeval = 12345;
      42: edgeval = -12345; 43: edgeval = 32767; 44: edgeval = -32768;
      default: edgeval = 0;
    endcase
  endfunction

  int err_nat, err_booth, err_school, err_kar, ntests;

  task automatic sample;
    logic signed [31:0] r;
    begin
      r = refmul(a, b);
      ntests++;
      if (p_nat    !== r) begin err_nat++;    $display("NAT    fail %0d * %0d : exp %0d got %0d", a, b, r, p_nat); end
      if (p_booth  !== r) begin err_booth++;  $display("BOOTH  fail %0d * %0d : exp %0d got %0d", a, b, r, p_booth); end
      if (p_school !== r) begin err_school++; $display("SCHOOL fail %0d * %0d : exp %0d got %0d", a, b, r, p_school); end
      if (p_kar    !== r) begin err_kar++;    $display("KAR    fail %0d * %0d : exp %0d got %0d", a, b, r, p_kar); end
    end
  endtask

  initial begin
    err_nat = 0; err_booth = 0; err_school = 0; err_kar = 0; ntests = 0;
    a = 0; b = 0;
    repeat (2) @(posedge clk);

    for (int k = 0; k <= TOTAL; k++) begin
      @(posedge clk);
      sample;                                   // 采样已稳定的当前向量
      if (k < TOTAL) begin
        if (k < NEDGE) begin
          a = edgeval(k / 45); b = edgeval(k % 45);
        end else begin
          a = $random; b = $random;
        end
      end
    end

    $display("=========================================================");
    $display(" tests=%0d  nat err=%0d  booth err=%0d  school err=%0d  kar err=%0d",
             ntests, err_nat, err_booth, err_school, err_kar);
    if ((err_nat == 0) && (err_booth == 0) && (err_school == 0) && (err_kar == 0))
      $display("RESULT: ALL 16-bit MULTIPLIERS PASS");
    else
      $display("RESULT: SOME MULTIPLIER FAILED");
    $display("=========================================================");
    $finish;
  end
endmodule
