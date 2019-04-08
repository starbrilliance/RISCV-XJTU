package  axi_yolo

import chisel3._

class RegfileToFsmIO extends Bundle {
  val firstLayer = Output(Bool())
  val finalLayer = Output(Bool())
}

class RegfileToInfoIO extends Bundle {
  val readData = Output(Vec(7, UInt(32.W)))
}

class Regfile extends Module {
  val io = IO(new Bundle {
    val decoderPorts = Flipped(new DecoderToRegsIO)
    // from dataFIFO
    val writeData = Input(UInt(32.W))
    // condition
    val realLayerEnd = Input(Bool())
    // outputs
    val toInfo = new RegfileToInfoIO
    val toFsm = new RegfileToFsmIO
  })
  
  val dataRegs = RegInit(VecInit(Seq.fill(7)(0.U(32.W))))

  when(io.decoderPorts.writeEn) {
    dataRegs(io.decoderPorts.regNum) := io.writeData
  } .elsewhen(io.realLayerEnd) {
    dataRegs(5) := dataRegs(6)(19, 0)
  }
  
  io.toInfo.readData := dataRegs
  io.toFsm.firstLayer := dataRegs(6)(25)
  io.toFsm.finalLayer := dataRegs(6)(26)
}