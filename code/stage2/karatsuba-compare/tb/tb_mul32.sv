// ============================================================================
// tb_mul32.sv —— 32 位有符号乘法器自检 testbench（时钟同步 stimulus）
//   例化 Native / Booth / Schoolbook / Karatsuba 四个组合 DUT，
//   参考模型 longint 精确相乘（|ab| ≤ 2^62，64 位不溢出）；
//   覆盖 31×31 边界笛卡尔积 + 2 万组随机（含负数），分别统计错误数。
// ============================================================================
`timescale 1ns/1ps
module tb_mul32;
  localparam int NEDGE = 31*31;   // 961 组边界
  localparam int NRAND = 20000;
  localparam int TOTAL = NEDGE + NRAND;

  logic clk;
  logic signed [31:0] a, b;
  wire signed [63:0] p_nat, p_booth, p_school, p_kar;

  MulNative #(32) u_nat   (.a(a), .b(b), .p(p_nat));
  MulBooth  #(32) u_booth (.a(a), .b(b), .p(p_booth));
  MulSchool32     u_school(.a(a), .b(b), .p(p_school));
  MulKar32        u_kar   (.a(a), .b(b), .p(p_kar));

  initial begin clk = 0; forever #5 clk = ~clk; end

  // 64 位精确参考（32×32 不溢出）
  function automatic logic signed [63:0] refmul(input logic signed [31:0] x, y);
    longint rx, ry;
    rx = x; ry = y;
    refmul = rx * ry;
  endfunction

  // 31 个边界/典型值
  function automatic logic signed [31:0] edgeval(input int i);
    case (i)
       0: edgeval = 0;           1: edgeval = 1;          2: edgeval = -1;
       3: edgeval = 2;           4: edgeval = -2;         5: edgeval = 3;
       6: edgeval = -3;          7: edgeval = 30;         8: edgeval = -30;
       9: edgeval = 127;        10: edgeval = -128;      11: edgeval = 255;
      12: edgeval = -255;       13: edgeval = 256;       14: edgeval = -256;
      15: edgeval = 32767;      16: edgeval = -32768;    17: edgeval = 32768;
      18: edgeval = -32769;     19: edgeval = 65535;     20: edgeval = -65535;
      21: edgeval = 65536;      22: edgeval = -65536;    23: edgeval = 100000;
      24: edgeval = -100000;    25: edgeval = 123456;    26: edgeval = -123456;
      27: edgeval = 1000000;    28: edgeval = -1000000;  29: edgeval = 2147483647;
      30: edgeval = -2147483648;
      default: edgeval = 0;
    endcase
  endfunction

  int err_nat, err_booth, err_school, err_kar, ntests;

  task automatic sample;
    logic signed [63:0] r;
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
      sample;
      if (k < TOTAL) begin
        if (k < NEDGE) begin
          a = edgeval(k / 31); b = edgeval(k % 31);
        end else begin
          a = $random; b = $random;
        end
      end
    end

    $display("=========================================================");
    $display(" tests=%0d  nat err=%0d  booth err=%0d  school err=%0d  kar err=%0d",
             ntests, err_nat, err_booth, err_school, err_kar);
    if ((err_nat == 0) && (err_booth == 0) && (err_school == 0) && (err_kar == 0))
      $display("RESULT: ALL 32-bit MULTIPLIERS PASS");
    else
      $display("RESULT: SOME MULTIPLIER FAILED");
    $display("=========================================================");
    $finish;
  end
endmodule
