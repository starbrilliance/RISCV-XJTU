package axi_yolo

import chisel3._
import chisel3.util._
import chisel3.experimental._
import freechips.rocketchip.tile._
import freechips.rocketchip.config._

@chiselName
class Top(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    // RoCC
    val rocc = new RoCCCoreIO()(p)
    // axi ports
    val aclk = Input(Clock())
    val aresetn = Input(Bool())
    val axi = Flipped(new HasAXI4IO) 
  })

  val instRst = Wire(Bool())

  withClockAndReset(io.aclk, !io.aresetn || instRst) {
    val iFifo = Module(new Fifo(32, 4))
    val dFifo = Module(new Fifo(32, 4))
    val decoder = Module(new Decoder)
    val infoRegs = Module(new Regfile)
    val information = Module(new Information)
    val fsm = Module(new Fsm)
    val ddrControl = Module(new DdrControl)
    val axiWrapper = Module(new axi4_wrapper)  
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

    val read_buffer = Wire(UInt(512.W))
    val write_bufferA = Wire(UInt(512.W))

    iFifo.io.wr.dataIn := io.rocc.cmd.bits.inst
    iFifo.io.wr.writeEn := io.rocc.cmd.valid
    iFifo.io.wr.writeClk := clock
    io.rocc.cmd.ready := !iFifo.io.wr.full

    dFifo.io.wr.dataIn := io.rocc.cmd.bits.rs1
    dFifo.io.wr.writeEn := io.rocc.cmd.valid
    dFifo.io.wr.writeClk := clock 
    
    iFifo.io.rd <> decoder.io.fifoPorts
    iFifo.io.sys.readClk := io.aclk
    iFifo.io.sys.systemRst := !io.aresetn || instRst
  
    dFifo.io.rd.readEn := decoder.io.fifoPorts.readEn
    dFifo.io.sys.readClk := io.aclk
    dFifo.io.sys.systemRst := !io.aresetn || instRst

    decoder.io.regsPorts <> infoRegs.io.decoderPorts
    decoder.io.fsmPorts <> fsm.io.enable
    instRst := decoder.io.systemRst

    infoRegs.io.writeData := dFifo.io.rd.dataOut
    infoRegs.io.layerComplete := pad.io.fsmToPadding.layerComplete
    infoRegs.io.toInfo <> information.io.fromRegfile
    infoRegs.io.toFsm <> fsm.io.fromReg

    information.io.fromFsm <> fsm.io.toInfo
    information.io.toDdrC <> ddrControl.io.fromInfo
    information.io.toPadding <> pad.io.informationToPadding

    fsm.io.fsmToDdr <> ddrControl.io.ddrToFsm
    fsm.io.fsmToPad <> pad.io.fsmToPadding
    io.rocc.busy :=  decoder.io.systemRst || !fsm.io.isIdle
    io.rocc.interrupt := ddrControl.io.ddrToFsm.writeDataComplete
    
    ddrControl.io.padToDdr <> pad.io.paddingToDdrcontrol
    ddrControl.io.fromStore <> store.io.toDdrC
    ddrControl.io.wrdata <> axiWrapper.io.wrdata 
    ddrControl.io.rddata <> axiWrapper.io.rddata
    ddrControl.io.control.cmd_ack := axiWrapper.io.cmd_ack
    axiWrapper.io.cmd_en := ddrControl.io.control.cmd_en   
    axiWrapper.io.cmd := ddrControl.io.control.cmd
    axiWrapper.io.blen := ddrControl.io.control.blen
    axiWrapper.io.addr := ddrControl.io.control.addr 

    axiWrapper.io.aclk := io.aclk
    axiWrapper.io.aresetn := io.aresetn
    axiWrapper.io.ctl := 6.U
    axiWrapper.io.wdog_mask := false.B
    axiWrapper.io.axi <> io.axi
    
    write_bufferA := Mux(fsm.io.fsmToDdr.readDataEn, ddrControl.io.ddrToBuffer, writeback.io.dataOut)
    dataBufferA.io.clka := io.aclk
    dataBufferA.io.ena := dataBufferControl.io.writeEnA
    dataBufferA.io.wea := dataBufferControl.io.writeEnA
    dataBufferA.io.addra := dataBufferControl.io.writeAddressA
    dataBufferA.io.dina := write_bufferA
    dataBufferA.io.clkb := io.aclk
    dataBufferA.io.enb := dataBufferControl.io.readEnA
    dataBufferA.io.addrb := dataBufferControl.io.readAddressA

    dataBufferB.io.clka := io.aclk
    dataBufferB.io.ena := dataBufferControl.io.writeEnB
    dataBufferB.io.wea := dataBufferControl.io.writeEnB
    dataBufferB.io.addra := dataBufferControl.io.writeAddressB
    dataBufferB.io.dina := writeback.io.dataOut
    dataBufferB.io.clkb := io.aclk
    dataBufferB.io.enb := dataBufferControl.io.readEnB
    dataBufferB.io.addrb := dataBufferControl.io.readAddressB

    weightBuffer.io.clka := io.aclk
    weightBuffer.io.ena := weightBufferControl.io.writeEnW
    weightBuffer.io.wea := weightBufferControl.io.writeEnW 
    weightBuffer.io.addra := weightBufferControl.io.writeAddressW
    weightBuffer.io.dina := ddrControl.io.ddrToBuffer
    weightBuffer.io.clkb := io.aclk
    weightBuffer.io.enb := pad.io.weightbufferToPadding.en
    weightBuffer.io.addrb := pad.io.weightbufferToPadding.addr

    biasBuffer.io.clka := io.aclk
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
    store.io.ddrWriteRdy := axiWrapper.io.wrdata.rdy
  }
}