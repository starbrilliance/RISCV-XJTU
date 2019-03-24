package axi_yolo

import chisel3._
import chisel3.util._

import AXI4Parameters._

class StoreToDdrCIO extends Bundle {
  val storeValid = Output(Bool())
  val dataOut = Output(UInt(C_S_AXI_DATA_WIDTH.W))
}

class Store extends Module {
  val io = IO(new Bundle {
    // from fsm
    val ddrStoreEn = Input(Bool())
    // from data buffer
    val dataIn = Input(UInt(56.W))
    val readEn = Output(Bool())
    // from ducm
    val ddrWriteRdy = Input(Bool())
    // to ddrC
    val toDdrC = new StoreToDdrCIO
  })

  val dataTemp0 = Reg(Vec(64, UInt(56.W)))
  val dataTemp1 = Wire(Vec(7, UInt(C_S_AXI_DATA_WIDTH.W)))
  val count0 = RegInit(0.U(6.W))
  val count1 = RegInit(0.U(3.W))
  val readDelay = ShiftRegister(io.readEn, 2)
  val storeDelay = ShiftRegister(io.ddrStoreEn, 2)
  val count0Valid = Wire(Bool())
  val countStop = Wire(Bool())

  import Store._

  dataTemp1(0) := Cat(dataTemp0(9)(7, 0), concatenate(dataTemp0, 8))
  dataTemp1(1) := Cat(dataTemp0(18)(15, 0), concatenate(dataTemp0, 17, 10), dataTemp0(9)(55, 8))
  dataTemp1(2) := Cat(dataTemp0(27)(23, 0), concatenate(dataTemp0, 26, 19), dataTemp0(18)(55, 16))
  dataTemp1(3) := Cat(dataTemp0(36)(31, 0), concatenate(dataTemp0, 35, 28), dataTemp0(27)(55, 24))
  dataTemp1(4) := Cat(dataTemp0(45)(39, 0), concatenate(dataTemp0, 44, 37), dataTemp0(36)(55, 32))
  dataTemp1(5) := Cat(dataTemp0(54)(47, 0), concatenate(dataTemp0, 53, 46), dataTemp0(45)(55, 40))
  dataTemp1(6) := Cat(concatenate(dataTemp0, 63, 55), dataTemp0(54)(55, 48))

  when(readDelay) {
    count0 := count0 + 1.U
    dataTemp0(count0) := io.dataIn
  }

  when(io.toDdrC.storeValid && !countStop) {
    count1 := Mux(count1 === 6.U, 0.U, count1 + 1.U)
  }

  count0Valid := ((count0 === 10.U) || (count0 === 19.U) || 
                 (count0 === 28.U) || (count0 === 37.U) || 
                 (count0 === 46.U) || (count0 === 55.U) ||
                 (count0 === 0.U)) && RegNext(storeDelay)

  countStop := !io.ddrWriteRdy && count0Valid

  io.readEn := io.ddrStoreEn && !countStop          

  io.toDdrC.storeValid := RegNext(count0Valid)

  io.toDdrC.dataOut := RegNext(dataTemp1(count1))
}

object Store {
  def concatenate(hiData: Vec[UInt], index0: Int, index1: Int = 0): UInt = {
    if(index0 > index1) 
      Cat(hiData(index0), concatenate(hiData, index0 - 1, index1))
    else
      hiData(index0)
  }
} 