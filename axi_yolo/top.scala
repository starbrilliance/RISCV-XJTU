package axi_yolo

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
  val ui_clk = Wire(Clock())
  val ui_rst = Wire(Bool())
  
  val clockGen = Module(new ClockGenerator)
  clockGen.io.clk_in1_p := io.sys_clk_p
  clockGen.io.clk_in1_n := io.sys_clk_n
  clockGen.io.reset := io.clk_rst  
  io.clk_valid := clockGen.io.locked

  val ddrFpga = Module(new DdrUserControlModule)
  ui_clk := ddrFpga.io.aclk
  ui_rst := !ddrFpga.io.aresetn
  ddrFpga.io.sys.clk_i := clockGen.io.ddr_clk_i
  ddrFpga.io.sys.rst := io.sys_rst
  ddrFpga.io.clk_ref_i := clockGen.io.ref_clk_i
  io.ddr3 <> ddrFpga.io.ddr3

  val simulator = withClockAndReset(ui_clk, ui_rst) {
    if(simulation) Some(Module(new Simulator)) else None
  }

  withClockAndReset(ui_clk, ui_rst || io.isRst) {
    val iFifo = Module(new Fifo(32, 4))
    val dFifo = Module(new Fifo(32, 4))
    val decoder = Module(new Decoder)
    val infoRegs = Module(new Regfile)
    val information = Module(new Information)
    val fsm = Module(new Fsm)
    val ddrControl = Module(new DdrControl(simulation))  
    val dataBufferA = Module(new DataBufferA)
    val dataBufferB = Module(new DataBufferB)
    val weightBuffer = Module(new WeightBuffer)
    val biasBuffer = Module(new BiasBuffer)
    val dataBufferControl = Module(new DataBufferControl)
    val weightBufferControl = Module(new WeightBufferControl)
    val biasBufferControl = Module(new BiasBufferControl)
    val pad = Module(new Padding)
    val shiftRegs = Module(new SrBuffer)
    val balance = Module(new Balance)
    val pe = Module(new PE)
    val active = Module(new Active)
    val pool = Module(new MaxPool)
    val writeback = Module(new WriteBack)
    val store = Module(new Store)

    val read_buffer = Wire(UInt(512.W))
    val write_bufferA = Wire(UInt(512.W))

    if(simulation) {
      io.sim.get <> simulator.get.io.toTop

      simulator.get.io.init <> ddrFpga.io.init
      ddrControl.io.sim.get <> simulator.get.io.app

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
    iFifo.io.sys.readClk := ui_clk
    iFifo.io.sys.systemRst := ui_rst || io.isRst
  
    dFifo.io.rd.readEn := decoder.io.fifoPorts.readEn
    dFifo.io.sys.readClk := ui_clk
    dFifo.io.sys.systemRst := ui_rst || io.isRst

    decoder.io.readBiasComplete := ddrControl.io.ddrToFsm.readBiasComplete
    decoder.io.regsPorts <> infoRegs.io.decoderPorts
    fsm.io.fromDecoder <> decoder.io.fsmPorts
    io.isRst :=  decoder.io.systemRst
    io.instIllegal := decoder.io.illegal

    infoRegs.io.writeData := dFifo.io.rd.dataOut
    infoRegs.io.realLayerEnd := balance.io.realLayerEnd
    information.io.fromRegfile <> infoRegs.io.toInfo
    fsm.io.fromReg <> infoRegs.io.toFsm

    information.io.fromFsm <> fsm.io.toInfo
    ddrControl.io.fromInfo <> information.io.toDdrC
    pad.io.informationToPadding <> information.io.toPadding

    fsm.io.fsmToDdr <> ddrControl.io.ddrToFsm
    fsm.io.fsmToPad <> pad.io.fsmToPadding
    fsm.io.realLayerEnd := balance.io.realLayerEnd
    io.isIdle := fsm.io.isIdle
    
    ddrControl.io.padToDdr <> pad.io.paddingToDdrcontrol
    ddrControl.io.fromStore <> store.io.toDdrC
    ddrControl.io.wrdata <> ddrFpga.io.wrdata 
    ddrControl.io.rddata <> ddrFpga.io.rddata
    ddrControl.io.control.cmd_ack := ddrFpga.io.cmd_ack
    ddrFpga.io.cmd_en := ddrControl.io.control.cmd_en   
    ddrFpga.io.cmd := ddrControl.io.control.cmd
    ddrFpga.io.blen := ddrControl.io.control.blen
    ddrFpga.io.addr := ddrControl.io.control.addr 
    
    write_bufferA := Mux(fsm.io.fsmToDdr.readDataEn, ddrControl.io.ddrToBuffer, writeback.io.dataOut)
    dataBufferA.io.clka := ui_clk
    dataBufferA.io.ena := dataBufferControl.io.writeEnA
    dataBufferA.io.wea := dataBufferControl.io.writeEnA
    dataBufferA.io.addra := dataBufferControl.io.writeAddressA
    dataBufferA.io.dina := write_bufferA
    dataBufferA.io.clkb := ui_clk
    dataBufferA.io.enb := dataBufferControl.io.readEnA
    dataBufferA.io.addrb := dataBufferControl.io.readAddressA

    dataBufferB.io.clka := ui_clk
    dataBufferB.io.ena := dataBufferControl.io.writeEnB
    dataBufferB.io.wea := dataBufferControl.io.writeEnB
    dataBufferB.io.addra := dataBufferControl.io.writeAddressB
    dataBufferB.io.dina := writeback.io.dataOut
    dataBufferB.io.clkb := ui_clk
    dataBufferB.io.enb := dataBufferControl.io.readEnB
    dataBufferB.io.addrb := dataBufferControl.io.readAddressB

    weightBuffer.io.clka := ui_clk
    weightBuffer.io.ena := weightBufferControl.io.writeEnW
    weightBuffer.io.wea := weightBufferControl.io.writeEnW 
    weightBuffer.io.addra := weightBufferControl.io.writeAddressW
    weightBuffer.io.dina := ddrControl.io.ddrToBuffer
    weightBuffer.io.clkb := ui_clk
    weightBuffer.io.enb := pad.io.weightbufferToPadding.en
    weightBuffer.io.addrb := pad.io.weightbufferToPadding.addr

    biasBuffer.io.clka := ui_clk
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
    dataBufferControl.io.realLayerEnd := balance.io.realLayerEnd

    weightBufferControl.io.ddrWeightEn := ddrControl.io.weightBufferEn
    weightBufferControl.io.padToWB <> pad.io.paddingToDdrcontrol

    biasBufferControl.io.ddrBiasEn := ddrControl.io.biasBufferEn
    biasBufferControl.io.realOCEnd := balance.io.realOCEnd
    biasBufferControl.io.douta := biasBuffer.io.douta
    
    pad.io.paddingToSr <> shiftRegs.io.srToPadding
    pad.io.weightbufferToPadding.datain := weightBuffer.io.doutb
    for(i <- 0 until 64) {
      pad.io.databufferToPadding.datain(i) := read_buffer(8 * i + 7, 8 * i)
    } 
 
    shiftRegs.io.firstLayer := infoRegs.io.toFsm.firstLayer
    shiftRegs.io.finalLayer := infoRegs.io.toFsm.finalLayer

    balance.io.fromPad <> pad.io.paddingToBalance
    balance.io.kernelSize := infoRegs.io.toInfo.readData(6)(21)
    balance.io.writeComplete := writeback.io.writeComplete
    
    pe.io.fromSr <> shiftRegs.io.srToPe
    pe.io.fromPad <> pad.io.paddingToPe
    pe.io.fromBalance <> balance.io.toPE
    pe.io.biasIn := biasBufferControl.io.biasOut
    pe.io.firstLayer := infoRegs.io.toFsm.firstLayer
    pe.io.validIn := shiftRegs.io.srToPadding.shift    

    active.io.writeComplete := writeback.io.writeComplete
    active.io.realLineEnd := balance.io.realLineEnd
    active.io.validIn := pe.io.validOut
    active.io.dataIn := pe.io.dataOut

    pool.io.poolEn := infoRegs.io.toInfo.readData(6)(24)
    pool.io.writeComplete := writeback.io.writeComplete
    pool.io.activeComplete := active.io.activeComplete
    for(i <- 0 until 112) {
      pool.io.validIn(i) := active.io.validOut(i * 2 + 1)
    }
    pool.io.dataIn := active.io.dataOut

    writeback.io.writeStart := Mux(infoRegs.io.toInfo.readData(6)(24).toBool, pool.io.poolComplete, active.io.activeComplete)
    writeback.io.poolEn := infoRegs.io.toInfo.readData(6)(24)
    writeback.io.actData := active.io.dataOut
    writeback.io.poolData := pool.io.dataOut
    writeback.io.inputSize := infoRegs.io.toInfo.readData(5)(19, 12)
    writeback.io.outputSize := infoRegs.io.toInfo.readData(6)(19, 12)   

    read_buffer := Mux(dataBufferControl.io.readEnA, dataBufferA.io.doutb, dataBufferB.io.doutb)
    store.io.ddrStoreEn := fsm.io.fsmToDdr.writeDataEn
    store.io.dataIn := read_buffer(55, 0)
  }
}