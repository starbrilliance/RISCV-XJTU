package axi_yolo

import chisel3._

// write the data back from active/pool to databuffer 
class WriteBack extends Module {
  val io = IO(new Bundle {
    val writeStart = Input(Bool())  // previous wb unit has completed
    val writeComplete = Output(Bool())  // enable the next wb unit 
    val poolEn = Input(Bool())
    val actData = Input(Vec(448, UInt(8.W)))   // no pool
    val poolData = Input(Vec(112, UInt(8.W)))  // need pool
    val outputSize = Input(UInt(8.W)) 
    val dataOut = Output(UInt(512.W))
    val validOut = Output(Bool())  
  })

  val actDataTemp0 = Wire(Vec(4, UInt(512.W)))
  val actDataTemp1 = Wire(Vec(4, UInt(512.W)))
  val poolDataTemp = Wire(Vec(2, UInt(512.W)))
  val actOut = Wire(UInt(512.W))
  val complete = Wire(Vec(2, Bool()))
  val valid = Wire(Vec(2, Bool()))
  val writeNum = RegInit(7.U(3.W))
  val count = RegInit(VecInit(Seq.fill(2)(0.U(3.W))))
  val enable = RegInit(VecInit(Seq.fill(2)(false.B)))

  import Store._
  actDataTemp0(0) := concatenate(io.actData, 63)
  actDataTemp0(1) := concatenate(io.actData, 127, 64)
  actDataTemp0(2) := concatenate(io.actData, 191, 128)
  actDataTemp0(3) := concatenate(io.actData, 223, 192)
  actDataTemp1(0) := concatenate(io.actData, 287, 224)
  actDataTemp1(1) := concatenate(io.actData, 351, 288)
  actDataTemp1(2) := concatenate(io.actData, 415, 352)
  actDataTemp1(3) := concatenate(io.actData, 447, 416)
  poolDataTemp(0) := concatenate(io.poolData, 63)
  poolDataTemp(1) := concatenate(io.poolData, 111, 64)

  when(io.writeStart) {
    writeNum := io.outputSize(7, 6) + io.outputSize(5, 0).orR.asUInt
  }

  when(io.writeStart) {
    enable(0) := true.B
  } .elsewhen(complete(0)) {
    enable(0) := false.B
  }

  when(complete(0)) {
    count(0) := 0.U
  } .elsewhen(enable(0)) {
    count(0) := count(0) + 1.U
  }

  when(complete(0) && !io.poolEn) {
    enable(1) := true.B
  } .elsewhen(complete(1)) {
    enable(1) := false.B
  }

  when(complete(1)) {
    count(1) := 0.U
  } .elsewhen(enable(1)) {
    count(1) := count(1) + 1.U
  }

  complete(0) := (count(0) === writeNum)
  complete(1) := (count(1) === writeNum)
  valid(0) := enable(0) && !complete(0)
  valid(1) := enable(1) && !complete(1)
  actOut := Mux(enable(1), actDataTemp1(count(1)), actDataTemp0(count(0)))

  io.dataOut := Mux(io.poolEn, poolDataTemp(count(0)), actOut)
  io.writeComplete := Mux(io.poolEn, complete(0), complete(1))
  io.validOut := valid(0) || valid(1)
}
