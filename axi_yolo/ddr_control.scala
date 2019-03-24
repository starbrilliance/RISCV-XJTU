package axi_yolo

import chisel3._
import chisel3.util._

import AXI4Parameters._

class DdrControl extends Module {
  val io = IO(new Bundle {
    // buffer data ports
    val ddrToBuffer = Output(UInt(C_S_AXI_DATA_WIDTH.W))
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
    // user ports
    val control = Flipped(new AXI4CMDIO)   // user cmd ports
    val wrdata = Flipped(new AXI4WriteIO)  // user write data ports 
    val rddata = Flipped(new AXI4ReadIO)   // user read data ports
  })

  val infoRegs = RegInit(VecInit(Seq.fill(7)(0.U(32.W))))
  val infoEnDelay1 = RegNext(io.fromInfo.validOut)
  val infoEnDelay2 = RegNext(infoEnDelay1)
  val infoEn = io.fromInfo.validOut && !infoEnDelay1
  val weightFillEn = RegInit(false.B)
  val weightFillCount = RegInit(0.U(12.W))
  val cmdEnFlag = RegInit(false.B)
  val transCount = RegInit(0.U(8.W))

  when(RegNext(io.control.cmd_ack)) {
    cmdEnFlag := true.B
  } .elsewhen(RegNext(io.wrdata.cmptd || io.rddata.cmptd)) {
    cmdEnFlag := false.B
  }

  when(io.wrdata.vld && io.wrdata.rdy || io.rddata.vld) {
    transCount := transCount + 1.U
  }

  when(io.padToDdr.padFillHead || io.padToDdr.padFillTail) {
    weightFillEn := true.B
  } .elsewhen(weightFillCount(10).toBool) {
    weightFillEn := false.B
  }

  when(io.padToDdr.padFillHead || io.padToDdr.padFillTail) {
    weightFillCount := 0.U
  } .elsewhen((weightFillEn || io.ddrToFsm.readWeightEn) && io.rddata.vld) {
    weightFillCount := weightFillCount + 1.U
  }

  when(infoEn) {
    infoRegs(0) := io.fromInfo.dataOut(0)
  } .elsewhen(io.wrdata.cmptd) {
    infoRegs(0) := Cat(infoRegs(0)(31, 14) + 1.U, infoRegs(0)(13, 0)) 
  }

  when(infoEn) {
    infoRegs(1) := io.fromInfo.dataOut(1)
  } .elsewhen(io.ddrToFsm.readDataEn && io.rddata.cmptd) {
    infoRegs(1) := Cat(infoRegs(1)(31, 14) + 1.U, infoRegs(1)(13, 0)) 
  }

  when(infoEn) {
    infoRegs(2) := io.fromInfo.dataOut(2)
  } .elsewhen(io.ddrToFsm.readWeightEn && io.rddata.cmptd) {
    infoRegs(2) := Cat(infoRegs(2)(31, 14) + 1.U, infoRegs(2)(13, 0))
  }

  when(infoEn) {
    infoRegs(3) := io.fromInfo.dataOut(3)
  } .elsewhen(io.ddrToFsm.readBiasEn && io.rddata.cmptd) {
    infoRegs(3) := Cat(infoRegs(3)(31, 14) + 1.U, infoRegs(3)(13, 0))
  }

  when(infoEn) {
    infoRegs(4) := io.fromInfo.dataOut(4)
  } .elsewhen(io.ddrToFsm.readBiasEn && io.rddata.vld) {
    infoRegs(4) := Mux(infoRegs(4) < 64.U, 0.U, infoRegs(4) - 64.U)
  }

  when(infoEn) {
    infoRegs(5) := io.fromInfo.dataOut(5)
  } .elsewhen(io.ddrToFsm.readDataEn && io.rddata.vld) {
    infoRegs(5) := Mux(infoRegs(5) < 64.U, 0.U, infoRegs(5) - 64.U)
  }

  when(infoEn) {
    infoRegs(6) := io.fromInfo.dataOut(6)
  } .elsewhen(io.fromStore.storeValid && io.wrdata.rdy) {
    infoRegs(6) := Mux(infoRegs(6) < 64.U, 0.U, infoRegs(6) - 64.U)
  }

  io.control.cmd_en := (io.ddrToFsm.readDataEn || io.ddrToFsm.readBiasEn ||
                        io.ddrToFsm.readWeightEn || io.ddrToFsm.writeDataEn ||
                        weightFillEn) && !cmdEnFlag 

  io.control.cmd := Mux(io.ddrToFsm.writeDataEn, "b100".U, 0.U)

  io.control.addr := MuxCase(0.U, Array(io.ddrToFsm.writeDataEn -> infoRegs(0),
                                        io.ddrToFsm.readDataEn -> infoRegs(1), 
                                        (io.ddrToFsm.readWeightEn || weightFillEn) -> infoRegs(2),
                                        io.ddrToFsm.readBiasEn -> infoRegs(3)))

  io.wrdata.vld := Mux(infoRegs(6) === 0.U, io.ddrToFsm.writeDataEn && (transCount =/= 0.U), io.fromStore.storeValid)

  io.wrdata.data := io.fromStore.dataOut 

  io.wrdata.bvld := Mux(infoRegs(6) === 0.U, 0.U, "hffff_ffff_ffff_ffff".U)

  io.control.blen := 255.U

  io.wrdata.cmptd := (transCount === 255.U) && io.wrdata.rdy && io.wrdata.vld

  io.rddata.rdy := cmdEnFlag

  io.ddrToFsm.readWeightComplete := io.ddrToFsm.readWeightEn && weightFillCount(11).toBool && (transCount === 0.U)

  io.ddrToFsm.readBiasComplete := io.ddrToFsm.readBiasEn && (infoRegs(4) === 0.U) && (transCount === 0.U)

  io.ddrToFsm.readDataComplete := io.ddrToFsm.readDataEn && (infoRegs(5) === 0.U) && (transCount === 0.U)

  io.ddrToFsm.writeDataComplete := io.ddrToFsm.writeDataEn && (infoRegs(6) === 0.U) && (transCount === 0.U)
  
  io.ddrToFsm.infoComplete := infoEnDelay1 && !infoEnDelay2

  io.ddrToBuffer := io.rddata.data

  io.dataBufferEn := io.ddrToFsm.readDataEn && io.rddata.vld && (infoRegs(5) =/= 0.U)

  io.weightBufferEn := (io.ddrToFsm.readWeightEn && !weightFillCount(11).toBool || 
                        weightFillEn && !weightFillCount(10).toBool) && io.rddata.vld

  io.biasBufferEn := io.ddrToFsm.readBiasEn && io.rddata.vld && (infoRegs(4) =/= 0.U)
}