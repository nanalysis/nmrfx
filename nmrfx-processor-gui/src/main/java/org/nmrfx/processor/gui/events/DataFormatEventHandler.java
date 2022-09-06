package org.nmrfx.processor.gui.events;

public interface DataFormatEventHandler {

    void handlePaste(Object o);

    default boolean handleDelete() {
        return false;
    }
}
