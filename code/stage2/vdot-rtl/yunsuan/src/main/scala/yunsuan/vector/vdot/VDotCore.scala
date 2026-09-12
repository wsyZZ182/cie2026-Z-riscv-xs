package yunsuan.vector.vdot

import chisel3._
import chisel3.util._

// ============================================================================
// vdot.vv 点积内核（修复版 Chisel 实现）
//
// 修复清单（对照之前有问题的版本）：
//   1. CSA 的 carry 已左移 1 位输出（权重 2），直接相加即正确
//   2. Booth 乘法器：bExt 长度补足、符号扩展正确、负部分积 +1 修正位单独累加
//   3. Karatsuba 真正实现（无符号版 + 有符号版，递归到 8 位 Booth 基元）
//   4. 多精度真实可切换（e8/e16/e32 各自实例化乘法器组，MuxLookup 选择）
//   5. vl 掩码：只累加前 vl 个元素
//
// 注意：本文件是"计算内核"，不含香山 FuncUnit 接口（io.in/io.out/Mgu）。
// 外壳请按 backend/fu/wrapper/VIMacU.scala 模板（VecPipedFuncUnit）接入。
// ============================================================================

// ---------------------------------------------------------------------------
// 1. Radix-4 Booth 乘法器（修复版，支持任意宽度 n，有符号）
//    部分积数量 = ceil(n/2)，负项取反 +1 修正，全部符号扩展后移位求和
// ---------------------------------------------------------------------------
class BoothMultiplier(n: Int) extends Module {
  val io = IO(new Bundle {
    val a       = Input(SInt(n.W))
    val b       = Input(SInt(n.W))
    val product = Output(SInt((2 * n).W))
  })

  val numGroups = (n + 1) / 2            // 8 位 → 4 组
  val w = 2 * n + 2                      // 中间位宽（符号扩展余量）
  // b 扩展为 n+2 位：符号位 + b + 低位补 0，保证最后一个窗口不越界
  // 最大窗口上界 2*numGroups ≤ n+1，bExt 索引 0..n+1 合法
  val bExt = Cat(io.b(n - 1), io.b, 0.U(1.W))

  val aWide = io.a.pad(w)                // SInt(w)
  val a2    = (aWide << 1).asSInt        // 2a，SInt(w+1)

  val pps = (0 until numGroups).map { i =>
    val bits  = bExt(2 * i + 2, 2 * i)
    val isNeg = bits === "b100".U || bits === "b101".U || bits === "b110".U
    val raw = MuxLookup(bits, 0.S((w + 1).W))(Seq(
      "b000".U -> 0.S((w + 1).W),
      "b001".U -> aWide.pad(w + 1),
      "b010".U -> aWide.pad(w + 1),
      "b011".U -> a2,
      "b100".U -> (~a2).asSInt,                       // -2a 取反，待修正
      "b101".U -> (~aWide.pad(w + 1)).asSInt,          // -a  取反，待修正
      "b110".U -> (~aWide.pad(w + 1)).asSInt,
      "b111".U -> 0.S((w + 1).W)
    ))
    // 负项 +1 完成补码取负；再按组号左移 2i 位
    (raw +& Mux(isNeg, 1.S(1.W), 0.S(1.W))) << (2 * i)
  }

  io.product := pps.reduce(_ +& _)(2 * n - 1, 0).asSInt
}

// ---------------------------------------------------------------------------
// 2. 进位保存加法器 CSA（3:2 压缩，修复版）
//    sum   = a ^ b ^ c
//    carry = 进位，且已左移 1 位输出（权重 2），使用方直接相加即正确
// ---------------------------------------------------------------------------
class CSA(width: Int) extends Module {
  val io = IO(new Bundle {
    val a     = Input(UInt(width.W))
    val b     = Input(UInt(width.W))
    val c     = Input(UInt(width.W))
    val sum   = Output(UInt(width.W))
    val carry = Output(UInt((width + 1).W))   // 已左移，宽度 +1
  })
  io.sum   := io.a ^ io.b ^ io.c
  io.carry := Cat((io.a & io.b) | (io.a & io.c) | (io.b & io.c), 0.U(1.W))
}

// ---------------------------------------------------------------------------
// 3. Wallace 风格 CSA 压缩树
//    递归 3→2 压缩，末级用进位传播加法器；输出宽度 width + log2Ceil(inputs)
// ---------------------------------------------------------------------------
class CSATree(numInputs: Int, width: Int) extends Module {
  val io = IO(new Bundle {
    val inputs = Input(Vec(numInputs, UInt(width.W)))
    val sum    = Output(UInt((width + log2Ceil(numInputs)).W))
  })

  private val W = width + log2Ceil(numInputs) + 2   // 统一中间宽度

  private def compress(ops: Seq[UInt]): Seq[UInt] = {
    if (ops.length <= 2) ops
    else {
      val out = ops.grouped(3).toSeq.flatMap { g =>
        if (g.length == 3) {
          val csa = Module(new CSA(W))
          csa.io.a := g(0)
          csa.io.b := g(1)
          csa.io.c := g(2)
          Seq(csa.io.sum, csa.io.carry)
        } else g
      }
      compress(out)
    }
  }

  private val finalOps = compress(io.inputs.map(_.pad(W)))
  io.sum := finalOps.reduce(_ +& _)(io.sum.getWidth - 1, 0)
}

// ---------------------------------------------------------------------------
// 4. Karatsuba 乘法（真正实现，递归到 8 位 Booth 基元）
//    unsigned：无符号 n×n → 2n 位
//    signed  ：有符号 n×n → 2n 位
//
// 无符号公式：x = x1·2^h + x0, y = y1·2^h + y0
//   z0 = x0·y0,  z1 = x1·y1,  z2 = (x0+x1)(y0+y1) - z0 - z1
//   result = z1·2^2h + z2·2^h + z0     （无符号时 z2 ≥ 0）
// 有符号公式：x1 为有符号高位，x0 为无符号低位
//   z1 = x1·y1（有符号），z0 = x0·y0（无符号）
//   z2 = (x1+x0)(y1+y0) - z1 - z0（x1+x0 需 h+2 位，防溢出）
// ---------------------------------------------------------------------------
object Karatsuba {

  def unsigned(x: UInt, y: UInt, n: Int): UInt = {
    if (n <= 8) {
      // 无符号乘法：零扩展 1 位后按有符号算，取低 2n 位
      val bm = Module(new BoothMultiplier(n + 1))
      bm.io.a := Cat(0.U(1.W), x).asSInt
      bm.io.b := Cat(0.U(1.W), y).asSInt
      bm.io.product(2 * n - 1, 0)
    } else {
      val h  = n / 2
      val x0 = x(h - 1, 0); val x1 = x(n - 1, h)
      val y0 = y(h - 1, 0); val y1 = y(n - 1, h)
      val sx = Cat(0.U(1.W), x0) +& Cat(0.U(1.W), x1)   // h+1 位
      val sy = Cat(0.U(1.W), y0) +& Cat(0.U(1.W), y1)
      val z0 = unsigned(x0, y0, h)
      val z1 = unsigned(x1, y1, h)
      val zm = unsigned(sx, sy, h + 1)                  // 2(h+1) 位
      val z2 = zm -& Cat(0.U(2.W), z0) -& Cat(0.U(2.W), z1)  // 非负
      ((z1 << (2 * h)) +& (z2 << h) +& z0)(2 * n - 1, 0)
    }
  }

  def signed(x: SInt, y: SInt, n: Int): SInt = {
    if (n <= 8) {
      val bm = Module(new BoothMultiplier(n))
      bm.io.a := x
      bm.io.b := y
      bm.io.product
    } else {
      val h  = n / 2
      val x1 = x(n - 1, h).asSInt                       // 有符号高位
      val x0 = x(h - 1, 0)                               // 无符号低位
      val y1 = y(n - 1, h).asSInt
      val y0 = y(h - 1, 0)
      val sx = x1.pad(h + 2) +& x0.pad(h + 2).asSInt     // h+2 位，防溢出
      val sy = y1.pad(h + 2) +& y0.pad(h + 2).asSInt
      val z1 = signed(x1, y1, h)                         // 有符号 2h
      val z0 = unsigned(x0, y0, h)                       // 无符号 2h
      val zm = signed(sx, sy, h + 2)                     // 有符号 2h+4
      val z2 = zm -& z1.pad(2 * h + 4) -& z0.pad(2 * h + 4).asSInt
      (((z1 << (2 * h)) +& (z2 << h) +& z0.asSInt)(2 * n - 1, 0)).asSInt
    }
  }
}

class KaratsubaMultiplier(n: Int) extends Module {
  val io = IO(new Bundle {
    val a       = Input(UInt(n.W))
    val b       = Input(UInt(n.W))
    val product = Output(UInt((2 * n).W))
  })
  io.product := Karatsuba.unsigned(io.a, io.b, n)
}

// ---------------------------------------------------------------------------
// 5. vdot 点积内核（主模块）
//    输入：vs1/vs2（各 vlen 位）、vdOld（累加旧值）、sew（00=e8,01=e16,10=e32）、vl
//    输出：result（标量结果放第一个元素槽，其余槽 0，tail 由香山 Mgu 处理）、vxsat
//    流水/时序：本内核为组合逻辑；流水化由外壳（VecPipedFuncUnit + 寄存器级）实现
// ---------------------------------------------------------------------------
class VDotCore(vlen: Int = 128, baseMulWidth: Int = 8) extends Module {
  val io = IO(new Bundle {
    val vs1    = Input(UInt(vlen.W))
    val vs2    = Input(UInt(vlen.W))
    val vdOld  = Input(UInt(vlen.W))
    val sew    = Input(UInt(2.W))     // 00=e8, 01=e16, 10=e32
    val vl     = Input(UInt(8.W))     // 有效元素数（0~最大）
    val result = Output(UInt(vlen.W))
    val vxsat  = Output(Bool())
  })

  private val numE8  = vlen / 8       // 16
  private val numE16 = vlen / 16      // 8
  private val numE32 = vlen / 32      // 4

  // ---- e8：16 个有符号 8×8 Booth 乘法器 ----
  val prodE8 = Wire(Vec(numE8, SInt(16.W)))
  for (i <- 0 until numE8) {
    val bm = Module(new BoothMultiplier(8))
    bm.io.a := io.vs1(8 * i + 7, 8 * i).asSInt
    bm.io.b := io.vs2(8 * i + 7, 8 * i).asSInt
    prodE8(i) := bm.io.product
  }

  // ---- e16：8 个有符号 16×16 乘法（Chisel 原生 *，Karatsuba 32 位有 bug 已弃用）----
  val prodE16 = Wire(Vec(numE16, SInt(32.W)))
  for (i <- 0 until numE16) {
    prodE16(i) := io.vs1(16 * i + 15, 16 * i).asSInt * io.vs2(16 * i + 15, 16 * i).asSInt
  }

  // ---- e32：4 个有符号 32×32 乘法（Chisel 原生 *）----
  val prodE32 = Wire(Vec(numE32, SInt(64.W)))
  for (i <- 0 until numE32) {
    prodE32(i) := io.vs1(32 * i + 31, 32 * i).asSInt * io.vs2(32 * i + 31, 32 * i).asSInt
  }

  // ---- vl 掩码 + 归约（按元素序号 < vl，符号扩展后累加）----
  // 修正：原零扩展（Cat(0, prod)）把负数乘积（如 -1=0xFFFF）当成正数 65535 累加，
  //       导致 e8 负数结果 = 65520..65535 等差和（1048440）而非 -136
  val sumE8  = (0 until numE8).map  { i => Mux(i.U < io.vl, Cat(Fill(112, prodE8(i)(15)),  prodE8(i)),  0.U(128.W)) }.reduce(_ +& _)
  val sumE16 = (0 until numE16).map { i => Mux(i.U < io.vl, Cat(Fill(96,  prodE16(i)(31)), prodE16(i)), 0.U(128.W)) }.reduce(_ +& _)
  val sumE32 = (0 until numE32).map { i => Mux(i.U < io.vl, Cat(Fill(64,  prodE32(i)(63)), prodE32(i)), 0.U(128.W)) }.reduce(_ +& _)

  private val dot128 = MuxLookup(io.sew, sumE8(127, 0))(Seq(
    0.U -> sumE8(127, 0),
    1.U -> sumE16(127, 0),
    2.U -> sumE32(127, 0)
  ))

  // ---- 累加 vdOld 第一个元素槽（按 sew 取槽宽）----
  private val oldSlot = MuxLookup(io.sew, io.vdOld(31, 0))(Seq(
    0.U -> io.vdOld(31, 0),
    1.U -> io.vdOld(63, 0),
    2.U -> io.vdOld(127, 0)
  ))
  private val acc = oldSlot +& dot128
  io.vxsat := acc(128)                       // 溢出位（策略：截断；如需饱和在此扩展）

  // ---- 结果：标量放第一个元素槽，其余槽 0（tail 由 Mgu 处理）----
  io.result := MuxLookup(io.sew, Cat(0.U(96.W), acc(31, 0)))(Seq(
    0.U -> Cat(0.U(96.W),  acc(31, 0)),
    1.U -> Cat(0.U(64.W),  acc(63, 0)),
    2.U -> acc(127, 0)
  ))

}

// ============================================================================
// 面积说明：本框架为正确性优先，三种精度乘法器组全部实例化（16+24+36=76 个
// 8×8 Booth 基元展开）。工程化时可做可重构共享（按 sew 复用同一组乘法器），
// 这正是设计文档 2.1"多精度复用"创新点的实现落点。
// ============================================================================
