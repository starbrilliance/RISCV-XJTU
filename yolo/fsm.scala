package  yolo

import chisel3._
import chisel3.util._

class FsmDdrIO extends Bundle {
  val ddrDataEn = Output(Bool())
  val ddrWeightEn = Output(Bool())
  val ddrBiasEn = Output(Bool())
  val ddrComplete = Input(Bool())
  val infoComplete = Input(Bool())
}

class FsmInfoIO extends Bundle {
  val infoEn = Output(Bool())
}

class FsmIO extends Bundle {
  // from decoder
  val enable = Flipped(new DecoderToFsmIO)
  // fsm <> ddrC
  val fsmToDdr = new FsmDdrIO
  // calculate
  val fsmToPad = Flipped(new FsmToPaddingIO)
  // from regfiles
  val fromReg = Flipped(new RegfileToFsmIO)
  // to information
  val toInfo = new FsmInfoIO
  // to store
  val ddrStoreEn = Output(Bool())
  // is idle
  val isIdle = Output(Bool())
}

class Fsm extends Module {
  val io = IO(new FsmIO)

  val idle :: info :: readImage :: readWeight :: readBias :: calculate :: store :: Nil = Enum(7)

  val state = RegInit(idle)
  val stateSignals = Wire(UInt(3.W))

  switch(state) {
    is(idle) {
      when(io.enable.fsmStar) {  
        state := info
      }
    }
    is(info) {
      when(io.fsmToDdr.infoComplete) {  
        when(io.fromReg.firstLayer) {
          state := readImage
        } .otherwise {
          state := calculate
        }
      }
    }
    is(readImage) {
      when(io.fsmToDdr.ddrComplete) {  
        state := readWeight
      }
    }
    is(readWeight) {
      when(io.fsmToDdr.ddrComplete) {  
        state := readBias
      }
    }
    is(readBias) {
      when(io.fsmToDdr.ddrComplete) {  
        state := calculate
      }
    }
    is(calculate) {
      when(io.fsmToPad.layerComplete) {
        when(io.fromReg.finalLayer) {
          state := store
        } .otherwise {
          state := idle
        } 
      }
    }
    is(store) {
      when(io.fsmToDdr.ddrComplete) {  
        state := idle
      }
    }
  }

  import Fsm._

  stateSignals := MuxCase("b1111".U, Array((state === idle)       -> BitPat.bitPatToUInt(IDLE),
                                           (state === info)       -> BitPat.bitPatToUInt(INFO),
                                           (state === readImage)  -> BitPat.bitPatToUInt(RI),
                                           (state === readWeight) -> BitPat.bitPatToUInt(RW),
                                           (state === readBias)   -> BitPat.bitPatToUInt(RB),
                                           (state === calculate)  -> BitPat.bitPatToUInt(CAL),
                                           (state === store)      -> BitPat.bitPatToUInt(STORE)))
  
  val signals = ListLookup(stateSignals, default, map)

  io.isIdle := signals(0)
  io.toInfo.infoEn := signals(1)
  io.fsmToDdr.ddrDataEn := signals(2)
  io.fsmToDdr.ddrWeightEn := signals(3)
  io.fsmToDdr.ddrBiasEn := signals(4)
  io.fsmToPad.padEn := signals(5)
  io.ddrStoreEn := signals(6)
}

object Fsm {
  def IDLE = BitPat("b000")
  def INFO = BitPat("b001")
  def RI = BitPat("b010")
  def RW = BitPat("b011")
  def RB = BitPat("b100")
  def CAL = BitPat("b101")
  def STORE = BitPat("b110")

  val Y = true.B
  val N = false.B

  val default =
    //               idle   infoEn  ddrDataEn  ddrWeightEn  ddrBiasEn  padEn  ddrStoreEn
    //                |       |        |            |           |        |        |  
                List( N,      N,       N,           N,          N,       N,       N  )
  val map = Array(
        IDLE -> List( Y,      N,       N,           N,          N,       N,       N  ),
        INFO -> List( N,      Y,       N,           N,          N,       N,       N  ),
        RI   -> List( N,      N,       Y,           N,          N,       N,       N  ),
        RW   -> List( N,      N,       N,           Y,          N,       N,       N  ),
        RB   -> List( N,      N,       N,           N,          Y,       N,       N  ),
        CAL  -> List( N,      N,       N,           N,          N,       Y,       N  ),
        STORE-> List( N,      N,       N,           N,          N,       N,       Y  ))  
}