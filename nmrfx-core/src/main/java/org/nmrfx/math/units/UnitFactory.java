/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2022 One Moon Scientific, Inc., Westfield, N.J., USA
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

package org.nmrfx.math.units;

import org.nmrfx.annotations.PythonAPI;

@PythonAPI("pyproc")
public class UnitFactory {

    /**
     * Creates a Unit from the String of the Unit which contains a token at the end (unless it's an Index or Point).
     * While creating the Unit, it will strip the Token off of the end of the String and then use the appropriate
     * Integer / Double -- parseInt / parseDouble functions.
     *
     * @param value
     * @return
     * @throws NumberFormatException if the value is not properly parseable in the appropriate constructor with the
     *                               token stripped off the end of the String.
     */
    public static Unit newUnit(String value) {
        if (value.contains("s")) {
            return new Time(value.substring(0, value.indexOf("s")));
        } else if (value.contains("p")) {
            return new PPM(value.substring(0, value.indexOf("p")));
        } else if (value.contains("h")) {
            return new Frequency(value.substring(0, value.indexOf("h")));
        } else if (value.contains("f")) {
            return new Fraction(value.substring(0, value.indexOf("f")));
        } else if (value.contains(".")) { //Point is a Double value --> contains a decimal point
            return new Point(value);
        } else { //regex to see if it's just an integer
            return new Index(value);
        }
    }

    /**
     * @param type  The Type of unit to create.
     * @param value The value of the Unit. (Integer / Double only, no trailing tokens.)
     * @return
     */
    public static Unit newUnit(String type, String value) {
        switch (type) {
            case "Fraction":
                return new Fraction(value);
            case "Frequency":
                return new Frequency(value);
            case "Index":
                return new Index(value);
            case "PPM":
            case "ppms":
            case "ppm":
            case "PPMS":
                return new PPM(value);
            case "Point":
            case "pts":
                return new Point(value);
            case "Time":
                return new Time(value);
            default:
                return null;
        }
    }

    public static Unit newUnit(String type, Number value) {
        switch (type) {
            case "Fraction":
                return new Fraction((Double) value);
            case "Frequency":
                return new Frequency((Double) value);
            case "Index":
                return new Index((Integer) value);
            case "PPM":
            case "ppms":
            case "ppm":
            case "PPMS":
                return new PPM((Double) value);
            case "Point":
            case "pts":
                return new Point((Double) value);
            case "Time":
                return new Time((Double) value);
            default:
                return null;
        }
    }
}
