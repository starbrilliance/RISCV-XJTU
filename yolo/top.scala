package yolo

import chisel3._
import chisel3.util._
import chisel3.experimental._

class Top(simulation: Boolean = false) extends RawModule {
  val io = IO(new Bundle {
    // from simulator
    val sim = if(simulation) Some(new SimToTopIO) else None
    // RoCC(no simulator)
    val core_clk = if(!simulation) Some(Input(Clock())) else None 
    val core_cmd_inst = if(!simulation) Some(Input(UInt(32.W))) else None 
    val core_cmd_rs1 = if(!simulation) Some(Input(UInt(32.W))) else None
    val core_cmd_valid = if(!simulation) Some(Input(Bool())) else None
    // system ports
    val sys_clk_p = Input(Clock()) 
    val sys_clk_n = Input(Clock())
    val ddr_rst = Input(Bool())
    val clk_rst = Input(Bool())
    val sys_rst = Input(Bool())
    // exception
    val isIdle = Output(Bool())
    val instIllegal = Output(Bool())
    // ddr3 ports
    val ddr3 = new HasDdrFpgaIO
  })
 
  // system clock & reset
  val sys_clk = Wire(Clock())
  val ddr_clk_i = Wire(Clock()) // ddr
  val ref_clk_i = Wire(Clock()) // ddr
  val decoder_rst = Wire(Bool())
  val active_out = Wire(UInt(512.W))
  val read_buffer = Wire(UInt(512.W))
  val write_bufferA = Wire(UInt(512.W))

  val clockGen = Module(new ClockGenerator)
  clockGen.io.clk_in1_p := io.sys_clk_p
  clockGen.io.clk_in1_n := io.sys_clk_n
  clockGen.io.reset := io.clk_rst
  sys_clk := clockGen.io.sys_clk
  ddr_clk_i := clockGen.io.ddr_clk_i
  ref_clk_i := clockGen.io.ref_clk_i

  withClockAndReset(sys_clk, io.sys_rst || decoder_rst) {
    val simulator = if(simulation) Some(Module(new Simulator)) else None
    val iFifo = Module(new Fifo(32, 3))
    val dFifo = Module(new Fifo(32, 3))
    val decoder = Module(new Decoder)
    val infoRegs = Module(new Regfile)
    val information = Module(new Information)
    val fsm = Module(new Fsm)
    val ddrControl = Module(new DdrControl(simulation))
    val ddrFpga = Module(new MIG)
    val dataBufferA = Module(new DataBufferA)
    val dataBufferB = Module(new DataBufferB)
    val weightBuffer = Module(new WeightBuffer)
    val dataBufferControl = Module(new DataBufferControl)
    val weightBufferControl = Module(new WeightBufferControl)
    val pad = Module(new Padding)
    val shiftRegs = Module(new SrBuffer)
    val pe = Module(new PE)
    val pool = Module(new MaxPool)
    val accActive = VecInit(Seq.fill(2)(Module(new ActiveAccumulator).io))
    val poolActive = Module(new ActivePool)

    decoder_rst :=  decoder.io.systemRst

    if(simulation) {
      io.sim.get <> simulator.get.io.toTop

      simulator.get.io.init <> ddrFpga.io.init
      simulator.get.io.simDDR <> ddrControl.io.simWrite.get

      iFifo.io.wr.dataIn := simulator.get.io.core_cmd_inst
      iFifo.io.wr.writeEn := simulator.get.io.core_cmd_valid
      iFifo.io.wr.writeClk := simulator.get.io.core_clk

      dFifo.io.wr.dataIn := simulator.get.io.core_cmd_rs1
      dFifo.io.wr.writeEn := simulator.get.io.core_cmd_valid
      dFifo.io.wr.writeClk := simulator.get.io.core_clk
    } else {
      iFifo.io.wr.dataIn := io.core_cmd_inst.get
      iFifo.io.wr.writeEn := io.core_cmd_valid.get
      iFifo.io.wr.writeClk := io.core_clk.get

      dFifo.io.wr.dataIn := io.core_cmd_rs1.get
      dFifo.io.wr.writeEn := io.core_cmd_valid.get
      dFifo.io.wr.writeClk := io.core_clk.get
    } 
    
    iFifo.io.rd <> decoder.io.fifoPorts
    iFifo.io.sys.readClk := sys_clk
    iFifo.io.sys.systemRst := io.sys_rst || decoder_rst
  
    dFifo.io.rd.readEn := decoder.io.fifoPorts.readEn
    dFifo.io.sys.readClk := sys_clk
    dFifo.io.sys.systemRst := io.sys_rst || decoder_rst

    decoder.io.regsPorts <> infoRegs.io.decoderPorts
    decoder.io.fsmPorts <> fsm.io.enable
    io.instIllegal := decoder.io.illegal

    infoRegs.io.writeData := dFifo.io.rd.dataOut
    infoRegs.io.layerComplete := pad.io.fsmToPadding.layerComplete
    infoRegs.io.toInfo <> information.io.fromRegfile
    infoRegs.io.toFsm <> fsm.io.fromReg

    information.io.fromFsm <> fsm.io.toInfo
    information.io.toDdrC <> ddrControl.io.fromInfo

    fsm.io.fsmToDdr <> ddrControl.io.ddrToFsm
    fsm.io.fsmToPad <> pad.io.fsmToPadding
    io.isIdle := fsm.io.isIdle

    read_buffer := Mux(dataBufferControl.io.readEnA, dataBufferA.io.doutb, dataBufferB.io.doutb)
    ddrControl.io.padToDdr <> pad.io.paddingToDdrcontrol
    ddrControl.io.init <> ddrFpga.io.init
    ddrControl.io.app <> ddrFpga.io.app
    ddrControl.io.bufferToDdr := read_buffer    
    
    ddrFpga.io.ddr3 <> io.ddr3
    ddrFpga.io.sys.clk_i := ddr_clk_i
    ddrFpga.io.clk_ref_i := ref_clk_i
    ddrFpga.io.sys.rst := io.ddr_rst
    
    write_bufferA := Mux(fsm.io.fsmToDdr.ddrDataEn, ddrControl.io.ddrToBuffer, active_out)
    dataBufferA.io.clka := sys_clk
    dataBufferA.io.ena := dataBufferControl.io.writeEnA
    dataBufferA.io.wea := false.B
    dataBufferA.io.addra := dataBufferControl.io.writeAddressA
    dataBufferA.io.dina := write_bufferA
    dataBufferA.io.clkb := sys_clk
    dataBufferA.io.enb := dataBufferControl.io.readEnA
    dataBufferA.io.addrb := dataBufferControl.io.readAddressA

    dataBufferB.io.clka := sys_clk
    dataBufferB.io.ena := dataBufferControl.io.writeEnB
    dataBufferB.io.wea := false.B
    dataBufferB.io.addra := dataBufferControl.io.writeAddressB
    dataBufferB.io.dina := active_out
    dataBufferB.io.clkb := sys_clk
    dataBufferB.io.enb := dataBufferControl.io.readEnB
    dataBufferB.io.addrb := dataBufferControl.io.readAddressB

    weightBuffer.io.clka := sys_clk
    weightBuffer.io.ena := weightBufferControl.io.writeEnW
    weightBuffer.io.wea := false.B 
    weightBuffer.io.addra := weightBufferControl.io.writeAddressW
    weightBuffer.io.dina := ddrControl.io.ddrToBuffer
    weightBuffer.io.clkb := sys_clk
    weightBuffer.io.enb := pad.io.weightbufferToPadding.en
    weightBuffer.io.addrb := pad.io.weightbufferToPadding.addr

    val activeDelay = RegNext(accActive(1).validOut)
    dataBufferControl.io.ddrDataEn := fsm.io.fsmToDdr.ddrDataEn
    dataBufferControl.io.ddrComplete := ddrControl.io.ddrToFsm.ddrComplete
    dataBufferControl.io.ddrStoreEn := fsm.io.fsmToDdr.ddrStoreEn
    dataBufferControl.io.activeAccumulator(0) := accActive(0).validOut
    dataBufferControl.io.activeAccumulator(1) := activeDelay
    dataBufferControl.io.activePool := poolActive.io.validOut
    dataBufferControl.io.padReadEn := pad.io.databufferToPadding.en
    dataBufferControl.io.padReadAddress := pad.io.databufferToPadding.addr
    dataBufferControl.io.layerComplete := pad.io.fsmToPadding.layerComplete
    dataBufferControl.io.outputSize := infoRegs.io.toInfo.readData(4)(18, 11)

    weightBufferControl.io.ddrWeightEn := fsm.io.fsmToDdr.ddrWeightEn
    weightBufferControl.io.padToWB <> pad.io.paddingToDdrcontrol
  
    pad.io.paddingToPe <> pe.io.fromPad
    pad.io.paddingToSr <> shiftRegs.io.srToPadding
    pad.io.weightbufferToPadding.datain := weightBuffer.io.doutb
    for(i <- 0 until 64) {
      pad.io.databufferToPadding.datain(i) := read_buffer(8 * i + 7, 8 * i)
    } 
    pad.io.regfileToPadding <> infoRegs.io.toInfo
    pad.io.fsmInfo <> fsm.io.toInfo

    shiftRegs.io.srToPe <> pe.io.fromSr

    pe.io.kernelSize := infoRegs.io.toInfo.readData(4)(20)
    pe.io.validIn := shiftRegs.io.srToPadding.shift
    pe.io.accClear <> pad.io.paddingToPool

    pool.io.poolEn := infoRegs.io.toInfo.readData(4)(22)
    pool.io.fromPad <> pad.io.paddingToPool
    pool.io.validIn := pe.io.validOut(1)
    for(i <- 0 until 112) {
      pool.io.dataIn0(i) := pe.io.dataOut0(i * 2)
      pool.io.dataIn1(i) := pe.io.dataOut0(i * 2 + 1)
      pool.io.dataIn2(i) := pe.io.dataOut1(i * 2)
      pool.io.dataIn3(i) := pe.io.dataOut1(i * 2 + 1)
    }

    for(i <- 0 until 2) {
      accActive(i).poolEn := infoRegs.io.toInfo.readData(4)(22)
      accActive(i).fromPad <> pad.io.paddingToPool
      for(j <- 0 until 3) {
        accActive(i).validIn(j) := pe.io.validOut(j * 64 + 63)
      }
    }
    accActive(0).dataIn := pe.io.dataOut0
    accActive(1).dataIn := pe.io.dataOut1

    poolActive.io.lineComplete := pad.io.paddingToPool.lineComplete
    poolActive.io.validIn := pool.io.validOut
    poolActive.io.dataIn := pool.io.dataOut

    val act_out = Wire(Vec(3, UInt(512.W)))
    val active_out_temp = Wire(UInt(512.W))
    
    
    def concatenate(hiData: Vec[UInt], index: Int): UInt = {
      if(index > 0) 
        Cat(hiData(index), concatenate(hiData, index - 1))
      else
        hiData(index)
    }

    act_out(0) := concatenate(accActive(0).dataOut, 63)
    act_out(1) := concatenate(accActive(1).dataOut, 63)
    act_out(2) := concatenate(poolActive.io.dataOut, 63)
    
    active_out_temp := Mux(accActive(1).validOut, act_out(1), act_out(0))
    active_out := Mux(infoRegs.io.toInfo.readData(4)(22), act_out(2), active_out_temp) 
  }
}