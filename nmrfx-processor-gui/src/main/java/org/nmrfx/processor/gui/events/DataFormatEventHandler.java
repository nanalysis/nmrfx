package org.nmrfx.processor.gui.events;

import org.nmrfx.processor.gui.PolyChart;

public interface DataFormatEventHandler {

    boolean handlePaste(Object o, PolyChart chart);
}
