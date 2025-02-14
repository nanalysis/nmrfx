/*
 * NMRFx Analyst :
 * Copyright (C) 2004-2021 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.chemistry.relax;

import org.nmrfx.annotations.PluginAPI;

/**
 * @author brucejohnson
 */
@PluginAPI("ring")
public interface RelaxationValues {

    Double getValue();

    Double getError();

    String[] getParNames();

    Double getValue(String name);

    Double getError(String name);

    ResonanceSource getResonanceSource();

    public static void appendValueErrorWithSep(StringBuilder stringBuilder, Double val, Double err, String format, String sepChar) {
        stringBuilder.append(sepChar);
        if (val != null) {
            stringBuilder.append(String.format(format, val));
        }
        stringBuilder.append(sepChar);
        if (err != null) {
            stringBuilder.append(String.format(format, err));
        }
    }

    public static void appendValueError(StringBuilder stringBuilder, Double val, Double err, String format, String defaultValue, String sepChar) {
        if (val != null) {
            stringBuilder.append(String.format(format, val));
        } else {
            stringBuilder.append(defaultValue);
        }
        stringBuilder.append(sepChar);
        if (err != null) {
            stringBuilder.append(String.format(format, err));
        } else {
            stringBuilder.append(defaultValue);
        }
    }

    public static void appendValue(StringBuilder stringBuilder, Double val, String format, String defaultValue) {
        if (val != null) {
            stringBuilder.append(String.format(format, val));
        } else {
            stringBuilder.append(defaultValue);
        }
    }
}
