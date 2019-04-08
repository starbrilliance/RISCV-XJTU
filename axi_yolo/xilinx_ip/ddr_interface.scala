package axi_yolo

import chisel3._
import chisel3.util._
import chisel3.experimental._

class HasDdrFpgaIO extends Bundle {
  // Inouts
  val dq = Analog(64.W)
  val dqs_n = Analog(8.W)
  val dqs_p = Analog(8.W)
  // Outputs
  val addr = Output(UInt(16.W))
  val ba = Output(UInt(3.W))
  val ras_n = Output(Bool())
  val cas_n = Output(Bool())
  val we_n = Output(Bool())
  val reset_n = Output(Bool())
  val ck_p = Output(Bool())
  val ck_n = Output(Bool())
  val cke = Output(Bool())
  val cs_n = Output(Bool())
  val dm = Output(UInt(8.W))
  val odt = Output(Bool())
}

class HasDdrSystemIO extends Bundle {
  // Differential system clocks
  val clk_i = Input(Clock())
  // reset
  val rst = Input(Bool())
}

class HasCalibPort extends Bundle {
  // calibration
  val calib_complete = Output(Bool())
}

class HasAppIO extends Bundle {
  // application ports
  val sr_req = Input(Bool())
  val ref_req = Input(Bool())
  val zq_req = Input(Bool()) 
  val sr_active = Output(Bool())
  val ref_ack = Output(Bool())
  val zq_ack = Output(Bool())
}

class HasAXI4IO extends Bundle {
  //slave interface write address ports
  val awid = Input(UInt(4.W))
  val awaddr = Input(UInt(32.W))
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
  val wdata = Input(UInt(512.W))
  val wstrb = Input(UInt(64.W))
  val wlast = Input(Bool())
  val wvalid = Input(Bool())
  val wready = Output(Bool())

  //slave interface write response ports
  val bid = Output(UInt(4.W))
  val bresp = Output(UInt(2.W))
  val bvalid = Output(Bool())
  val bready = Input(Bool())

  //slave interface read address ports
  val arid = Input(UInt(4.W))
  val araddr = Input(UInt(32.W))
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
  val rid = Output(UInt(4.W))
  val rdata = Output(UInt(512.W))
  val rresp = Output(UInt(2.W))
  val rlast = Output(Bool())
  val rvalid = Output(Bool())
  val rready = Input(Bool())
}

class MigIO extends Bundle {
  val ddr3 = new HasDdrFpgaIO
  val sys = new HasDdrSystemIO
  val init = new HasCalibPort
  val app = new HasAppIO
  val s_axi = new HasAXI4IO
  val clk_ref_i = Input(Clock())
  val ui_clk = Output(Clock())
  val ui_clk_sync_rst = Output(Bool())
  val mmcm_locked = Output(Bool())
  val aresetn = Input(Bool())
}

class DdrInterface extends BlackBox {
  val io = IO(new MigIO)
}