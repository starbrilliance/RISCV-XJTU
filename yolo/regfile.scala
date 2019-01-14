package  yolo

import chisel3._

class RegfileToFsmIO extends Bundle {
  val firstLayer = Output(Bool())
  val finalLayer = Output(Bool())
}

class RegfileToInfoIO extends Bundle {
  val readData = Output(Vec(5, UInt(32.W)))
}

class Regfile extends Module {
  val io = IO(new Bundle {
    val decoderPorts = Flipped(new DecoderToRegsIO)
    // from dataFIFO
    val writeData = Input(UInt(32.W))
    // condition
    val layerComplete = Input(Bool())
    // outputs
    val toInfo = new RegfileToInfoIO
    val toFsm = new RegfileToFsmIO
  })
  
  val dataRegs = RegInit(VecInit(Seq.fill(5)(0.U(32.W))))

  when(io.decoderPorts.writeEn) {
    dataRegs(io.decoderPorts.regNum) := io.writeData
  } .elsewhen(io.layerComplete) {
    dataRegs(3) := dataRegs(4)(18, 0)
  }
  
  io.toInfo.readData := dataRegs
  io.toFsm.firstLayer := dataRegs(4)(23)
  io.toFsm.finalLayer := dataRegs(4)(24)
}