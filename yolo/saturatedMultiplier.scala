package yolo

import chisel3._
import chisel3.util._
import chisel3.experimental._

class Multiplier extends BlackBox {
  val io = IO(new Bundle {
    val CLK = Input(Clock())  // input wire CLK
    val A = Input(SInt(8.W))  // input wire [7 : 0] A
    val B = Input(SInt(8.W))  // input wire [7 : 0] B
    val CE = Input(Bool())    // input wire CE
    val P = Output(SInt(16.W)) // output wire [15 : 0] P  
  })
}

class SatMultiplier extends Module {
  val io = IO(new Bundle {
    val dataIn0 = Input(SInt(8.W))
    val dataIn1 = Input(SInt(8.W))
    val validIn = Input(Bool())
    val dataOut = Output(SInt(8.W))
    val validOut = Output(Bool())
  })

  val mul = Module(new Multiplier)
  val dataOutTemp = Wire(SInt(16.W))
  val validInDelay = RegNext(io.validIn)

  mul.io.CLK := clock
  mul.io.A := io.dataIn0
  mul.io.B := io.dataIn1
  mul.io.CE := io.validIn
  dataOutTemp := mul.io.P

  io.dataOut := MuxCase(dataOutTemp(7, 0).asSInt, Array((dataOutTemp > 126.S) -> 127.S,
                                                        (dataOutTemp < -127.S) -> -128.S))
  io.validOut := validInDelay
}