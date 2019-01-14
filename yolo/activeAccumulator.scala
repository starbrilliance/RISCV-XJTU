package yolo

import chisel3._

class ActiveAccumulator extends Module {
  val io = IO(new Bundle {
    // from information regs
    val poolEn = Input(Bool())
    // from pad
    val fromPad = Flipped(new PaddingToPoolIO)
    // from accumulators
    val validIn = Input(Vec(3, Bool()))
    val dataIn = Input(Vec(224, SInt(8.W)))
    val dataOut = Output(Vec(64, UInt(8.W)))
    val validOut = Output(Bool())
  })

  val realValid = Wire(Bool())
  val dataCount = RegInit(0.U(2.W))
  val realValidDelay = RegNext(realValid)
  val relu = Module(new ReLu(64))

  realValid := (io.validIn.asUInt.orR || io.fromPad.lineComplete) && io.fromPad.finalInputChannel && !io.poolEn

  when(realValidDelay) {
    dataCount := dataCount + 1.U
  }

  relu.io.validIn := realValid
  
  when(dataCount === 3.U) {
    for(i <- 0 until 32) {
      relu.io.dataIn(i) := io.dataIn(dataCount * 64.U + i.U)
    }
    for(i <- 32 until 64) {
      relu.io.dataIn(i) := 0.S
    }
  } .otherwise {
    for(i <- 0 until 64) {
      relu.io.dataIn(i) := io.dataIn(dataCount * 64.U + i.U)
    }
  }
  
  io.dataOut := relu.io.dataOut
  io.validOut := relu.io.validOut
}