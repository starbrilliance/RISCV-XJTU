package yolo

import chisel3._

class ActivePool extends Module {
  val io = IO(new Bundle {
    // from pad
    val lineComplete = Input(Bool())
    // from pool
    val validIn = Input(Bool())
    val dataIn = Input(SInt(8.W))
    val dataOut = Output(Vec(64, UInt(8.W)))
    val validOut = Output(Bool())
  })

  val relu = Module(new ReLu(1))
  val realValid = Wire(Bool())
  val dataCount = RegInit(0.U(6.W)) 
  val dataTemp = RegInit(VecInit(Seq.fill(64)(0.U(8.W))))
  val validOutDelay = RegNext(relu.io.validOut)
  val lineCompleteDelay = RegNext(io.lineComplete)

  relu.io.validIn := io.validIn
  relu.io.dataIn(0) := io.dataIn

  when(lineCompleteDelay) {
    dataCount := 0.U
  } .elsewhen(validOutDelay) {
    dataCount := dataCount + 1.U
  }

  when(lineCompleteDelay) {
    dataTemp := VecInit(Seq.fill(64)(0.U(8.W)))
  } .elsewhen(relu.io.validOut) {
    dataTemp(dataCount) := relu.io.dataOut(0)
  }

  realValid := ((dataCount === 63.U) && relu.io.validOut) || io.lineComplete
  
  io.dataOut := dataTemp
  io.validOut := realValid
}