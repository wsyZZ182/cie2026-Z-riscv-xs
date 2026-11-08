package k07

import chisel3._

/** Add rtl/K07.sv to the simulator or synthesis file list alongside emitted RTL.
  *
  * reset 端口声明为 Bool 而不是 AsyncReset：
  * K07.sv 内部是纯同步复位（唯一的 always @(posedge clock) 里 if (reset)），
  * 声明成 AsyncReset 会让 firtool 把整个 reset 网络推断为异步，
  * 把 VDotFu 自己的 vm_r/vl_r/pass2Fire 等寄存器全部生成成异步复位单元，
  * 与香山的同步复位域混用。
  */
class K07 extends BlackBox {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(AsyncReset())
    val io_flush = Input(Bool())

    val io_in_ready = Output(Bool())
    val io_in_valid = Input(Bool())
    val io_in_bits_vs1 = Input(UInt(128.W))
    val io_in_bits_vs2 = Input(UInt(128.W))
    val io_in_bits_vdOld = Input(UInt(32.W))
    val io_in_bits_a_signed = Input(Bool())
    val io_in_bits_b_signed = Input(Bool())
    val io_in_bits_e16 = Input(Bool())
    val io_in_bits_mask = Input(UInt(16.W))
    val io_in_bits_tag = Input(UInt(8.W))

    val io_out_ready = Input(Bool())
    val io_out_valid = Output(Bool())
    val io_out_bits_result = Output(UInt(32.W))
    val io_out_bits_tag = Output(UInt(8.W))
  })
}
