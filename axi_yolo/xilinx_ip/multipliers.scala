package axi_yolo

import chisel3._
import chisel3.experimental._

// 3bits × 3bits
class ThreeMThree extends BlackBox {
  val io = IO(new Bundle {
    val CLK = Input(Clock())  // input wire CLK
    val A = Input(UInt(3.W))  // input wire [2 : 0] A
    val B = Input(UInt(3.W))  // input wire [2 : 0] B
    val CE = Input(Bool())    // input wire CE
    val P = Output(UInt(6.W)) // output wire [5 : 0] P  
  })
}

// 8bits × 8bits
class EightMEight extends BlackBox {
  val io = IO(new Bundle {
    val CLK = Input(Clock())  // input wire CLK
    val A = Input(UInt(8.W))  // input wire [7 : 0] A
    val B = Input(UInt(8.W))  // input wire [7 : 0] B
    val CE = Input(Bool())    // input wire CE
    val P = Output(UInt(16.W)) // output wire [15 : 0] P  
  })
}

// 6bits × 11bits
class SixMEleven extends BlackBox {
  val io = IO(new Bundle {
    val CLK = Input(Clock())  // input wire CLK
    val A = Input(UInt(6.W))  // input wire [5 : 0] A
    val B = Input(UInt(11.W))  // input wire [10 : 0] B
    val CE = Input(Bool())    // input wire CE
    val P = Output(UInt(17.W)) // output wire [16 : 0] P  
  })
}

// 16bits × 11bits
class SixteenMEleven extends BlackBox {
  val io = IO(new Bundle {
    val CLK = Input(Clock())  // input wire CLK
    val A = Input(UInt(16.W))  // input wire [15 : 0] A
    val B = Input(UInt(11.W))  // input wire [10 : 0] B
    val CE = Input(Bool())    // input wire CE
    val P = Output(UInt(27.W)) // output wire [26 : 0] P  
  })
}

// 17bits × 11bits
class SeventeenMEleven extends BlackBox {
  val io = IO(new Bundle {
    val CLK = Input(Clock())  // input wire CLK
    val A = Input(UInt(17.W))  // input wire [16 : 0] A
    val B = Input(UInt(11.W))  // input wire [10 : 0] B
    val CE = Input(Bool())    // input wire CE
    val P = Output(UInt(28.W)) // output wire [27 : 0] P  
  })
}

// 9bits × 8bits SInt  第一层RGB数据是无符号数，中间层是有符号数
class SIntNineMEight extends BlackBox {
  val io = IO(new Bundle {
    val CLK = Input(Clock())  // input wire CLK
    val A = Input(SInt(9.W))  // input wire [8 : 0] A
    val B = Input(SInt(8.W))  // input wire [7 : 0] B
    val CE = Input(Bool())    // input wire CE
    val P = Output(SInt(17.W)) // output wire [16 : 0] P  
  })
}

// pp change
class ElevenMEleven extends BlackBox {
  val io = IO(new Bundle {
    val CLK = Input(Clock())  // input wire CLK
    val A = Input(UInt(11.W))  // input wire [10 : 0] A
    val B = Input(UInt(11.W))  // input wire [10 : 0] B
    val CE = Input(Bool())    // input wire CE
    val P = Output(UInt(22.W)) // output wire [21 : 0] P  
  })
}

class TwentyoneMFour extends BlackBox {
  val io = IO(new Bundle {
    val CLK = Input(Clock())  // input wire CLK
    val A = Input(UInt(21.W))  // input wire [10 : 0] A
    val B = Input(UInt(4.W))  // input wire [10 : 0] B
    val CE = Input(Bool())    // input wire CE
    val P = Output(UInt(25.W)) // output wire [21 : 0] P  
  })
}

class TwentyeightMFour extends BlackBox {
  val io = IO(new Bundle {
    val CLK = Input(Clock())  // input wire CLK
    val A = Input(UInt(28.W))  // input wire [10 : 0] A
    val B = Input(UInt(4.W))  // input wire [10 : 0] B
    val CE = Input(Bool())    // input wire CE
    val P = Output(UInt(32.W)) // output wire [21 : 0] P  
  })
}

class EightMSixteen extends BlackBox {
  val io = IO(new Bundle {
    val CLK = Input(Clock())  // input wire CLK
    val A = Input(UInt(8.W))  // input wire [10 : 0] A
    val B = Input(UInt(16.W))  // input wire [10 : 0] B
    val CE = Input(Bool())    // input wire CE
    val P = Output(UInt(24.W)) // output wire [21 : 0] P  
  })
}