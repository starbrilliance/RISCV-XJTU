package axi_yolo

import chisel3._
import chisel3.util._

class MulAddUnit extends Module {
  val io = IO(new Bundle {
    val dataIn = Input(Vec(9, SInt(8.W)))
    val weightIn = Input(Vec(9, SInt(8.W)))
    val biasIn = Input(SInt(8.W))
    val kernelSize = Input(Bool())
    val biasSelect = Input(Bool())
    val firstLayer = Input(Bool())
    val validIn = Input(Bool())
    val dataOut = Output(SInt(22.W))
    val validOut = Output(Bool()) 
  })

  val mul = VecInit(Seq.fill(9)(Module(new SIntNineMEight).io))
  val add0 = VecInit(Seq.fill(4)(Module(new SIntSeventeenASeventeen).io))
  val add1 = VecInit(Seq.fill(2)(Module(new SIntEighteenAEighteen).io))
  val add2 = Module(new SIntNineteenANineteen)
  val add3 = Module(new SIntTwentyASeventeen)
  val add4 = Module(new SIntTwentyOneAEight)
  val fifo = Module(new Fifo(17, 4))
  val add0ValidTemp0 = RegNext(io.validIn)
  val add0ValidTemp1 = RegNext(add0ValidTemp0)
  val add0Valid = add0ValidTemp0 || add0ValidTemp1 
  val add1Valid = RegNext(RegNext(add0Valid))
  val add2Valid = RegNext(RegNext(add1Valid))
  val add3Valid0 = RegNext(add2Valid)
  val add3Valid1 = RegNext(add3Valid0)
  val add4Valid0 = RegNext(RegNext(add3Valid1)) 
  val dataInTemp = Wire(Vec(9, SInt(9.W)))
  val biasTemp = Wire(SInt(8.W))
  val add4ATemp = Wire(SInt(21.W))
  val add4CE = Wire(Bool())

  for(i <- 0 until 9) {
    dataInTemp(i) := Mux(io.firstLayer, Cat(0.U, io.dataIn(i)), Cat(io.dataIn(i)(7), io.dataIn(i))).asSInt
  }

  for(i <- 0 until 9) {
    mul(i).CLK := clock
    mul(i).A := dataInTemp(i)
    mul(i).B := io.weightIn(i)
    mul(i).CE := io.validIn
  }

  for(i <- 0 until 4) {
    add0(i).CLK := clock
    add0(i).A := mul(i).P
    add0(i).B := mul(i + 4).P
    add0(i).CE := add0Valid
  }

  for(i <- 0 until 2) {
    add1(i).CLK := clock
    add1(i).A := add0(i).S
    add1(i).B := add0(i + 2).S
    add1(i).CE := add1Valid
  }

  add2.io.CLK := clock
  add2.io.A := add1(0).S
  add2.io.B := add1(1).S
  add2.io.CE := add2Valid

  fifo.io.wr.dataIn := mul(8).P.asUInt
  fifo.io.wr.writeEn := add0Valid
  fifo.io.wr.writeClk := clock
  fifo.io.rd.readEn := add3Valid0
  fifo.io.sys.readClk := clock
  fifo.io.sys.systemRst := reset.toBool

  add3.io.CLK := clock
  add3.io.A := add2.io.S
  add3.io.B := fifo.io.rd.dataOut.asSInt
  add3.io.CE := add3Valid1

  add4ATemp := Mux(io.kernelSize, add3.io.S, mul(4).P)
  add4CE := Mux(io.kernelSize, add4Valid0, add0Valid) 
  biasTemp := Mux(io.biasSelect, io.biasIn, 0.S)
  add4.io.CLK := clock
  add4.io.A := add4ATemp
  add4.io.B := biasTemp
  add4.io.CE := add4CE

  io.dataOut := add4.io.S
  io.validOut := RegNext(RegNext(add4CE))                                                    
}