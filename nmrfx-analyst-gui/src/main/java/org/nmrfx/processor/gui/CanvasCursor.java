package org.nmrfx.processor.gui;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.scene.Cursor;
import org.apache.commons.lang3.SystemUtils;

import java.util.Arrays;

public enum CanvasCursor {
    SELECTOR(SystemUtils.IS_OS_LINUX ? Cursor.HAND : Cursor.MOVE, "Selector", FontAwesomeIcon.MOUSE_POINTER),
    CROSSHAIR(Cursor.CROSSHAIR, "Crosshair", FontAwesomeIcon.PLUS),
    PEAK(Cursor.N_RESIZE, "Peak", FontAwesomeIcon.ARROWS_V),
    REGION(Cursor.E_RESIZE, "Region", FontAwesomeIcon.ARROWS_H),
    SLICE(Cursor.H_RESIZE, "Slice", FontAwesomeIcon.PLUS);
    private final Cursor cursor;
    private final String label;
    private final FontAwesomeIcon icon;

    CanvasCursor(Cursor cursor, String label, FontAwesomeIcon icon) {
        this.cursor = cursor;
        this.label = label;
        this.icon = icon;
    }

    public static CanvasCursor getCanvasCursor(Cursor cursor) {
        var opt = Arrays.stream(values()).filter(c -> c.getCursor() == cursor).findFirst();
        if (opt.isPresent()) {
            return opt.get();
        } else {
            return CROSSHAIR;
        }
    }

    public Cursor getCursor() {
        return cursor;
    }

    public FontAwesomeIcon getIcon() {
        return icon;
    }

    public String getLabel() {
        return label;
    }

    public static boolean isSelector(Cursor cursor) {
        return SELECTOR.getCursor() == cursor;
    }

    public static boolean isCrosshair(Cursor cursor) {
        return CROSSHAIR.getCursor() == cursor || SLICE.getCursor() == cursor;
    }

    public static boolean isPeak(Cursor cursor) {
        return PEAK.getCursor() == cursor;
    }

    public static boolean isRegion(Cursor cursor) {
        return REGION.getCursor() == cursor;
    }

    public static boolean isSlice(Cursor cursor) {
        return SLICE.getCursor() == cursor;
    }
}
