package org.nmrfx.utils;

public interface TableItem {
    default boolean getActive() {
        return true;
    }

    Double getDouble(String elemName);

    default int getGroup() {
        return 0;
    }
}
