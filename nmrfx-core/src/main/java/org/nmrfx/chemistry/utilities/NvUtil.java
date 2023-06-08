/*
 * NMRFx Structure : A Program for Calculating Structures
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
package org.nmrfx.chemistry.utilities;

import java.util.List;

import static java.util.Objects.requireNonNullElse;

public class NvUtil {
    private static String version = null;

    public static int getStringPars(String[] pars, String searchPar, int subSize) {
        if (subSize > searchPar.length()) {
            subSize = searchPar.length();
        }

        for (int i = 0; i < pars.length; i++) {
            if (pars[i].length() < subSize) {
                continue;
            }

            if (searchPar.toUpperCase().toLowerCase().startsWith(pars[i].substring(
                    0, subSize).toUpperCase().toLowerCase())) {
                return (i);
            } else if (searchPar.equals(String.valueOf(i))) {
                return (i);
            }
        }

        return (-1);
    }

    public static void swap(double[] limits) {
        double hold;

        if (limits[1] < limits[0]) {
            hold = limits[0];
            limits[0] = limits[1];
            limits[1] = hold;
        }
    }

    public static void swap(int[] limits) {
        int hold;

        if (limits[1] < limits[0]) {
            hold = limits[0];
            limits[0] = limits[1];
            limits[1] = hold;
        }
    }

    public static int getAxis(String axisName) {
        if (axisName.equals("x")) {
            return (0);
        } else if (axisName.equals("y")) {
            return (1);
        } else if (axisName.equals("z")) {
            return (2);
        } else if (axisName.equals("a")) {
            return (3);
        } else if (axisName.equals("z2")) {
            return (3);
        }

        return (-1);
    }

    public static String getColumnValue(List<String> list, int index) {
        String result = null;
        if (list != null) {
            result = (String) list.get(index);
            if ((result != null) && (result.equals(".") || result.equals("?"))) {
                result = null;
            }
        }
        return result;
    }

    public static int toInt(String value) throws NumberFormatException {
        int iValue = Integer.parseInt(value);
        return iValue;
    }

    public static long toLong(String value) throws NumberFormatException {
        long lValue = Long.parseLong(value);
        return lValue;
    }

    public static double toDouble(String value) throws NumberFormatException {
        double dValue = Double.parseDouble(value);
        return dValue;
    }

    public static float toFloat(String value) throws NumberFormatException {
        float fValue = Float.parseFloat(value);
        return fValue;
    }

    public static String[] splitPattern(String s) {
        int count = 0;
        int start = 0;
        while (true) {
            int index = s.indexOf(',', start);
            if (index == -1) {
                break;
            }
            count++;
            start = index + 1;
        }
        String[] result = new String[count + 1];
        if (count == 0) {
            result[0] = s;
        } else {
            count = 0;
            start = 0;
            while (true) {
                int index = s.indexOf(',', start);
                if (index == -1) {
                    result[count] = s.substring(start);
                    break;
                }
                result[count] = s.substring(start, index);
                count++;
                start = index + 1;
            }

        }
        return result;

    }

    public static String getVersion() {
        if (version == null) {
            version = requireNonNullElse(NvUtil.class.getPackage().getImplementationVersion(), "development");
        }
        return version;
    }
}
