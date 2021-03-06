package axi_yolo

import chisel3._
import chisel3.util._
import chisel3.experimental._

object Control {
  val Y = true.B
  val N = false.B
  // write regs
  val WR_EN  = Y
  val WR_NOT = N
  // regs numer
  val REG_0  = 0.U(3.W)
  val REG_1  = 1.U(3.W)
  val REG_2  = 2.U(3.W)
  val REG_3  = 3.U(3.W)
  val REG_4  = 4.U(3.W)
  val REG_5  = 5.U(3.W)
  val REG_6  = 6.U(3.W)
  val REG_XXX = 7.U(3.W)
  // FSM enable
  val FSM_EN  = Y
  val FSM_NOT = N
  // reset enable
  val RES_EN  = Y
  val RES_NOT = N

  import Instructions._

  val default =
    //                     wr_en   reg_num  FSM_en   reg_reset  illegal? 
    //                       |        |        |        |          |
                      List(WR_NOT, REG_XXX, FSM_NOT, RES_NOT,      Y)
  val map = Array(
    YoloOutputAddr -> List(WR_EN,  REG_0,   FSM_NOT, RES_NOT,      N),
    YoloInputAddr  -> List(WR_EN,  REG_1,   FSM_NOT, RES_NOT,      N),
    YoloWeightAddr -> List(WR_EN,  REG_2,   FSM_NOT, RES_NOT,      N),
    YoloBiasAddr   -> List(WR_EN,  REG_3,   FSM_NOT, RES_NOT,      N),
    YoloBiasInfo   -> List(WR_EN,  REG_4,   FSM_NOT, RES_NOT,      N),
    YoloInputInfo  -> List(WR_EN,  REG_5,   FSM_NOT, RES_NOT,      N),
    YoloOutputInfo -> List(WR_EN,  REG_6,   FSM_NOT, RES_NOT,      N),
    YoloNOP        -> List(WR_NOT, REG_XXX, FSM_NOT, RES_NOT,      N),
    YoloFSMStart   -> List(WR_NOT, REG_XXX, FSM_EN,  RES_NOT,      N),    
    YoloRegReset   -> List(WR_NOT, REG_XXX, FSM_NOT, RES_EN,       N))
}

class DecoderToRegsIO extends Bundle {
  val writeEn = Output(Bool())
  val regNum = Output(UInt(3.W))
}

class DecoderToFsmIO extends Bundle {
  val fsmStar = Output(Bool())
  val biasEn = Output(Bool())
}

class DecoderIO extends Bundle {
  val fifoPorts = Flipped(new FifoToDecoderIO(32))
  val regsPorts = new DecoderToRegsIO 
  val fsmPorts = new DecoderToFsmIO
  val readBiasComplete = Input(Bool())
  val powerOnRst = Input(Bool())
  val systemRst = Output(Bool())
  val illegal = Output(Bool())
}

class Decoder extends Module {
  val io = IO(new DecoderIO)

  val read = RegNext(!io.fifoPorts.empty)
  val read_delay = RegNext(read)
  val inst = Mux(read_delay, io.fifoPorts.dataOut, "h0000_000B".U)
  val ctrlSignals = ListLookup(inst, Control.default, Control.map)
  
  val biasFlag = withReset(io.powerOnRst) {
    RegInit(true.B)
  }

  when(io.readBiasComplete) {
    biasFlag := false.B
  }

  val rstDelay0 = RegNext(ctrlSignals(3).toBool)
  val rstDelay1 = RegNext(rstDelay0)
  val rstDelay2 = RegNext(rstDelay1)

  io.fifoPorts.readEn := read
  io.regsPorts.writeEn := ctrlSignals(0)
  io.regsPorts.regNum := ctrlSignals(1)
  io.fsmPorts.fsmStar := ctrlSignals(2)
  io.fsmPorts.biasEn := biasFlag
  io.systemRst := ctrlSignals(3).toBool || rstDelay0 || rstDelay1 || rstDelay2
  io.illegal := ctrlSignals(4)
}