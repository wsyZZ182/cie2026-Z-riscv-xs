package vdot
import chisel3._
import chisel3.util._
class CounterProof(n:Int) extends RawModule {
 val x=IO(Input(UInt(n.W)));val bad=IO(Output(Bool()))
 val bits=Vector.tabulate(n)(i=>FastLogic.Bit(x(i),0))
 val expected=PopCount(x)
 val ref=VecInit(FastLogic.referenceCount(bits).map(_.v)).asUInt
 val pre=VecInit(FastLogic.prefixCount(bits).map(_.v)).asUInt
 val parity=FastLogic.balancedXor(bits).v
 val threshold=FastLogic.sortedBits(bits)((n+1)/2-1).v
 // Prove every selectable expression, including choices made for unequal arrival times.
 bad := (ref =/= expected) || (pre =/= expected) ||
   (parity =/= expected(0)) || (threshold =/= expected(log2Ceil(n+1)-1))
}
object EmitCounterProof extends App {
 val n=args(0).toInt
 _root_.circt.stage.ChiselStage.emitSystemVerilogFile(new CounterProof(n),
 args=Array("--target-dir",args(1)),firtoolOpts=Array("-disable-all-randomization","-strip-debug-info","--disable-layers=Verification"))
}
