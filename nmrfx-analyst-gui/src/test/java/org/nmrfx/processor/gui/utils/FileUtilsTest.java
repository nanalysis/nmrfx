package org.nmrfx.processor.gui.utils;

import junit.framework.TestCase;

import java.io.File;

public class FileUtilsTest extends TestCase {

    public void testAddFileExtensionIfMissing() {
        File filenameWithExtension = new File("testFile.par");
        File testFile = FileUtils.addFileExtensionIfMissing(filenameWithExtension, "txt");
        // txt will not be added since an extension already present
        assertEquals(filenameWithExtension.getName(), testFile.getName());

        File filenameWithoutExtension = new File("testFile");
        testFile = FileUtils.addFileExtensionIfMissing(filenameWithoutExtension, "txt");
        // txt should be added since an extension was not already present
        assertEquals("testFile.txt", testFile.getName());

    }
}