package yolo

import chisel3._
import chisel3.util._

class SrToPeIO extends Bundle{
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
}

class SrBuffer extends Module{
  val io =IO(new Bundle {
    val srToPe = new SrToPeIO
    val srToPadding = new SrToPaddingIO
  })

  // regeister 
  val reg1 = Reg(Vec(226, UInt(8.W)))
  val reg2 = Reg(Vec(226, UInt(8.W)))
  val reg3 = Reg(Vec(226, UInt(8.W)))
  val reg4 = Reg(Vec(226, UInt(8.W)))
  val count = Reg(UInt(8.W))
  val b = Reg(Bool())
  val shiftReg1 = Reg(Vec(226, UInt(8.W)))
  val shiftReg2 = Reg(Vec(226, UInt(8.W)))
  val shiftReg3 = Reg(Vec(226, UInt(8.W)))
  val shiftReg4 = Reg(Vec(226, UInt(8.W)))
  val dpadValid = RegInit(false.B)
  val shiftEnd = RegInit(false.B)
  val shiftStart = RegInit(false.B)
  val shift = RegInit(false.B)
  val s = RegInit(0.U(8.W))
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
  val szero :: sreset :: sinput1 :: sinput2 :: sinput3 ::sinput4:: sdpad::Nil = Enum(7)
  val state = RegInit(szero)
  val cwait :: cstart :: Nil =Enum(2)
  val cstate = RegInit(cwait)
  io.srToPadding.shiftEnd := shiftEnd
  io.srToPadding.dstart := (state === sreset)
  io.srToPadding.shiftStart := shiftStart
  io.srToPadding.shift := shift
  when(shiftStart)
  {
    shiftStart := false.B
  }
  when(shiftEnd)
  {
    shiftEnd := false.B
    dpadValid := false.B
  }
  switch(state) 
{
    is(szero) 
    {
      when(io.srToPadding.wr_en) {
        count := io.srToPadding.n
        state := sreset
      }
    }
    is(sreset) 
    {
      reg1(0) := 0.U
      reg1(count * 64.U + 1.U) := 0.U
      reg2(0) := 0.U
      reg2(count * 64.U + 1.U) := 0.U
      reg3(0) := 0.U
      reg3(count * 64.U + 1.U) := 0.U
      reg4(0) := 0.U
      reg4(count * 64.U + 1.U) := 0.U
      state := sinput1
    }
    is(sinput1)
     {
      count := Mux(count === 1.U, io.srToPadding.n, count - 1.U)
     // when (count>1.U)
      //{
      for (k <- 0 to 63) {
        reg1((io.srToPadding.n - count) * 64.U + k.U + 1.U) := io.srToPadding.datain(k)
       }
     //}
      /*.otherwise
      {
       for{ k<-0 to 15
                if(k.U>=)
      }
      */
      when(count === 1.U) {
        state := sinput2
       }
    } 
    is(sinput2) {
      count := Mux(count === 1.U, io.srToPadding.n, count - 1.U)
      
      for(k <- 0 to 63) {
        reg2((io.srToPadding.n - count) * 64.U + k.U + 1.U) := io.srToPadding.datain(k)
      }

      when(count === 1.U) {
        state := sinput3
      }
    } 
    is(sinput3) {
      count := Mux(count === 1.U, io.srToPadding.n, count - 1.U)
      
      for(k <- 0 to 63) {
        reg3((io.srToPadding.n - count) * 64.U + k.U + 1.U) := io.srToPadding.datain(k)
      }

      when(count === 1.U) {
        state := sinput4
      }
    }
    is(sinput4) {
      count := Mux(count === 1.U, io.srToPadding.n, count - 1.U)
      
      for(k <- 0 to 63) {
        reg4((io.srToPadding.n - count) * 64.U + k.U + 1.U) := io.srToPadding.datain(k)
      }

      when(count === 1.U) {
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
        when(dpadValid&&io.srToPadding.wpadValid)
        {
          cstate := cstart
          shift := 1.U
          s := io.srToPadding.wide
          shiftStart := true.B
        }
      }
      
      is(cstart)
    {
      s := s - 1.U
      for(a <- 0 until 225)
       {
        shiftReg1(a) := shiftReg1(a.U + 1.U)
        shiftReg2(a) := shiftReg2(a.U + 1.U)
        shiftReg3(a) := shiftReg3(a.U + 1.U)
        shiftReg4(a) := shiftReg4(a.U + 1.U)
       }
     when(s === 1.U) 
      {
        cstate := cwait
        shift := 0.U
        shiftEnd := 1.U
      } 
    }
  }



}