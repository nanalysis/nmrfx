package org.nmrfx.chemistry.io;

import junit.framework.TestCase;

public class SDFileTest extends TestCase {

    public void testInMolFileFormatValidFormatLong() {
        String molFile = """
                Test File
                  Program-name:165943769
                                
                  1  2  0     0  0  0  0  0  0999 V2000
                    0.4671    0.3562    0.0000 O   0  0  0  0  0  0  0  0  0  0  0  0
                  1  2  1  0  0  0  0
                  1  9  1  0  0  0  0
                M  END
                > <ID>
                00001
                                
                > <DESCRIPTION>
                Other content at end of file.
                                
                $$$$""";
        assertTrue(SDFile.inMolFileFormat(molFile));
    }

    public void testInMolFileFormatValidFormatShort() {
        String molFile = """
                	Test file
                  Program-name:165943769         \s
                                
                  1  1  0  0  0  0            999 V2000
                   -0.5345    0.4365    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0
                  1  2  2  0  0  0  0
                M  END""";
        assertTrue(SDFile.inMolFileFormat(molFile));
    }

    public void testInMolFileFormatInValidFormatVersion() {
        String molFile = """
                	Test file
                  Program-name:165943769         \s
                                
                  0 0 0 0 0 999 V3000
                  M V30 BEGIN CTAB
                M  END""";
        assertFalse(SDFile.inMolFileFormat(molFile));
    }

    public void testInMolFileFormatInValidFormat() {
        String molFile = """
                	Test file
                  Program-name:165943769         \s
                                
                  some other text on this linea124 3252 45465 23354536 5747 999 V2000
                   -0.5345    0.4365    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0
                  1  2  2  0  0  0  0
                M  END""";
        assertFalse(SDFile.inMolFileFormat(molFile));
    }

}