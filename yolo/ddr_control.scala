package yolo

import chisel3._
import chisel3.util._

import DdrParameter._
class DdrControl(simulation: Boolean = false) extends Module {
  val io = IO(new Bundle{
    // buffer data ports
    val bufferToDdr = Input(UInt(APP_DATA_WIDTH.W))
    val ddrToBuffer = Output(UInt(APP_DATA_WIDTH.W))
    // ddrC <> Fsm
    val ddrToFsm = Flipped(new FsmDdrIO)
    // pad <> ddrC
    val padToDdr = Flipped(new PaddingToDdrControlIO)
    // from info
    val fromInfo = Flipped(new InfoDdrIO)
    // Xilinx DDR3 IP ports
    // calibration
    val init = Flipped(new HasCalibPort)
    // user ports
    val app = Flipped(new HasDdrUserIO)
    // if define sim
    val simWrite = if(simulation) Some(Flipped(new SimToDdrCIO)) else None
  })

  import WeightBufferParameter._
  val ddrWeightReg = RegInit(writeDepth << 6)
  val infoRegs = RegInit(VecInit(Seq.fill(6)(0.U(32.W))))
  val infoEnDelay = RegNext(io.fromInfo.validOut)
  val fillCount = RegInit(writeDepth >> 1)

  when(io.ddrToFsm.ddrComplete) {
    fillCount := writeDepth >> 1
  } .elsewhen(io.padToDdr.padFillHead || io.padToDdr.padFillTail) {
    fillCount := 0.U
  } .elsewhen(!fillCount(10).toBool) {
    fillCount := fillCount + 1.U
  }

  when(io.padToDdr.padFillHead || io.padToDdr.padFillTail) {
    ddrWeightReg := Mux((halfWriteDepth << 6) < infoRegs(5), halfWriteDepth << 6, infoRegs(5))
  } .elsewhen(io.init.calib_complete && (io.ddrToFsm.ddrWeightEn || !fillCount(10).toBool)) {
    ddrWeightReg := Mux(ddrWeightReg < 64.U, 0.U, ddrWeightReg - 64.U)
  }

  when(io.fromInfo.validOut) {
    infoRegs(0) := io.fromInfo.dataOut(0)
  } .elsewhen(io.app.wdf_wren) {
    infoRegs(0) := infoRegs(0) + 8.U
  }

  when(io.fromInfo.validOut) {
    infoRegs(1) := io.fromInfo.dataOut(1)
  } .elsewhen(io.init.calib_complete && io.ddrToFsm.ddrDataEn) {
    infoRegs(1) := infoRegs(1) + 8.U
  }

  when(io.fromInfo.validOut) {
    infoRegs(2) := io.fromInfo.dataOut(2)
  } .elsewhen(io.init.calib_complete && (io.ddrToFsm.ddrWeightEn || !fillCount(10).toBool)) {
    infoRegs(2) := infoRegs(2) + 8.U
  }

  when(io.fromInfo.validOut) {
    infoRegs(3) := io.fromInfo.dataOut(3)
  } .elsewhen(io.init.calib_complete && io.ddrToFsm.ddrDataEn) {
    infoRegs(3) := Mux(infoRegs(3) < 64.U, 0.U, infoRegs(3) - 64.U)
  }

  when(io.fromInfo.validOut) {
    infoRegs(4) := io.fromInfo.dataOut(4)
  } .elsewhen(io.app.wdf_wren) {
    infoRegs(4) := Mux(infoRegs(4) < 64.U, 0.U, infoRegs(4) - 64.U)
  }

  when(io.fromInfo.validOut) {
    infoRegs(5) := io.fromInfo.dataOut(5)
  } .elsewhen(io.ddrToFsm.ddrComplete) {
    when(io.ddrToFsm.ddrWeightEn) {
      infoRegs(5) := Mux(infoRegs(5) < (writeDepth << 6), 0.U, infoRegs(5) - (writeDepth << 6))
    } .otherwise {
      infoRegs(5) := Mux(infoRegs(5) < (halfWriteDepth << 6), 0.U, infoRegs(5) - (halfWriteDepth << 6))
    }
  }

  val writeAddress = RegNext(infoRegs(0))
  val readAddress = RegNext(infoRegs(1))
  val weightAddress = RegNext(infoRegs(2))

  if(simulation) {
    io.app.addr := MuxCase(0.U, Array((io.init.calib_complete && io.ddrToFsm.ddrDataEn) -> readAddress(29, 0), 
                                      (io.init.calib_complete && (io.ddrToFsm.ddrWeightEn || !fillCount(10).toBool)) -> weightAddress(29, 0),
                                      (io.app.wdf_wren && !io.simWrite.get.simDDRwren) -> writeAddress(29, 0),
                                      io.simWrite.get.simDDRwren -> io.simWrite.get.simDDRaddress))
    
    io.app.wdf_wren := io.init.calib_complete && io.app.wdf_rdy && (io.ddrToFsm.ddrStoreEn || io.simWrite.get.simDDRwren)

    io.app.wdf_data := Mux(io.simWrite.get.simDDRwren, io.simWrite.get.simDDRdata, io.bufferToDdr)
  } else {
    io.app.addr := MuxCase(0.U, Array((io.init.calib_complete && io.ddrToFsm.ddrDataEn) -> readAddress(29, 0), 
                                      (io.init.calib_complete && (io.ddrToFsm.ddrWeightEn || !fillCount(10).toBool)) -> weightAddress(29, 0),
                                      io.app.wdf_wren -> writeAddress(29, 0)))

    io.app.wdf_wren := io.init.calib_complete && io.ddrToFsm.ddrStoreEn && io.app.wdf_rdy

    io.app.wdf_data := io.bufferToDdr
  }
  
  io.app.cmd := MuxCase(0.U, Array((io.init.calib_complete && io.ddrToFsm.ddrDataEn) -> 1.U, 
                                   (io.init.calib_complete && (io.ddrToFsm.ddrWeightEn || !fillCount(10).toBool)) -> 1.U,
                                    io.app.wdf_wren -> 0.U)) 

  io.app.en := MuxCase(false.B, Array((io.init.calib_complete && io.ddrToFsm.ddrDataEn) -> true.B, 
                                      (io.init.calib_complete && (io.ddrToFsm.ddrWeightEn || !fillCount(10).toBool)) -> true.B,
                                       io.app.wdf_wren -> true.B))                                                  
  
  io.app.wdf_end := io.app.wdf_wren 

  io.app.wdf_mask := 0.U
  
  io.ddrToFsm.ddrComplete := (io.ddrToFsm.ddrDataEn && (infoRegs(3) === 0.U))  || 
                             (io.ddrToFsm.ddrStoreEn && (infoRegs(4) === 0.U)) || 
                             ((io.ddrToFsm.ddrWeightEn || !fillCount(10).toBool) && (ddrWeightReg === 0.U))
  
  io.ddrToFsm.infoComplete := infoEnDelay

  io.ddrToBuffer := io.app.rd_data
}
