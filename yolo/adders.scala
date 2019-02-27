package yolo

import chisel3._
import chisel3.experimental._

// all adders delay 2 clock
// 16bits + 16bits SInt
class SIntSixteenASixteen extends BlackBox {
  val io = IO(new Bundle {
    val A = Input(SInt(16.W))  // input wire [15 : 0] A
    val B = Input(SInt(16.W))  // input wire [15 : 0] B
    val CLK = Input(Clock())   // input wire CLK
    val CE = Input(Bool())     // input wire CE
    val S = Output(SInt(17.W)) // output wire [16 : 0] S  
  })
}

// 17bits + 17bits SInt
class SIntSeventeenASeventeen extends BlackBox {
  val io = IO(new Bundle {
    val A = Input(SInt(17.W))   // input wire [16 : 0] A
    val B = Input(SInt(17.W))   // input wire [16 : 0] B
    val CLK = Input(Clock())    // input wire CLK
    val CE = Input(Bool())      // input wire CE
    val S = Output(SInt(18.W))  // output wire [17 : 0] S  
  })
}

// 18bits + 18bits SInt
class SIntEighteenAEighteen extends BlackBox {
  val io = IO(new Bundle {
    val A = Input(SInt(18.W))   // input wire [17 : 0] A
    val B = Input(SInt(18.W))   // input wire [17 : 0] B
    val CLK = Input(Clock())    // input wire CLK
    val CE = Input(Bool())      // input wire CE
    val S = Output(SInt(19.W))  // output wire [18 : 0] S  
  })
}

// 19bits + 16bits SInt
class SIntNineteenASixteen extends BlackBox {
  val io = IO(new Bundle {
    val A = Input(SInt(19.W))   // input wire [18 : 0] A
    val B = Input(SInt(16.W))   // input wire [15 : 0] B
    val CLK = Input(Clock())    // input wire CLK
    val CE = Input(Bool())      // input wire CE
    val S = Output(SInt(20.W))  // output wire [19 : 0] S  
  })
}

// 16bits + 8bits SInt
class SIntSixteenAEight extends BlackBox {
  val io = IO(new Bundle {
    val A = Input(SInt(16.W))   // input wire [15 : 0] A
    val B = Input(SInt(8.W))    // input wire [7 : 0] B
    val CLK = Input(Clock())    // input wire CLK
    val CE = Input(Bool())      // input wire CE
    val S = Output(SInt(17.W))  // output wire [16 : 0] S  
  })
}

// 20bits + 8bits SInt
class SIntTwentyAEight extends BlackBox {
  val io = IO(new Bundle {
    val A = Input(SInt(20.W))   // input wire [19 : 0] A
    val B = Input(SInt(8.W))    // input wire [7 : 0] B
    val CLK = Input(Clock())    // input wire CLK
    val CE = Input(Bool())      // input wire CE
    val S = Output(SInt(21.W))  // output wire [20 : 0] S  
  })
}