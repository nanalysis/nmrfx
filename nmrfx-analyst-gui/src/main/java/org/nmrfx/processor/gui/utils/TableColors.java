package org.nmrfx.processor.gui.utils;

import javafx.scene.paint.Color;

import java.util.List;
import java.util.function.BiConsumer;

public class TableColors {

    private TableColors() {
    }

    public static <T> void unifyColor(List<T> items, Color color, BiConsumer<T, Color> applyMethod) {
        for (T item : items) {
            applyMethod.accept(item, color);
        }
    }

    public static Color interpolateColor(Color color1, Color color2, double f) {
        f = Math.min(1.0, Math.max(0.0, f));
        double hue1 = color1.getHue();
        double hue2 = color2.getHue();
        double sat1 = color1.getSaturation();
        double sat2 = color2.getSaturation();
        double bright1 = color1.getBrightness();
        double bright2 = color2.getBrightness();
        double hue = (1.0 - f) * hue1 + f * hue2;
        double sat = (1.0 - f) * sat1 + f * sat2;
        double bright = (1.0 - f) * bright1 + f * bright2;
        hue = Math.min(360.0, Math.max(0.0, hue));
        sat = Math.min(1.0, Math.max(0.0, sat));
        bright = Math.min(1.0, Math.max(0.0, bright));
        return Color.hsb(hue, sat, bright);

    }


    public static <T> void interpolateColors(List<T> items, Color color0, Color color1, BiConsumer<T, Color> applyMethod) {
        double delta = 1.0 / (items.size() - 1.0);
        double f = 0.0;
        for (T item : items) {
            Color color = interpolateColor(color0, color1, f);
            f += delta;
            applyMethod.accept(item, color);
        }
    }
}
