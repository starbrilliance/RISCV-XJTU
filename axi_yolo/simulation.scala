package axi_yolo

import chisel3._
import chisel3.util._
import chisel3.experimental._

import AXI4Parameters._

class SimToDdrCIO extends Bundle {
  val simEn = Input(Bool())
  val control = new AXI4CMDIO   // user cmd ports
  val wrdata = new AXI4WriteIO  // user write data ports 
  val rddata = new AXI4ReadIO   // user read data ports
}

class SimToTopIO extends Bundle {
  // write DDR
  val ddrReady = new HasCalibPort
  val simDDR = new SimToDdrCIO
  // sys clk
  val sys_clk = Output(Clock())
  // enable RoCC
  val simInstStart = Input(Bool())
  val simInstEnd = Output(Bool())
}

class Simulator extends Module {
  val io = IO(new Bundle {
    // to top
    val toTop = new SimToTopIO
    // to DDR
    val init = Flipped(new HasCalibPort)
    val app = Flipped(new SimToDdrCIO)
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

  val inst = VecInit(// global reset
                     "h0600_000B".U,  // YoloRegReset
                     // layer 1
                     "h0400_000B".U,  // YoloBiasReset
                     "h0000_200B".U,  // YoloOutputAddr
                     "h0200_200B".U,  // YoloInputAddr
                     "h0400_200B".U,  // YoloWeightAddr
                     "h0600_200B".U,  // YoloBiasAddr
                     "h0800_200B".U,  // YoloBiasInfo
                     "h0A00_200B".U,  // YoloInputInfo
                     "h0C00_200B".U,  // YoloOutputInfo
                     "h0200_000B".U,  // YoloFSMStart
                     "h0000_000B".U,  // YoloNOP
                     // layer 2
                     "h0C00_200B".U,  // YoloOutputInfo
                     "h0200_000B".U,  // YoloFSMStart
                     "h0400_000B".U,  // YoloNOP
                     // layer 3
                     "h0C00_200B".U,  // YoloOutputInfo
                     "h0200_000B".U,  // YoloFSMStart
                     "h0400_000B".U,  // YoloNOP
                     // layer 4
                     "h0C00_200B".U,  // YoloOutputInfo
                     "h0200_000B".U,  // YoloFSMStart
                     "h0400_000B".U,  // YoloNOP
                     // layer 5
                     "h0C00_200B".U,  // YoloOutputInfo
                     "h0200_000B".U,  // YoloFSMStart
                     "h0400_000B".U)  // YoloNOP
  
  val info = VecInit(// global reset
                     "h0000_0000".U,     // YoloRegReset      nothing
                     // layer 1
                     "h0000_0000".U,     // YoloBiasReset     nothing
                     "h0000_0000".U,     // YoloOutputAddr    address 0x00000000
                     "h0000_0000".U,     // YoloInputAddr     address 0x00000000
                     "h1000_0000".U,     // YoloWeightAddr    address 0x10000000
                     "h1800_0000".U,     // YoloBiasAddr      address 0x18000000
                     "h0000_0008".U,     // YoloBiasInfo      bias num 8
                     "h0_0_0_E0_001".U,  // YoloInputInfo     ic-1 dsize-224
                     "h0_3_3_70_002".U,  // YoloOutputInfo    oc-2 dsize-112 ksize-3 pool-1 first-1 final-0
                     "h0000_0000".U,     // YoloFSMStart      nothing
                     "h0000_0000".U,     // YoloNOP           nothing
                     // layer 2
                     "h0_1_3_38_001".U,  // YoloOutputInfo    oc-1 dsize-56  ksize-3 pool-1 first-0 final-0
                     "h0000_0000".U,     // YoloFSMStart      nothing
                     "h0000_0000".U,     // YoloNOP           nothing
                     // layer 3
                     "h0_1_3_1C_002".U,  // YoloOutputInfo    oc-2 dsize-28  ksize-3 pool-1 first-0 final-0
                     "h0000_0000".U,     // YoloFSMStart      nothing
                     "h0000_0000".U,     // YoloNOP           nothing
                     // layer 4
                     "h0_1_1_0E_001".U,  // YoloOutputInfo    oc-1 dsize-14  ksize-1 pool-1 first-0 final-0
                     "h0000_0000".U,     // YoloFSMStart      nothing
                     "h0000_0000".U,     // YoloNOP           nothing
                     // layer 5
                     "h0_5_1_07_002".U,  // YoloOutputInfo    oc-2 dsize-7   ksize-1 pool-1 first-0 final-1
                     "h0000_0000".U,     // YoloFSMStart      nothing
                     "h0000_0000".U)     // YoloNOP           nothing
                     
  val count = RegInit(0.U(5.W))

  when(io.toTop.simInstStart) {
    count := count + 1.U
  }

  val instEnd = (count === 11.U) || (count === 14.U) ||
                (count === 17.U) || (count === 20.U) ||
                (count === 23.U)

  io.toTop.simInstEnd := instEnd
  io.core_clk := clock
  io.core_cmd_inst := inst(count)
  io.core_cmd_rs1 := info(count)
  io.core_cmd_valid := io.toTop.simInstStart
}