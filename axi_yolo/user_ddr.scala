package axi_yolo

import chisel3._
import chisel3.util._
import chisel3.experimental._

object AXI4Parameters {
  val C_S_AXI_ID_WIDTH       = 4
  val C_S_AXI_ADDR_WIDTH     = 32
  val C_S_AXI_DATA_WIDTH     = 512
  val C_S_AXI_NBURST_SUPPORT = 0
  val C_S_CTL_SIG_WIDTH      = 3
  val DBG_WR_STS_WIDTH       = 40
  val DBG_RD_STS_WIDTH       = 40
  val C_S_EN_UPSIZER         = 0
  val C_S_WDG_TIMER_WIDTH    = 11 
}

import AXI4Parameters._

class AXI4CMDIO extends Bundle { 
  // User interface command port
  val cmd_en = Input(Bool())    // Asserted to indicate a valid command and address
  val cmd = Input(UInt(3.W))    // Write or read command
                                // 000 - READ with INCR bursts
                                // 001 - READ with WRAP bursts
                                // 01x - Reserved
                                // 100 - WRITE with INCR bursts
                                // 101 - WRITE with WRAP bursts
  val blen = Input(UInt(8.W))   // Burst length calculated as blen+1
  val addr = Input(UInt(C_S_AXI_ADDR_WIDTH.W))  // Address for the read or the write transaction
  val cmd_ack = Output(Bool())  // Indicates the command has been accepted
}

class AXI4WriteIO extends Bundle {
  // User interface write ports
  val vld = Input(Bool())  // Asserted to indicate a valid write data
  val data = Input(UInt(C_S_AXI_DATA_WIDTH.W))    // Write data
  val bvld = Input(UInt((C_S_AXI_DATA_WIDTH/8).W))  // Byte valids for the write data
  val cmptd = Input(Bool())  // Last data to be transferred
  val rdy = Output(Bool())   // Indicates that the write data is ready to be accepted
  val sts_vld = Output(Bool())  // Indicates a write status after completion of a write transfer                               
  val sts = Output(UInt(DBG_WR_STS_WIDTH.W))   // Status of the write transaction
}

class AXI4ReadIO extends Bundle {
  // User interface read ports
  val rdy = Input(Bool())    // Data ready to be accepted
  val vld = Output(Bool())   // Indicates a valid read data available
  val data = Output(UInt(C_S_AXI_DATA_WIDTH.W))    // Read data
  val bvld = Output(UInt((C_S_AXI_DATA_WIDTH/8).W))  // Byte valids for read data 
  val cmptd = Output(Bool()) // Indicates last data present and valid status
  val sts = Output(UInt(DBG_RD_STS_WIDTH.W))  // Status of the read transaction
}

class AXI4WrapperIO extends AXI4CMDIO {
  val aclk = Input(Clock())     // using ui_clk of the ddr_interface, axi4 clk
  val aresetn = Input(Bool())   // using aresetn of the ddr_interface, axi4 rst
  val ctl = Input(UInt(C_S_CTL_SIG_WIDTH.W))    // control command for read or write transaction
                                                // assignign to 6 when dq is 64bit
  val wdog_mask = Input(Bool())                 // Mask the watchdog timeouts
                                                // assigning to ~init_calib_complete 
  val wrdata = new AXI4WriteIO  // write data ports
  val rddata = new AXI4ReadIO   // read data ports
  val axi = Flipped(new HasAXI4IO)  // axi4 ports
}

class axi4_wrapper extends BlackBox(Map("C_AXI_ID_WIDTH" -> C_S_AXI_ID_WIDTH,
                                        "C_AXI_ADDR_WIDTH" -> C_S_AXI_ADDR_WIDTH,
                                        "C_AXI_DATA_WIDTH" -> C_S_AXI_DATA_WIDTH,
                                        "C_AXI_NBURST_SUPPORT" -> C_S_AXI_NBURST_SUPPORT,
                                        "CTL_SIG_WIDTH" -> C_S_CTL_SIG_WIDTH,
                                        "WR_STS_WIDTH" -> DBG_WR_STS_WIDTH,
                                        "RD_STS_WIDTH" -> DBG_RD_STS_WIDTH,
                                        "EN_UPSIZER" -> C_S_EN_UPSIZER,
                                        "WDG_TIMER_WIDTH" -> C_S_WDG_TIMER_WIDTH))
                             with HasBlackBoxResource {
  
  val io = IO(new AXI4WrapperIO)

  setResource("/axi_wrapper.v")
}

// using AXI4 ports rather than ddr3 fpga ports when integrated into system
class DdrUserControlIO extends AXI4CMDIO {
  val aclk = Output(Clock())     // using ui_clk of the ddr_interface
  val aresetn = Output(Bool())   // using aresetn of the ddr_interface
  val wrdata = new AXI4WriteIO  // user write data ports 
  val rddata = new AXI4ReadIO   // user read data ports
  val clk_ref_i = Input(Clock())  // referential clock 200MHz
  val ddr3 = new HasDdrFpgaIO   // ddr3 fpga ports
  val sys = new HasDdrSystemIO  // system clk and rst
  val init = new HasCalibPort   // calibration  
}

class DdrUserControlModule extends RawModule {
  val io = IO(new DdrUserControlIO)

  val xilinxMIG = Module(new DdrInterface)
  val axi4Wrapper = Module(new axi4_wrapper)

  val app_sr_active = Wire(Bool())
  val app_ref_ack = Wire(Bool())
  val app_zq_ack = Wire(Bool())
  val mmcm_locked = Wire(Bool())
  val nothing = Wire(Bool())
  nothing := DontCare
  val axi_reset = withClockAndReset(xilinxMIG.io.ui_clk, nothing) {
    RegNext(~xilinxMIG.io.ui_clk_sync_rst)
  }

  io.ddr3 <> xilinxMIG.io.ddr3
  io.sys <> xilinxMIG.io.sys
  io.init <> xilinxMIG.io.init
  io.aclk := xilinxMIG.io.ui_clk
  io.aresetn := axi_reset
  io.cmd_ack := axi4Wrapper.io.cmd_ack
  xilinxMIG.io.clk_ref_i := io.clk_ref_i
  xilinxMIG.io.app.sr_req := false.B
  xilinxMIG.io.app.ref_req := false.B
  xilinxMIG.io.app.zq_req := false.B
  xilinxMIG.io.aresetn := axi_reset
  app_sr_active := xilinxMIG.io.app.sr_active
  app_ref_ack := xilinxMIG.io.app.ref_ack
  app_zq_ack := xilinxMIG.io.app.zq_ack
  mmcm_locked := xilinxMIG.io.mmcm_locked

  axi4Wrapper.io.cmd_en := io.cmd_en
  axi4Wrapper.io.cmd := io.cmd
  axi4Wrapper.io.blen := io.blen
  axi4Wrapper.io.addr := io.addr
  axi4Wrapper.io.aclk := xilinxMIG.io.ui_clk
  axi4Wrapper.io.aresetn := axi_reset
  axi4Wrapper.io.ctl := 6.U
  axi4Wrapper.io.wdog_mask := ~xilinxMIG.io.init.calib_complete
  axi4Wrapper.io.wrdata <> io.wrdata
  axi4Wrapper.io.rddata <> io.rddata
  axi4Wrapper.io.axi <> xilinxMIG.io.s_axi 
}