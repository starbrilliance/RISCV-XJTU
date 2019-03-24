module BlackBox_Inout( inout [15:0] a,
                       input [15:0] b,
                       input        sel,
                       output [15:0] c);
a = sel ? 'z : b;
c = sel ? a : 'z;
endmodule
                       
