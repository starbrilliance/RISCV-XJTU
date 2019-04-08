package axi_yolo

import chisel3._
import chisel3.util._

class BalanceToPEIO extends Bundle {
  val accClear = Output(Bool())
  val accRead = Output(Bool())
  val biasSelect = Output(Bool())
  val kernelSize = Output(Bool())
}

class BalanceIO extends Bundle {
  // from pad
  val fromPad = Flipped(new PaddingToBalanceIO)
  // to PE
  val toPE = new BalanceToPEIO
  // to active
  val realLineEnd = Output(Bool())
  // to biasbuffer control
  val realOCEnd = Output(Bool())
  // to FSM & dbc & inforegs
  val realLayerEnd = Output(Bool())
  // from info regs
  val kernelSize = Input(Bool())
  // from final wb
  val writeComplete = Input(Bool())
}

class Balance extends Module {
  val io = IO(new BalanceIO)

  // PE
  io.toPE.kernelSize := io.kernelSize

  val biasDelay0 = RegNext(io.fromPad.finalInputChannel)
  val readDelay0 = ShiftRegister(biasDelay0, 2)
  val biasDelay1 = ShiftRegister(readDelay0, 6)
  io.toPE.biasSelect := Mux(io.kernelSize, biasDelay1, biasDelay0)
 
  val readDelay1 = ShiftRegister(biasDelay1, 2)
  io.toPE.accRead := Mux(io.kernelSize, readDelay1, readDelay0)

  val clearDelay0 = ShiftRegister(io.fromPad.lineComplete && io.fromPad.finalInputChannel, 5)
  val clearDelay1 = ShiftRegister(clearDelay0, 8)
  io.toPE.accClear := Mux(io.kernelSize, clearDelay1, clearDelay0)

  // bias buffer control
  val outputEnd0 = RegNext(io.fromPad.outputChannelEnd)
  val outputEnd1 = ShiftRegister(outputEnd0, 8)
  io.realOCEnd := Mux(io.kernelSize, outputEnd1, outputEnd0)

  // active
  io.realLineEnd := io.toPE.accClear
  
  // layer complete
  val layerDelay0 = ShiftRegister(io.fromPad.layerComplete, 8)
  val layerDelay1 = ShiftRegister(layerDelay0, 8)
  val layerEn = Mux(io.kernelSize, layerDelay1, layerDelay0)
  val realLayerComplete = RegInit(false.B)
  when(layerEn) {
    realLayerComplete := true.B
  } .elsewhen(io.realLayerEnd) {
    realLayerComplete := false.B
  }
  io.realLayerEnd := realLayerComplete && io.writeComplete
}