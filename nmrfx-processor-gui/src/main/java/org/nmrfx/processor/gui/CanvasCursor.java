package org.nmrfx.processor.gui;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.image.Image;
import org.apache.commons.lang3.SystemUtils;

public enum CanvasCursor {
    CROSSHAIR(Cursor.CROSSHAIR, FontAwesomeIcon.PLUS),
    SELECTOR(SystemUtils.IS_OS_LINUX ? Cursor.HAND : Cursor.MOVE, FontAwesomeIcon.MOUSE_POINTER),
   // PEAK(IconUtilities.getCursor("fxgl_cross32", 16, 16), FontAwesomeIcon.CROSSHAIRS),
    PEAK(Cursor.N_RESIZE, FontAwesomeIcon.ARROWS_V),
    REGION(Cursor.E_RESIZE, FontAwesomeIcon.ARROWS_H);

    final Cursor cursor;
    final FontAwesomeIcon icon;

    CanvasCursor(Cursor cursor, FontAwesomeIcon icon) {
        this.cursor = cursor;
        this.icon = icon;
    }

    public Cursor getCursor() {
        return cursor;
    }

    public FontAwesomeIcon getIcon() {
        return icon;
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
