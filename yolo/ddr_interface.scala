package yolo

import chisel3._
import chisel3.util._
import chisel3.experimental._

object DdrParameter {
  val ADDR_WIDTH = 30
  val CMD_WIDTH = 3
  val APP_DATA_WIDTH = 512
  val APP_MASK_WIDTH = APP_DATA_WIDTH >> 3
}

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

class HasDdrUserIO extends Bundle {
  // user ports
  import DdrParameter._
  val addr = Input(UInt(ADDR_WIDTH.W))
  val cmd = Input(UInt(CMD_WIDTH.W))
  val en = Input(Bool())
  val rdy = Output(Bool())
  val wdf_data = Input(UInt(APP_DATA_WIDTH.W))
  val wdf_mask = Input(UInt(APP_MASK_WIDTH.W))
  val wdf_end = Input(Bool())
  val wdf_wren = Input(Bool())
  val wdf_rdy = Output(Bool())
  val rd_data = Output(UInt(APP_DATA_WIDTH.W))
  val rd_data_end = Output(Bool())
  val rd_data_valid = Output(Bool())
  val sr_active = Output(Bool())
  val ref_ack = Output(Bool())
  val zq_ack = Output(Bool())
}

class MIO extends Bundle {
  val ddr3 = new HasDdrFpgaIO
  val sys = new HasDdrSystemIO
  val init = new HasCalibPort
  val app = new HasDdrUserIO
  val clk_ref_i = Input(Clock())
}

class MigIO extends MIO {
  val app_sr_req = Input(Bool())
  val app_ref_req = Input(Bool())
  val app_zq_req = Input(Bool())
  val ui_clk = Output(Clock())
  val ui_clk_sync_rst = Output(Bool())
  val device_temp = Output(UInt(12.W))
}

class DdrInterface extends BlackBox {
  val io = IO(new MigIO)
}

class MIG extends Module {
  val io = IO(new MIO)

  val ddrInterface = Module(new DdrInterface)

  io.ddr3 <> ddrInterface.io.ddr3
  io.sys <> ddrInterface.io.sys
  io.init <> ddrInterface.io.init
  io.app <> ddrInterface.io.app
  ddrInterface.io.clk_ref_i := io.clk_ref_i

  val app_sr_req = Wire(Bool())
  val app_ref_req = Wire(Bool())
  val app_zq_req = Wire(Bool())
  val ui_clk = Wire(Clock())
  val ui_clk_sync_rst = Wire(Bool())
  val device_temp = Wire(UInt(12.W))

  app_sr_req := false.B
  ddrInterface.io.app_sr_req := app_sr_req
  app_ref_req := false.B
  ddrInterface.io.app_ref_req := app_ref_req
  app_zq_req := false.B
  ddrInterface.io.app_zq_req := app_zq_req

  ui_clk := ddrInterface.io.ui_clk
  ui_clk_sync_rst := ddrInterface.io.ui_clk_sync_rst
  device_temp := ddrInterface.io.device_temp
}