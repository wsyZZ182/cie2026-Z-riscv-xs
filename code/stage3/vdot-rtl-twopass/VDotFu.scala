package yunsuan.vector.alu

import chisel3._
import chisel3.util._
import yunsuan.vector._
import yunsuan.vector.alu.VAluOpcode._
import yunsuan.vector.alu.VSew._
import yunsuan.vector.vdot.VDotCore
import k07.K07

// ============================================================================
// VDotFu：vdot.vv 归约执行模块（挂载在 VIAlu 内，与 Reduction / VMask 并列）
//
// 接入链路（路线 A：复用香山现有 vipu 归约通路）：
//   VecDecoder(VDOT_VV -> OPMVV(FuType.vipu, VipuType.vdot_vv))
//     -> VIPU(wrapper) -> VIAluDecoder(VipuType.vdot_vv -> VAluOpcode.vdot)
//     -> VIAlu -> VDotFu（本模块） -> K07（计算内核）
//
// 语义（vdot.vv vd, vs2, vs1, vm，SEW=e8/e16/e32 有符号）：
//   vd[0] = sum_{ i < vl && (vm || mask[i]) } vs1[i] * vs2[i]   （截断，溢出置 vxsat）
//   其余元素槽按 vta：ta=1 全 1，ta=0 保留旧值；vl=0 或 vstart>=vl 时整条不写。
//
// 模式分工（VLEN=128）：
//   e8  : K07 单笔 —— 16 个字节 lane 一次算完（16 对）
//   e16 : K07 两笔 —— K07 的 e16 契约是"每笔只算输入低 64 位的 4 对"，
//         而 e16 有 8 个元素，因此第 1 笔算元素 0..3，第 2 笔算元素 4..7
//   e32 : VDotCore 单笔 —— K07 无 e32 模式
//
// 两笔时序（与 VipuCfg 的 CertainLatency(2) 精确对齐）：
//   t0（fire 沿）: 第 1 笔请求进 K07（e8 全 16 lane；e16 低 4 对，vdOld = old_vd）
//   t1           : K07 给出第 1 笔结果；e16 组合发出第 2 笔
//                  （高 64 位下移 + vdOld = 第 1 笔结果，加法在 K07 的 CPA 内融合）
//   t2           : K07 给出 e16 第 2 笔结果，正好是 VIAlu 的 opcodeS2 选择沿
//                  e8 的第 1 笔结果则在 t1 沿锁存到 S2 级
//   因此 **不需要把 VIAlu 的 opcodeS2 拉成三级，也不改 VipuCfg.latency**。
//
// 吞吐约束：K07 每拍只接受一个请求，e16 的第 2 笔占用 fire 后第 1 拍的请求槽，
//   所以 e16 指令之后必须空一拍才能再发 vdot（e8 之间可以真正背靠背）。
//   冲突由 pass2Conflict 断言暴露，不做静默丢弃。
// ============================================================================
class VDotFu extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(ValidIO(new VIFuInput))
    val out = Output(new VIFuOutput)
  })

  private val fire = io.in.valid

  // -------------------------------------------------------------------------
  // 请求侧（S0，全部组合自 io.in.bits —— 第 1 笔必须在 fire 当拍进 K07）
  // -------------------------------------------------------------------------
  private val k07 = Module(new K07)
  private val k07E8Mode  = io.in.bits.vdType(1, 0) === 0.U
  private val k07E16Mode = io.in.bits.vdType(1, 0) === 1.U
  private val k07Fire    = io.in.valid && (k07E8Mode || k07E16Mode)
  private val k07Vm      = io.in.bits.info.vm
  private val k07Vl      = io.in.bits.info.vl
  private val k07MaskIn  = io.in.bits.mask

  // e8：128 位上的 16 个字节 lane
  private val vs1E8 = VecInit((0 until 16).map { i =>
    val active = (i.U < k07Vl) && (k07Vm || k07MaskIn(i))
    Mux(active, io.in.bits.vs1(8 * i + 7, 8 * i), 0.U(8.W))
  }).asUInt
  private val vs2E8 = VecInit((0 until 16).map { i =>
    val active = (i.U < k07Vl) && (k07Vm || k07MaskIn(i))
    Mux(active, io.in.bits.vs2(8 * i + 7, 8 * i), 0.U(8.W))
  }).asUInt

  // e16 第 1 笔：低 64 位里的元素 0..3
  private val vs1E16Lo = VecInit((0 until 4).map { i =>
    val active = (i.U < k07Vl) && (k07Vm || k07MaskIn(i))
    Mux(active, io.in.bits.vs1(16 * i + 15, 16 * i), 0.U(16.W))
  }).asUInt
  private val vs2E16Lo = VecInit((0 until 4).map { i =>
    val active = (i.U < k07Vl) && (k07Vm || k07MaskIn(i))
    Mux(active, io.in.bits.vs2(16 * i + 15, 16 * i), 0.U(16.W))
  }).asUInt

  // e16 第 2 笔：元素 4..7（高 64 位下移到低位）
  private val vs1E16Hi = VecInit((0 until 4).map { i =>
    val e = i + 4
    val active = (e.U < k07Vl) && (k07Vm || k07MaskIn(e))
    Mux(active, io.in.bits.vs1(16 * e + 15, 16 * e), 0.U(16.W))
  }).asUInt
  private val vs2E16Hi = VecInit((0 until 4).map { i =>
    val e = i + 4
    val active = (e.U < k07Vl) && (k07Vm || k07MaskIn(e))
    Mux(active, io.in.bits.vs2(16 * e + 15, 16 * e), 0.U(16.W))
  }).asUInt

  // -------------------------------------------------------------------------
  // S1：e16 第 2 笔的请求槽
  //   第 2 笔的操作数必须在 t0 沿锁存（t1 时 io.in.bits 可能已换成下一条指令）；
  //   vdOld 例外——它在 t1 直接取 K07 的输出寄存器（第 1 笔结果），不额外打拍。
  // -------------------------------------------------------------------------
  private val pass2Fire = RegNext(k07Fire && k07E16Mode, false.B)
  private val vs1HiS1   = RegEnable(vs1E16Hi, k07Fire && k07E16Mode)
  private val vs2HiS1   = RegEnable(vs2E16Hi, k07Fire && k07E16Mode)

  // K07 单请求寄存器：e16 第 2 笔占住 fire 后第 1 拍，新 vdot 不能再挤进来。
  private val pass2Conflict = pass2Fire && k07Fire
  assert(!pass2Conflict,
    "VDotFu: e16 pass-2 request slot collides with a new vdot request " +
      "(e16 must be followed by at least one bubble)")

  k07.io.clock := clock
  // ---- K07 的复位边界（2026-11-08 实测澄清）--------------------------------
  // K07.sv 内部是**同步复位**：全文只有一个 always @(posedge clock)，
  // 其中 `if (reset) valid <= 0`；outBits_result 不受复位影响。
  //
  // 但**香山整体是异步复位域**：utility/ResetGen 输出 AsyncReset，生成 RTL 里
  // 1619 个模块中有 829 个使用 `always @(posedge clock or posedge reset)`
  //（VIPU / Reduction / ExeUnit 均是）。所以这里必须用 asAsyncReset，
  // 让 VDotFu 的复位域与香山一致；声明成 Bool 反而会让 firtool 报
  // `resetReg[] simultaneously connected to async and sync resets`。
  //
  // 二者组合的实际语义：K07 在异步复位断言后要等下一个时钟沿才清 valid
  //（复位响应晚一拍）。复位期间本来就没有有效事务，功能无害。
  k07.io.reset := reset.asAsyncReset
  k07.io.io_flush := false.B
  k07.io.io_in_valid := k07Fire || pass2Fire
  k07.io.io_in_bits_vs1 := Mux(pass2Fire, Cat(0.U(64.W), vs1HiS1),
    Mux(k07E8Mode, vs1E8, Cat(0.U(64.W), vs1E16Lo)))
  k07.io.io_in_bits_vs2 := Mux(pass2Fire, Cat(0.U(64.W), vs2HiS1),
    Mux(k07E8Mode, vs2E8, Cat(0.U(64.W), vs2E16Lo)))
  k07.io.io_in_bits_vdOld := Mux(pass2Fire, k07.io.io_out_bits_result,
    io.in.bits.old_vd(31, 0))
  k07.io.io_in_bits_a_signed := true.B
  k07.io.io_in_bits_b_signed := true.B
  k07.io.io_in_bits_e16 := Mux(pass2Fire, true.B, k07E16Mode)
  k07.io.io_in_bits_mask := "hffff".U
  k07.io.io_in_bits_tag := 0.U
  k07.io.io_out_ready := true.B

  // -------------------------------------------------------------------------
  // S1 锁存（语义侧：vm/ta/vl/vstart/sew/mask/vs/old_vd）
  // -------------------------------------------------------------------------
  private val vm_r     = RegEnable(io.in.bits.info.vm,     false.B, fire)
  private val ta_r     = RegEnable(io.in.bits.info.ta,     false.B, fire)
  private val vl_r     = RegEnable(io.in.bits.info.vl,     0.U,     fire)
  private val vstart_r = RegEnable(io.in.bits.info.vstart, 0.U,     fire)
  private val vsew_r   = RegEnable(io.in.bits.vdType(1, 0), 0.U,    fire)
  private val mask_r   = RegEnable(io.in.bits.mask,        0.U,     fire)
  private val vs1_r    = RegEnable(io.in.bits.vs1,         0.U,     fire)
  private val vs2_r    = RegEnable(io.in.bits.vs2,         0.U,     fire)
  private val oldVd_r  = RegEnable(io.in.bits.old_vd,      0.U,     fire)

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

  // ---- 计算内核（组合）：VDotCore，仅 e32 使用 ----
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

  // -------------------------------------------------------------------------
  // S2：与 VIAlu 的 opcodeS2 对齐
  //   e8  : K07 在 fire+1 拍给出结果 → 在 t1 沿锁存进 S2 级
  //   e16 : K07 在 fire+2 拍给出第 2 笔结果 → 就是 S2 级的组合值，无需再打拍
  //   e32 : VDotCore 的 S1 组合值 → 在 t1 沿锁存进 S2 级
  // -------------------------------------------------------------------------
  private val validS1       = RegNext(fire, false.B)
  private val pass2S2       = RegNext(pass2Fire, false.B)
  // e8 结果锁存：K07 输出有效、且当前这拍不是 e16 第 2 笔（避免把 e16 的第 1 笔
  // 结果错当成 e8 的最终结果留在寄存器里）
  private val k07E8ResultS2 = RegEnable(k07.io.io_out_bits_result,
    k07.io.io_out_valid && !pass2S2)
  private val coreResultS2  = RegEnable(core.io.result, validS1)
  private val coreSatS2     = RegEnable(core.io.vxsat,  validS1)
  private val oldVdS2       = RegEnable(oldVd_r,  validS1)
  private val taS2          = RegEnable(ta_r,     validS1)
  private val vlS2          = RegEnable(vl_r,     validS1)
  private val vstartS2      = RegEnable(vstart_r, validS1)
  private val vsewS2        = RegEnable(vsew_r,   validS1)

  private val execS2 = vstartS2 < vlS2
  private val dotResult = Mux(vsewS2 === 0.U,
    Cat(0.U(96.W), k07E8ResultS2),
    Mux(vsewS2 === 1.U,
      Cat(0.U(96.W), k07.io.io_out_bits_result),
      coreResultS2))
  // 当前 VIPU 配置不把 vxsat 提交到 CSR；K07 本身没有饱和输出。
  // e8/e16 保持 false，e32 沿用 VDotCore 的原生饱和信号。
  private val dotSat = Mux(vsewS2 <= 1.U, false.B, coreSatS2)

  // ---- tail 处理（对齐 Reduction 的 red_vd_tail 范式）----
  // slotMask：固定低 32 位（vdot 结果 32 位，放 vd 低 32 位，跨 SEW 槽）
  private val slotMask = "h000000000000000000000000ffffffff".U(128.W)
  private val redVd   = Mux(execS2, dotResult, oldVdS2)      // vstart>=vl 不执行
  private val tailVd  = (oldVdS2 & ~slotMask) | (redVd & slotMask)   // ta=0：保留旧值
  private val tailOne = (~slotMask) | (redVd & slotMask)             // ta=1：其余槽全 1
  private val tailOut = Mux(vlS2 === 0.U, oldVdS2, Mux(taS2, tailOne, tailVd))

  io.out.vd    := tailOut
  io.out.vxsat := dotSat && execS2
}
