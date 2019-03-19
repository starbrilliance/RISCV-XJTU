package yolo

import chisel3._
import chisel3.util._
import chisel3.experimental._

@chiselName
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
    val clk_valid = Output(Bool())
    // exception
    val isRst = Output(Bool())
    val isIdle = Output(Bool())
    val instIllegal = Output(Bool())
    // ddr3 ports
    val ddr3 = new HasDdrFpgaIO
  })
 
  // system clock & reset
  val sys_clk = Wire(Clock())
  val ddr_clk_i = Wire(Clock()) // ddr
  val ref_clk_i = Wire(Clock()) // ddr
  val read_buffer = Wire(UInt(512.W))
  val write_bufferA = Wire(UInt(512.W))

  val clockGen = Module(new ClockGenerator)
  clockGen.io.clk_in1_p := io.sys_clk_p
  clockGen.io.clk_in1_n := io.sys_clk_n
  clockGen.io.reset := io.clk_rst
  sys_clk := clockGen.io.sys_clk
  ddr_clk_i := clockGen.io.ddr_clk_i
  ref_clk_i := clockGen.io.ref_clk_i
  io.clk_valid := clockGen.io.locked

  withClockAndReset(sys_clk, io.sys_rst || io.isRst) {
    val simulator = if(simulation) Some(Module(new Simulator)) else None
    val iFifo = Module(new Fifo(32, 4))
    val dFifo = Module(new Fifo(32, 4))
    val decoder = Module(new Decoder)
    val infoRegs = Module(new Regfile)
    val information = Module(new Information)
    val fsm = Module(new Fsm)
    val ddrControl = Module(new DdrControl(simulation))
    val ddrFpga = Module(new MIG)
    val dataBufferA = Module(new DataBufferA)
    val dataBufferB = Module(new DataBufferB)
    val weightBuffer = Module(new WeightBuffer)
    val biasBuffer = Module(new BiasBuffer)
    val dataBufferControl = Module(new DataBufferControl)
    val weightBufferControl = Module(new WeightBufferControl)
    val biasBufferControl = Module(new BiasBufferControl)
    val pad = Module(new Padding)
    val shiftRegs = Module(new SrBuffer)
    val pe = Module(new PE)
    val active = Module(new Active)
    val pool = Module(new MaxPool)
    val writeback = Module(new WriteBack)
    val store = Module(new Store)

    if(simulation) {
      io.sim.get <> simulator.get.io.toTop

      simulator.get.io.init <> ddrFpga.io.init
      simulator.get.io.app <> ddrControl.io.sim.get

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
    iFifo.io.sys.systemRst := io.sys_rst || io.isRst
  
    dFifo.io.rd.readEn := decoder.io.fifoPorts.readEn
    dFifo.io.sys.readClk := sys_clk
    dFifo.io.sys.systemRst := io.sys_rst || io.isRst

    decoder.io.regsPorts <> infoRegs.io.decoderPorts
    decoder.io.fsmPorts <> fsm.io.enable
    io.isRst :=  decoder.io.systemRst
    io.instIllegal := decoder.io.illegal

    infoRegs.io.writeData := dFifo.io.rd.dataOut
    infoRegs.io.layerComplete := pad.io.fsmToPadding.layerComplete
    infoRegs.io.toInfo <> information.io.fromRegfile
    infoRegs.io.toFsm <> fsm.io.fromReg

    information.io.fromFsm <> fsm.io.toInfo
    information.io.toDdrC <> ddrControl.io.fromInfo
    information.io.toPadding <> pad.io.informationToPadding

    fsm.io.fsmToDdr <> ddrControl.io.ddrToFsm
    fsm.io.fsmToPad <> pad.io.fsmToPadding
    io.isIdle := fsm.io.isIdle
    
    ddrControl.io.padToDdr <> pad.io.paddingToDdrcontrol
    ddrControl.io.init <> ddrFpga.io.init
    ddrControl.io.app <> ddrFpga.io.app
    ddrControl.io.fromStore <> store.io.toDdrC
    
    ddrFpga.io.ddr3 <> io.ddr3
    ddrFpga.io.sys.clk_i := ddr_clk_i
    ddrFpga.io.sys.rst := io.ddr_rst
    ddrFpga.io.clk_ref_i := ref_clk_i
    
    write_bufferA := Mux(fsm.io.fsmToDdr.readDataEn, ddrControl.io.ddrToBuffer, writeback.io.dataOut)
    dataBufferA.io.clka := sys_clk
    dataBufferA.io.ena := dataBufferControl.io.writeEnA
    dataBufferA.io.wea := dataBufferControl.io.writeEnA
    dataBufferA.io.addra := dataBufferControl.io.writeAddressA
    dataBufferA.io.dina := write_bufferA
    dataBufferA.io.clkb := sys_clk
    dataBufferA.io.enb := dataBufferControl.io.readEnA
    dataBufferA.io.addrb := dataBufferControl.io.readAddressA

    dataBufferB.io.clka := sys_clk
    dataBufferB.io.ena := dataBufferControl.io.writeEnB
    dataBufferB.io.wea := dataBufferControl.io.writeEnB
    dataBufferB.io.addra := dataBufferControl.io.writeAddressB
    dataBufferB.io.dina := writeback.io.dataOut
    dataBufferB.io.clkb := sys_clk
    dataBufferB.io.enb := dataBufferControl.io.readEnB
    dataBufferB.io.addrb := dataBufferControl.io.readAddressB

    weightBuffer.io.clka := sys_clk
    weightBuffer.io.ena := weightBufferControl.io.writeEnW
    weightBuffer.io.wea := weightBufferControl.io.writeEnW 
    weightBuffer.io.addra := weightBufferControl.io.writeAddressW
    weightBuffer.io.dina := ddrControl.io.ddrToBuffer
    weightBuffer.io.clkb := sys_clk
    weightBuffer.io.enb := pad.io.weightbufferToPadding.en
    weightBuffer.io.addrb := pad.io.weightbufferToPadding.addr

    biasBuffer.io.clka := sys_clk
    biasBuffer.io.ena := biasBufferControl.io.ena
    biasBuffer.io.wea := biasBufferControl.io.wea
    biasBuffer.io.addra := biasBufferControl.io.addra
    biasBuffer.io.dina := ddrControl.io.ddrToBuffer
    
    dataBufferControl.io.ddrDataEn := ddrControl.io.dataBufferEn
    dataBufferControl.io.ddrDataComplete := ddrControl.io.ddrToFsm.readDataComplete
    dataBufferControl.io.ddrStoreEn := store.io.readEn
    dataBufferControl.io.writeValid := writeback.io.validOut
    dataBufferControl.io.padReadEn := pad.io.databufferToPadding.en
    dataBufferControl.io.padReadAddress := pad.io.databufferToPadding.addr
    dataBufferControl.io.layerComplete := pad.io.fsmToPadding.layerComplete

    weightBufferControl.io.ddrWeightEn := ddrControl.io.weightBufferEn
    weightBufferControl.io.padToWB <> pad.io.paddingToDdrcontrol

    biasBufferControl.io.ddrBiasEn := ddrControl.io.biasBufferEn
    biasBufferControl.io.outputChannelEnd := pad.io.outputChannelEnd
    biasBufferControl.io.douta := biasBuffer.io.douta
  
    pad.io.paddingToPe <> pe.io.fromPad
    pad.io.paddingToSr <> shiftRegs.io.srToPadding
    pad.io.weightbufferToPadding.datain := weightBuffer.io.doutb
    for(i <- 0 until 64) {
      pad.io.databufferToPadding.datain(i) := read_buffer(8 * i + 7, 8 * i)
    } 

    shiftRegs.io.srToPe <> pe.io.fromSr

    pe.io.biasIn := biasBufferControl.io.biasOut
    pe.io.kernelSize := infoRegs.io.toInfo.readData(6)(20)
    pe.io.firstLayer := infoRegs.io.toFsm.firstLayer
    pe.io.validIn := shiftRegs.io.srToPadding.shift    

    active.io.finalInputChannel := pad.io.paddingToPe.finalInputChannel
    active.io.writeComplete := writeback.io.writeComplete
    active.io.poolEn := infoRegs.io.toInfo.readData(6)(22)
    active.io.outputSize := infoRegs.io.toInfo.readData(6)(18, 11)
    active.io.validIn := pe.io.validOut
    active.io.dataIn := pe.io.dataOut

    pool.io.poolEn := infoRegs.io.toInfo.readData(6)(22)
    pool.io.outputSize := infoRegs.io.toInfo.readData(6)(18, 11)
    pool.io.writeComplete := writeback.io.writeComplete
    for(i <- 0 until 112) {
      pool.io.validIn(i) := active.io.validOut(i * 2 + 1)
    }
    pool.io.dataIn := active.io.dataOut

    writeback.io.writeStart := active.io.activeComplete || pool.io.poolComplete
    writeback.io.poolEn := infoRegs.io.toInfo.readData(6)(22)
    writeback.io.actData := active.io.dataOut
    writeback.io.poolData := pool.io.dataOut
    writeback.io.outputSize := infoRegs.io.toInfo.readData(6)(18, 11)   

    read_buffer := Mux(dataBufferControl.io.readEnA, dataBufferA.io.doutb, dataBufferB.io.doutb)
    store.io.ddrStoreEn := fsm.io.fsmToDdr.writeDataEn
    store.io.dataIn := read_buffer(55, 0)
    store.io.ddrWriteRdy := ddrFpga.io.app.wdf_rdy
    store.io.ddrRdy := ddrFpga.io.app.rdy
  }
}