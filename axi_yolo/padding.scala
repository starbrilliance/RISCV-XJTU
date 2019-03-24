package axi_yolo

import chisel3._
import chisel3.util._
import WeightBufferParameter._


class InformationToPaddingIO extends Bundle {
  val readData = Input(Vec(3,UInt(32.W)))
  val valid = Input(Bool())
} 

class PaddingToDdrControlIO extends Bundle { 
  val padFillHead = Output(Bool())
  val padFillTail = Output(Bool())
}

class PaddingToPeIO extends Bundle {
  val weight = Output(Vec(9, SInt(8.W)))
  val finalInputChannel = Output(Bool())
  val lineComplete = Output(Bool())
}

class FsmToPaddingIO extends Bundle {
  val padEn = Input(Bool())
  val layerComplete = Output(Bool())
}

class WeightbuffertoPaddingIO extends Bundle {
  val addr = Output(UInt(16.W))
  val en = Output(Bool()) 
  val datain = Input(UInt(16.W))
}

class DatabufferToPaddingIO extends Bundle {
  val addr = Output(UInt(14.W))
  val en = Output(Bool()) 
  val datain = Input(Vec(64, UInt(8.W)))
}

class Padding extends Module {
  val io =IO(new Bundle {
    val paddingToPe = new PaddingToPeIO
    val paddingToSr = Flipped(new SrToPaddingIO)
    val weightbufferToPadding = new WeightbuffertoPaddingIO
    val databufferToPadding = new DatabufferToPaddingIO      
    val fsmToPadding = new FsmToPaddingIO
    val informationToPadding = new InformationToPaddingIO
    val paddingToDdrcontrol = new PaddingToDdrControlIO
    val outputChannelEnd =Output(Bool())      
  })
     val mul11 = Module(new ElevenMEleven)
     val mul214 = VecInit(Seq.fill(2)(Module(new TwentyoneMFour).io)) 
     val paddingValid = Wire(Bool())
     val weightReg1 = Reg(Vec(10, SInt(8.W))) //a regesiter to load the weight
     val weightReg2 = Reg(Vec(9, SInt(8.W)))
     val addrBias = RegInit(0.U(32.W))
     val addrBiasCurrent = RegInit(0.U(32.W))
     val dataAddr = RegInit(0.U(14.W))
     val datainSize = RegInit(0.U(32.W))   //datainsize to show how big the picture is
     val datainChannel = RegInit(0.U(11.W))  // to show how many input channel is
     val dataoutSize = RegInit(0.U(32.W))
     val dataoutChannel = RegInit(0.U(11.W))
     val weightSize = RegInit(0.U(32.W))
     val vitualAddr = RegInit(0.U(32.W))
     val w_f = RegInit(false.B)
     val d = RegInit(false.B)
     val e = RegInit(true.B)
     val delay =Reg(Vec(2,Bool()))
     val outputChannelEnd = RegInit(false.B)
     val outputChannelStart = RegInit(false.B)
     //val f = RegInit(false.B)
     // get info from regfile
     // some register for dstate
     val reginch  = RegInit(0.U(32.W))
     val regoutch = RegInit(0.U(32.W))
     val regsize  = RegInit(0.U(32.W))
     val finalInput = RegInit(false.B)
     val s1 = RegInit(0.U(8.W))
     val s2 = RegInit(0.U(8.W))
     val s3 = RegInit(0.U(8.W))
     val s4 = RegInit(0.U(8.W))
     val s5 = RegInit(0.U(8.W)) 
     val signal1 = Wire(Bool())
     val a = RegInit(0.U(8.W))
     val b = RegInit(0.U(8.W))
     val c = RegInit(0.U(8.W))    
     val reverse = RegInit(false.B)
     val reverseCurrent = RegInit(false.B)
     val inChannel = RegInit(0.U(11.W))
     val outChannel = RegInit(0.U(11.W))
     val vitualAddrStart = RegInit(0.U(32.W))
     val vitualAddrStartCurrent = RegInit(0.U(32.W))
     val weightAccum = Wire(UInt(32.W))
     val inSize = RegInit(0.U(32.W))
     val weightRepeat = RegInit(0.U(32.W))
     val fresh = RegInit(false.B)
     val weightEnd = RegInit(false.B)
     val weightValid = Wire(Bool())
     val addrStart = RegInit(0.U(16.W))
     val accum1 = RegInit(0.U(32.W))
     val layerfinal = RegInit(false.B)
     val wpadValid = RegInit(false.B)

     when(io.informationToPadding.valid)
     {
     datainSize := io.informationToPadding.readData(0)(18,11)
     datainChannel := io.informationToPadding.readData(0)(10,0)
     dataoutSize := io.informationToPadding.readData(1)(18,11)
     dataoutChannel := io.informationToPadding.readData(1)(10,0)
     weightSize := io.informationToPadding.readData(1)(21,19)
     accum1 := io.informationToPadding.readData(2)
     }
     
     //  some variable state machine 
     val wstate0::wstate1::wstate2::wstate3::Nil = Enum(4)
     val wstate = RegInit(wstate0) 

     val dstate0::dstate1::dstate2::dstate3::dstate4::dstate5::Nil = Enum(6)
     val dstate = RegInit(dstate0)                            
     
     // output
     io.paddingToPe.finalInputChannel := finalInput
     io.paddingToPe.lineComplete := io.paddingToSr.shiftEnd
     io.outputChannelEnd := outputChannelStart&&io.paddingToSr.shiftEnd
     io.fsmToPadding.layerComplete := layerfinal&&io.paddingToSr.shiftEnd
     when((dstate === dstate0) && (a === datainChannel - 1.U) && io.paddingToSr.shiftStart)
      {
        outputChannelStart := true.B
      }
       when(outputChannelStart && io.paddingToSr.shiftEnd)
      {
        outputChannelStart := false.B
      }
     when(dstate === dstate3)
      {
        when((a === 0.U) && io.paddingToSr.shiftStart)
         {
           finalInput := true.B
           }
      }
      .elsewhen((a === datainChannel - 1.U) && io.paddingToSr.shiftStart)
      {
        finalInput := true.B
      }
      when(finalInput && io.paddingToSr.shiftEnd)
      {
        finalInput := false.B
      }
      when((regoutch === dataoutChannel) && io.paddingToSr.shiftStart)
      {
        layerfinal := true.B
      }
      when(layerfinal && io.paddingToSr.shiftEnd)
      {
        layerfinal := false.B
      }
     when(io.paddingToSr.shiftEnd)
     {
       wpadValid := false.B
     }
     // io.paddingToSr
     io.paddingToSr.n := MuxCase(0.U,  Array((datainSize === 224.U) -> 4.U,
                                             (datainSize === 112.U) -> 2.U,
                                             (datainSize === 56.U) -> 1.U,
                                             (datainSize === 28.U) -> 1.U,
                                             (datainSize === 14.U) -> 1.U,
                                             (datainSize === 7.U) -> 1.U))
     io.paddingToSr.rest := MuxCase(0.U,  Array((datainSize === 224.U) -> 0.U,
                                             (datainSize === 112.U) -> 0.U,
                                             (datainSize === 56.U) -> 8.U,
                                             (datainSize === 28.U) -> 4.U,
                                             (datainSize === 14.U) -> 2.U,
                                             (datainSize === 7.U) -> 9.U))
     io.paddingToSr.wide := datainSize
     /*io.paddingToSr.shift := MuxCase(false.B,Array(((s2===3.U*io.paddingToSr.n-1.U)&&w_f) -> true.B,
                                                   ((s3===4.U*io.paddingToSr.n-1.U)&&w_f) -> true.B,
                                                   ((s5===1.U*io.paddingToSr.n-1.U)&&w_f) -> true.B))
    */
     io.paddingToSr.datain := Mux(signal1, io.databufferToPadding.datain, VecInit(Seq.fill(64)(0.U(8.W))))
     io.paddingToSr.wr_en := paddingValid
     io.paddingToSr.wpadValid := wpadValid
     // io.paddingToPe
     io.paddingToPe.weight := weightReg2

     // io.weightbufferToPadding
     io.weightbufferToPadding.en := weightValid
     io.weightbufferToPadding.addr := vitualAddr(15,0)

     
     // io.databufferToPadding
     io.databufferToPadding.en := paddingValid
     io.databufferToPadding.addr := MuxCase(0.U,Array((dstate===dstate2) -> (s2+dataAddr),
                                                      (dstate===dstate3) -> (s3+dataAddr),
                                                      (dstate===dstate4) -> (s4+dataAddr) )) 
     // io.paddingToDdrcontrol

     when((addrStart < addrHalf) && d)
      {
        io.paddingToDdrcontrol.padFillTail := true.B
        d := ~d
        e := ~e
      }
     .otherwise
      {
       io.paddingToDdrcontrol.padFillTail :=false.B
      }

     when((addrStart > addrHalf) && e)
      {
       io.paddingToDdrcontrol.padFillHead := true.B
       e := ~e
       d := ~d
      }
     .otherwise
      {
        io.paddingToDdrcontrol.padFillHead :=false.B
      }

     
     // variable using in state machine
     when(!io.fsmToPadding.padEn)
      {
        regoutch := 0.U
      }

     signal1 := Mux((dstate === dstate1) || (dstate === dstate5), false.B, true.B)   
     paddingValid := io.fsmToPadding.padEn && (regoutch <= (dataoutChannel - 1.U))
     
     // weight padding
     when(c === 1.U)
     {
       inChannel := Mux(inChannel === datainChannel - 1.U, 0.U, inChannel + 1.U)
       when(inChannel === datainChannel - 1.U)
       {
         inSize := Mux(inSize === weightRepeat - 1.U, 0.U, inSize + 1.U)
         when(inSize === weightRepeat - 1.U)
        {
          outChannel := Mux(outChannel === dataoutChannel - 1.U, 0.U, outChannel + 1.U)
          when(outChannel === dataoutChannel - 1.U)
          {
            fresh := true.B
            weightEnd := true.B
          }
        }
       }
     when(!io.fsmToPadding.padEn)
      {
       weightEnd := false.B  
      }
     when(fresh)
      {
        fresh := false.B 
        vitualAddrStart := vitualAddrStart+accum1
      }  
     //accum1 := (datainChannel*dataoutChannel>>1)*9.U
     }

     // make some information
     mul11.io.A := outChannel
     mul11.io.B := datainChannel
     mul11.io.CE := c===1.U
     mul11.io.CLK := clock
     delay(0) := mul11.io.CE

     mul214(0).A := mul11.io.P(21,1)
     mul214(0).B := 9.U
     mul214(0).CE := delay(0)
     mul214(0).CLK := clock
     delay(1) := delay(0)

     mul214(1).A := weightAccum(21,1)
     mul214(1).B := 9.U
     mul214(1).CE :=delay(1)
     mul214(1).CLK :=clock
     vitualAddr := vitualAddrStartCurrent+addrBiasCurrent+c
    
     addrStart := mul214(0).P+vitualAddrStart
     weightAccum := mul11.io.P+inChannel
     addrBias := Mux(weightAccum(0)===0.U,mul214(1).P,mul214(1).P+4.U)
     reverse := weightAccum(0)=/=0.U 
     weightValid := io.fsmToPadding.padEn&&(!weightEnd)
     when(datainSize(0) === 0.U)
      {
        weightRepeat := datainSize >> 1
      }
     .otherwise
      {
        weightRepeat := (datainSize >> 1) + 1.U
      }
  
     // weight state

      switch(wstate)
      {
          is(wstate0)
            {
            
             //io.weightbufferToPadding.en := true.B
             when(weightValid)
               {
                wstate := wstate1
                }

            }


          is(wstate1)
            {
             
             //weightAddr := Mux(c===8.U,weightAddr,weightAddr+1.U)
             c := Mux(c === 4.U, c, c + 1.U)
             weightReg1(2.U * c) := io.weightbufferToPadding.datain(7,0).asSInt 
             weightReg1(2.U * c + 1.U) := io.weightbufferToPadding.datain(15,8).asSInt

             when((c === 4.U) && !wpadValid)
              { 
                wstate := wstate2 
              }
            }
           
           is(wstate2)
             {
             wstate := Mux(weightValid, wstate1, wstate0)
             for(i <- 0 to 8)
             { weightReg2(i) := Mux(reverseCurrent, weightReg1(i+1), weightReg1(i))}
             c := 0.U  
             addrBiasCurrent := addrBias
             reverseCurrent := reverse
             vitualAddrStartCurrent := vitualAddrStart
             wpadValid := true.B
             }

           
      }

     

      // data state

                                   
     switch(dstate)
   {
         
          is(dstate0)
          {
           when(paddingValid)
             {  
                
                when(io.paddingToSr.dstart)
                {
                 dstate := dstate1
                 a := 0.U
                 }
              }  
          }
          // val s1 =RegInit(0.U(8.W))
          // val signal1 = Reg(Bool())
          // val a =RegInit(0.U(8.W))
          // 
          is(dstate1)
           {
           s1 := Mux(s1 === io.paddingToSr.n - 1.U, 0.U, s1 + 1.U)
           when(s1 === io.paddingToSr.n - 1.U)
             {
               dstate := dstate2  
               dataAddr := a*datainSize*io.paddingToSr.n     
              }
           
           
           }   
          

          //val s2 =RegInit(0.U(8.W))
          //io.paddingToSr.datain := Mux(siganl1,io.databufferToPadding.datain,0.U) 
          is(dstate2) 
           { 
           s2 := Mux(s2 === 3.U * io.paddingToSr.n - 1.U, s2, s2 + 1.U)
          
           when((s2 === (3.U * io.paddingToSr.n - 1.U)) && io.paddingToSr.dstart)
            {
               s2 := 0.U
               a := Mux(a === datainChannel - 1.U, 0.U, a + 1.U)
               dstate := Mux(a === datainChannel - 1.U, dstate3, dstate1)
               when(a === datainChannel - 1.U)
                 {
                   dataAddr := io.paddingToSr.n
                  }
            }
           }
            
          
          is(dstate3)
            {
             s3 := Mux(s3 === 4.U * io.paddingToSr.n - 1.U, s3, s3 + 1.U)               
             
             when(s3 === 4.U * io.paddingToSr.n - 2.U)
              {
               //s3 := 0.U
                 a := Mux(a === datainChannel - 1.U, 0.U, a + 1.U)
                 when(a === datainChannel - 1.U)
                  {
                   b := b + 1.U
                  }
              }
             when((s3 === 4.U * io.paddingToSr.n - 1.U) && io.paddingToSr.dstart)
                   {
                    dataAddr := Mux((b === ((datainSize - 4.U) >> 1.U)) && (a === 0.U), io.paddingToSr.n * (datainSize - 3.U), io.paddingToSr.n + 2.U * b * io.paddingToSr.n + a * datainSize * io.paddingToSr.n)
                    s3 := 0.U
                    when((b === ((datainSize - 4.U) >> 1.U)) && (a === 0.U))
                     {
                      dstate := dstate4    
                      b := 0.U 
                     }
                   } 
             }
           is(dstate4)
            { 
             s4 := Mux(s4 === 3.U * io.paddingToSr.n - 1.U, 0.U, s4 + 1.U)
             
              when(s4 === 3.U * io.paddingToSr.n - 1.U)
               {
                dstate := dstate5 
                //dataAddr := Mux(a===datainChannel-1.U,0.U,io.paddingToSr.n* (datainSize-3.U)+(a+1.U)*datainSize*io.paddingToSr.n)
                //a := Mux(a===datainChannel,a,a+1.U) 
               }
             }

          
           is(dstate5)
            {
             s5 := Mux(s5 === io.paddingToSr.n - 1.U, s5, s5 + 1.U)
             when(s5 === io.paddingToSr.n - 1.U)
              {
                //a := Mux(a===datainChannel-1.U,0.U,a+1.U)
                //dataAddr := Mux(a===datainChannel,0.U,io.paddingToSr.n* (datainSize-3.U)+a*datainSize*io.paddingToSr.n)
                when(a === datainChannel - 1.U)
                {
                  dstate := dstate0
                  s5 := 0.U
                  regoutch := regoutch + 1.U
                }
                .elsewhen(io.paddingToSr.dstart)
                {
                  a := a + 1.U
                  dstate := dstate4
                  dataAddr := dataAddr + datainSize * io.paddingToSr.n
                  s5 := 0.U
                }
              }
           
             }
  
      } 
}         







     
     
     
                                             

     

     
     


