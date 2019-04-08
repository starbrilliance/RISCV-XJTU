package axi_yolo

import chisel3._
import chisel3.util._

class SrToPeIO extends Bundle {
  val dataout1 = Output(Vec(9, SInt(8.W))) 
  val dataout2 = Output(Vec(9, SInt(8.W))) 
}

class SrToPaddingIO extends Bundle {
  val datain = Input(Vec(64, UInt(8.W)))
  val n = Input(UInt(8.W)) // n is to show how many data will be in a row
  val wr_en =Input(Bool())
  val wide = Input(UInt(8.W))
  val shiftEnd = Output(Bool())
  val rest = Input(UInt(8.W))
  val dstart = Output(Bool()) // to confirm data is ready to receive
  val wpadValid = Input(Bool())
  val shiftStart = Output(Bool())
  val shift =Output(Bool())
  val firstLayer = Output(Bool())
  val finalLayer = Output(Bool())
  val fl_en = Input(Bool())
  val datainChannel = Input(UInt(11.W))
}

class SrBuffer extends Module {
  val io =IO(new Bundle {
    val srToPe = new SrToPeIO
    val srToPadding = new SrToPaddingIO
    val firstLayer = Input(Bool())
    val finalLayer = Input(Bool())
  })

  // regeister 
  val reg1 = Reg(Vec(226, UInt(8.W)))
  val reg2 = Reg(Vec(226, UInt(8.W)))
  val reg3 = Reg(Vec(226, UInt(8.W)))
  val reg4 = Reg(Vec(226, UInt(8.W)))
  val regLeft = Reg(Vec(3,Vec(32,UInt(8.W))))
  val regBuffer = Reg(Vec(6,Vec(226,UInt(8.W))))
  val count = Reg(UInt(8.W))
  val b = Reg(Bool())
  val shiftReg1 = Reg(Vec(226, UInt(8.W)))
  val shiftReg2 = Reg(Vec(226, UInt(8.W)))
  val shiftReg3 = Reg(Vec(226, UInt(8.W)))
  val shiftReg4 = Reg(Vec(226, UInt(8.W)))
  val flreg = Reg(Vec(112, UInt(8.W)))
  val dpadValid = RegInit(false.B)
  val shiftEnd = RegInit(false.B)
  val shift = RegInit(false.B)
  val s = RegInit(0.U(8.W))
  val i = RegInit(0.U(8.W))
  val j = RegInit(0.U(8.W))
  val flCount = RegInit(0.U(8.W))
  val sreset1 = RegInit(1.U(2.W))
  val sreset2 = RegInit(1.U(2.W))
  //output
  io.srToPe.dataout1(0) := shiftReg1(0).asSInt
  io.srToPe.dataout1(1) := shiftReg1(1).asSInt
  io.srToPe.dataout1(2) := shiftReg1(2).asSInt
  io.srToPe.dataout1(3) := shiftReg2(0).asSInt
  io.srToPe.dataout1(4) := shiftReg2(1).asSInt
  io.srToPe.dataout1(5) := shiftReg2(2).asSInt
  io.srToPe.dataout1(6) := shiftReg3(0).asSInt
  io.srToPe.dataout1(7) := shiftReg3(1).asSInt
  io.srToPe.dataout1(8) := shiftReg3(2).asSInt

  io.srToPe.dataout2(0) := shiftReg2(0).asSInt
  io.srToPe.dataout2(1) := shiftReg2(1).asSInt
  io.srToPe.dataout2(2) := shiftReg2(2).asSInt
  io.srToPe.dataout2(3) := shiftReg3(0).asSInt
  io.srToPe.dataout2(4) := shiftReg3(1).asSInt
  io.srToPe.dataout2(5) := shiftReg3(2).asSInt
  io.srToPe.dataout2(6) := shiftReg4(0).asSInt
  io.srToPe.dataout2(7) := shiftReg4(1).asSInt
  io.srToPe.dataout2(8) := shiftReg4(2).asSInt
  
  
  // input 
  val flstate0 :: flstate1 :: flstate2 :: flstate3 :: flsdpad :: Nil = Enum(5)
  val flstate = RegInit(flstate0)
  val szero :: sreset :: sinput1 :: sinput2 :: sinput3 :: sinput4 :: sdpad :: Nil = Enum(7)
  val state = RegInit(szero)
  val cwait :: cstart :: Nil = Enum(2)
  val cstate = RegInit(cwait)
  io.srToPadding.shiftEnd := shiftEnd
  io.srToPadding.dstart := (state === sreset)
  io.srToPadding.shiftStart := dpadValid && io.srToPadding.wpadValid && (cstate === cwait)
  io.srToPadding.shift := shift
  io.srToPadding.firstLayer := io.firstLayer
  io.srToPadding.finalLayer := io.finalLayer

  when(shiftEnd)
  {
    shiftEnd := false.B
  }
  switch(flstate)
  {
    is(flstate0)
    {
      when(io.srToPadding.wr_en && io.srToPadding.fl_en && io.firstLayer)
      {
        when(j === 0.U)
        { flstate := flstate1 }
        .elsewhen(j === 111.U)
        { flstate := flstate3 }
        .otherwise
        { flstate := flstate2 }
        reg1(0) := 0.U
        reg1(225) := 0.U
        reg2(0) := 0.U
        reg2(225) := 0.U
        reg3(0) := 0.U
        reg3(225) := 0.U
        reg4(0) := 0.U
        reg4(225) := 0.U
      }
    }
    is(flstate1)
    { 
      flCount := Mux(flCount === 10.U, 0.U, flCount + 1.U)
      when(flCount === 0.U)
      {
        reg1 := VecInit(Seq.fill(226)(0.U(8.W))) 
        for(k <- 0 to 63)
        { reg2(k+1) := io.srToPadding.datain(k) }
      }
      .elsewhen(flCount === 1.U)
      {
        for(k <- 0 to 63)
        { reg2(65+k) := io.srToPadding.datain(k) }
      }
      .elsewhen(flCount === 2.U)
      {
        for(k <- 0 to 63)
        { reg2(129+k) := io.srToPadding.datain(k) }
      }
      .elsewhen(flCount === 3.U)
      { 
        for(k <- 0 to 31)
        {
         reg2(193+k) := io.srToPadding.datain(k)
         reg3(1+k) := io.srToPadding.datain(k+32)
         }
      }
      .elsewhen(flCount === 4.U)
      {
        for(k <- 0 to 63)
        { reg3(33+k) := io.srToPadding.datain(k) }
      }
      .elsewhen(flCount === 5.U)
      {
        for(k <- 0 to 63)
        { reg3(97+k) := io.srToPadding.datain(k) }
      }
      .elsewhen(flCount === 6.U)
      {
        for(k <- 0 to 63)
        { reg3(161+k) := io.srToPadding.datain(k) }
      }
      .elsewhen(flCount === 7.U)
      {
        for(k <- 0 to 63)
        { reg4(1+k) := io.srToPadding.datain(k) }
      }
       .elsewhen(flCount === 8.U)
      {
        for(k <- 0 to 63)
        { reg4(65+k) := io.srToPadding.datain(k) }
      }
      .elsewhen(flCount === 9.U)
      {
        for(k <- 0 to 63)
        { reg4(129+k) := io.srToPadding.datain(k) }
      }
      .elsewhen(flCount === 10.U)
      {
        for(k <- 0 to 31)
        {
          reg4(193+k) := io.srToPadding.datain(k)
          regLeft(i)(k) := io.srToPadding.datain(k+32)
        }
       flstate := flsdpad
      }
    }
    is(flstate2)
    {
      flCount := Mux(flCount === 6.U , 0.U, flCount+1.U)
      when(flCount === 0.U)
      { 
        reg1 := regBuffer(2.U*i)
        reg2 := regBuffer(2.U*i+1.U)
        for(k<- 0 to 31)
        {reg3(k+1) := regLeft(i)(k)}
        for(k <- 0 to 63)
        {reg3(33+k) := io.srToPadding.datain(k)}
      }
      .elsewhen(flCount === 1.U)
      {
        for(k <- 0 to 63)
        {reg3(97+k) := io.srToPadding.datain(k)}
      }
      .elsewhen(flCount === 2.U)
      {
        for(k <- 0 to 63)
        {reg3(161+k) := io.srToPadding.datain(k)}
      }
      .elsewhen(flCount === 3.U)
      {
        for(k <- 0 to 63)
        {reg4(1+k) := io.srToPadding.datain(k)}
      }
       .elsewhen(flCount === 4.U)
      {
        for(k <- 0 to 63)
        {reg4(65+k) := io.srToPadding.datain(k)}
      }
      .elsewhen(flCount === 5.U)
      {
        for(k <- 0 to 63)
        {reg4(129+k) := io.srToPadding.datain(k)}
      }
      .elsewhen(flCount === 6.U)
      {
        for(k <- 0 to 31)
        {
          reg4(193+k) := io.srToPadding.datain(k)
          regLeft(i)(k) := io.srToPadding.datain(k+32)
        }
       flstate := flsdpad
      }
    }
    is(flstate3)
    {
      flCount := Mux(flCount === 2.U , 0.U, flCount+1.U)
      when(flCount === 0.U)
      { 
        reg1 := regBuffer(2.U*i)
        reg2 := regBuffer(2.U*i+1.U)
        reg4 := VecInit(Seq.fill(226)(0.U(8.W)))
        for(k <- 0 to 31)
        {reg3(k+1) := regLeft(i)(k)}
        for(k <- 0 to 63)
        {reg3(33+k) := io.srToPadding.datain(k)}
      }
      .elsewhen(flCount === 1.U)
      {
        for(k <- 0 to 63)
        {reg3(97+k) := io.srToPadding.datain(k)}
      }
      .elsewhen(flCount === 2.U)
      {
        for(k <- 0 to 63)
        {reg3(161+k) := io.srToPadding.datain(k)}
        flstate := flsdpad
      }
      
    }
    is(flsdpad)
    {
      when(!dpadValid)
      {
        shiftReg1 := reg1
        shiftReg2 := reg2
        shiftReg3 := reg3
        shiftReg4 := reg4
        regBuffer(2.U*i) := reg3
        regBuffer(2.U*i+1.U) := reg4
        dpadValid := true.B
        flstate := flstate0
        i := Mux(i === io.srToPadding.datainChannel-1.U, 0.U, i+1.U)
        when(i === io.srToPadding.datainChannel-1.U)
        {
          j := Mux(j === 111.U, 0.U, j+1.U)
        }
      }
    } 
  }

  switch(state) 
  {
    is(szero) 
    {
      when(io.srToPadding.wr_en && !io.firstLayer)  
      {
        count := 1.U
        state := sreset
      }
    }
    is(sreset) 
    {
      count := Mux(count === 3.U, 1.U, count+1.U)
      reg1(0) := 0.U
      reg1(io.srToPadding.n * 64.U + 1.U) := 0.U
      reg2(0) := 0.U
      reg2(io.srToPadding.n * 64.U + 1.U) := 0.U
      reg3(0) := 0.U
      reg3(io.srToPadding.n * 64.U + 1.U) := 0.U
      reg4(0) := 0.U
      reg4(io.srToPadding.n * 64.U + 1.U) := 0.U
      when(count === 3.U)
      {
        state := sinput1
      }
    }
    is(sinput1)
     {
      count := Mux(count === io.srToPadding.n, 1.U, count + 1.U)
      
       when(count === 1.U)
      {
        for(k <- 0 to 63)
        {reg1(k+1) := io.srToPadding.datain(k)}
      }
      .elsewhen(count === 2.U)
      {
        for(k <- 0 to 63)
        {reg1(k+65) := io.srToPadding.datain(k)}
      }
      .elsewhen(count === 3.U)
      {
        for(k <- 0 to 63)
        {reg1(k+129) := io.srToPadding.datain(k)}
      }
      .otherwise
      {
        for(k <- 0 to 31){
        reg1(193+k) := io.srToPadding.datain(k)}
      }
      when(count === io.srToPadding.n) {
        state := sinput2
        
       }
    } 

    is(sinput2) {
      count := Mux(count === io.srToPadding.n, 1.U, count + 1.U)
      
       when(count === 1.U)
      {
        for(k <- 0 to 63)
        {reg2(k+1) := io.srToPadding.datain(k)}
      }
      .elsewhen(count === 2.U)
      {
        for(k <- 0 to 63)
        {reg2(k+65) := io.srToPadding.datain(k)}
      }
      .elsewhen(count === 3.U)
      {
        for(k <- 0 to 63)
        {reg2(k+129) := io.srToPadding.datain(k)}
      }
      .otherwise
      {
        for(k <- 0 to 31){
        reg2(193+k) := io.srToPadding.datain(k)}
      }
      when(count === io.srToPadding.n) {
        state := sinput3

       }
    } 
    is(sinput3) {
      count := Mux(count === io.srToPadding.n, 1.U, count + 1.U)
      
       when(count === 1.U)
      {
        for(k <- 0 to 63)
        {reg3(k+1) := io.srToPadding.datain(k)}
      }
      .elsewhen(count === 2.U)
      {
        for(k <- 0 to 63)
        {reg3(k+65) := io.srToPadding.datain(k)}
      }
      .elsewhen(count === 3.U)
      {
        for(k <- 0 to 63)
        {reg3(k+129) := io.srToPadding.datain(k)}
      }
      .otherwise
      {
        for(k <- 0 to 31){
        reg3(193+k) := io.srToPadding.datain(k)}
      }
      when(count === io.srToPadding.n) {
        state := sinput4

       }
    }
    is(sinput4) {
      count := Mux(count === io.srToPadding.n, 1.U, count + 1.U)
      
       when(count === 1.U)
      {
        for(k <- 0 to 63)
        {reg4(k+1) := io.srToPadding.datain(k)}
      }
      .elsewhen(count === 2.U)
      {
        for(k <- 0 to 63)
        {reg4(k+65) := io.srToPadding.datain(k)}
      }
      .elsewhen(count === 3.U)
      {
        for(k <- 0 to 63)
        {reg4(k+129) := io.srToPadding.datain(k)}
      }
      .otherwise
      {
        for(k <- 0 to 31){
        reg4(193+k) := io.srToPadding.datain(k)}
      }

      when(count === io.srToPadding.n) {
        state := sdpad
      }
    }

    is(sdpad) {
      
      when(!dpadValid)
      {
        shiftReg1 := reg1
        shiftReg2 := reg2
        shiftReg3 := reg3
        shiftReg4 := reg4
        state := szero
        dpadValid := true.B
      }
    } 
  } 

    switch(cstate)
  {
      is(cwait){
        when(dpadValid && io.srToPadding.wpadValid)
        {
          cstate := cstart
          shift := true.B
          s := io.srToPadding.wide
        }
      }
      
      is(cstart)
    {
      when(s > 1.U)
      {s := s - 1.U
      for(a <- 0 until 225)
       {
        shiftReg1(a) := shiftReg1(a+1)
        shiftReg2(a) := shiftReg2(a+1)
        shiftReg3(a) := shiftReg3(a+1)
        shiftReg4(a) := shiftReg4(a+1)
       }
      }
     when(s === 1.U) 
      {
        cstate := cwait
        shift := false.B
        shiftEnd := true.B
        dpadValid := false.B
      } 
    }
  }
}