package vdot
import chisel3._
import chisel3.util._
case class BatchConfig(id:String, flags:Set[String]) {
 def has(x:String)=flags(x)
 require(!(has("S08") && (has("S01") || has("Q1"))))
}
object BatchCatalog {
 val single=(1 to 8).map(i=>f"S$i%02d")++Seq("Q1","Q2","Q3")
 val combinations=Seq(
  "K01"->Set("S02","S03"),
  "K02"->Set("S02","S03","S01"),
  "K03"->Set("S02","S03","S04"),
  "K04"->Set("S02","S03","S01","S04"),
  "K05"->Set("S02","S03","S01","S04","S06","S07"),
  "K06"->Set("S02","S03","Q1","S04","S06","S07"),
  "K07"->Set("S02","S03","S04","S06","S07"),
  "K08"->Set("S02","S03","S04","S06","S07","S08"),
  "K09"->Set("S02","S03","S04","S06","S07","S08","Q2"),
  "K10"->Set("S02","S03","S04","S06","S07","S08","Q3"),
  "K11"->Set("S02","S03","S04","S06","S07","S08","Q2","Q3"),
  "K12"->Set("S05","S01","S06","S07"))
 val configs=Seq(BatchConfig("B",Set.empty))++single.map(x=>BatchConfig(x,Set(x)))++combinations.map{case(k,v)=>BatchConfig(k,v)}
 val byId=configs.map(c=>c.id->c).toMap
}
/** Elaboration only; emit one top per process. Not runtime hardware. */
object BatchContext {var cfg=BatchCatalog.byId("K07")}
object BatchAdders {
 def csla(a:UInt,b:UInt,blocks:Seq[Int]):UInt={
  require(blocks.sum==32)
  var carry=false.B;var offset=0
  val bits=scala.collection.mutable.ArrayBuffer.empty[Bool]
  for(w<-blocks){
   val zero=a(offset+w-1,offset)+&b(offset+w-1,offset)
   // Increment w+1 bits, including carry-out.
   val one=(zero+1.U)(w,0)
   val selected=Mux(carry,one,zero)
   bits++=(0 until w).map(selected(_));carry=selected(w);offset+=w
  }
  VecInit(bits.toSeq).asUInt
 }
}
object EmitBatch extends App {
 require(args.length>=2,"runMain vdot.EmitBatch ID output-directory")
 BatchContext.cfg=BatchCatalog.byId(args(0))
 println(s"CONFIG ${args(0)} ${BatchContext.cfg.flags.toSeq.sorted.mkString(",")}")
 _root_.circt.stage.ChiselStage.emitSystemVerilogFile(new BWFastTop,
  args=Array("--target-dir",args(1)),firtoolOpts=Array("-disable-all-randomization","-strip-debug-info","--disable-layers=Verification","--lowering-options=disallowLocalVariables,disallowPackedArrays,disallowExpressionInliningInPorts"))
}
