package yolo

import chisel3._
import chisel3.util._

import DdrParameter._
class DdrControl(simulation: Boolean = false) extends Module {
  val io = IO(new Bundle{
    // buffer data ports
    val ddrToBuffer = Output(UInt(APP_DATA_WIDTH.W))
    val dataBufferEn = Output(Bool())
    val weightBufferEn = Output(Bool())
    val biasBufferEn = Output(Bool())
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
    val sim = if(simulation) Some(Flipped(new SimToDdrCIO)) else None
  })

  val infoRegs = RegInit(VecInit(Seq.fill(9)(0.U(32.W))))
  val infoEnDelay1 = RegNext(io.fromInfo.validOut)
  val infoEnDelay2 = RegNext(infoEnDelay1)
  val infoEn = io.fromInfo.validOut && !infoEnDelay1
  val weightFillEn = RegInit(false.B)
  val weightFillCmd = RegInit(0x400.U(12.W))
  val weightFillData = RegInit(0x400.U(12.W))
  val cmdComplete = Wire(Bool())
  val dataCmdOver = Wire(Bool())
  val weightCmdOver = Wire(Bool())
  val biasCmdOver = Wire(Bool())
  val writeCmdOver = Wire(Bool())

  when(io.padToDdr.padFillHead || io.padToDdr.padFillTail) {
    weightFillEn := true.B
  } .elsewhen(weightFillCmd(10).toBool) {
    weightFillEn := false.B
  }
  
  when(io.padToDdr.padFillHead || io.padToDdr.padFillTail) {
    weightFillCmd := 0.U
  } .elsewhen((!weightFillCmd(10).toBool || io.ddrToFsm.readWeightEn) && io.app.rdy) {
    weightFillCmd := weightFillCmd + 1.U
  }

  when(io.padToDdr.padFillHead || io.padToDdr.padFillTail) {
    weightFillData := 0.U
  } .elsewhen((!weightFillData(10).toBool || io.ddrToFsm.readWeightEn) && io.app.rd_data_valid) {
    weightFillData := weightFillData + 1.U
  }

  when(infoEn) {
    infoRegs(0) := io.fromInfo.dataOut(0)
  } .elsewhen(io.app.wdf_wren && io.app.wdf_rdy) {
    infoRegs(0) := infoRegs(0) + 8.U
  }

  when(infoEn) {
    infoRegs(1) := io.fromInfo.dataOut(1)
  } .elsewhen(io.ddrToFsm.readDataEn && io.app.rdy && (infoRegs(6) =/= 0.U)) {
    infoRegs(1) := infoRegs(1) + 8.U
  }

  when(infoEn) {
    infoRegs(2) := io.fromInfo.dataOut(2)
  } .elsewhen(io.app.rdy && ((io.ddrToFsm.readWeightEn && !weightFillCmd(11).toBool) || !weightFillCmd(10).toBool)) {
    infoRegs(2) := infoRegs(2) + 8.U
  }

  when(infoEn) {
    infoRegs(3) := io.fromInfo.dataOut(3)
  } .elsewhen(io.ddrToFsm.readBiasEn && io.app.rdy && (infoRegs(4) =/= 0.U)) {
    infoRegs(3) := infoRegs(3) + 8.U
  }

  when(infoEn) {
    infoRegs(4) := io.fromInfo.dataOut(4)
  } .elsewhen(io.ddrToFsm.readBiasEn && io.app.rdy) {
    infoRegs(4) := Mux(infoRegs(4) < 64.U, 0.U, infoRegs(4) - 64.U)
  }

  when(infoEn) {
    infoRegs(5) := io.fromInfo.dataOut(4)
  } .elsewhen(io.ddrToFsm.readBiasEn && io.app.rd_data_valid) {
    infoRegs(5) := Mux(infoRegs(5) < 64.U, 0.U, infoRegs(5) - 64.U)
  }

  when(infoEn) {
    infoRegs(6) := io.fromInfo.dataOut(5)
  } .elsewhen(io.ddrToFsm.readDataEn && io.app.rdy) {
    infoRegs(6) := Mux(infoRegs(6) < 64.U, 0.U, infoRegs(6) - 64.U)
  }

  when(infoEn) {
    infoRegs(7) := io.fromInfo.dataOut(5)
  } .elsewhen(io.ddrToFsm.readDataEn && io.app.rd_data_valid) {
    infoRegs(7) := Mux(infoRegs(7) < 64.U, 0.U, infoRegs(7) - 64.U)
  }

  when(infoEn) {
    infoRegs(8) := io.fromInfo.dataOut(6)
  } .elsewhen(io.app.wdf_wren && io.app.wdf_rdy) {
    infoRegs(8) := Mux(infoRegs(8) < 64.U, 0.U, infoRegs(8) - 64.U)
  }

  dataCmdOver := io.ddrToFsm.readDataEn && (infoRegs(6) === 0.U)
  weightCmdOver := (io.ddrToFsm.readWeightEn && weightFillCmd(11).toBool || weightFillEn) && weightFillCmd(10).toBool
  biasCmdOver := io.ddrToFsm.readBiasEn && (infoRegs(4) === 0.U)
  writeCmdOver := io.ddrToFsm.writeDataEn && (infoRegs(8) === 0.U)
  cmdComplete := dataCmdOver || weightCmdOver || biasCmdOver || writeCmdOver

  if(simulation) {
    io.app.addr := MuxCase(0.U, Array(io.ddrToFsm.readDataEn -> infoRegs(1)(29, 0), 
                                      (io.ddrToFsm.readWeightEn || !weightFillCmd(10).toBool) -> infoRegs(2)(29, 0),
                                      io.ddrToFsm.readBiasEn -> infoRegs(3)(29, 0), 
                                      (io.app.wdf_wren && !io.sim.get.simEn) -> infoRegs(0)(29, 0),
                                      io.sim.get.simEn -> io.sim.get.addr))
    
    io.app.wdf_wren := io.init.calib_complete && (io.fromStore.storeValid || io.sim.get.wren)

    io.app.wdf_data := Mux(io.sim.get.simEn, io.sim.get.wdata, io.fromStore.dataOut)

    io.app.cmd := MuxCase(0.U, Array(io.ddrToFsm.readDataEn -> 1.U, 
                                     io.ddrToFsm.readBiasEn -> 1.U,
                                     (io.ddrToFsm.readWeightEn || !weightFillCmd(10).toBool) -> 1.U,
                                     io.app.wdf_wren -> 0.U,
                                     io.sim.get.simEn -> io.sim.get.cmd))

    io.app.en := io.sim.get.en || io.init.calib_complete && !cmdComplete &&
                 (io.ddrToFsm.readDataEn || io.ddrToFsm.readBiasEn ||
                 io.ddrToFsm.readWeightEn || !weightFillCmd(10).toBool ||
                 !io.app.rdy || io.app.wdf_rdy && io.app.wdf_wren)

    io.sim.get.rdy := io.app.rdy

    io.sim.get.wrdy := io.app.wdf_rdy

    io.sim.get.rdata := io.app.rd_data

    io.sim.get.rvalid := io.app.rd_data_valid
  } else {
    io.app.addr := MuxCase(0.U, Array(io.ddrToFsm.readDataEn -> infoRegs(1)(29, 0), 
                                      (io.ddrToFsm.readWeightEn || !weightFillCmd(10).toBool) -> infoRegs(2)(29, 0),
                                      io.ddrToFsm.readBiasEn -> infoRegs(3)(29, 0),
                                      io.app.wdf_wren -> infoRegs(0)(29, 0)))

    io.app.wdf_wren := io.init.calib_complete && io.fromStore.storeValid

    io.app.wdf_data := io.fromStore.dataOut
  
    io.app.cmd := MuxCase(0.U, Array(io.ddrToFsm.readDataEn -> 1.U, 
                                     io.ddrToFsm.readBiasEn -> 1.U,
                                     (io.ddrToFsm.readWeightEn || !weightFillCmd(10).toBool) -> 1.U,
                                     io.app.wdf_wren -> 0.U)) 

    io.app.en := io.init.calib_complete && !cmdComplete &&
                 (io.ddrToFsm.readDataEn || io.ddrToFsm.readBiasEn ||
                 io.ddrToFsm.readWeightEn || !weightFillCmd(10).toBool ||
                 !io.app.rdy || io.app.wdf_rdy && io.app.wdf_wren)                                           
  }

  io.app.wdf_end := io.app.wdf_wren 

  io.app.wdf_mask := 0.U

  io.ddrToFsm.readWeightComplete := io.ddrToFsm.readWeightEn && weightFillData(11).toBool && weightFillData(10).toBool

  io.ddrToFsm.readBiasComplete := io.ddrToFsm.readBiasEn && (infoRegs(5) === 0.U)

  io.ddrToFsm.readDataComplete := io.ddrToFsm.readDataEn && (infoRegs(7) === 0.U)

  io.ddrToFsm.writeDataComplete := writeCmdOver
  
  io.ddrToFsm.infoComplete := infoEnDelay1 && !infoEnDelay2

  io.ddrToBuffer := io.app.rd_data

  io.dataBufferEn := io.ddrToFsm.readDataEn && io.app.rd_data_valid

  io.weightBufferEn := (io.ddrToFsm.readWeightEn || weightFillEn) && io.app.rd_data_valid

  io.biasBufferEn := io.ddrToFsm.readBiasEn && io.app.rd_data_valid
}