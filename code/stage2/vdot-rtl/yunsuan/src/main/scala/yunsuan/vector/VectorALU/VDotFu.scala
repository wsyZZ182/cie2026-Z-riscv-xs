package yunsuan.vector.alu

import chisel3._
import chisel3.util._
import yunsuan.vector._
import yunsuan.vector.alu.VAluOpcode._
import yunsuan.vector.alu.VSew._
import yunsuan.vector.vdot.VDotCore

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

  private val dotResult = core.io.result
  private val dotSat    = core.io.vxsat

  // ---- tail 处理（对齐 Reduction 的 red_vd_tail 范式）----
  // slotMask：固定低 32 位（vdot 结果 32 位，放 vd 低 32 位，跨 SEW 槽）
  // 修正：原按 SEW 截断（e8 只留 8 位），导致 -136/2032 被截成 120/240
  private val slotMask = "h000000000000000000000000ffffffff".U(128.W)
  private val redVd   = Mux(exec, dotResult, oldVd)      // vstart>=vl 不执行
  private val tailVd  = (oldVd & ~slotMask) | (redVd & slotMask)   // ta=0：保留旧值
  private val tailOne = (~slotMask) | (redVd & slotMask)           // ta=1：其余槽全 1
  private val tailOut = Mux(vl === 0.U, oldVd, Mux(ta, tailOne, tailVd))

  io.out.vd    := tailOut
  io.out.vxsat := dotSat && exec
}
