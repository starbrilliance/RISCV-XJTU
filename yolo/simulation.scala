package yolo

import chisel3._
import chisel3.util._
import chisel3.experimental._

import DdrParameter._

class SimToDdrCIO extends Bundle {
  val simEn = Output(Bool())
  val wdata = Output(UInt(APP_DATA_WIDTH.W))
  val addr = Output(UInt(ADDR_WIDTH.W))
  val wren = Output(Bool())
  val cmd = Output(UInt(CMD_WIDTH.W))
  val en = Output(Bool())
  val rdy = Input(Bool())
  val wrdy = Input(Bool())
  val rdata = Input(UInt(APP_DATA_WIDTH.W))
  val rvalid = Input(Bool())
}

class SimToTopIO extends Bundle {
  // write DDR
  val ddrReady = new HasCalibPort
  val simDDR = Flipped(new SimToDdrCIO)
  // sys clk
  val sys_clk = Output(Clock())
  // enable RoCC
  val simInstStart = Input(Bool())
  val simInstEnd = Output(Bool())
  // inst rst
  val simInstReset = Input(Bool())
}

class Simulator extends Module {
  val io = IO(new Bundle {
    // to top
    val toTop = new SimToTopIO
    // to DDR
    val init = Flipped(new HasCalibPort)
    val app = new SimToDdrCIO
    // virtual RoCC
    val core_clk = Output(Clock()) 
    val core_cmd_inst = Output(UInt(32.W))
    val core_cmd_rs1 = Output(UInt(32.W))
    val core_cmd_valid = Output(Bool())
  })

  io.toTop.ddrReady <> io.init
  io.toTop.simDDR <> io.app
  io.toTop.sys_clk := clock

  import Instructions._
  val inst = VecInit(0xB.U,        // YoloOutputAddr
                     0x8B.U,       // YoloInputAddr
                     0x10B.U,      // YoloWeightAddr
                     0x18B.U,      // YoloBiasAddr
                     0x20B.U,      // YoloBiasInfo
                     0x28B.U,      // YoloInputInfo
                     0x30B.U,      // YoloOutputInfo
                     0x400000B.U,  // YoloFSMStart
                     0x600000B.U,  // YoloNOP
                     0x200000B.U)   // YoloRegReset
  
  val info = VecInit(0.U,           // YoloOutputAddr
                     0.U,           // YoloInputAddr
                     0x10000000.U,  // YoloWeightAddr
                     0x18000000.U,  // YoloBiasAddr
                     0x1.U,         // YoloBiasInfo
                     0x70003.U,     // YoloInputInfo
                     0xDB8001.U,    // YoloOutputInfo
                     0.U,           // YoloFSMStart
                     0.U,           // YoloNOP
                     0.U)           // YoloRegReset
                     

  val count = RegInit(0.U(4.W))

  when(io.toTop.simInstStart) {
    count := Mux(io.toTop.simInstEnd, count, count + 1.U)
  }

  io.toTop.simInstEnd := (count === 9.U)
  io.core_clk := clock
  io.core_cmd_inst := inst(count)
  io.core_cmd_rs1 := info(count)
  io.core_cmd_valid := (io.toTop.simInstStart && !io.toTop.simInstEnd) || io.toTop.simInstReset
}