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
  val shiftend = Output(Bool())
  val shift = Input(Bool())
  val rest = Input(UInt(8.W))
  val dstart = Output(Bool()) // to confirm data is ready to receive
  val padend = Input(Bool())
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
  val shiftend =RegInit(true.B)

  // output
  io.srToPe.dataout1(0) := reg1(0).asSInt
  io.srToPe.dataout1(1) := reg1(1).asSInt
  io.srToPe.dataout1(2) := reg1(2).asSInt
  io.srToPe.dataout1(3) := reg2(0).asSInt
  io.srToPe.dataout1(4) := reg2(1).asSInt
  io.srToPe.dataout1(5) := reg2(2).asSInt
  io.srToPe.dataout1(6) := reg3(0).asSInt
  io.srToPe.dataout1(7) := reg3(1).asSInt
  io.srToPe.dataout1(8) := reg3(2).asSInt

  io.srToPe.dataout2(0) := reg2(0).asSInt
  io.srToPe.dataout2(1) := reg2(1).asSInt
  io.srToPe.dataout2(2) := reg2(2).asSInt
  io.srToPe.dataout2(3) := reg3(0).asSInt
  io.srToPe.dataout2(4) := reg3(1).asSInt
  io.srToPe.dataout2(5) := reg3(2).asSInt
  io.srToPe.dataout2(6) := reg4(0).asSInt
  io.srToPe.dataout2(7) := reg4(1).asSInt
  io.srToPe.dataout2(8) := reg4(2).asSInt
 
  // input 
  val szero :: sreset :: sinput1 :: sinput2 :: sinput3 ::sinput4:: sshift :: Nil = Enum(7)
  val state = RegInit(szero)
  
  io.srToPadding.shiftend := shiftend
  when((count === 1.U)&&(state===sshift))
    {shiftend := true.B}
  .elsewhen(io.srToPadding.padend)
    {shiftend := false.B}

  io.srToPadding.dstart := (state === sreset)
  
  switch(state) {
    is(szero) {
      when(io.srToPadding.wr_en) {
        count := io.srToPadding.n
        state := sreset
      }
    }
    is(sreset) {
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
    is(sinput1) {
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
        state := sinput3
      }
    }
    is(sinput4) {
      
      when(count > 1.U) 
        {count := count - 1.U}
      .elsewhen((count===1.U)&&io.srToPadding.shift)
        {count := io.srToPadding.wide-1.U
        state := sshift}
      .elsewhen(count===1.U)
        {count := 0.U}
      .elsewhen((count===0.U)&&io.srToPadding.shift)
        {count := io.srToPadding.wide-1.U
        state := sshift}
      
      when(count>=1.U)
      {
        for(k <- 0 to 63) 
        {
          reg4((io.srToPadding.n - count) * 64.U + k.U + 1.U) := io.srToPadding.datain(k)
        }
      } 
      
    } 
    is(sshift) {
      count := count - 1.U
   
      for(a <- 0 until 225) {
        reg1(a) := reg1(a.U + 1.U)
        reg2(a) := reg2(a.U + 1.U)
        reg3(a) := reg3(a.U + 1.U)
        reg4(a) := reg4(a.U + 1.U)
      }
     
      when(count === 1.U) {
        state := szero
      } 
    } 
  }
}