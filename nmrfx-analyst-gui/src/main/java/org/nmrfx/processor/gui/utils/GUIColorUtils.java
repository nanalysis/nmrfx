/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2018 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.processor.gui.utils;

import javafx.scene.paint.Color;

/**
 * @author brucejohnson
 */
public class GUIColorUtils {

    public static Color toColor(int[] rgb) {
        Color color = rgb.length == 3 ? Color.rgb(rgb[0], rgb[1], rgb[2])
                : Color.rgb(rgb[0], rgb[1], rgb[2], rgb[3] / 255.0);
        return color;
    }

    /**
     * Select black or white equivalent for a color, based on its brightness
     *
     * @param color any color
     * @return BLACK or WHITE
     */
    public static Color toBlackOrWhite(Color color) {
        return color.getBrightness() > 0.5 ? Color.BLACK : Color.WHITE;
    }
}
