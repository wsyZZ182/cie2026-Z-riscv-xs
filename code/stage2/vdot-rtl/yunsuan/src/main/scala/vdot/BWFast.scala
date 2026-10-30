package vdot
import chisel3._
import chisel3.util._
import scala.collection.mutable.ArrayBuffer

/** Exact modular arithmetic. Arrival numbers are gate-depth proxies, NOT ps/FO4. */
private[vdot] object FastLogic {
  def cfg=BatchContext.cfg
  case class Bit(v: Bool, t: Int)
  type Heap = Vector[Vector[Bit]]
  def xor(a: Bit,b: Bit)=Bit(a.v ^ b.v,math.max(a.t,b.t)+1)
  def and(a: Bit,b: Bit)=Bit(a.v & b.v,math.max(a.t,b.t)+1)
  def or(a: Bit,b: Bit)=Bit(a.v | b.v,math.max(a.t,b.t)+1)
  def fa(a: Bit,b: Bit,c: Bit): Vector[Bit] = {
    val p=xor(a,b)
    Vector(xor(p,c),or(and(a,b),and(p,c)))
  }
  // Recursive exact count: 7=3+3+1 and 15=7+7+1.
  // This is an FA-based reference implementation, not a sorting-network paper reproduction.
  def referenceCount(xs: Vector[Bit]): Vector[Bit] = {
    require(Set(3,7,15,31)(xs.size))
    if(xs.size==3) fa(xs(0),xs(1),xs(2)) else {
      val n=(xs.size-1)/2
      val a=referenceCount(xs.take(n)); val b=referenceCount(xs.slice(n,2*n))
      var c=xs.last
      val out=a.indices.map { i => val z=fa(a(i),b(i),c);c=z(1);z(0) }.toVector
      out :+ c
    }
  }
  // Parallel prefix combination of two smaller counts and the last bit.
  // Exact independently derived candidate; NOT claimed as Guo-Li reproduction.
  def prefixCount(xs: Vector[Bit]): Vector[Bit] = {
    if(xs.size==3) return fa(xs(0),xs(1),xs(2))
    val n=(xs.size-1)/2
    val a=prefixCount(xs.take(n));val b=prefixCount(xs.slice(n,2*n))
    val bp=a.zip(b).map{case(x,y)=>xor(x,y)}
    var p=bp;var g=a.zip(b).map{case(x,y)=>and(x,y)};var d=1
    while(d<a.size){val pp=p;val gg=g
      for(i<-d until a.size){g=g.updated(i,or(gg(i),and(pp(i),gg(i-d))));p=p.updated(i,and(pp(i),pp(i-d)))}
      d*=2
    }
    val carry=g.indices.map(i=>or(g(i),and(p(i),xs.last))).toVector
    bp.indices.map(i=>xor(bp(i),if(i==0) xs.last else carry(i-1))).toVector :+ carry.last
  }
  def balancedXor(xs:Vector[Bit]):Bit = {
    if(xs.size==1) xs.head else {val n=xs.size/2;xor(balancedXor(xs.take(n)),balancedXor(xs.drop(n)))}
  }
  // Batcher bitonic compare/exchange network, descending threshold outputs.
  // Exact hybrid candidate, not a reproduction of optimized Guo-Li equations.
  def sortedBits(xs:Vector[Bit]):Vector[Bit] = {
    var v=xs :+ Bit(false.B,0);var k=2
    while(k<=v.size){var j=k/2
      while(j>0){val prev=v
        for(i<-v.indices if ((i^j)>i)){
          val other=i^j;val hi=or(prev(i),prev(other));val lo=and(prev(i),prev(other))
          val descending=(i&k)==0
          v=v.updated(i,if(descending) hi else lo).updated(other,if(descending) lo else hi)
        };j/=2
      };k*=2
    };v
  }
  def fastCount(xs:Vector[Bit]):Vector[Bit] = {
    val ref=referenceCount(xs);val pre=prefixCount(xs)
    val sorted=sortedBits(xs)
    ref.indices.map {i=>
      val chosen=if(pre(i).t<ref(i).t) pre(i) else ref(i)
      if(i==0){val parity=balancedXor(xs);if(parity.t<chosen.t) parity else chosen}
      else if(i==ref.size-1 && sorted((1<<i)-1).t<chosen.t) sorted((1<<i)-1)
      else chosen
    }.toVector
  }
  def count(xs: Vector[Bit]): Vector[Bit] = {
    if((xs.size==7 && cfg.has("S02")) || (xs.size==15 && cfg.has("S03")) || xs.size==31) fastCount(xs)
    else referenceCount(xs)
  }
  def predict(ts:Vector[Int],fast:Boolean):Vector[Int] = {
    def faT(a:Int,b:Int,c:Int)=Vector(math.max(math.max(a,b)+1,c)+1,
      math.max(math.max(a,b)+1,math.max(math.max(a,b)+1,c)+1)+1)
    def ref(v:Vector[Int]):Vector[Int]={
      if(v.size==3) faT(v(0),v(1),v(2)) else {
        val n=(v.size-1)/2;val a=ref(v.take(n));val b=ref(v.slice(n,2*n));var c=v.last
        val out=a.indices.map{i=>val z=faT(a(i),b(i),c);c=z(1);z(0)}.toVector;out:+c
      }
    }
    // Conservative FA cost for scheduling; no fictitious one-level large counter.
    ref(ts)
  }
  def chooseSize(pending:Vector[Bit]):Int={
    val choices=Vector(3,7,15)++(if(cfg.has("Q2")) Vector(31) else Vector.empty)
    val feasible=choices.filter(_<=pending.size)
    // Bounded two-counter lookahead. Evaluate each output time and residual height.
    def score(n:Int):Double={
      val out=predict(pending.take(n).map(_.t),false)
      val remain=pending.drop(n)
      val next=choices.filter(_<=remain.size)
      val nextTime=if(next.isEmpty) remain.map(_.t).maxOption.getOrElse(0).toDouble
        else next.map(m=>predict(remain.take(m).map(_.t),false).max.toDouble +
          math.log1p(math.max(0,remain.size-m))).min
      math.max(out.max.toDouble,nextTime)+2.0*math.log1p(pending.size-n+out.size)
    }
    feasible.minBy(n=>(score(n),-n))
  }
  def empty: Heap=Vector.fill(32)(Vector.empty)
  def word(x: UInt,t: Int=0): Heap=Vector.tabulate(32)(i=>Vector(Bit(x(i),t)))
  def join(xs: Seq[Heap]): Heap=Vector.tabulate(32)(i=>xs.flatMap(_(i)).toVector)
  def bw(a: UInt,b: UInt,sa: Bool,sb: Bool,en: Bool): Heap = {
    val h=Array.fill(32)(ArrayBuffer.empty[Bit])
    for(j<-0 until 8;i<-0 until 8) {
      val raw=a(i)&b(j)
      val v=if(i==7 && j==7) raw^(sa^sb) else if(i==7) raw^sa else if(j==7) raw^sb else raw
      h(i+j)+=Bit(v & en,if(i==7||j==7) 4 else 3)
    }
    h(7)+=Bit((sa^sb)&en,3); h(8)+=Bit(sa&sb&en,3)
    if(cfg.has("S06")) h(15)+=Bit(!((sa|sb)&en),4)
    else for(i<-15 until 32) h(i)+=Bit((sa|sb)&en,3)
    h.map(_.toVector).toVector
  }
  /** Last compression layer: each column acts on OLD inputs only.
    * A two-bit column is a half adder, not a bypass waiting for its neighbour.
    * Output carry goes straight into the second row at k+1. It is NOT consumed
    * again in this layer. Discard only carry beyond bit31 (modulo 2^32).
    */
  def parallelTail(input: Heap): Heap = {
    require(input.size==32 && input.forall(_.size<=3))
    val next=Array.fill(32)(ArrayBuffer.empty[Bit])
    for(k<-0 until 32) {
      val in=input(k).sortBy(_.t)
      val out=in.size match {
        case 0 => Vector.empty[Bit]
        case 1 => in
        case 2 => Vector(xor(in(0),in(1)),and(in(0),in(1)))
        case 3 => fa(in(0),in(1),in(2))
      }
      if(out.nonEmpty) next(k)+=out(0)
      if(out.size==2 && k+1<32) next(k+1)+=out(1)
    }
    val result=next.map(_.toVector).toVector
    require(result.forall(_.size<=2),"Tail must produce at most two rows")
    result
  }
  def reduce(input: Heap,label: String, leaveThree: Boolean=false): Heap = {
    var h=input;var layer=0
    while(h.exists(_.size>3)) {
      val next=Array.fill(32)(ArrayBuffer.empty[Bit])
      val usage=ArrayBuffer.empty[String]
      for(k<-0 until 32) {
        var pending=h(k).sortBy(_.t)
        while(pending.size>=3) {
          // Early-arrival clustering; bounded greedy policy, not global optimal search.
          val n=if(cfg.has("S05")) 3 else if(cfg.has("S04")) chooseSize(pending) else if(cfg.has("Q2") && pending.size>=31) 31 else if(pending.size>=15 && pending(14).t-pending.head.t<=2) 15
            else if(pending.size>=7 && pending(6).t-pending.head.t<=2) 7 else 3
          val in=pending.take(n);pending=pending.drop(n)
          val out=count(in)
          out.zipWithIndex.foreach {case(b,d)=>if(k+d<32) next(k+d)+=b}
          usage+=s"$k:$n:${in.map(_.t).mkString(",")}->${out.map(_.t).mkString(",")}"
        }
        next(k)++=pending
      }
      h=next.map(_.toVector).toVector
      println(s"TREE $label layer=$layer max=${h.map(_.size).max} ${usage.mkString(";")}")
      layer+=1;require(layer<128)
    }
    val mixedLayers=layer
    val hasTail= !leaveThree && h.exists(_.size>2)
    if(hasTail) {
      h=parallelTail(h)
      println(s"TREE $label layer=$layer max=${h.map(_.size).max} parallel_tail")
      layer+=1
    }
    val maxArrival=h.flatten.map(_.t).maxOption.getOrElse(0)
    println(s"TREE_SUMMARY $label layers=$layer mixed=$mixedLayers tail=${if(hasTail) 1 else 0} max_arrival=$maxArrival")
    h
  }
  def rows(h: Heap): (UInt,UInt) = {
    require(h.forall(_.size<=2))
    def row(n: Int)=VecInit(h.map(_.lift(n).map(_.v).getOrElse(false.B))).asUInt
    (row(0),row(1)) // Carry row already aligned: NEVER shift it again.
  }
  def ks(a: UInt,b: UInt): UInt = {
    val bp=Vector.tabulate(32)(i=>a(i)^b(i));var p=bp
    var g=Vector.tabulate(32)(i=>a(i)&b(i));var d=1
    while(d<32) {
      val pp=p;val gg=g
      for(i<-d until 32) {g=g.updated(i,gg(i)|(pp(i)&gg(i-d)));p=p.updated(i,pp(i)&pp(i-d))}
      d*=2
    }
    VecInit((0 until 32).map(i=>bp(i)^(if(i==0) false.B else g(i-1)))).asUInt
  }
  def fusedThree(h:Heap):UInt={
    require(h.forall(_.size<=3))
    def v(k:Int,i:Int)=h(k).lift(i).map(_.v).getOrElse(false.B)
    val sums=Vector.tabulate(32)(k=>v(k,0)^v(k,1)^v(k,2))
    val carry=Vector.tabulate(32)(k=>if(k==0) false.B else {
      val a=v(k-1,0);val b=v(k-1,1);val c=v(k-1,2)
      (a&b)|(a&c)|(b&c)
    })
    // Majority-based carry and parity feed prefix directly, no binary group sum.
    ks(VecInit(sums).asUInt,VecInit(carry).asUInt)
  }
  def finish(h: Heap,label: String): UInt = {
    if(label=="e8_merge" && cfg.has("S08")) return fusedThree(reduce(h,label,true))
    val (s,c)=rows(reduce(h,label))
    if(label=="e8_merge" && cfg.has("Q1")) BatchAdders.csla(s,c,Seq(2,3,4,5,6,6,6))
    else if(label=="e8_merge" && cfg.has("S01")) BatchAdders.csla(s,c,Seq.fill(8)(4))
    else ks(s,c)
  }

}
class FastRequest extends Bundle {
  val vs1=UInt(128.W);val vs2=UInt(128.W);val vdOld=UInt(32.W)
  val a_signed=Bool();val b_signed=Bool();val sew=UInt(2.W)
  val mask=UInt(16.W);val tag=UInt(8.W)
}
class FastResponse extends Bundle {val result=UInt(32.W);val tag=UInt(8.W)}
class GroupPair extends Bundle {val s=UInt(32.W);val c=UInt(32.W);val third=UInt(32.W)}

/** One physical frontend: four groups, four BW8 generators per group. */
class SharedBWFrontend extends RawModule {
  import FastLogic._
  val req=IO(Input(new FastRequest));val second=IO(Input(Bool()))
  val groups=IO(Output(Vec(4,new GroupPair)));val e8=IO(Output(UInt(32.W)))
  val e16=req.sew===1.U
  val av=VecInit((0 until 16).map(i=>req.vs1(8*i+7,8*i)))
  val bv=VecInit((0 until 16).map(i=>req.vs2(8*i+7,8*i)))
  var generatorCount=0
  val heaps=(0 until 4).map {g=>
    val ah=(g==1||g==3);val bh=(g==2||g==3)
    val tiles=(0 until 4).map {j=>
      val ix=Mux(second,(j+4).U(3.W),j.U(3.W))
      val ai=Cat(ix,ah.B);val bi=Cat(ix,bh.B)
      val e8sel= !e16;val lo=e16 && !second;val hi=e16 && second
      def route(v:Vec[UInt],high:Boolean):UInt={
        val idx=2*j+(if(high) 1 else 0)
        (v(g*4+j)&Fill(8,e8sel))|(v(idx)&Fill(8,lo))|(v(idx+8)&Fill(8,hi))
      }
      val a=if(cfg.has("S07")) route(av,ah) else Mux(e16,av(ai),av(g*4+j))
      val b=if(cfg.has("S07")) route(bv,bh) else Mux(e16,bv(bi),bv(g*4+j))
      val sa=req.a_signed & (!e16 || ah.B);val sb=req.b_signed & (!e16 || bh.B)
      val en=Mux(e16,req.mask(ix),req.mask(g*4+j))
      generatorCount+=1
      bw(a,b,sa,sb,en)
    }
    val correction=Vector.tabulate(32)(k=>if(k>=17) Vector(Bit(true.B,0)) else Vector.empty[Bit])
    val input=join(if(cfg.has("S06")) tiles:+correction else tiles)
    val h=reduce(input,s"group$g",cfg.has("Q3"))
    def row(i:Int)=VecInit(h.map(_.lift(i).map(_.v).getOrElse(false.B))).asUInt
    groups(g).s:=row(0);groups(g).c:=row(1);groups(g).third:=row(2);h
  }
  require(generatorCount==16)
  println(s"RESOURCE_SUMMARY groups=4 bw8_generators=$generatorCount")
  e8:=finish(join(heaps:+word(req.vdOld)),"e8_merge")
}

/** E8 L=1, II=1 without stalls. E16 L=4, two frontend batches and two merge cycles.
  * Response held stable under backpressure. Flush cancels response and in-flight E16.
  * sew 2/3 are unsupported and return vdOld (no E32 multiplier).
  */
class BWFastTop extends Module {
  import FastLogic._
  val io=IO(new Bundle {
    val in=Flipped(Decoupled(new FastRequest));val out=Decoupled(new FastResponse)
    val flush=Input(Bool())
  })
  val state=RegInit(0.U(2.W)) // 0 idle, 1 batch1, 2 merge0, 3 merge1
  val saved=Reg(new FastRequest)
  val p0=Reg(Vec(4,new GroupPair));val p1=Reg(Vec(4,new GroupPair))
  val acc=Reg(UInt(32.W));val outBits=Reg(new FastResponse);val valid=RegInit(false.B)
  val front=Module(new SharedBWFrontend)
  front.req:=Mux(state===0.U,io.in.bits,saved);front.second:=state===1.U
  val selected=Mux(state===3.U,p1,p0)
  val shifted=(0 until 4).flatMap {g=>
    val shift=Seq(0,8,8,16)(g)
    val main=Seq(word((selected(g).s<<shift)(31,0)),word((selected(g).c<<shift)(31,0)))
    if(cfg.has("Q3")) main:+word((selected(g).third<<shift)(31,0)) else main
  }
  val merged=finish(join(shifted:+word(Mux(state===3.U,acc,saved.vdOld))),"e16_merge")
  io.in.ready:=state===0.U && (!valid||io.out.ready) && !io.flush
  io.out.valid:=valid && !io.flush;io.out.bits:=outBits
  when(io.out.fire) {valid:=false.B}
  when(state===1.U) {p1:=front.groups;state:=2.U}
  when(state===2.U) {acc:=merged;state:=3.U}
  when(state===3.U) {outBits.result:=merged;outBits.tag:=saved.tag;valid:=true.B;state:=0.U}
  when(io.in.fire) {
    when(io.in.bits.sew===1.U) {saved:=io.in.bits;p0:=front.groups;state:=1.U}
      .otherwise {outBits.result:=Mux(io.in.bits.sew===0.U,front.e8,io.in.bits.vdOld)
        outBits.tag:=io.in.bits.tag;valid:=true.B}
  }
  when(io.flush) {state:=0.U;valid:=false.B}
}
object EmitBWFast extends App {
  // firtoolOpts 说明：
  //      子目录，在无法映射该路径的 Windows 代码页上直接失败；这些文件只是断言脚手架，
  //      不改变综合出的数据通路。
  //  --lowering-options=... : 让输出更贴近可综合子集（不开 always 块内局部变量、
  //      不用 packed array 端口），这样 Yosys 能直接读入做门级综合。
  //      这些选项只改变写法，不改变逻辑。
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(new BWFastTop,
    args=Array("--target-dir",args.headOption.getOrElse("generated")),
    firtoolOpts=Array("-disable-all-randomization","-strip-debug-info",
      "--lowering-options=disallowLocalVariables,disallowPackedArrays,disallowExpressionInliningInPorts"))
}
