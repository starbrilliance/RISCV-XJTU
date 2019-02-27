package yolo

import chisel3._
import chisel3.util._
import chisel3.experimental._

import DdrParameter._

class SimToDdrCIO extends Bundle {
  val simDDRdata = Output(UInt(APP_DATA_WIDTH.W))
  val simDDRaddress = Output(UInt(ADDR_WIDTH.W))
  val simDDRwren = Output(Bool())
}

class SimToTopIO extends Bundle {
  // write DDR
  val ddrReady = new HasCalibPort
  val simData = Flipped(new SimToDdrCIO)
  // enable RoCC
  val simulationStart = Input(Bool())
  val instEnd = Output(Bool())
}

class Simulator extends Module {
  val io = IO(new Bundle {
    // to top
    val toTop = new SimToTopIO
    // to DDR
    val init = Flipped(new HasCalibPort)
    val simDDR = new SimToDdrCIO
    // virtual RoCC
    val core_clk = Output(Clock()) 
    val core_cmd_inst = Output(UInt(32.W))
    val core_cmd_rs1 = Output(UInt(32.W))
    val core_cmd_valid = Output(Bool())
  })

  io.toTop.ddrReady <> io.init
  io.toTop.simData <> io.simDDR

  import Instructions._
  val inst = VecInit(0x200000B.U,  // YoloRegReset
                     0xB.U,        // YoloOutputAddr
                     0x8B.U,       // YoloInputAddr
                     0x10B.U,      // YoloWeightAddr
                     0x18B.U,      // YoloBiasAddr
                     0x20B.U,      // YoloBiasInfo
                     0x18B.U,      // YoloInputInfo
                     0x20B.U,      // YoloOutputInfo
                     0x400000B.U,  // YoloFSMStart
                     0x600000B.U)  // YoloNOP
  
  val info = VecInit(0.U,           // YoloRegReset
                     0.U,           // YoloOutputAddr
                     0.U,           // YoloInputAddr
                     0x10000000.U,  // YoloWeightAddr
                     0x20000000.U,  // YoloBiasAddr
                     0x4.U,         // YoloBiasInfo
                     0x70003.U,     // YoloInputInfo
                     0xDB8004.U,    // YoloOutputInfo
                     0.U,           // YoloFSMStart
                     0.U)           // YoloNOP

  val count = RegInit(0.U(4.W))

  when(io.toTop.simulationStart) {
    count := Mux(io.toTop.instEnd, count, count + 1.U)
  }

  io.toTop.instEnd := count === 10.U
  io.core_clk := clock
  io.core_cmd_inst := inst(count)
  io.core_cmd_rs1 := info(count)
  io.core_cmd_valid := io.toTop.simulationStart && !io.toTop.instEnd
}