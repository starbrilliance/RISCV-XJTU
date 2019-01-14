package yolo

import chisel3._
import chisel3.util._
import chisel3.experimental._

class Adder extends BlackBox {
  val io = IO(new Bundle {
    val A = Input(SInt(8.W))  // input wire [7 : 0] A
    val B = Input(SInt(8.W))  // input wire [7 : 0] B
    val CLK = Input(Clock())  // input wire CLK
    val CE = Input(Bool())    // input wire CE
    val S = Output(SInt(9.W)) // output wire [8 : 0] S  
  })
}

class SatAdder extends Module {
  val io = IO(new Bundle {
      val dataIn0 = Input(SInt(8.W))
      val dataIn1 = Input(SInt(8.W))
      val validIn = Input(Bool())
      val dataOut = Output(SInt(8.W))
      val validOut = Output(Bool())
  })

  val adder = Module(new Adder)
  val dataOutTemp = Wire(SInt(9.W))
  val validInDelay = RegNext(io.validIn)

  adder.io.A := io.dataIn0
  adder.io.B := io.dataIn1
  adder.io.CLK := clock
  adder.io.CE := io.validIn
  dataOutTemp := adder.io.S

  io.dataOut := MuxCase(dataOutTemp(7, 0).asSInt, Array(((~dataOutTemp(8) & dataOutTemp(7)) === true.B) -> 127.S,
                                                        ((dataOutTemp(8) & ~dataOutTemp(7)) === true.B) -> -128.S))
  io.validOut := validInDelay
}