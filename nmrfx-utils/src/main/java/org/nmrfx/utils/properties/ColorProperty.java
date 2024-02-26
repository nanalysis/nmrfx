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

package org.nmrfx.utils.properties;

import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.paint.Color;

public class ColorProperty extends SimpleObjectProperty<Color> {
    public ColorProperty(Object object, String name, Color color) {
        super(object, name, color);
    }

    public void setColor(Color color) {
        set(color);
    }

    public Color getColor() {
        return get();
    }

    public String getColorAsRGB() {
        return toRGBCode(getColor());
    }

    @Override
    public String toString() {
        return super.toString() + " " + get();
    }

    public static String toRGBCode(Color color) {
        if (color == null)
            return null;

        return String.format("#%02X%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255),
                (int) (color.getOpacity() * 255)
        );
    }
}
