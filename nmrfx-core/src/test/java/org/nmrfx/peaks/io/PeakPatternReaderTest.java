package org.nmrfx.peaks.io;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class PeakPatternReaderTest {

    @Test
    public void loadYamlWithClass() throws IOException{
        PeakPatternReader.loadYaml();
    }
}
