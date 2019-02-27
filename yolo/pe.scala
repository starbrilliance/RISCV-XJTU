package yolo

import chisel3._
import chisel3.util._
import chisel3.experimental._

class PE extends Module {
  val io = IO(new Bundle {
    val fromSr = Flipped(new SrToPeIO)  // data
    val fromPad = Flipped(new PaddingToPeIO) // weight & clear
    val biasIn = Input(SInt(8.W))  // bias
    val kernelSize = Input(UInt(1.W))
    val validIn = Input(Bool())
    val dataOut = Output(Vec(448, SInt(31.W)))
    val validOut = Output(Vec(224, Bool()))
  })

  val maus = VecInit(Seq.fill(2)(Module(new MulAddUnit).io))
  val accs = VecInit(Seq.fill(448)(Module(new Accumulator).io))
  val accCount = RegInit(0.U(8.W))
  val accDecode = Wire(UInt(224.W))
  val accValid = Wire(Bool())
  val accValidDelay = Reg(Vec(224, Bool()))

  for(i <- 0 until 9) {
    maus(0).dataIn(i) := io.fromSr.dataout1(i)
    maus(0).weightIn(i) := io.fromPad.weight(i)
    maus(1).dataIn(i) := io.fromSr.dataout2(i)
    maus(1).weightIn(i) := io.fromPad.weight(i)
  }
  for(i <- 0 until 2) {
    maus(i).biasIn := io.biasIn
    maus(i).kernelSize := io.kernelSize
    maus(i).validIn := io.validIn
  }

  accValid := maus(0).validOut

  when(accValid) {
    when(accCount === 223.U) {
      accCount := 0.U
    } .otherwise {
      accCount := accCount + 1.U
    }
  }

  val x = for(i <- 0 until 224)
            yield accCount === i.U
  
  val y = for(i <- 0 until 224)
            yield 1.U << i
  
  accDecode := MuxCase(0.U, x zip y)

  for(i <- 0 until 2) {
    for(j <- 0 until 224) {
      accs(i * 224 + j).B := maus(i).dataOut
      accs(i * 224 + j).CLK := clock
      accs(i * 224 + j).CE := accDecode(j).toBool && accValid
      accs(i * 224 + j).SCLR := reset.toBool || (io.fromPad.finalInputChannel && io.fromPad.lineComplete)
      io.dataOut(i * 224 + j) := accs(i * 224 + j).Q
      accValidDelay(j) := RegNext(RegNext(accDecode(j).toBool && accValid))
      io.validOut(j) := accValidDelay(j)
    }
  }
}