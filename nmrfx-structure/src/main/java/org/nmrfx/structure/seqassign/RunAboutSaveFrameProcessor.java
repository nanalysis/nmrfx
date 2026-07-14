package org.nmrfx.structure.seqassign;

import org.nmrfx.star.ParseException;
import org.nmrfx.star.Saveframe;
import org.nmrfx.star.SaveframeProcessor;

import java.io.IOException;

public class RunAboutSaveFrameProcessor implements SaveframeProcessor {
    @Override
    public void process(Saveframe saveframe) throws ParseException, IOException {
        RunAbout runAbout = new RunAbout();
        runAbout.readSTARSaveFrame(saveframe);
    }
}
