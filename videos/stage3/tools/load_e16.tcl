set vcd "C:/Users/29125/Downloads/gtkwave/wave/vdot_e16.vcd"
gtkwave::loadFile $vcd

set p "TOP.SimTop.l_soc.core_with_l2.core.backend.inner_vfExuBlock.exus_1.Vipu.vialu.vDotAlu"

set sigs [list \
  "$p.k07E8Mode" \
  "$p.k07E16Mode" \
  "$p.k07Fire" \
  "$p.pass2Fire" \
  "$p.validS1" \
  "$p.vsewS2" \
  "$p.k07.io_in_bits_e16" \
  "$p.k07.io_in_bits_vs1" \
  "$p.k07.io_in_bits_vdOld" \
  "$p.k07.io_out_valid" \
  "$p.k07.io_out_bits_result" \
  "$p.k07E8ResultS2" \
  "$p.vs1HiS1" \
  "$p.vs2HiS1" \
]

gtkwave::addSignalsFromList $sigs
gtkwave::/Time/Zoom/Zoom_Full
