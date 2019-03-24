package axi_yolo

import chisel3._

class InfoDdrIO extends Bundle {
  val dataOut = Output(Vec(7, UInt(32.W)))
  val validOut = Output(Bool())
}

class Information extends Module {
  val io = IO(new Bundle {
    // from regfile
    val fromRegfile = Flipped(new RegfileToInfoIO)
    // from fsm
    val fromFsm = Flipped(new FsmInfoIO)
    // to ddrC
    val toDdrC = new InfoDdrIO
    val toPadding = Flipped(new InformationToPaddingIO)
  })

  val threeMThree = Module(new ThreeMThree)
  val sixMEleven = Module(new SixMEleven)
  val seventeenMEleven = Module(new SeventeenMEleven)
  val eightMEight = VecInit(Seq.fill(2)(Module(new EightMEight).io))
  val sixteenMEleven = VecInit(Seq.fill(2)(Module(new SixteenMEleven).io))
  val validDelay = Reg(Vec(3, Bool()))
  val twentyeightMFour = Module(new TwentyeightMFour)

  // 3 * 3
  threeMThree.io.CLK := clock
  threeMThree.io.A := io.fromRegfile.readData(6)(21, 19)
  threeMThree.io.B := io.fromRegfile.readData(6)(21, 19)
  threeMThree.io.CE := io.fromFsm.infoEn
  validDelay(0) := io.fromFsm.infoEn

  // 6 * 11
  sixMEleven.io.CLK := clock
  sixMEleven.io.A := threeMThree.io.P
  sixMEleven.io.B := io.fromRegfile.readData(6)(10, 0)
  sixMEleven.io.CE := validDelay(0)
  validDelay(1) := validDelay(0)

  // 17 * 11
  seventeenMEleven.io.CLK := clock
  seventeenMEleven.io.A := sixMEleven.io.P
  seventeenMEleven.io.B := io.fromRegfile.readData(5)(10, 0)
  seventeenMEleven.io.CE := validDelay(1)
  validDelay(2) := validDelay(1)

  // 8 * 8
  eightMEight(0).CLK := clock
  eightMEight(0).A := io.fromRegfile.readData(5)(18, 11)
  eightMEight(0).B := io.fromRegfile.readData(5)(18, 11)
  eightMEight(0).CE := io.fromFsm.infoEn

  eightMEight(1).CLK := clock
  eightMEight(1).A := io.fromRegfile.readData(6)(18, 11)
  eightMEight(1).B := io.fromRegfile.readData(6)(18, 11)
  eightMEight(1).CE := io.fromFsm.infoEn
  
  // 16 * 11
  sixteenMEleven(0).CLK := clock
  sixteenMEleven(0).A := eightMEight(0).P
  sixteenMEleven(0).B := io.fromRegfile.readData(5)(10, 0)
  sixteenMEleven(0).CE := validDelay(0)

  sixteenMEleven(1).CLK := clock
  sixteenMEleven(1).A := eightMEight(1).P
  sixteenMEleven(1).B := io.fromRegfile.readData(6)(10, 0)
  sixteenMEleven(1).CE := validDelay(0)
  
  // outputs
  io.toDdrC.validOut := validDelay(2)
  io.toDdrC.dataOut(0) := io.fromRegfile.readData(0)
  io.toDdrC.dataOut(1) := io.fromRegfile.readData(1)
  io.toDdrC.dataOut(2) := io.fromRegfile.readData(2)
  io.toDdrC.dataOut(3) := io.fromRegfile.readData(3)
  io.toDdrC.dataOut(4) := io.fromRegfile.readData(4)
  io.toDdrC.dataOut(5) := sixteenMEleven(0).P
  io.toDdrC.dataOut(6) := sixteenMEleven(1).P
  
  //pp change 
  io.toPadding.readData(0) := io.fromRegfile.readData(5)
  io.toPadding.readData(1) := io.fromRegfile.readData(6)
  io.toPadding.readData(2) := seventeenMEleven.io.P(27,1)
  io.toPadding.valid := validDelay(2)
}