package axi_yolo

import chisel3._
import chisel3.util._

// only write
class WeightBufferIO extends Bundle {
  // from ddrC
  val ddrWeightEn = Input(Bool())
  // from pad
  val padToWB = Flipped(new PaddingToDdrControlIO)
  // to weightbuffer
  val writeAddressW = Output(UInt(11.W))
  val writeEnW = Output(Bool())
}

import WeightBufferParameter._
class WeightBufferControl extends Module {
  val io = IO(new WeightBufferIO)

  val addressGenerator = RegInit(0.U(11.W))

  when(io.padToWB.padFillHead) {
    addressGenerator := 0.U
  } .elsewhen(io.padToWB.padFillTail) {
    addressGenerator := halfWriteDepth
  } .elsewhen(io.ddrWeightEn) {
    addressGenerator := addressGenerator + 1.U
  }

  io.writeAddressW := addressGenerator
  io.writeEnW := io.ddrWeightEn
}