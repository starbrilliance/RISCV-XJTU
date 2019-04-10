package axi_yolo

import chisel3._
import chisel3.util._

class BiasBufferControl extends Module {
  val io = IO(new Bundle {
    val ddrBiasEn = Input(Bool())
    val readBiasComplete = Input(Bool())
    val writeDataComplete = Input(Bool())
    val realOCEnd = Input(Bool())
    val ena = Output(Bool()) 
    val wea = Output(Bool())
    val addra = Output(UInt(9.W))
    val douta = Input(UInt(256.W))
    val biasOut = Output(SInt(8.W))
  })

  val addressGenerator = RegInit(0.U(9.W))
  val count = RegInit(0.U(5.W))
  val biasTemp = Reg(Vec(32, UInt(8.W)))
  val readEn = Wire(Bool())

  readEn := RegNext(io.realOCEnd) && (count === 0.U) || RegNext(io.readBiasComplete || io.writeDataComplete)

  when(io.readBiasComplete || io.writeDataComplete) {
    addressGenerator := 0.U
  } .elsewhen(io.ddrBiasEn || readEn) {
    addressGenerator := addressGenerator + 1.U
  }

  when(io.realOCEnd) {
    count := count + 1.U
  }

  when(RegNext(readEn)) {
    for(i <- 0 until 32) {
      biasTemp(i) := io.douta(i * 8 + 7, i * 8)
    }
  }

  io.ena := readEn || io.ddrBiasEn
  io.wea := io.ddrBiasEn
  io.addra := addressGenerator
  io.biasOut := biasTemp(count).asSInt
}