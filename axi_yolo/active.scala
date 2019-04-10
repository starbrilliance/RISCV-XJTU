package axi_yolo

import chisel3._
import chisel3.util._

class Active extends Module {
  val io = IO(new Bundle {
    // clear
    val layerComplete = Input(Bool())
    // complete
    val realLineEnd = Input(Bool())
    val activeComplete = Output(Bool()) 
    // from pe
    val validIn = Input(Vec(224, Bool()))
    val dataIn = Input(Vec(448, SInt(32.W)))
    // output
    val dataOut = Output(Vec(448, UInt(8.W)))
    val validOut = Output(Vec(224, Bool()))
  })

  val dataTemp = RegInit(VecInit(Seq.fill(448)(0.S(32.W))))
  val dataOutTemp = Wire(Vec(448, UInt(8.W)))
  val dataOutReg = RegNext(dataOutTemp)

  for(i <- 0 until 224) {
    when(io.layerComplete) {
      dataTemp(i) := 0.S
      dataTemp(i + 224) := 0.S
    } .elsewhen(io.validIn(i)) {
      dataTemp(i) := io.dataIn(i)
      dataTemp(i + 224) := io.dataIn(i + 224)
    }
    dataOutTemp(i) := MuxCase(dataTemp(i)(6, 0), Array(dataTemp(i)(31).toBool -> 0.U,
                                                       dataTemp(i)(30, 7).orR -> 127.U))
    dataOutTemp(i + 224) := MuxCase(dataTemp(i + 224)(6, 0), Array(dataTemp(i + 224)(31).toBool -> 0.U,
                                                                   dataTemp(i + 224)(30, 7).orR -> 127.U))
  }
  
  io.dataOut := dataOutReg
  io.validOut := RegNext(RegNext(io.validIn))
  io.activeComplete := RegNext(RegNext(io.realLineEnd))
}