package yolo

import chisel3._
import chisel3.util._
import chisel3.experimental._

class FifoToRoccIO(width: Int) extends Bundle {
  val dataIn = Input(UInt(width.W))
  val writeEn = Input(Bool())
  val writeClk = Input(Clock())
  val full = Output(Bool())
  override def cloneType = (new FifoToRoccIO(width)).asInstanceOf[this.type] 
}

class FifoToDecoderIO(width: Int) extends Bundle {
  val readEn = Input(Bool())
  val dataOut = Output(UInt(width.W))
  val empty = Output(Bool())
  override def cloneType = (new FifoToDecoderIO(width)).asInstanceOf[this.type]
}

class SystemIO extends Bundle {
  val readClk = Input(Clock())
  val systemRst = Input(Bool())
}

@chiselName
class Fifo(width: Int, depth: Int) extends RawModule {
  val io = IO(new Bundle {
    val wr = new FifoToRoccIO(width)
    val rd = new FifoToDecoderIO(width)
    val sys = new SystemIO
  })

  val ram = SyncReadMem(1 << depth, UInt(width.W))   // 2^depth
  val writeToReadPtr = Wire(UInt((depth + 1).W))  // to read clock domain
  val readToWritePtr = Wire(UInt((depth + 1).W))  // to write clock domain

  // write clock domain
  withClockAndReset(io.wr.writeClk, io.sys.systemRst) {
    val binaryWritePtr = RegInit(0.U((depth + 1).W))
    val binaryWritePtrNext = Wire(UInt((depth + 1).W))
    val grayWritePtr = RegInit(0.U((depth + 1).W))
    val grayWritePtrNext = Wire(UInt((depth + 1).W))
    val isFull = RegInit(false.B)
    val fullValue = Wire(Bool())
    val grayReadPtrDelay0 = RegNext(readToWritePtr)
    val grayReadPtrDelay1 = RegNext(grayReadPtrDelay0)

    binaryWritePtrNext := binaryWritePtr + (io.wr.writeEn && !isFull).asUInt
    binaryWritePtr := binaryWritePtrNext
    grayWritePtrNext := (binaryWritePtrNext >> 1) ^ binaryWritePtrNext
    grayWritePtr := grayWritePtrNext
    writeToReadPtr := grayWritePtr
    fullValue := (grayWritePtrNext === Cat(~grayReadPtrDelay1(depth, depth - 1), grayReadPtrDelay1(depth - 2, 0)))
    isFull := fullValue

    when(io.wr.writeEn && !isFull) {
      ram.write(binaryWritePtr(depth - 1, 0), io.wr.dataIn)
    }

    io.wr.full := isFull    
  }
  // read clock domain
  withClockAndReset(io.sys.readClk, io.sys.systemRst) {
    val binaryReadPtr = RegInit(0.U((depth + 1).W))
    val binaryReadPtrNext = Wire(UInt((depth + 1).W))
    val grayReadPtr = RegInit(0.U((depth + 1).W))
    val grayReadPtrNext = Wire(UInt((depth + 1).W))
    val isEmpty = RegInit(true.B)
    val emptyValue = Wire(Bool())
    val grayWritePtrDelay0 = RegNext(writeToReadPtr)
    val grayWritePtrDelay1 = RegNext(grayWritePtrDelay0)

    binaryReadPtrNext := binaryReadPtr + (io.rd.readEn && !isEmpty).asUInt
    binaryReadPtr := binaryReadPtrNext
    grayReadPtrNext := (binaryReadPtrNext >> 1) ^ binaryReadPtrNext
    grayReadPtr := grayReadPtrNext
    readToWritePtr := grayReadPtr
    emptyValue := (grayReadPtrNext === grayWritePtrDelay1)
    isEmpty := emptyValue

    io.rd.dataOut := ram.read(binaryReadPtr(depth - 1, 0), io.rd.readEn && !isEmpty)
    io.rd.empty := isEmpty
  }  
}