package org.nmrfx.processor.gui;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.scene.Cursor;
import org.apache.commons.lang3.SystemUtils;

public enum CanvasCursor {
    SELECTOR(SystemUtils.IS_OS_LINUX ? Cursor.HAND : Cursor.MOVE, "Selector", FontAwesomeIcon.MOUSE_POINTER),
    CROSSHAIR(Cursor.CROSSHAIR, "Crosshair", FontAwesomeIcon.PLUS),
    PEAK(Cursor.N_RESIZE, "Peak", FontAwesomeIcon.ARROWS_V),
    REGION(Cursor.E_RESIZE, "Region", FontAwesomeIcon.ARROWS_H);

    private final Cursor cursor;
    private final String label;
    private final FontAwesomeIcon icon;

    CanvasCursor(Cursor cursor, String label, FontAwesomeIcon icon) {
        this.cursor = cursor;
        this.label = label;
        this.icon = icon;
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
        return CROSSHAIR.getCursor() == cursor;
    }

    public static boolean isPeak(Cursor cursor) {
        return PEAK.getCursor() == cursor;
    }

    public static boolean isRegion(Cursor cursor) {
        return REGION.getCursor() == cursor;
    }
}
