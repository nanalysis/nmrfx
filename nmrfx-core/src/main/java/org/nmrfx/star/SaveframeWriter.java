package org.nmrfx.star;

import java.io.IOException;
import java.io.Writer;

public interface SaveframeWriter {

    public void write(Writer chan) throws ParseException, IOException;

}
