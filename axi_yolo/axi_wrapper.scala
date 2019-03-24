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

class HasAXI4IO extends Bundle {
  //slave interface write address ports
  val awid = Input(UInt(C_S_AXI_ID_WIDTH.W))
  val awaddr = Input(UInt(C_S_AXI_ADDR_WIDTH.W))
  val awlen = Input(UInt(8.W))
  val awsize = Input(UInt(3.W))
  val awburst = Input(UInt(2.W))
  val awlock = Input(Bool())
  val awcache = Input(UInt(4.W))
  val awprot = Input(UInt(3.W))
  val awqos = Input(UInt(4.W))
  val awvalid = Input(Bool())
  val awready = Output(Bool())
  
  //slave interface write data ports
  val wdata = Input(UInt(C_S_AXI_DATA_WIDTH.W))
  val wstrb = Input(UInt((C_S_AXI_DATA_WIDTH/8).W))
  val wlast = Input(Bool())
  val wvalid = Input(Bool())
  val wready = Output(Bool())

  //slave interface write response ports
  val bid = Output(UInt(C_S_AXI_ID_WIDTH.W))
  val bresp = Output(UInt(2.W))
  val bvalid = Output(Bool())
  val bready = Input(Bool())

  //slave interface read address ports
  val arid = Input(UInt(C_S_AXI_ID_WIDTH.W))
  val araddr = Input(UInt(C_S_AXI_ADDR_WIDTH.W))
  val arlen = Input(UInt(8.W))
  val arsize = Input(UInt(3.W))
  val arburst = Input(UInt(2.W))
  val arlock = Input(Bool())
  val arcache = Input(UInt(4.W))
  val arprot = Input(UInt(3.W))
  val arqos = Input(UInt(4.W))
  val arvalid = Input(Bool())
  val arready = Output(Bool())

  //slave interface read data ports
  val rid = Output(UInt(C_S_AXI_ID_WIDTH.W))
  val rdata = Output(UInt(C_S_AXI_DATA_WIDTH.W))
  val rresp = Output(UInt(2.W))
  val rlast = Output(Bool())
  val rvalid = Output(Bool())
  val rready = Input(Bool())
}

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