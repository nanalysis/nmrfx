package org.nmrfx.utils;

import org.python.core.Py;

public class FormatUtils {

    private FormatUtils() {
    }

    public static String formatStringForPythonInterpreter(String s) {
        return Py.newUnicode(s).encode("UTF-8");
    }
}
