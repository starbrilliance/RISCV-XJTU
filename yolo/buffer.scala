package yolo

import chisel3._
import chisel3.util._
import chisel3.experimental._

// DataBufferA  10240×512b
class DataBufferA extends BlackBox {
  val io = IO(new Bundle {
    // write ports A
    val clka = Input(Clock())
    val ena = Input(Bool())  
    val wea = Input(Bool())
    val addra = Input(UInt(14.W))
    val dina = Input(UInt(512.W)) 
    // read ports B
    val clkb = Input(Clock())
    val enb = Input(Bool())  
    val addrb = Input(UInt(14.W))
    val doutb = Output(UInt(512.W))
  })
}

// DataBufferB  8192×512b
class DataBufferB extends BlackBox {
  val io = IO(new Bundle {
    // write ports A
    val clka = Input(Clock())
    val ena = Input(Bool())  
    val wea = Input(Bool())
    val addra = Input(UInt(13.W))
    val dina = Input(UInt(512.W)) 
    // read ports B
    val clkb = Input(Clock())
    val enb = Input(Bool())  
    val addrb = Input(UInt(13.W))
    val doutb = Output(UInt(512.W))
  })
}

// WeightBuffer  2048×512b
class WeightBuffer extends BlackBox {
  val io = IO(new Bundle {
    // write ports A
    val clka = Input(Clock())
    val ena = Input(Bool())
    val wea = Input(Bool())
    val addra = Input(UInt(11.W))
    val dina = Input(UInt(512.W))
    // read ports B
    val clkb = Input(Clock())
    val enb = Input(Bool())
    val addrb = Input(UInt(16.W))
    val doutb = Output(UInt(16.W))
  })
}

object WeightBufferParameter {
  val writeDepth = 0x800.U(12.W)
  val halfWriteDepth = 0x400.U(11.W)
  val readDepth = 0x10000.U(17.W)
  val addrEnd = readDepth - 1.U
  val addrHalf = (readDepth >> 1) - 1.U
}