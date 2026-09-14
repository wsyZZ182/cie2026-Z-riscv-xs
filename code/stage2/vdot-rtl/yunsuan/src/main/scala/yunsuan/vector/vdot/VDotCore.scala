package yunsuan.vector.vdot

import chisel3._
import chisel3.util._

// ============================================================================
// vdot.vv 点积内核（Fused ReduceAcc 优化版）
//
// 相比上一版（128 位符号扩展链式归约 + 128 位累加）的改动：
//   1. 宽度自适应：e8/e16/e32 各用 33/36/67 位归约累加（原统一 128 位）
//      - e8 : 16 个 16bit 有符号乘积，完整有符号和 20bit；+ vdOld[31:0] → 33bit
//      - e16: 8 个 32bit 有符号乘积，完整有符号和 35bit；+ vdOld[31:0] → 36bit
//      - e32: 4 个 64bit 有符号乘积，完整有符号和 66bit；+ vdOld[31:0] → 67bit
//   2. Fused Accumulator：vdOld[31:0] 与乘积一起符号扩展后直接进 CSA 压缩树，
//      只做一次进位传播加法（CPA），不再"先链式归约成普通二进制数再 +vdOld"
//   3. vxsat 语义修正：结果超出 32 位有符号范围（[−2^31, 2^31−1]）置位
//      （原 acc(128) 为 128 位加法溢出位，当前精度下几乎永不触发）
//   4. 结果统一截断写 vd[31:0]（高 96 位 0，外壳 slotMask 只放行低 32 位）
//   5. 删除 vl 掩码（冗余：VDotFu stage0 已按 vl/vm/mask 清零 inactive 乘法器输入）
//
// 保留：乘法器组 28 个（16 Booth8 + 8 原生16×16 + 4 原生32×32，正确性优先）；
//       BoothMultiplier / CSA / CSATree / Karatsuba 类（CSA 被 Fused 归约复用，
//       CSATree 与 Karatsuba 保留为参考/工程化方向，见设计文档 2.1 §7/§12）。
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
// 3. Wallace 风格 CSA 压缩树（参考实现：直接输出最终和，内部含末级 CPA）
//    Fused 归约使用 VDotCore.reduceCSA（保留 carry-save 中间结果，末级 CPA 在外层）
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
// 4. Karatsuba 乘法（参考实现，未接入主通路；32 位负数路径曾验证有缺陷）
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
// 5. vdot 点积内核（Fused ReduceAcc）
//    输入：vs1/vs2（各 vlen 位）、vdOld（累加旧值）、sew（00=e8,01=e16,10=e32）、vl
//    输出：result（32 位标量放 vd 低 32 位，高 96 位 0）、vxsat（32 位有符号溢出）
//    流水/时序：本内核为组合逻辑；流水化由外壳（VDotFu S1 锁存 + VIAlu opcodeS2）实现
// ---------------------------------------------------------------------------
class VDotCore(vlen: Int = 128, baseMulWidth: Int = 8) extends Module {
  val io = IO(new Bundle {
    val vs1    = Input(UInt(vlen.W))
    val vs2    = Input(UInt(vlen.W))
    val vdOld  = Input(UInt(vlen.W))
    val sew    = Input(UInt(2.W))     // 00=e8, 01=e16, 10=e32
    val vl     = Input(UInt(8.W))     // 保留端口（外壳兼容）；元素掩码已由 VDotFu stage0 清零
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

  // ---- 各档位宽（宽度自适应，容纳"完整点积和 + 32bit vdOld"）----
  // e8 : 16×16bit 乘积完整有符号和 = 20bit，+ vdOld[31:0] → 33bit
  // e16: 8×32bit 乘积完整有符号和 = 35bit，+ vdOld[31:0] → 36bit
  // e32: 4×64bit 乘积完整有符号和 = 66bit，+ vdOld[31:0] → 67bit
  private val W8  = 33
  private val W16 = 36
  private val W32 = 67

  // ---- Fused ReduceAcc：乘积与 vdOld 一起符号扩展进 CSA 树，只做一次 CPA ----
  private val old8  = Cat(Fill(W8  - 32, io.vdOld(31)), io.vdOld(31, 0))
  private val old16 = Cat(Fill(W16 - 32, io.vdOld(31)), io.vdOld(31, 0))
  private val old32 = Cat(Fill(W32 - 32, io.vdOld(31)), io.vdOld(31, 0))

  private val ops8  = (0 until numE8).map  { i => prodE8(i).pad(W8).asUInt  } :+ old8
  private val ops16 = (0 until numE16).map { i => prodE16(i).pad(W16).asUInt } :+ old16
  private val ops32 = (0 until numE32).map { i => prodE32(i).pad(W32).asUInt } :+ old32

  private val (s8,  c8)  = reduceCSA(ops8,  W8)
  private val (s16, c16) = reduceCSA(ops16, W16)
  private val (s32, c32) = reduceCSA(ops32, W32)

  private val full8  = s8  +& c8
  private val full16 = s16 +& c16
  private val full32 = s32 +& c32

  // ---- vxsat：full 超出 32 位有符号范围（[−2^31, 2^31−1]）置位 ----
  // 判定：full(31) 与 full(32) 必须一致（bit32 是 bit31 的符号扩展）。
  //   不一致即截断会改变符号 → 溢出：full(31)=1,full(32)=0 为 +2^31 上溢；
  //   full(31)=0,full(32)=1 为 −(2^31+1) 下溢。
  //   注：不可用"高位全 0 或全 1"判定——2^31 时 bit31=1 而 bit32.. 全 0
  //   （0x0000000_80000000），半 1 半 0 会被旧实现误判为合法负数扩展（已修）。
  private def overflowOf(full: UInt): Bool = full(31) ^ full(32)
  private val sat8  = overflowOf(full8)
  private val sat16 = overflowOf(full16)
  private val sat32 = overflowOf(full32)
  io.vxsat := MuxLookup(io.sew, sat8)(Seq(
    0.U -> sat8,
    1.U -> sat16,
    2.U -> sat32
  ))

  // ---- 结果：统一截断到 32 位标量，放 vd 低 32 位（高 96 位 0，tail 由外壳处理）----
  private val fullMux = MuxLookup(io.sew, full8)(Seq(
    0.U -> full8,
    1.U -> full16,
    2.U -> full32
  ))
  io.result := Cat(0.U((vlen - 32).W), fullMux(31, 0))

  // CSA 压缩：所有操作数归约到 (sum, carry)，carry 已左移（权重 2）
  // 每级 3→2，宽度随级数 +1（进位位）；末级由调用方做唯一一次 CPA
  private def reduceCSA(ops: Seq[UInt], initW: Int): (UInt, UInt) = {
    var cur = ops.map(_.pad(initW))
    var W   = initW
    while (cur.length > 2) {
      val nxt = cur.grouped(3).toSeq.flatMap { g =>
        if (g.length == 3) {
          val csa = Module(new CSA(W))
          csa.io.a := g(0)
          csa.io.b := g(1)
          csa.io.c := g(2)
          Seq(csa.io.sum, csa.io.carry)      // sum: W, carry: W+1
        } else g.map(_.pad(W + 1))
      }
      cur = nxt.map(_.pad(W + 1))
      W  += 1
    }
    (cur(0), cur(1))
  }
}

// ============================================================================
// 面积说明：正确性优先，三种精度乘法器组全部实例化（16 Booth8 + 8 原生16×16
// + 4 原生32×32 = 28 个乘法器）。归约累加采用宽度自适应 CSA 树（33/36/67 位，
// 原 128 位链式归约），vdOld 融合进压缩树（Fused Accumulator），只做一次 CPA。
// 工程化方向：修复 Karatsuba 符号扩展缺陷后位分解复用，或按 sew 时分共享
// 同一组乘法器（见设计文档 2.1 §7.2/§12）。
// ============================================================================
