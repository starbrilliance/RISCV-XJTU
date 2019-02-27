package yolo

import chisel3._
import chisel3.util._

import DdrParameter._
class DdrControl(simulation: Boolean = false) extends Module {
  val io = IO(new Bundle{
    // buffer data ports
    val ddrToBuffer = Output(UInt(APP_DATA_WIDTH.W))
    // ddrC <> Fsm
    val ddrToFsm = Flipped(new FsmDdrIO)
    // pad <> ddrC
    val padToDdr = Flipped(new PaddingToDdrControlIO)
    // from info
    val fromInfo = Flipped(new InfoDdrIO)
    // from store
    val fromStore = Flipped(new StoreToDdrCIO)
    // Xilinx DDR3 IP ports
    // calibration
    val init = Flipped(new HasCalibPort)
    // user ports
    val app = Flipped(new HasDdrUserIO)
    // if define sim
    val simWrite = if(simulation) Some(Flipped(new SimToDdrCIO)) else None
  })

  val infoRegs = RegInit(VecInit(Seq.fill(7)(0.U(32.W))))
  val infoEnDelay = RegNext(io.fromInfo.validOut)
  val weightFillCount = RegInit(0xC00.U(12.W))

  when(io.ddrToFsm.ddrComplete) {
    weightFillCount := 0xC00.U
  } .elsewhen(io.padToDdr.padFillHead || io.padToDdr.padFillTail) {
    weightFillCount := 0.U
  } .elsewhen(!weightFillCount(10).toBool || io.ddrToFsm.ddrWeightEn) {
    weightFillCount := weightFillCount + 1.U
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
  } .elsewhen(io.init.calib_complete && (io.ddrToFsm.ddrWeightEn || !weightFillCount(10).toBool)) {
    infoRegs(2) := infoRegs(2) + 8.U
  }

  when(io.fromInfo.validOut) {
    infoRegs(3) := io.fromInfo.dataOut(3)
  } .elsewhen(io.init.calib_complete && io.ddrToFsm.ddrBiasEn) {
    infoRegs(3) := infoRegs(3) + 8.U
  }

  when(io.fromInfo.validOut) {
    infoRegs(4) := io.fromInfo.dataOut(4)
  } .elsewhen(io.init.calib_complete && io.ddrToFsm.ddrBiasEn) {
    infoRegs(4) := Mux(infoRegs(4) < 64.U, 0.U, infoRegs(4) - 64.U)
  }

  when(io.fromInfo.validOut) {
    infoRegs(5) := io.fromInfo.dataOut(5)
  } .elsewhen(io.init.calib_complete && io.ddrToFsm.ddrDataEn) {
    infoRegs(5) := Mux(infoRegs(5) < 64.U, 0.U, infoRegs(5) - 64.U)
  }

  when(io.fromInfo.validOut) {
    infoRegs(6) := io.fromInfo.dataOut(6)
  } .elsewhen(io.app.wdf_wren) {
    infoRegs(6) := Mux(infoRegs(6) < 64.U, 0.U, infoRegs(6) - 64.U)
  }

  if(simulation) {
    io.app.addr := MuxCase(0.U, Array((io.init.calib_complete && io.ddrToFsm.ddrDataEn) -> infoRegs(1)(29, 0), 
                                      (io.init.calib_complete && (io.ddrToFsm.ddrWeightEn || !weightFillCount(10).toBool)) -> infoRegs(2)(29, 0),
                                      (io.init.calib_complete && io.ddrToFsm.ddrBiasEn) -> infoRegs(3)(29, 0), 
                                      (io.app.wdf_wren && !io.simWrite.get.simDDRwren) -> infoRegs(0)(29, 0),
                                      io.simWrite.get.simDDRwren -> io.simWrite.get.simDDRaddress))
    
    io.app.wdf_wren := io.init.calib_complete && io.app.wdf_rdy && (io.fromStore.storeValid || io.simWrite.get.simDDRwren)

    io.app.wdf_data := Mux(io.simWrite.get.simDDRwren, io.simWrite.get.simDDRdata, io.fromStore.dataOut)
  } else {
    io.app.addr := MuxCase(0.U, Array((io.init.calib_complete && io.ddrToFsm.ddrDataEn) -> infoRegs(1)(29, 0), 
                                      (io.init.calib_complete && (io.ddrToFsm.ddrWeightEn || !weightFillCount(10).toBool)) -> infoRegs(2)(29, 0),
                                      (io.init.calib_complete && io.ddrToFsm.ddrBiasEn) -> infoRegs(3)(29, 0),
                                      io.app.wdf_wren -> infoRegs(0)(29, 0)))

    io.app.wdf_wren := io.init.calib_complete && io.fromStore.storeValid && io.app.wdf_rdy

    io.app.wdf_data := io.fromStore.dataOut
  }
  
  io.app.cmd := MuxCase(0.U, Array((io.init.calib_complete && io.ddrToFsm.ddrDataEn) -> 1.U, 
                                   (io.init.calib_complete && io.ddrToFsm.ddrBiasEn) -> 1.U,
                                   (io.init.calib_complete && (io.ddrToFsm.ddrWeightEn || !weightFillCount(10).toBool)) -> 1.U,
                                    io.app.wdf_wren -> 0.U)) 

  io.app.en := io.init.calib_complete && !io.ddrToFsm.ddrComplete &&
               (io.ddrToFsm.ddrDataEn || io.ddrToFsm.ddrBiasEn ||
                io.ddrToFsm.ddrWeightEn || !weightFillCount(10).toBool ||
               io.app.wdf_wren)                                           
  
  io.app.wdf_end := io.app.wdf_wren 

  io.app.wdf_mask := 0.U
  
  io.ddrToFsm.ddrComplete := (io.ddrToFsm.ddrBiasEn && (infoRegs(4) === 0.U))  ||
                             (io.ddrToFsm.ddrDataEn && (infoRegs(5) === 0.U))  ||
                             (io.fromStore.storeValid && (infoRegs(6) === 0.U)) ||
                             (!weightFillCount(11).toBool && weightFillCount(10).toBool)
  
  io.ddrToFsm.infoComplete := infoEnDelay

  io.ddrToBuffer := io.app.rd_data
}