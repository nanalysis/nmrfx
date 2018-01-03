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
package org.nmrfx.processor.utilities;

import javafx.scene.paint.Color;

public class ColorUtil {

    public static String toRGBCode(int red, int green, int blue, int opacity) {
        return String.format("0x%02X%02X%02X%02X", red, green, blue, opacity);
    }

    public static String toRGBCode(int red, int green, int blue) {
        return String.format("0x%02X%02X%02X%02X", red, green, blue, 255);
    }

    public static String toRGBCode(Color color) {
        return String.format("0x%02X%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255),
                (int) (color.getOpacity() * 255)
        );
    }

    public static Color getColor(String colorString) {
        return Color.web(colorString);
    }

}
