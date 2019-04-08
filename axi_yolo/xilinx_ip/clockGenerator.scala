package axi_yolo

import chisel3._
import chisel3.experimental._

class ClockGenerator extends BlackBox {
  val io = IO(new Bundle {
    // Clock out ports
    val ddr_clk_i = Output(Clock()) // 100MHz
    val ref_clk_i = Output(Clock()) // 200MHz
    // Status and control signals
    val reset = Input(Bool())    // input reset
    val locked = Output(Bool())  // output locked
    // Clock in ports
    val clk_in1_p = Input(Clock())  // input clk_in1_p
    val clk_in1_n = Input(Clock())  // input clk_in1_n  
  })
}