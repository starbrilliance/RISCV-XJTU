package axi_yolo

import chisel3._

class MaxComparator extends Module {
  val io = IO(new Bundle {
    val writeComplete = Input(Bool())
    val dataIn = Input(Vec(4, UInt(8.W)))
    val validIn = Input(Bool())
    val dataOut = Output(UInt(8.W))
  })

  val dataTemp = Wire(Vec(3, UInt(8.W)))
  val dataTempReg = RegInit(0.U(8.W))

  dataTemp(0) := Mux(io.dataIn(0) > io.dataIn(1), io.dataIn(0), io.dataIn(1))
  dataTemp(1) := Mux(io.dataIn(2) > io.dataIn(3), io.dataIn(2), io.dataIn(3))
  dataTemp(2) := Mux(dataTemp(0) > dataTemp(1), dataTemp(0), dataTemp(1))

  when(io.writeComplete) {
    dataTempReg := 0.U
  } .elsewhen(io.validIn) {
    dataTempReg := dataTemp(2)
  }

  io.dataOut := dataTempReg
}

class MaxPool extends Module {
  val io = IO(new Bundle {
    // information regs
    val poolEn = Input(Bool())
    // clear
    val writeComplete = Input(Bool())
    // active
    val activeComplete = Input(Bool())
    val validIn = Input(Vec(112, Bool()))
    val dataIn = Input(Vec(448, UInt(8.W)))
    // outputs
    val dataOut = Output(Vec(112, UInt(8.W)))
    val poolComplete = Output(Bool())
  })

  val maxComparator = VecInit(Seq.fill(112)(Module(new MaxComparator).io))

  for(i <- 0 until 112) {
    maxComparator(i).dataIn(0) := io.dataIn(i * 2)
    maxComparator(i).dataIn(1) := io.dataIn(i * 2 + 1)
    maxComparator(i).dataIn(2) := io.dataIn(i * 2 + 224)
    maxComparator(i).dataIn(3) := io.dataIn(i * 2 + 225)
    maxComparator(i).validIn := io.poolEn && io.validIn(i)
    maxComparator(i).writeComplete := io.writeComplete
    io.dataOut(i) := maxComparator(i).dataOut
  }

  io.poolComplete := io.poolEn && RegNext(io.activeComplete)
}