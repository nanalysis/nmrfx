/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.utilities;

public class ColorUtil {

    public static int[] fromRGBCode(String code) {
        int[] rgba = null;
        if ((code != null) && (code.trim().length() != 0)) {
            rgba = new int[4];
            rgba[0] = Integer.valueOf(code.substring(2, 4), 16);
            rgba[1] = Integer.valueOf(code.substring(4, 6), 16);
            rgba[2] = Integer.valueOf(code.substring(6, 8), 16);
            rgba[3] = Integer.valueOf(code.substring(8, 10), 16);

        }
        return rgba;
    }

    public static String toRGBCode(int red, int green, int blue, int opacity) {
        return String.format("0x%02X%02X%02X%02X", red, green, blue, opacity);
    }

    public static String toRGBCode(int red, int green, int blue) {
        return String.format("0x%02X%02X%02X%02X", red, green, blue, 255);
    }

    public static String toRGBCode(int[] colorInts) {
        int opacity = colorInts.length == 4 ? colorInts[3] : 255;
        return String.format("0x%02X%02X%02X%02X", colorInts[0], colorInts[1], colorInts[2], opacity);
    }
}
