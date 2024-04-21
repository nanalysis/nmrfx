package org.nmrfx.analyst.dataops;

import junit.framework.TestCase;

import java.io.IOException;
import java.nio.file.Path;

public class DBDataTest extends TestCase {
    public void testName() throws IOException {
        DBData dbData = new DBData();
        dbData.loadData(Path.of("/Users/brucejohnson/metabo/save/all.json"));
    }
}