package org.nmrfx.star;

import java.io.IOException;

public interface SaveframeProcessor {

    public void process(Saveframe saveframe) throws ParseException, IOException;

}
