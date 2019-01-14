package yolo

import chisel3._

class MaxComparator extends Module {
  val io = IO(new Bundle {
    val dataIn = Input(Vec(4, SInt(8.W)))
    val validIn = Input(Bool())
    val dataOut = Output(SInt(8.W))
    val validOut = Output(Bool())
  })

  val dataTemp = RegInit(VecInit(Seq.fill(2)(0.S(8.W))))
  val validDelay = RegNext(io.validIn)

  when(io.validIn) {
    dataTemp(0) := Mux(io.dataIn(0) > io.dataIn(1), io.dataIn(0), io.dataIn(1))
    dataTemp(1) := Mux(io.dataIn(2) > io.dataIn(3), io.dataIn(2), io.dataIn(3))
  }

  io.dataOut := Mux(dataTemp(0) > dataTemp(1), dataTemp(0), dataTemp(1))
  io.validOut := validDelay
}

class MaxPool extends Module {
  val io = IO(new Bundle {
    // information regs
    val poolEn = Input(Bool())
    // from pad
    val fromPad = Flipped(new PaddingToPoolIO)
    // accumulators
    val validIn = Input(Bool())
    val dataIn0 = Input(Vec(112, SInt(8.W)))
    val dataIn1 = Input(Vec(112, SInt(8.W)))
    val dataIn2 = Input(Vec(112, SInt(8.W)))
    val dataIn3 = Input(Vec(112, SInt(8.W)))
    // outputs
    val dataOut = Output(SInt(8.W))
    val validOut = Output(Bool())
  })

  val maxComparator = Module(new MaxComparator)

  val realValid = RegInit(false.B)
  val dataCount = RegInit(0.U(8.W))

  when(io.poolEn && io.fromPad.finalInputChannel && io.validIn) {
    realValid := true.B
  } .elsewhen(io.fromPad.lineComplete) {
    realValid := false.B
  }

  when(realValid) {
    dataCount := dataCount + 1.U
  } .otherwise {
    dataCount := 0.U
  }

  maxComparator.io.validIn := realValid && !dataCount(0).toBool
  maxComparator.io.dataIn(0) := io.dataIn0(dataCount(7, 1))
  maxComparator.io.dataIn(1) := io.dataIn1(dataCount(7, 1))
  maxComparator.io.dataIn(2) := io.dataIn2(dataCount(7, 1))
  maxComparator.io.dataIn(3) := io.dataIn3(dataCount(7, 1))
  io.dataOut := maxComparator.io.dataOut
  io.validOut := maxComparator.io.validOut
}