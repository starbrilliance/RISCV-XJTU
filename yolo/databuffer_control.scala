package yolo

import chisel3._
import chisel3.util._

class DataBufferIO extends Bundle {
  // from ddr
  val ddrDataEn = Input(Bool())
  val ddrComplete = Input(Bool())
  val ddrStoreEn = Input(Bool())
  // from writeback
  val writeValid = Input(Bool())
  // from pad
  val padReadEn = Input(Bool())
  val padReadAddress = Input(UInt(14.W))
  // from sr
  val layerComplete = Input(Bool())
  // to databufferA
  val readAddressA = Output(UInt(14.W))
  val readEnA = Output(Bool())
  val writeAddressA = Output(UInt(14.W))
  val writeEnA = Output(Bool())
  // to databufferB
  val readAddressB = Output(UInt(13.W))
  val readEnB = Output(Bool())
  val writeAddressB = Output(UInt(13.W))
  val writeEnB = Output(Bool())
}

class DataBufferControl extends Module {
  val io = IO(new DataBufferIO)

  val addressGenerator = RegInit(0.U(14.W))
  val bufferSelect = RegInit(false.B) 

  when(io.ddrComplete || io.layerComplete) {
    addressGenerator := 0.U
  } .elsewhen(io.ddrDataEn || io.ddrStoreEn || io.writeValid) {
    addressGenerator := addressGenerator + 1.U
  }

  when(io.ddrComplete || io.layerComplete) {
    bufferSelect := !bufferSelect
  }

  io.readAddressA := Mux(io.ddrStoreEn, addressGenerator, io.padReadAddress)
  io.readEnA := bufferSelect && (io.ddrStoreEn || io.padReadEn)
  io.writeAddressA := addressGenerator
  io.writeEnA := !bufferSelect && (io.ddrDataEn || io.writeValid)

  io.readAddressB := Mux(io.ddrStoreEn, addressGenerator(12, 0), io.padReadAddress(12, 0))
  io.readEnB := !bufferSelect && (io.ddrStoreEn || io.padReadEn)
  io.writeAddressB := addressGenerator(12, 0)
  io.writeEnB := bufferSelect && io.writeValid                              
}