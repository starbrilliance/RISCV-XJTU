package axi_yolo

import chisel3._
import chisel3.util._

class BiasBufferControl extends Module {
  val io = IO(new Bundle {
    val ddrBiasEn = Input(Bool())
    val realOCEnd = Input(Bool())
    val ena = Output(Bool()) 
    val wea = Output(Bool())
    val addra = Output(UInt(9.W))
    val douta = Input(UInt(256.W))
    val biasOut = Output(SInt(8.W))
  })

  val padEnEdge0 = RegNext(io.ddrBiasEn)
  val padEnEdge1 = RegNext(padEnEdge0)  
  val addressGenerator = RegInit(0.U(9.W))
  val count = RegInit(0.U(5.W))
  val biasTemp = RegInit(VecInit(Seq.fill(32)(0.U(8.W))))
  val clearEn = Wire(Bool())
  val clearEnDelay = RegNext(clearEn)
  val readEn = Wire(Bool())
  val readEnDelay = RegNext(readEn)

  clearEn := !padEnEdge0 && padEnEdge1
  readEn := RegNext(io.realOCEnd) && (count === 0.U)

  when(clearEn) {
    addressGenerator := 0.U
  } .elsewhen(io.ddrBiasEn || readEn) {
    addressGenerator := addressGenerator + 1.U
  }

  when(io.realOCEnd) {
    count := count + 1.U
  }

  when(RegNext(clearEnDelay || readEnDelay)) {
    for(i <- 0 until 32) {
      biasTemp(i) := io.douta(i * 8 + 7, i * 8)
    }
  }

  io.ena := clearEnDelay || readEnDelay || io.ddrBiasEn
  io.wea := io.ddrBiasEn
  io.addra := addressGenerator
  io.biasOut := biasTemp(count).asSInt
}