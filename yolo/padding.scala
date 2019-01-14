package yolo

import chisel3._
import chisel3.util._
import WeightBufferParameter._



class PaddingToDdrControlIO extends Bundle{
   val padFillHead = Output(Bool())
   val padFillTail = Output(Bool())
}

class PaddingToPeIO extends Bundle{
   val weight = Output(Vec(9, SInt(8.W)))
}

class FsmToPaddingIO extends Bundle{
   val padEn = Input(Bool())
   val layerComplete = Output(Bool())
}

class WeightbuffertoPaddingIO extends Bundle{
   val addr = Output(UInt(16.W))
   val en = Output(Bool()) 
   val datain = Input(UInt(16.W))
}

class DatabufferToPaddingIO extends Bundle{
   val addr = Output(UInt(14.W))
   val en = Output(Bool()) 
   val datain = Input(Vec(64, UInt(8.W)))
}

class RegfileToPaddingIO extends Bundle {
   val readData = Input(Vec(5,UInt(32.W)))
}

class PaddingToPoolIO extends Bundle {
  val finalInputChannel = Output(Bool())
  val lineComplete = Output(Bool())
}

class Padding extends Module {
  val io =IO(new Bundle{
    val paddingToPe = new PaddingToPeIO
    val paddingToSr = Flipped(new SrToPaddingIO)
    val weightbufferToPadding = new WeightbuffertoPaddingIO
    val databufferToPadding = new DatabufferToPaddingIO 
    val fsmToPadding = new FsmToPaddingIO
    val regfileToPadding = Flipped(new RegfileToInfoIO)
    val paddingToDdrcontrol = new PaddingToDdrControlIO
    val paddingToPool = new PaddingToPoolIO
    val fsmInfo = Flipped(new FsmInfoIO)      
})
     val paddingValid = Wire(Bool())
     val weightReg1 = Reg(Vec(10, SInt(8.W))) //a regesiter to load the weight
     val weightReg2 = Reg(Vec(9, SInt(8.W)))
     val addrBias = RegInit(0.U(32.W))
     val dataAddr = RegInit(0.U(14.W))
     val datainSize = RegInit(0.U(32.W))   //datainsize to show how big the picture is
     val datainChannel = RegInit(0.U(32.W))  // to show how many input channel is
     val dataoutSize = RegInit(0.U(32.W))
     val dataoutChannel = RegInit(0.U(32.W))
     val weightSize = RegInit(0.U(32.W))
     val vitualAddr = RegInit(0.U(32.W))
     val w_f = RegInit(false.B)
     val d = RegInit(0.U(2.W))
     val e = RegInit(2.U(2.W)) 
     //val f = RegInit(false.B)
     // get info from regfile

     when(io.fsmInfo.infoEn)
     {
     datainSize:= io.regfileToPadding.readData(3)(18,11)
     datainChannel:= io.regfileToPadding.readData(3)(10,0)
     dataoutSize:= io.regfileToPadding.readData(4)(18,11)
     dataoutChannel:= io.regfileToPadding.readData(4)(10,0)
     weightSize:= io.regfileToPadding.readData(4)(21,19) 
     }
     // some register for dstate
     val reginch  = RegInit(0.U(32.W))
     val regoutch = RegInit(0.U(32.W))
     val regsize  = RegInit(0.U(32.W))
     val finalInput = RegInit(false.B)
     val lineCom = RegInit(false.B)
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
     val inChannel = RegInit(0.U(32.W))
     val outChannel = RegInit(0.U(32.W))
     val vitualAddrStart = RegInit(0.U(32.W))
     val weightAccum = Wire(UInt(32.W))
     val inSize = RegInit(0.U(32.W))
     val weightRepeat = RegInit(0.U(32.W))
     val fresh = RegInit(false.B)
     val weightEnd = RegInit(false.B)
     val weightValid = Wire(Bool())
     val addrStart = RegInit(0.U(16.W))
     val accum1 = RegInit(0.U(32.W))
     val layerCom =RegInit(false.B)
     //  some variable state machine 
     val wstate0::wstate1::wstate2::wstate3::Nil = Enum(4)
     val wstate = RegInit(wstate0) 

     val dstate0::dstate1::dstate2::dstate3::dstate4::dstate5::Nil = Enum(6)
     val dstate = RegInit(dstate0)                            
     
     // output
     io.paddingToPool.finalInputChannel := finalInput
     io.paddingToPool.lineComplete := lineCom

     when((a===datainChannel-1.U)&&io.paddingToSr.shift)
      {
        finalInput := true.B
      }
     .elsewhen(io.paddingToSr.shiftend)
      {
        finalInput := false.B
        lineCom := true.B
      }

     when(lineCom)
      {
        lineCom := false.B
      }

     layerCom := (regoutch===dataoutChannel-1.U)&&(dstate===dstate5)&&(a===datainChannel-1.U)&&io.paddingToSr.shiftend
     io.fsmToPadding.layerComplete := layerCom
     // io.paddingToSr
     io.paddingToSr.n := MuxCase(0.U,  Array((datainSize===224.U)->4.U,
                                             (datainSize===112.U)->2.U,
                                             (datainSize===56.U)->1.U,
                                             (datainSize===28.U)->1.U,
                                             (datainSize===14.U)->1.U,
                                             (datainSize===7.U)->1.U))
     io.paddingToSr.rest := MuxCase(0.U,  Array((datainSize===224.U)->0.U,
                                             (datainSize===112.U)->0.U,
                                             (datainSize===56.U)->8.U,
                                             (datainSize===28.U)->4.U,
                                             (datainSize===14.U)->2.U,
                                             (datainSize===7.U)->9.U))
     io.paddingToSr.wide := datainSize
     io.paddingToSr.shift := MuxCase(false.B,Array(((s2===3.U*io.paddingToSr.n-1.U)&&w_f) -> true.B,
                                                   ((s3===4.U*io.paddingToSr.n-1.U)&&w_f) -> true.B,
                                                   ((s5===1.U*io.paddingToSr.n-1.U)&&w_f) -> true.B))
     io.paddingToSr.datain := Mux(signal1,io.databufferToPadding.datain,VecInit(Seq.fill(64)(0.U(8.W))))
     io.paddingToSr.wr_en := Mux(io.fsmToPadding.padEn,true.B,false.B)
     io.paddingToSr.padend := Mux(wstate===wstate2,true.B,false.B)
     // io.paddingToPe
     io.paddingToPe.weight := weightReg2

     // io.weightbufferToPadding
     io.weightbufferToPadding.en := Mux(weightValid,true.B,false.B)
     io.weightbufferToPadding.addr := vitualAddr(15,0)

     
     // io.databufferToPadding
     io.databufferToPadding.en := Mux(paddingValid,true.B,false.B)
     io.databufferToPadding.addr := MuxCase(0.U,Array((dstate===dstate2) -> (s2+dataAddr),
                                                      (dstate===dstate3) -> (s3+dataAddr),
                                                      (dstate===dstate4) -> (s4+dataAddr) )) 
     // io.paddingToDdrcontrol
     when((addrStart<(addrHalf))&&(d>0.U))
      {
        io.paddingToDdrcontrol.padFillHead := true.B
        d := d-1.U
        e := 2.U
      }
     .otherwise
      {
       io.paddingToDdrcontrol.padFillHead :=false.B
      }

     when((addrStart>addrHalf)&&(e>0.U))
      {
       io.paddingToDdrcontrol.padFillTail := true.B
       e := e-1.U
       d := 2.U
      }
     .otherwise
      {
        io.paddingToDdrcontrol.padFillTail :=false.B
      }

     
     // variable using in state machine
     when(!io.fsmToPadding.padEn)
      {
        regoutch := 0.U
      }
     /*when((dstate===dstate5)&&(a===datainChannel-2.U)&&io.paddingToSr.shift)
      {
        f := true.B
      }
    */

     signal1 := Mux((dstate===dstate1)||(dstate===dstate5),false.B,true.B)   
     
     when(wstate===wstate2)
      {
       w_f:=true.B
      }
     .elsewhen(io.paddingToSr.shift)
      {
       w_f:=false.B
      } 

     /*when((c===4.U)&&io.paddingToSr.shiftend)
      {
        addrBias := Mux(addrBias===(datainChannel-1.U)*5.U,0.U,addrBias+4.U)

      }
      
     when(f&&io.paddingToSr.shiftend)
      {
        addrStart := Mux(addrStart<(addrEnd-5.U*datainChannel),addrStart+5.U*datainChannel,5.U*datainChannel-addrEnd+addrStart-1.U)
        f := false.B
      } */
     paddingValid := io.fsmToPadding.padEn&&(regoutch<dataoutChannel-1.U)
     
     // weight padding
     when((c===4.U)&&io.paddingToSr.shiftend)
     {
       inChannel:=Mux(inChannel===datainChannel-1.U,0.U,inChannel+1.U)
       when(inChannel===datainChannel-1.U)
       {
         inSize:=Mux(inSize===weightRepeat-1.U,0.U,inSize+1.U)
         when(inSize===weightRepeat-1.U)
        {
          outChannel:= Mux(outChannel===dataoutChannel-1.U,0.U,outChannel+1.U)
          when(outChannel===dataoutChannel-1.U)
          {
            fresh:=true.B
            weightEnd:=true.B
          }
        }
       }
     when(!io.fsmToPadding.padEn)
      {
       weightEnd:=false.B  
      }
     when(fresh)
      {
        fresh :=false.B 
        vitualAddrStart := vitualAddrStart+accum1
      }  
     accum1 := (datainChannel*dataoutChannel>>1)*9.U
     }
     addrStart := 9.U*(outChannel*datainChannel>>1.U)+vitualAddrStart
     vitualAddr := vitualAddrStart+addrBias+c
     weightAccum := outChannel*datainChannel+inChannel
     addrBias := Mux(weightAccum(0)===0.U,(weightAccum>>1.U)*9.U,(weightAccum>>1.U)*9.U+4.U)
     reverse := Mux(weightAccum(0)===0.U,false.B,true.B)  //reg to delay a cycle
     weightValid := io.fsmToPadding.padEn&&(!weightEnd)
     when(datainSize(0)===0.U)
      {
        weightRepeat:= datainSize>>1
      }
     .otherwise
      {
        weightRepeat:= (datainSize>>1)+1.U
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
             c := Mux(c===4.U,c,c+1.U)
             weightReg1(2.U*c) := io.weightbufferToPadding.datain(7,0).asSInt 
             weightReg1(2.U*c+1.U) := io.weightbufferToPadding.datain(15,8).asSInt
             when(c===4.U&&io.paddingToSr.shiftend)
               { 
                wstate := wstate2 
                }
            }
           
           is(wstate2)
             {
             wstate := Mux(weightValid,wstate1,wstate0)
             for(i<-0 to 8)
             {weightReg2(i) := Mux(reverse,weightReg1(i+1),weightReg1(i))}
             c := 0.U  
               
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
                 }
              }  
          }
          // val s1 =RegInit(0.U(8.W))
          // val signal1 = Reg(Bool())
          // val a =RegInit(0.U(8.W))
          // 
          is(dstate1)
           {
           s1 := Mux(s1===io.paddingToSr.n-1.U,0.U,s1+1.U)
           when(s1===io.paddingToSr.n-1.U)
             {
               dstate := dstate2  
               dataAddr := a*datainSize*io.paddingToSr.n     
              }
           
           
           }   
          

          //val s2 =RegInit(0.U(8.W))
          //io.paddingToSr.datain := Mux(siganl1,io.databufferToPadding.datain,0.U) 
          is(dstate2) 
           { 
           s2 := Mux(s2===3.U*io.paddingToSr.n-1.U,s2,s2+1.U)
          
           when((s2===(3.U*io.paddingToSr.n-1.U))&&io.paddingToSr.dstart)
            {
               s2 := 0.U
               a := Mux(a===datainChannel-1.U,0.U,a+1.U)
               dstate:=Mux(a===datainChannel-1.U,dstate3,dstate1)
               when(a===datainChannel-1.U)
                 {
                   dataAddr:=io.paddingToSr.n
                  }
            }
           }
            
          
          is(dstate3)
            {
             s3 := Mux(s3===4.U*io.paddingToSr.n-1.U,s3,s3+1.U)               
             
             when(s3===4.U*io.paddingToSr.n-2.U)
              {
               //s3 := 0.U
                 a := Mux(a===datainChannel-1.U,0.U,a+1.U)
                 when(a===datainChannel-1.U)
                  {
                   b := b+1.U
                  }
              }
             when((s3===4.U*io.paddingToSr.n-1.U)&&io.paddingToSr.dstart)
                   {
                    dataAddr := Mux( (b===((datainSize-4.U)>>1.U))&&(a===0.U),io.paddingToSr.n* (datainSize-3.U),io.paddingToSr.n+2.U*b*io.paddingToSr.n+a*datainSize*io.paddingToSr.n)
                    s3 := 0.U
                    when( ( b===((datainSize-4.U)>>1.U) ) && (a===0.U) )
                     {
                      dstate := dstate4    
                      b := 0.U 
                     }
                   } 
                                 
               }
             
             
           
           is(dstate4)
            { 
             s4 := Mux(s4===3.U*io.paddingToSr.n-1.U,0.U,s4+1.U)
             
              when(s4===3.U*io.paddingToSr.n-1.U)
               {
                dstate := dstate5 
                //dataAddr := Mux(a===datainChannel-1.U,0.U,io.paddingToSr.n* (datainSize-3.U)+(a+1.U)*datainSize*io.paddingToSr.n)
                //a := Mux(a===datainChannel,a,a+1.U) 
               }
             }

          
           is(dstate5)
            {
             s5 := Mux(s5===io.paddingToSr.n-1.U,s5,s5+1.U)
             when(s5===io.paddingToSr.n-1.U)
              {
                //a := Mux(a===datainChannel-1.U,0.U,a+1.U)
                //dataAddr := Mux(a===datainChannel,0.U,io.paddingToSr.n* (datainSize-3.U)+a*datainSize*io.paddingToSr.n)
                when(a===datainChannel-1.U)
                {
                  dstate := dstate0
                  s5 := 0.U
                  regoutch := regoutch+1.U
                }
                .elsewhen(io.paddingToSr.dstart)
                {
                  a := a+1.U
                  dstate := dstate4
                  dataAddr := dataAddr+datainSize*io.paddingToSr.n
                  s5 := 0.U
                }
              }
           
             }
  
      } 
}         







     
     
     
                                             

     

     
     


