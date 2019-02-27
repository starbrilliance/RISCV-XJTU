package yolo

import chisel3._
import chisel3.util._

class Active extends Module {
  val io = IO(new Bundle {
    // from pad
    val finalInputChannel = Input(Bool())
    // clear
    val writeComplete = Input(Bool())
    // complete
    val poolEn = Input(Bool())
    val outputSize = Input(UInt(8.W))
    val activeComplete = Output(Bool()) 
    // from accumulators
    val validIn = Input(Vec(224, Bool()))
    val dataIn = Input(Vec(448, SInt(31.W)))
    // output
    val dataOut = Output(Vec(448, UInt(8.W)))
    val validOut = Output(Vec(224, Bool()))
  })

  val dataTemp = RegInit(VecInit(Seq.fill(448)(0.S(31.W))))
  val dataOutTemp = Wire(Vec(448, UInt(8.W)))
  val dataOutReg = RegNext(dataOutTemp)
  val validOutReg = Reg(Vec(224, Bool()))

  for(i <- 0 until 224) {
    validOutReg(i) := RegNext(io.validIn(i) && io.finalInputChannel)
  }

  for(i <- 0 until 224) {
    when(io.writeComplete) {
      dataTemp(i) := 0.S
      dataTemp(i + 224) := 0.S
    } .elsewhen(io.finalInputChannel && io.validIn(i)) {
      dataTemp(i) := io.dataIn(i)
      dataTemp(i + 224) := io.dataIn(i + 224)
    }
    dataOutTemp(i) := MuxCase(dataTemp(i)(6, 0), Array(dataTemp(i)(30).toBool -> 0.U,
                                                       dataTemp(i)(29, 7).orR -> 127.U))
    dataOutTemp(i + 224) := MuxCase(dataTemp(i + 224)(6, 0), Array(dataTemp(i + 224)(30).toBool -> 0.U,
                                                                   dataTemp(i + 224)(29, 7).orR -> 127.U))
  }
  
  io.dataOut := dataOutReg
  io.validOut := validOutReg
  io.activeComplete := !io.poolEn && validOutReg(io.outputSize - 1.U)
}