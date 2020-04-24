/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry;

import java.io.File;
import org.apache.logging.log4j.core.util.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.structure.chemistry.io.NMRNEFReader;
import org.nmrfx.structure.chemistry.io.NMRNEFWriter;

/**
 *
 * @author Martha
 */
public class NEFFileTest {

    String classPath = this.getClass().getResource("NEFFileTest.class").getPath();
    String program = "nmrfxstructure";
    String mainPath = classPath.substring(0, classPath.indexOf(program) + program.length());
    String fileName = String.join(File.separator, mainPath, "sandbox", "1pqx.nef");
    String outFile = String.join(File.separator, mainPath, "sandbox", "1pqx_nef_outTest.txt");

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
//        File origFile = new File(fileName);
//        File writtenFile = new File(outFile);
        // fixme add test for file equality
    }

}
