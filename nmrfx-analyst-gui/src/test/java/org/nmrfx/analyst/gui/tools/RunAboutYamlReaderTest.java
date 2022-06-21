package org.nmrfx.analyst.gui.tools;

import org.junit.Test;
import org.nmrfx.peaks.io.PeakPatternReader;

import java.io.IOException;

import static org.junit.Assert.*;

public class RunAboutYamlReaderTest {

    @Test
    public void loadYaml() throws IOException {
        RunAboutYamlReader.loadYaml();
    }
}