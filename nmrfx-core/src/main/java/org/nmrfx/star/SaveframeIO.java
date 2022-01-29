package org.nmrfx.star;

import java.io.Writer;

public interface SaveframeIO {

    public void write(Writer chan);

    public void read(Saveframe saveFrame);

}
