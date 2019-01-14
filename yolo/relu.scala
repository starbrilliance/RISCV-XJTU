package yolo

import chisel3._

class ReLuIO(n: Int) extends Bundle {
  val dataIn = Input(Vec(n, SInt(8.W)))
  val validIn = Input(Bool())
  val dataOut = Output(Vec(n, UInt(8.W)))
  val validOut = Output(Bool())
  override def cloneType = (new ReLuIO(n)).asInstanceOf[this.type]
}

class ReLu(n: Int) extends Module {
  val io = IO(new ReLuIO(n))
  val validDelay = RegNext(io.validIn)

  for(i <- 0 until n) {
    io.dataOut(i) := Mux((io.validIn && !io.dataIn(i)(7).toBool), io.dataIn(i).asUInt, 0.U)
  }
  
  io.validOut := validDelay
}
