package org.nmrfx.star;

import java.io.IOException;
import java.io.Writer;

public interface SaveframeProcessor {

    public void process(Saveframe saveframe) throws ParseException, IOException;

}
