package yolo

import chisel3._
import chisel3.util._
import chisel3.experimental._

class PE extends Module {
  val io = IO(new Bundle {
    val fromSr = Flipped(new SrToPeIO)  // data
    val fromPad = Flipped(new PaddingToPeIO) // weight
    val accClear = Flipped(new PaddingToPoolIO) // clear
    val kernelSize = Input(UInt(1.W))
    val validIn = Input(Bool())
    val dataOut0 = Output(Vec(224, SInt(8.W)))
    val dataOut1 = Output(Vec(224, SInt(8.W)))
    val validOut = Output(Vec(224, Bool()))
  })

  val muls = VecInit(Seq.fill(18)(Module(new SatMultiplier).io))
  val adds = VecInit(Seq.fill(16)(Module(new SatAdder).io))
  val fifos = VecInit(Seq.fill(2)(Module(new Fifo(8, 3)).io))
  val accs = VecInit(Seq.fill(448)(Module(new Accumulator).io))
  val accCount = RegInit(0.U(8.W))
  val accDecode = Wire(UInt(224.W))
  val accDataIn = Wire(Vec(2, SInt(8.W)))
  val accValid = Wire(Bool())
  val accValidDelay = Reg(Vec(224, Bool()))

  accDataIn(0) := Mux(io.kernelSize.toBool, adds(7).dataOut, muls(0).dataOut)
  accDataIn(1) := Mux(io.kernelSize.toBool, adds(15).dataOut, muls(9).dataOut)
  accValid := Mux(io.kernelSize.toBool, adds(7).validOut && adds(15).validOut, muls(0).validOut && muls(9).validOut)

  fifos(0).wr.dataIn := muls(8).dataOut.asUInt
  fifos(0).wr.writeEn := muls(8).validOut
  fifos(0).wr.writeClk := clock
  fifos(0).rd.readEn := adds(4).validOut && adds(5).validOut
  fifos(0).sys.readClk := clock
  fifos(0).sys.systemRst := reset.toBool

  fifos(1).wr.dataIn := muls(17).dataOut.asUInt
  fifos(1).wr.writeEn := muls(17).validOut
  fifos(1).wr.writeClk := clock
  fifos(1).rd.readEn := adds(12).validOut && muls(13).validOut
  fifos(1).sys.readClk := clock
  fifos(1).sys.systemRst := reset.toBool

  for(i <- 0 until 9) {
    muls(i).dataIn0 := io.fromSr.dataout1(i)
    muls(i).dataIn1 := io.fromPad.weight(i)
    muls(i).validIn := io.validIn
    muls(i + 9).dataIn0 := io.fromSr.dataout2(i)
    muls(i + 9).dataIn1 := io.fromPad.weight(i)
    muls(i + 9).validIn := io.validIn
  }
  for(i <- 0 until 4) {
    adds(i).dataIn0 := muls(i * 2).dataOut
    adds(i).dataIn1 := muls(i * 2 + 1).dataOut
    adds(i).validIn := muls(i * 2).validOut && muls(i * 2 + 1).validOut
    adds(i + 8).dataIn0 := muls(i * 2 + 9).dataOut
    adds(i + 8).dataIn1 := muls(i * 2 + 10).dataOut
    adds(i + 8).validIn := muls(i * 2 + 9).validOut && muls(i * 2 + 10).validOut
  }
  for(i <- 0 until 2) {
    adds(i + 4).dataIn0 := adds(i * 2).dataOut
    adds(i + 4).dataIn1 := adds(i * 2 + 1).dataOut
    adds(i + 4).validIn := adds(i * 2).validOut && adds(i * 2 + 1).validOut
    adds(i + 12).dataIn0 := adds(i * 2 + 8).dataOut
    adds(i + 12).dataIn1 := adds(i * 2 + 9).dataOut
    adds(i + 12).validIn := adds(i * 2 + 8).validOut && muls(i * 2 + 9).validOut
  }
  adds(6).dataIn0 := adds(4).dataOut
  adds(6).dataIn1 := adds(5).dataOut
  adds(6).validIn := adds(4).validOut && adds(5).validOut
  adds(14).dataIn0 := adds(12).dataOut
  adds(14).dataIn1 := adds(13).dataOut
  adds(14).validIn := adds(12).validOut && muls(13).validOut

  adds(7).dataIn0 := adds(6).dataOut
  adds(7).dataIn1 := fifos(0).rd.dataOut.asSInt
  adds(7).validIn := adds(6).validOut
  adds(15).dataIn0 := adds(14).dataOut
  adds(15).dataIn1 := fifos(1).rd.dataOut.asSInt
  adds(15).validIn := adds(14).validOut

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

  for(i <- 0 until 224) {
    accs(i).B := accDataIn(0)
    accs(i).CLK := clock
    accs(i).CE := accDecode(i).toBool && accValid
    accs(i).SCLR := reset.toBool || (io.accClear.finalInputChannel && io.accClear.lineComplete)
    io.dataOut0(i) := accs(i).Q
    accValidDelay(i) := accDecode(i).toBool && accValid
    io.validOut(i) := accValidDelay(i)
    accs(i + 224).B := accDataIn(1)
    accs(i + 224).CLK := clock
    accs(i + 224).CE := accDecode(i).toBool && accValid
    accs(i + 224).SCLR := reset.toBool || (io.accClear.finalInputChannel && io.accClear.lineComplete)
    io.dataOut1(i) := accs(i + 224).Q
  }
}