package  yolo

import chisel3._
import chisel3.util._

class FsmDdrIO extends Bundle {
  val ddrDataEn = Output(Bool())
  val ddrWeightEn = Output(Bool())
  val ddrStoreEn = Output(Bool())
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
  // is idle
  val isIdle = Output(Bool())
}

class Fsm extends Module {
  val io = IO(new FsmIO)

  val idle :: info :: readImage :: readWeight :: calculate :: store :: Nil = Enum(6)

  val state = RegInit(idle)
  val stateSignals = Wire(UInt(4.W))

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
                                           (state === calculate)  -> BitPat.bitPatToUInt(CAL),
                                           (state === store)      -> BitPat.bitPatToUInt(STORE)))
  
  val signals = ListLookup(stateSignals, default, map)

  io.isIdle := signals(0)
  io.toInfo.infoEn := signals(1)
  io.fsmToDdr.ddrDataEn := signals(2)
  io.fsmToDdr.ddrWeightEn := signals(3)
  io.fsmToPad.padEn := signals(4)
  io.fsmToDdr.ddrStoreEn := signals(5)
}

object Fsm {
  def IDLE = BitPat("b0000")
  def INFO = BitPat("b0001")
  def RI = BitPat("b0010")
  def RW = BitPat("b0011")
  def CAL = BitPat("b0100")
  def STORE = BitPat("b0101")

  val Y = true.B
  val N = false.B

  val default =
    //               idle   infoEn  ddrDataEn  ddrWeightEn  padEn  ddrStoreEn
    //                |       |        |            |         |         |       
                List( N,      N,       N,           N,        N,        N  )
  val map = Array(
        IDLE -> List( Y,      N,       N,           N,        N,        N  ),
        INFO -> List( N,      Y,       N,           N,        N,        N  ),
        RI   -> List( N,      N,       Y,           N,        N,        N  ),
        RW   -> List( N,      N,       N,           Y,        N,        N  ),
        CAL  -> List( N,      N,       N,           N,        Y,        N  ),
        STORE-> List( N,      N,       N,           N,        N,        Y  ))  
}