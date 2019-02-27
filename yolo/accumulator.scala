package yolo

import chisel3._
import chisel3.experimental._

class Accumulator extends BlackBox {
  val io = IO(new Bundle {
    val B = Input(SInt(21.W))  // input
    val CLK = Input(Clock()) // clock
    val CE = Input(Bool())  // clock enable
    val SCLR = Input(Bool())  // clear
    val Q = Output(SInt(31.W))  // output
  })
}