package org.nmrfx.processor.gui;

import javafx.scene.Cursor;
import org.apache.commons.lang3.SystemUtils;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

import java.util.Arrays;

public enum CanvasCursor {
    SELECTOR(SystemUtils.IS_OS_LINUX ? Cursor.HAND : Cursor.MOVE, "Selector", Material2MZ.NORTH_WEST),
    CROSSHAIR(Cursor.CROSSHAIR, "Crosshair", Material2MZ.PLUS),
    PEAK(Cursor.N_RESIZE, "Peak", Material2AL.ARROW_UPWARD),
    REGION(Cursor.E_RESIZE, "Region", Material2AL.HORIZONTAL_DISTRIBUTE),
    SLICE(Cursor.H_RESIZE, "Slice", Material2AL.HORIZONTAL_DISTRIBUTE);

    private final Cursor cursor;
    private final String label;
    private final Ikon icon;

    CanvasCursor(Cursor cursor, String label, Ikon icon) {
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

    public Ikon getIcon() {
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
