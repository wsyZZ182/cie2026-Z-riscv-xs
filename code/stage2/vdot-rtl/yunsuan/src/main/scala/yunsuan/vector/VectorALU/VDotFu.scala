package yunsuan.vector.alu

import chisel3._
import chisel3.util._
import yunsuan.vector._
import yunsuan.vector.alu.VAluOpcode._
import yunsuan.vector.alu.VSew._
import yunsuan.vector.vdot.VDotCore
import _root_.vdot.BWFastTop

// ============================================================================
// VDotFu：vdot.vv 归约执行模块（挂载在 VIAlu 内，与 Reduction / VMask 并列）
//
// 接入链路（路线 A：复用香山现有 vipu 归约通路）：
//   VecDecoder(VDOT_VV -> OPMVV(FuType.vipu, VipuType.vdot_vv))
//     -> VIPU(wrapper) -> VIAluDecoder(VipuType.vdot_vv -> VAluOpcode.vdot)
//     -> VIAlu -> VDotFu（本模块） -> VDotCore（计算内核）
//
// 语义（vdot.vv vd, vs2, vs1, vm，SEW=e8/e16/e32 有符号）：
//   vd[0] = sum_{ i < vl && (vm || mask[i]) } vs1[i] * vs2[i]   （截断，溢出置 vxsat）
//   其余元素槽按 vta：ta=1 全 1，ta=0 保留旧值；vl=0 或 vstart>=vl 时整条不写。
//
// 限制（v1.0）：仅支持 LMUL=1（单 uop，uopIdx 恒 0）；LMUL>1 的多 uop 切分
// 与跨 uop 累加留后续版本（与 vredsum 的 VEC_VRED 切分机制对齐后可扩展）。
//
// 时序：与 Reduction 对齐 —— 输入在 fire（io.in.valid）沿锁存到 S1 级，
// 组合计算基于锁存值，输出在 opcodeS2（VIAlu 两级输出级）被选通时
// 仍对应原指令数据（修复纯组合导致的跨指令数据错位）。
// ============================================================================
class VDotFu extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(ValidIO(new VIFuInput))
    val out = Output(new VIFuOutput)
  })

  // ---- S1 输入锁存（对齐 Reduction 的 fire_reg_s1 / old_vd_reg_s1 范式）----
  private val fire     = io.in.valid

  // K07 E8 fallback: capture the current instruction on its valid edge.
  // E16/E32 remain on the original VDotCore path until an eight-pair unit is
  // fully integrated and verified. Inactive/masked elements are zeroed here;
  // the standalone K07 module then sees all 16 E8 lanes enabled.
  private val k07 = Module(new BWFastTop)
  private val k07E8Fire = io.in.valid && (io.in.bits.vdType(1, 0) === 0.U)
  private val k07Vs1 = VecInit((0 until 16).map { i =>
    val active = (i.U < io.in.bits.info.vl) && (io.in.bits.info.vm || io.in.bits.mask(i))
    Mux(active, io.in.bits.vs1(8 * i + 7, 8 * i), 0.U(8.W))
  }).asUInt
  private val k07Vs2 = VecInit((0 until 16).map { i =>
    val active = (i.U < io.in.bits.info.vl) && (io.in.bits.info.vm || io.in.bits.mask(i))
    Mux(active, io.in.bits.vs2(8 * i + 7, 8 * i), 0.U(8.W))
  }).asUInt
  k07.io.in.valid := k07E8Fire
  k07.io.in.bits.vs1 := k07Vs1
  k07.io.in.bits.vs2 := k07Vs2
  k07.io.in.bits.vdOld := io.in.bits.old_vd(31, 0)
  k07.io.in.bits.a_signed := true.B
  k07.io.in.bits.b_signed := true.B
  k07.io.in.bits.sew := 0.U
  k07.io.in.bits.mask := "hffff".U
  k07.io.in.bits.tag := 0.U
  k07.io.out.ready := true.B
  k07.io.flush := false.B
  // K07 consumes a request on the fire edge and replaces its output register.
  // VIAlu selects data with opcodeS2 (two valid stages), so capture the old
  // K07 response on the following edge before a back-to-back request replaces it.
  private val k07ResultS2 = RegEnable(k07.io.out.bits.result, k07.io.out.valid)
  private val vm_r     = RegEnable(io.in.bits.info.vm,     false.B, fire)
  private val ta_r     = RegEnable(io.in.bits.info.ta,     false.B, fire)
  private val vl_r     = RegEnable(io.in.bits.info.vl,     0.U,     fire)
  private val vstart_r = RegEnable(io.in.bits.info.vstart, 0.U,     fire)
  private val vsew_r   = RegEnable(io.in.bits.vdType(1, 0), 0.U,    fire)
  private val mask_r   = RegEnable(io.in.bits.mask,        0.U,     fire)
  private val vs1_r    = RegEnable(io.in.bits.vs1,         0.U,     fire)
  private val vs2_r    = RegEnable(io.in.bits.vs2,         0.U,     fire)
  private val oldVd_r  = RegEnable(io.in.bits.old_vd,      0.U,     fire)

  // ---- 计算输入（锁存值）----
  private val vm     = vm_r
  private val ta     = ta_r
  private val vl     = vl_r
  private val vstart = vstart_r
  private val mask   = mask_r
  private val vsew   = vsew_r
  private val vs1    = vs1_r
  private val vs2    = vs2_r
  private val oldVd  = oldVd_r

  // vstart >= vl 时不执行（RVV 归约语义，同 Reduction）
  private val exec = vstart < vl

  // ---- stage 0：inactive 元素清零（i >= vl 或 (!vm && !mask[i])）----
  private val vs1c8 = Wire(Vec(16, UInt(8.W)))
  private val vs2c8 = Wire(Vec(16, UInt(8.W)))
  for (i <- 0 until 16) {
    val act = (i.U < vl) && (vm || mask(i))
    vs1c8(i) := Mux(act, vs1(8 * i + 7, 8 * i), 0.U(8.W))
    vs2c8(i) := Mux(act, vs2(8 * i + 7, 8 * i), 0.U(8.W))
  }

  private val vs1c16 = Wire(Vec(8, UInt(16.W)))
  private val vs2c16 = Wire(Vec(8, UInt(16.W)))
  for (i <- 0 until 8) {
    val act = (i.U < vl) && (vm || mask(i))
    vs1c16(i) := Mux(act, vs1(16 * i + 15, 16 * i), 0.U(16.W))
    vs2c16(i) := Mux(act, vs2(16 * i + 15, 16 * i), 0.U(16.W))
  }

  private val vs1c32 = Wire(Vec(4, UInt(32.W)))
  private val vs2c32 = Wire(Vec(4, UInt(32.W)))
  for (i <- 0 until 4) {
    val act = (i.U < vl) && (vm || mask(i))
    vs1c32(i) := Mux(act, vs1(32 * i + 31, 32 * i), 0.U(32.W))
    vs2c32(i) := Mux(act, vs2(32 * i + 31, 32 * i), 0.U(32.W))
  }

  // ---- 计算内核（组合）：VDotCore ----
  private val core = Module(new VDotCore)
  core.io.vs1   := MuxLookup(vsew, vs1c8.asUInt)(Seq(
    0.U -> vs1c8.asUInt,
    1.U -> vs1c16.asUInt,
    2.U -> vs1c32.asUInt))
  core.io.vs2   := MuxLookup(vsew, vs2c8.asUInt)(Seq(
    0.U -> vs2c8.asUInt,
    1.U -> vs2c16.asUInt,
    2.U -> vs2c32.asUInt))
  core.io.vdOld := oldVd
  core.io.sew   := vsew
  core.io.vl    := vl

  // ---- S2: align with the K07 response and VIAlu opcodeS2 ----
  // K07 drives its E8 result one cycle after the request (in.fire sets
  // outBits/valid), while VIAlu selects the output with opcodeS2 (two valid
  // stages).  Keeping old_vd/ta/vl/vsew only in S1 makes back-to-back vdot
  // instructions combine the first result with the second instruction's old
  // vd: the low 32 bits are right but the upper 96 bits are wrong.
  private val validS1      = RegNext(fire, false.B)
  private val coreResultS2 = RegEnable(core.io.result, validS1)
  private val coreSatS2    = RegEnable(core.io.vxsat,  validS1)
  private val oldVdS2      = RegEnable(oldVd_r,  validS1)
  private val taS2         = RegEnable(ta_r,     validS1)
  private val vlS2         = RegEnable(vl_r,     validS1)
  private val vstartS2     = RegEnable(vstart_r, validS1)
  private val vsewS2       = RegEnable(vsew_r,   validS1)

  private val execS2 = vstartS2 < vlS2
  private val dotResult = Mux(vsewS2 === 0.U,
    Cat(0.U(96.W), k07ResultS2), coreResultS2)
  // The current VIPU configuration does not commit vxsat to the CSR; K07
  // itself has no saturation output. Keep native E16/E32 saturation signal.
  private val dotSat = Mux(vsewS2 === 0.U, false.B, coreSatS2)

  // ---- tail 处理（对齐 Reduction 的 red_vd_tail 范式）----
  // slotMask：固定低 32 位（vdot 结果 32 位，放 vd 低 32 位，跨 SEW 槽）
  // 修正：原按 SEW 截断（e8 只留 8 位），导致 -136/2032 被截成 120/240
  private val slotMask = "h000000000000000000000000ffffffff".U(128.W)
  private val redVd   = Mux(execS2, dotResult, oldVdS2)      // vstart>=vl 不执行
  private val tailVd  = (oldVdS2 & ~slotMask) | (redVd & slotMask)   // ta=0：保留旧值
  private val tailOne = (~slotMask) | (redVd & slotMask)             // ta=1：其余槽全 1
  private val tailOut = Mux(vlS2 === 0.U, oldVdS2, Mux(taS2, tailOne, tailVd))

  io.out.vd    := tailOut
  io.out.vxsat := dotSat && execS2
}
