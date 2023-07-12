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

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * @author brucejohnson
 */
public class Format {
    private static final Locale STD_LOCALE = new Locale("en", "US");
    private static final DecimalFormat[] FORMATTERS = new DecimalFormat[11];

    static {
        for (int i = 1; i < FORMATTERS.length; i++) {
            NumberFormat nFormat = NumberFormat.getInstance(STD_LOCALE);
            DecimalFormat format = (DecimalFormat) nFormat;
            format.setMinimumFractionDigits(i);
            format.setMaximumFractionDigits(i);
            format.setMinimumIntegerDigits(1);
            format.setGroupingUsed(false);
            FORMATTERS[i] = format;
        }
    }

    private Format() {
    }

    public static String format30(final double value) {
        return FORMATTERS[3].format(value);
    }

    public static String format41(final double value) {
        return FORMATTERS[4].format(value);
    }

    public static String format1(final double value) {
        return FORMATTERS[1].format(value);
    }

    public static String format2(final double value) {
        return FORMATTERS[2].format(value);
    }

    public static String format3(final double value) {
        return FORMATTERS[3].format(value);
    }

    public static String format4(final double value) {
        return FORMATTERS[4].format(value);
    }

    public static String format(final int precision, final double value) {
        return get(precision).format(value);
    }

    public static DecimalFormat get(final int precision) {
        final DecimalFormat format;
        if (precision < 1) {
            format = FORMATTERS[1];
        } else if (precision > 10) {
            format = FORMATTERS[10];
        } else {
            format = FORMATTERS[precision];
        }
        return format;
    }
}
