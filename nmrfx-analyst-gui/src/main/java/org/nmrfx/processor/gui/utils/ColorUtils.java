package org.nmrfx.processor.gui.utils;

import javafx.scene.paint.Color;

public class ColorUtils {
    private ColorUtils() {
        throw new IllegalAccessError("Utility class");
    }

    public static Color chooseBlackWhite(Color color) {
        Color result;
        if (color.getBrightness() > 0.5) {
            result = Color.BLACK;
        } else {
            result = Color.WHITE;
        }
        return result;
    }
}
