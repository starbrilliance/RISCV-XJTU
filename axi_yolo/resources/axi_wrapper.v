`timescale 1ps/1ps

module axi4_wrapper #(
  
   parameter C_AXI_ID_WIDTH       = 4, // The AXI id width used for read and write
                                       // This is an integer between 1-16
   parameter C_AXI_ADDR_WIDTH     = 32, // This is AXI address width for all 
                                        // SI and MI slots
   parameter C_AXI_DATA_WIDTH     = 32, // Width of the AXI write and read data

   parameter C_AXI_NBURST_SUPPORT = 0, // Support for narrow burst transfers
                                       // 1-supported, 0-not supported 
  // parameter C_BEGIN_ADDRESS      = 0, // Start address of the address map

  // parameter C_END_ADDRESS        = 32'hFFFF_FFFF, // End address of the address map

   parameter CTL_SIG_WIDTH        = 2, // Control signal width

   parameter WR_STS_WIDTH         = 16, // Write port status signal width

   parameter RD_STS_WIDTH         = 16,  // Read port status signal width

   parameter EN_UPSIZER           = 0, // There is no upsizer code
 
   parameter WDG_TIMER_WIDTH      = 9

)
(
   input                               aclk, // AXI input clock
   input                               aresetn, // Active low AXI reset signal
   
// User interface command port

   input                               cmd_en, // Asserted to indicate a valid command
                                               // and address
   input [2:0]                         cmd,    // Write or read command
                                               // 000 - READ with INCR bursts
                                               // 001 - READ with WRAP bursts
                                               // 01x - Reserved
                                               // 100 - WRITE with INCR bursts
                                               // 101 - WRITE with WRAP bursts
   input [7:0]                         blen,   // Burst length calculated as blen+1
   input [31:0]                        addr,   // Address for the read or the write
                                               // transaction
   input [CTL_SIG_WIDTH-1:0]           ctl,    // control command for read or write
                                               // transaction 
   input                               wdog_mask, // Mask the watchdog timeouts
   output                              cmd_ack,// Indicates the command has been accepted

// User interface write ports

   input                               wrdata_vld,  // Asserted to indicate a valid write
                                                    // data
   input [C_AXI_DATA_WIDTH-1:0]        wrdata_data, // Write data
   input [C_AXI_DATA_WIDTH/8-1:0]      wrdata_bvld, // Byte valids for the write data
   input                               wrdata_cmptd,// Last data to be transferred
   output reg                          wrdata_rdy,  // Indicates that the write data is
                                                    // ready to be accepted
   output reg                          wrdata_sts_vld, // Indicates a write status after
                                                       // completion of a write transfer                               
   output [WR_STS_WIDTH-1:0]           wrdata_sts,     // Status of the write transaction

// User interface read ports

   input                               rddata_rdy,   // Data ready to be accepted
   output reg                          rddata_vld,   // Indicates a valid read data available
   output reg [C_AXI_DATA_WIDTH-1:0]   rddata_data,  // Read data
   output [C_AXI_DATA_WIDTH/8-1:0]     rddata_bvld,  // Byte valids for read data 
   output reg                          rddata_cmptd, // Indicates last data present and 
                                                     // valid status
   output [RD_STS_WIDTH-1:0]           rddata_sts,   // Status of the read transaction

// AXI write address channel signals

   input                               axi_awready, // Indicates slave is ready to accept a 
                                                   // write address
   output [C_AXI_ID_WIDTH-1:0]         axi_awid,    // Write ID
   output [C_AXI_ADDR_WIDTH-1:0]       axi_awaddr,  // Write address
   output [7:0]                        axi_awlen,   // Write Burst Length
   output [2:0]                        axi_awsize,  // Write Burst size
   output [1:0]                        axi_awburst, // Write Burst type
   output [1:0]                        axi_awlock,  // Write lock type
   output [3:0]                        axi_awcache, // Write Cache type
   output [2:0]                        axi_awprot,  // Write Protection type
   output [3:0]                        axi_awqos,   // always be zero
   output reg                          axi_awvalid, // Write address valid

// AXI write data channel signals

   input                               axi_wready,  // Write data ready
   output reg [C_AXI_DATA_WIDTH-1:0]   axi_wdata,    // Write data
   output reg [C_AXI_DATA_WIDTH/8-1:0] axi_wstrb,    // Write strobes
   output reg                          axi_wlast,    // Last write transaction   
   output                              axi_wvalid,   // Write valid

// AXI write response channel signals
   input  [C_AXI_ID_WIDTH-1:0]         axi_bid,     // Response ID
   input  [1:0]                        axi_bresp,   // Write response
   input                               axi_bvalid,  // Write reponse valid
   output reg                          axi_bready,  // Response ready

// AXI read address channel signals
   input                               axi_arready,     // Read address ready
   output [C_AXI_ID_WIDTH-1:0]         axi_arid,        // Read ID
   output [C_AXI_ADDR_WIDTH-1:0]       axi_araddr,      // Read address
   output [7:0]                        axi_arlen,       // Read Burst Length
   output [2:0]                        axi_arsize,      // Read Burst size
   output [1:0]                        axi_arburst,     // Read Burst type
   output [1:0]                        axi_arlock,      // Read lock type
   output [3:0]                        axi_arcache,     // Read Cache type
   output [2:0]                        axi_arprot,      // Read Protection type
   output [3:0]                        axi_arqos,       // always be zero
   output reg                          axi_arvalid,     // Read address valid

// AXI read data channel signals   
   input  [C_AXI_ID_WIDTH-1:0]         axi_rid,     // Response ID
   input  [1:0]                        axi_rresp,   // Read response
   input                               axi_rvalid,  // Read reponse valid
   input  [C_AXI_DATA_WIDTH-1:0]       axi_rdata,    // Read data
   input                               axi_rlast,    // Read last
   output reg                          axi_rready   // Read Response ready
);
  
//*****************************************************************************
// Internal parameter declarations
//*****************************************************************************
 
  localparam [8:0]                     AXI_WRIDLE    = 9'd0,
                                       AXI_WRCTL     = 9'd1,
                                       AXI_WRRDY     = 9'd2,
                                       AXI_WRDAT     = 9'd3,
                                       AXI_WRDAT_WT  = 9'd4,
                                       AXI_WRDAT_LST = 9'd5,
                                       AXI_WRDAT_DMY = 9'd6,
                                       AXI_WRRESP_WT = 9'd7,
                                       AXI_WRTO      = 9'd8;

  localparam [5:0]                     AXI_RDIDLE    = 6'd0,
                                       AXI_RDCTL     = 6'd1,
                                       AXI_RDDAT     = 6'd2,
                                       AXI_RDDAT_LST = 6'd3,
                                       AXI_RDDAT_WT  = 6'd4,
                                       AXI_RDTO      = 6'd5;

//*****************************************************************************
// Internal register and wire declarations
//*****************************************************************************
  
  reg                                  wrap_w;
  reg [7:0]                            blen_w;
  reg [7:0]                            blen_w_minus_1;
  reg [C_AXI_ADDR_WIDTH-1:0]           addr_w;
  reg [CTL_SIG_WIDTH-1:0]              ctl_w;
  reg                                  wrap_r;
  reg [7:0]                            blen_r;
  reg [C_AXI_ADDR_WIDTH-1:0]           addr_r;
  reg [CTL_SIG_WIDTH-1:0]              ctl_r;
  reg [8:0]                            wstate;
  reg [8:0]                            next_wstate;
  reg                                  wr_cmd_start;
  reg [WDG_TIMER_WIDTH-1:0]            wr_wdog_cntr;
  reg                                  wrdata_vld_r;
  reg                                  wrdata_cmptd_r;
  reg [7:0]                            wr_len_cntr;
  reg [7:0]                            rd_len_cntr;
  reg [7:0]                            blen_cntr;
  reg [3:0]                            wr_cntr;
  reg [C_AXI_DATA_WIDTH-1:0]           wrdata_r1;
  reg [C_AXI_DATA_WIDTH-1:0]           wrdata_r2;
  reg                                  wrdata_mux_ctrl;
  reg [2:0]                            wrdata_fsm_sts;
  reg [3:0]                            brespid_r;
  reg [1:0]                            bresp_r;

  reg [5:0]                            rstate;
  reg [5:0]                            next_rstate;
  reg [WDG_TIMER_WIDTH-1:0]            rd_wdog_cntr;
  reg                                  rd_cmd_start;
  reg                                  rlast;
  reg [3:0]                            rd_cntr;
  reg                                  rddata_ppld;
  reg [C_AXI_DATA_WIDTH-1:0]           rddata_p1;
  reg                                  err_resp;
  reg [1:0]                            rddata_fsm_sts;
  reg                                  rrid_err;
  reg                                  pending_one_trans;
  reg                                  axi_awready_l;

  wire                                 wr_cmd_timeout;
  wire                                 wr_done;
  wire                                 wr_last;

  wire                                 rd_cmd_timeout;

//*****************************************************************************
// Address and control register logic
//*****************************************************************************

  always @(posedge aclk) begin
    if (!aresetn) begin
      wrap_w <= 1'b0; 
      blen_w <= 8'h0;
      blen_w_minus_1 <= 8'h0;
      addr_w <= {C_AXI_ADDR_WIDTH{1'b0}};
      ctl_w  <= {CTL_SIG_WIDTH{1'b0}};
    end
    else if (wstate[AXI_WRIDLE] & next_wstate[AXI_WRIDLE] & 
        cmd_en & cmd[2]) begin
      wrap_w <= cmd[0]; 
      blen_w <= blen;
      blen_w_minus_1 <= blen - 8'h01;
      addr_w <= addr;
      ctl_w  <= ctl;
    end
  end 

  always @(posedge aclk) begin
    if (!aresetn) begin
      wrap_r <= 1'b0; 
      blen_r <= 8'h0;
      addr_r <= {C_AXI_ADDR_WIDTH{1'b0}};
      ctl_r  <= {CTL_SIG_WIDTH{1'b0}};
    end
    else if (rstate[AXI_RDIDLE] & next_rstate[AXI_RDIDLE] & 
        cmd_en & !cmd[2]) begin
      wrap_r <= cmd[0]; 
      blen_r <= blen;
      addr_r <= addr;
      ctl_r  <= ctl;
    end
  end 

  assign cmd_ack = (wstate[AXI_WRIDLE] & next_wstate[AXI_WRCTL]) |
                   (rstate[AXI_RDIDLE] & next_rstate[AXI_RDCTL]);
  
//*****************************************************************************
// Write data state machine control signals
//*****************************************************************************

  always @(posedge aclk)
    if (!aresetn)
      wr_cmd_start <= 1'b0;
    else if (cmd_en & cmd[2] & wstate[AXI_WRIDLE])
      wr_cmd_start <= 1'b1;
    else if (wstate[AXI_WRCTL])
      wr_cmd_start <= 1'b0;
      
  always @(posedge aclk)
    if (wstate[AXI_WRIDLE] | 
        (axi_wready & (wstate[AXI_WRDAT] | wstate[AXI_WRDAT_WT] | wstate[AXI_WRDAT_LST] | wstate[AXI_WRDAT_DMY])) |
        (axi_bvalid & wstate[AXI_WRRESP_WT])) 
      wr_wdog_cntr <= 'h0;
    else if (!wstate[AXI_WRTO] & !wdog_mask)
      wr_wdog_cntr <= wr_wdog_cntr + 'h1;

  always @(posedge aclk)
    wrdata_vld_r <= wrdata_vld;

  always @(posedge aclk)
    if (wstate[AXI_WRIDLE])    
      wrdata_cmptd_r <= 1'b0;
    else if (wrdata_cmptd & wrdata_vld)
      wrdata_cmptd_r <= 1'b1;

  always @(posedge aclk)
    if (wstate[AXI_WRIDLE])    
      blen_cntr <= 8'h0; 
    else if (wrdata_vld & wrdata_rdy)
      blen_cntr <= blen_cntr + 8'h01; 

  always @(posedge aclk)
    if (wstate[AXI_WRIDLE])    
      pending_one_trans <= 1'b0; 
    else if (next_wstate[AXI_WRDAT] & wstate[AXI_WRDAT_WT])
      pending_one_trans <= 1'b0; 
    else if (wstate[AXI_WRDAT] & next_wstate[AXI_WRDAT_WT] & wr_last & !axi_wready)
      pending_one_trans <= 1'b1; 

  always @(posedge aclk)
    if (wstate[AXI_WRIDLE])    
      wr_len_cntr <= 8'h0; 
    else if ((wstate[AXI_WRDAT_DMY] | wstate[AXI_WRDAT] | wstate[AXI_WRDAT_WT]) & 
             axi_wvalid & axi_wready)             
      wr_len_cntr <= wr_len_cntr + 8'h01;

  always @(posedge aclk)
    if (wstate[AXI_WRIDLE])    
      axi_awready_l <= 1'b0; 
    else if (axi_awready)
      axi_awready_l <= 1'b1; 

  assign wr_cmd_timeout = wr_wdog_cntr[WDG_TIMER_WIDTH-1] & !wdog_mask; 
  assign wr_last        = (wr_len_cntr >= blen_w_minus_1);
  assign wr_done        = (blen_cntr >= blen_w);

//*****************************************************************************
// Write data state machine
//*****************************************************************************

  always @(posedge aclk) begin
    if (!aresetn)
      wstate <= 9'h1;
    else 
      wstate <= next_wstate;
  end

  always @(*) begin
    next_wstate = 9'h0;
    case (1'b1)
      wstate[AXI_WRIDLE]: begin // 9'h001
        if (wr_cmd_start)
          next_wstate[AXI_WRCTL] = 1'b1;
        else
          next_wstate[AXI_WRIDLE] = 1'b1;
      end
      wstate[AXI_WRCTL]: begin // 9'h002
        if (wr_cmd_timeout)
          next_wstate[AXI_WRTO] = 1'b1;
        else if (axi_awvalid)
          next_wstate[AXI_WRRDY] = 1'b1;
        else
          next_wstate[AXI_WRCTL] = 1'b1;
      end 
      wstate[AXI_WRRDY]: begin // 9'h004
        if (wrdata_cmptd_r & wrdata_rdy)
          next_wstate[AXI_WRDAT_LST] = 1'b1;
        else if (wrdata_vld_r & wrdata_rdy)
          next_wstate[AXI_WRDAT] = 1'b1;
        else
          next_wstate[AXI_WRRDY] = 1'b1;
      end
      wstate[AXI_WRDAT]: begin // 9'h008
        if (wr_cmd_timeout)
          next_wstate[AXI_WRTO] = 1'b1;
        else if (axi_wready & wrdata_cmptd_r & (wr_last | ~(|blen_w)))
          next_wstate[AXI_WRDAT_LST] = 1'b1;
        else if (axi_wready & wrdata_cmptd_r & !wr_done & 
                 (wr_len_cntr != 8'h00))
          next_wstate[AXI_WRDAT_DMY] = 1'b1;
        else if (!axi_wready)
          next_wstate[AXI_WRDAT_WT] = 1'b1;
        else
          next_wstate[AXI_WRDAT] = 1'b1;
      end
      wstate[AXI_WRDAT_WT]: begin // 9'h010
        if (wr_cmd_timeout)
          next_wstate[AXI_WRTO] = 1'b1;
        else if (axi_wready) begin
          if (pending_one_trans & wrdata_cmptd_r & (wr_last | ~(|blen_w)))
            next_wstate[AXI_WRDAT_LST] = 1'b1;
          else if (!pending_one_trans & wrdata_cmptd_r & !wr_done & 
                   (wr_len_cntr != 8'h00))
            next_wstate[AXI_WRDAT_DMY] = 1'b1;
          else
            next_wstate[AXI_WRDAT] = 1'b1;
        end
        else
          next_wstate[AXI_WRDAT_WT] = 1'b1;
      end
      wstate[AXI_WRDAT_LST]: begin // 9'h020
        if (wr_cmd_timeout)
          next_wstate[AXI_WRTO] = 1'b1;
        else if (axi_wvalid & axi_wready)
          next_wstate[AXI_WRRESP_WT] = 1'b1;
        else
          next_wstate[AXI_WRDAT_LST] = 1'b1;
      end
      wstate[AXI_WRDAT_DMY]: begin // 9'h040
        if (wr_cmd_timeout)
          next_wstate[AXI_WRTO] = 1'b1;
        else if (wrdata_cmptd_r & wr_last)
          next_wstate[AXI_WRDAT_LST] = 1'b1;
        else if (!wr_last & !axi_wready)
          next_wstate[AXI_WRDAT_WT] = 1'b1;  
        else
          next_wstate[AXI_WRDAT_DMY] = 1'b1;  
      end
      wstate[AXI_WRRESP_WT]: begin // 9'h080
        if (wr_cmd_timeout)
          next_wstate[AXI_WRTO] = 1'b1;
        else if (axi_bvalid & 
                 (EN_UPSIZER == 1 || (EN_UPSIZER == 0 & axi_awready_l)))
          next_wstate[AXI_WRIDLE] = 1'b1;
        else
          next_wstate[AXI_WRRESP_WT] = 1'b1;
      end
      wstate[AXI_WRTO]: begin // 9'h100
        next_wstate[AXI_WRIDLE] = 1'b1;
      end
    endcase
  end

//*****************************************************************************
// Write channel control signals
//*****************************************************************************

  always @(posedge aclk)
    if (!aresetn)
      wr_cntr <= 4'h0;
    else if (wstate[AXI_WRRESP_WT] & next_wstate[AXI_WRIDLE])
      wr_cntr <= wr_cntr + 4'h1;

  always @(posedge aclk)
    if (!aresetn)
      axi_awvalid <= 1'b0;
    else if ((wstate[AXI_WRCTL] & next_wstate[AXI_WRRDY] & axi_awready) ||
             (axi_awready & !wstate[AXI_WRCTL]))
      axi_awvalid <= 1'b0;
    else if (wstate[AXI_WRCTL])
      axi_awvalid <= 1'b1;
  
  assign axi_awaddr = addr_w;
  assign axi_awid   = wr_cntr;
  assign axi_awlen  = blen_w;
  assign axi_awburst = {1'b0, wrap_w} + 2'b01;
  assign axi_awsize = ctl_w[2:0];

// Not supported and hence assigned zeros
  assign axi_awlock = 2'b0;
  assign axi_awcache = 4'b0;
  assign axi_awprot = 3'b0;
  assign axi_awqos = 4'b0;

//*****************************************************************************
// Write channel data signals
//*****************************************************************************

  always @(posedge aclk) begin
    if (wstate[AXI_WRIDLE]) begin
      wrdata_r1 <= 'h0;
      wrdata_r2 <= 'h0; 
    end
    else if (wrdata_rdy & wrdata_vld & (wstate[AXI_WRDAT] | wstate[AXI_WRRDY] | 
             wstate[AXI_WRDAT_LST])) begin
      wrdata_r1 <= wrdata_data;
 wrdata_r2 <= wrdata_r1; 
    end 
  end
  
  always @(posedge aclk)
    if (!aresetn)
      wrdata_rdy <= 1'b0;
    else if (wstate[AXI_WRDAT_LST] | (wstate[AXI_WRDAT] & next_wstate[AXI_WRDAT_WT]))
      wrdata_rdy <= 1'b0;
    else if (wstate[AXI_WRDAT] | 
             (wstate[AXI_WRCTL] & next_wstate[AXI_WRRDY]) |
             (wstate[AXI_WRDAT_WT] & next_wstate[AXI_WRDAT])) 
      wrdata_rdy <= 1'b1;

  always @(posedge aclk)
    if (!aresetn)
      wrdata_sts_vld <= 1'b0;
    else if (wstate[AXI_WRIDLE])
      wrdata_sts_vld <= 1'b0;
    else if ((wstate[AXI_WRRESP_WT] | wstate[AXI_WRTO]) & next_wstate[AXI_WRIDLE])
      wrdata_sts_vld <= 1'b1;

  always @(posedge aclk)
    if (!aresetn)
      wrdata_mux_ctrl <= 1'b0;
    else if ((wstate[AXI_WRDAT_WT] & (next_wstate[AXI_WRDAT] | next_wstate[AXI_WRDAT_LST])) |
             wstate[AXI_WRIDLE])
      wrdata_mux_ctrl <= 1'b0;
    else if (wstate[AXI_WRDAT] & next_wstate[AXI_WRDAT_WT] & !pending_one_trans)
      wrdata_mux_ctrl <= 1'b1;

  always @(posedge aclk)
    if (!aresetn)
      axi_wlast <= 1'b0;
    else if (wstate[AXI_WRDAT_LST] & next_wstate[AXI_WRRESP_WT])
      axi_wlast <= 1'b0;
    else if ((wstate[AXI_WRDAT] | wstate[AXI_WRDAT_DMY] | wstate[AXI_WRRDY] | wstate[AXI_WRDAT_WT]) & 
             next_wstate[AXI_WRDAT_LST])
      axi_wlast <= 1'b1;

  generate 
    begin: data_axi_wr
      if (C_AXI_NBURST_SUPPORT != 1) begin
        
        always @(posedge aclk)
          if (wstate[AXI_WRIDLE])
            axi_wdata <= 'h0;
          else if (axi_wready & (wstate[AXI_WRDAT] | wstate[AXI_WRDAT_WT]) & wrdata_mux_ctrl &
                   ~next_wstate[AXI_WRDAT_LST])
            axi_wdata <= wrdata_r2;
          else if ((axi_wready & (wstate[AXI_WRDAT] | 
                                    (wstate[AXI_WRDAT_WT] & next_wstate[AXI_WRDAT_LST]) | 
                                    (wstate[AXI_WRDAT_LST] & !next_wstate[AXI_WRRESP_WT]))) |
                                    (wstate[AXI_WRRDY] & next_wstate[AXI_WRDAT]))
            axi_wdata <= wrdata_r1;

        always @(posedge aclk)
          if (wstate[AXI_WRIDLE])
            axi_wstrb <= {(C_AXI_DATA_WIDTH/8){1'b0}};
          else if ((axi_wready & (wstate[AXI_WRDAT] | 
                                    (next_wstate[AXI_WRDAT_LST] & (wstate[AXI_WRRDY] | wstate[AXI_WRDAT])) |
                                    ((wstate[AXI_WRRDY] | wstate[AXI_WRDAT_WT]) & 
                                     next_wstate[AXI_WRDAT]))) | 
                   (next_wstate[AXI_WRDAT_LST] & !axi_wready & 
                    (wstate[AXI_WRDAT] | wstate[AXI_WRDAT_LST] | 
                     wstate[AXI_WRDAT_DMY] | wstate[AXI_WRDAT_WT])) |
                   (wstate[AXI_WRRDY] & next_wstate[AXI_WRDAT]) | 
                   ((wstate[AXI_WRDAT] | wstate[AXI_WRDAT_DMY]) & next_wstate[AXI_WRDAT_WT]) | 
                   (wstate[AXI_WRDAT_WT])) 
            axi_wstrb <= {(C_AXI_DATA_WIDTH/8){1'b1}};
          else
            axi_wstrb <= {(C_AXI_DATA_WIDTH/8){1'b0}};

      end
    end
  endgenerate

  assign axi_wvalid = wstate[AXI_WRDAT] | wstate[AXI_WRDAT_LST] | 
                        wstate[AXI_WRDAT_DMY] | wstate[AXI_WRDAT_WT];

//*****************************************************************************
// Write response and status signals
//*****************************************************************************

  always @(posedge aclk)
    if (!aresetn)
      axi_bready <= 1'b0;
    else if (next_wstate[AXI_WRIDLE] & wstate[AXI_WRRESP_WT])
      axi_bready <= 1'b0;
    else if (wstate[AXI_WRRESP_WT])
      axi_bready <= 1'b1;

  always @(posedge aclk)
    if (wstate[AXI_WRIDLE]) 
      wrdata_fsm_sts <= 3'b000;  
    else begin
      if (next_wstate[AXI_WRTO]) begin
        if (wstate[AXI_WRDAT])
          wrdata_fsm_sts <= 3'b001;
        else if (wstate[AXI_WRDAT_WT])
          wrdata_fsm_sts <= 3'b010;
        else if (wstate[AXI_WRDAT_DMY])
          wrdata_fsm_sts <= 3'b011;
        else if (wstate[AXI_WRRESP_WT])
          wrdata_fsm_sts <= 3'b100;
      end
    end

  always @(posedge aclk)
    if (wstate[AXI_WRIDLE]) begin 
      brespid_r <= 4'h0;  
      bresp_r   <= 2'b00;
    end
    else if (wstate[AXI_WRRESP_WT] & axi_bvalid) begin
      brespid_r <= axi_bid;  
      bresp_r   <= axi_bresp;
    end

  assign wrdata_sts = {{{WR_STS_WIDTH-8}{1'b0}},wrdata_fsm_sts,brespid_r[3:0],bresp_r}; 

//*****************************************************************************
// Read data state machine control signals
//*****************************************************************************

  always @(posedge aclk)
    if (rstate[AXI_RDIDLE] | axi_arready | axi_rvalid) 
      rd_wdog_cntr <= 'h0;
    else if (!rstate[AXI_RDTO])
      rd_wdog_cntr <= rd_wdog_cntr + 'h1;

  always @(posedge aclk)
    if (!aresetn)
      rd_cmd_start <= 1'b0;
    else if (cmd_en & !cmd[2] & rstate[AXI_RDIDLE])
      rd_cmd_start <= 1'b1;
    else if (rstate[AXI_RDCTL])
      rd_cmd_start <= 1'b0;
      
  always @(posedge aclk)
    if (rstate[AXI_RDIDLE])
      rlast <= 1'b0;
    else if (axi_rlast & axi_rvalid)
      rlast <= 1'b1; 

  assign rd_cmd_timeout = rd_wdog_cntr[WDG_TIMER_WIDTH-1] & !wdog_mask; 

//*****************************************************************************
// Read data state machine
//*****************************************************************************

  always @(posedge aclk) begin
    if (!aresetn)
      rstate <= 6'h1;
    else 
      rstate <= next_rstate;
  end

  always @(*) begin
    next_rstate = 6'h0;
    case (1'b1)
      rstate[AXI_RDIDLE]: begin // 6'h01
        if (rd_cmd_start)
          next_rstate[AXI_RDCTL] = 1'b1;
        else
          next_rstate[AXI_RDIDLE] = 1'b1;
      end
      rstate[AXI_RDCTL]: begin // 6'h02
        if (rd_cmd_timeout)
          next_rstate[AXI_RDTO] = 1'b1;
        else if (axi_arready & axi_arvalid) begin
          if (rddata_rdy)
            next_rstate[AXI_RDDAT] = 1'b1;
          else
            next_rstate[AXI_RDDAT_WT] = 1'b1;
        end 
        else
          next_rstate[AXI_RDCTL] = 1'b1;
      end
      rstate[AXI_RDDAT]: begin // 6'h04
        if (rd_cmd_timeout)
          next_rstate[AXI_RDTO] = 1'b1;
        else if (rddata_rdy) begin
          if (rlast)
            next_rstate[AXI_RDDAT_LST] = 1'b1;
          else
            next_rstate[AXI_RDDAT] = 1'b1;
        end
        else
          next_rstate[AXI_RDDAT_WT] = 1'b1;
      end
      rstate[AXI_RDDAT_LST]: begin // 6'h08
        if (rddata_cmptd & rddata_vld & rddata_rdy)
          next_rstate[AXI_RDIDLE] = 1'b1;
        else      
          next_rstate[AXI_RDDAT_LST] = 1'b1;
      end
      rstate[AXI_RDDAT_WT]: begin // 6'h10
        if (rddata_rdy) begin
          if (rlast)
            next_rstate[AXI_RDDAT_LST] = 1'b1;
          else
            next_rstate[AXI_RDDAT] = 1'b1;
        end
        else
          next_rstate[AXI_RDDAT_WT] = 1'b1;
      end
      rstate[AXI_RDTO]: begin // 6'h20
        next_rstate[AXI_RDIDLE] = 1'b1;
      end
    endcase
  end

//*****************************************************************************
// Read Address control signals
//*****************************************************************************

  always @(posedge aclk)
    if (!aresetn)
      rd_cntr <= 4'h0;
    else if (rstate[AXI_RDDAT_LST] & next_rstate[AXI_RDIDLE])
      rd_cntr <= rd_cntr + 4'h1;

  always @(posedge aclk)
    if (!aresetn)
      axi_arvalid <= 1'b0;
    else if (rstate[AXI_RDCTL] & next_rstate[AXI_RDDAT])
      axi_arvalid <= 1'b0;
    else if (rstate[AXI_RDCTL])     
      axi_arvalid <= 1'b1;

  assign axi_arid = rd_cntr; 

  generate 
    begin: addr_axi_rd
      if (C_AXI_DATA_WIDTH == 256)
        assign axi_araddr = {addr_r[C_AXI_ADDR_WIDTH-1:5], 5'b0};
      else if (C_AXI_DATA_WIDTH == 128)
        assign axi_araddr = {addr_r[C_AXI_ADDR_WIDTH-1:4], 4'b0};
      else if (C_AXI_DATA_WIDTH == 64)
        assign axi_araddr = {addr_r[C_AXI_ADDR_WIDTH-1:3], 3'b0};
      else
        assign axi_araddr = {addr_r[C_AXI_ADDR_WIDTH-1:2], 2'b0};
    end
  endgenerate

  assign axi_arlen = blen_r;
  assign axi_arburst = {1'b0, wrap_r} + 2'b01;
  assign axi_arsize = ctl_r[2:0];

// Not supported and hence assigned zeros
  assign axi_arlock = 2'b0;
  assign axi_arcache = 4'b0;
  assign axi_arprot = 3'b0;
  assign axi_arqos = 4'b0;

//*****************************************************************************
// Read channel data signals
//*****************************************************************************

  always @(posedge aclk)
    if (!aresetn)
      rddata_vld <= 1'b0;
    else if ((rddata_vld & !axi_rvalid & rstate[AXI_RDDAT]) |
             (rddata_rdy & rstate[AXI_RDDAT_LST]) | 
             (rstate[AXI_RDDAT_WT] & next_rstate[AXI_RDDAT] & rddata_ppld) | 
             (rddata_rdy & axi_rvalid & axi_rlast) |
             rstate[AXI_RDIDLE])
      rddata_vld <= 1'b0;
    else if ((rstate[AXI_RDDAT] & axi_rvalid & !axi_rlast) |
             ((rstate[AXI_RDDAT] | rstate[AXI_RDDAT_WT]) & next_rstate[AXI_RDDAT_LST] & rlast) |
             (rstate[AXI_RDDAT_LST] & axi_rvalid & axi_rlast & axi_rready) | 
             rstate[AXI_RDTO])
      rddata_vld <= 1'b1;

  always @(posedge aclk)
    if (!aresetn)
      rddata_ppld <= 1'b0;
    else if (rddata_vld & rddata_rdy)
      rddata_ppld <= 1'b0;
    else if (!rddata_vld & axi_rvalid & axi_rready & rstate[AXI_RDDAT_WT])
      rddata_ppld <= 1'b1;

  always @(posedge aclk)
    if (!aresetn)
      axi_rready <= 1'b0;
    else if (rstate[AXI_RDIDLE] |
             (rstate[AXI_RDDAT] & next_rstate[AXI_RDDAT_WT]) |
             (rstate[AXI_RDDAT_WT] & !next_rstate[AXI_RDDAT] & rddata_ppld) |
             (next_rstate[AXI_RDDAT_LST] & (rstate[AXI_RDDAT] | rstate[AXI_RDDAT_WT])))
      axi_rready <= 1'b0;
    else if ((next_rstate[AXI_RDDAT] & (rstate[AXI_RDCTL] | rstate[AXI_RDDAT_WT])) |
             (next_rstate[AXI_RDDAT_LST] & rstate[AXI_RDDAT_WT] & rddata_ppld) |
             (rstate[AXI_RDDAT_WT] & !rddata_ppld) |
             (rstate[AXI_RDDAT_LST] & axi_rvalid & axi_rlast))
      axi_rready <= 1'b1;

  always @(posedge aclk)
    if (axi_rvalid)
      rddata_p1 <= axi_rdata;

  generate 
    begin: data_axi_rd
      if (C_AXI_NBURST_SUPPORT == 1) begin
      end
      else begin
        
        always @(posedge aclk)
          if (axi_rvalid & !rddata_ppld)
            rddata_data <= axi_rdata;
          else if (rddata_rdy & rddata_vld & rddata_ppld)
            rddata_data <= rddata_p1;

        assign rddata_bvld = {{C_AXI_DATA_WIDTH/32}{4'hF}};

      end
    end
  endgenerate

  always @(posedge aclk)
    if (!aresetn)
      rddata_cmptd <= 1'b0;
    else if ((next_rstate[AXI_RDIDLE] & rstate[AXI_RDDAT_LST]) |
             rstate[AXI_RDIDLE])
      rddata_cmptd <= 1'b0;
    else if (((rstate[AXI_RDDAT] | rstate[AXI_RDDAT_WT]) & next_rstate[AXI_RDDAT_LST] & rlast) |
             (rstate[AXI_RDDAT_LST] & axi_rvalid & axi_rlast & axi_rready) | 
             rstate[AXI_RDTO])
      rddata_cmptd <= 1'b1;

  always @(posedge aclk)
    if (rstate[AXI_RDIDLE])
      err_resp <= 1'b0;
    else if (axi_rvalid & axi_rresp[1])
      err_resp <= 1'b1;

  always @(posedge aclk)
    if (rstate[AXI_RDIDLE] & next_rstate[AXI_RDCTL])
      rddata_fsm_sts <= 2'b00;
    else if (rstate[AXI_RDCTL] & next_rstate[AXI_RDTO])
      rddata_fsm_sts <= 2'b01;
    else if (rstate[AXI_RDDAT] & next_rstate[AXI_RDTO])
      rddata_fsm_sts <= 2'b10;
  
  always @(posedge aclk)
    if (rstate[AXI_RDIDLE] & next_rstate[AXI_RDCTL])
      rrid_err <= 1'b0;
    else if (axi_rvalid & axi_rid != rd_cntr)
      rrid_err <= 1'b1;

  always @(posedge aclk)
    if (rstate[AXI_RDIDLE])    
      rd_len_cntr <= 8'h0; 
    else if (axi_rvalid & axi_rready)
      rd_len_cntr <= rd_len_cntr + 8'h01;

  assign rddata_sts = {{(RD_STS_WIDTH-12){1'b0}},rd_len_cntr,rddata_fsm_sts,rrid_err,err_resp}; 

// synthesis translate_off
  always @(posedge aclk) begin
    if (rd_cmd_timeout)
      $display ("ERR: Read timeout occured at time %t", $time);
    if (wr_cmd_timeout)
      $display ("ERR: Write timeout occured at time %t", $time);
  end
// synthesis translate_on

endmodule

