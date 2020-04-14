/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry;

import org.junit.Test;
import org.nmrfx.structure.chemistry.io.NMRNEFReader;
import org.nmrfx.structure.chemistry.io.NMRNEFWriter;

/**
 *
 * @author Martha
 */
public class NEFFileTest {
    
    String fileName = "C:/Users/Martha/Desktop/ASRC/PDBStats_NEW/PDBStats_NEW/1pqx/1pqx.nef";
    String outFile = "C:/Users/Martha/Desktop/ASRC/1pqx_nef_outTest.txt";
    
//    @Test
//    public void readNEF() {  
//        try {
//            NMRNEFReader.read(fileName);
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//        
//    }
    
    @Test
    public void writeNEF() {  
        try {
            NMRNEFReader.read(fileName);
            NMRNEFWriter.writeAll(outFile);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
    }
   
}
