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

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

public class NvUtil {
    private static Hashtable colorTable = null;
    private static Hashtable iColorTable = null;

    static {
        initColorTable();
    }

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

    public static String colorToString(NMRFxColor color) {
        if (colorTable == null) {
            initColorTable();
        }

        if (color == null) {
            return ("");
        } else {
            int alpha = color.getAlpha();
            NMRFxColor opaqueColor = color;
            if (alpha != 255) {
                opaqueColor = new NMRFxColor(color.getRed(), color.getGreen(), color.getBlue());
            }
            String colorName = (String) iColorTable.get(opaqueColor);

            if (colorName != null) {
                if (alpha != 255) {
                    colorName = colorName + " " + ((float) alpha / 255.0);
                }
                return (colorName);
            } else {
                return color.toRGBCode();
            }
        }
    }

    public static NMRFxColor color(String name) {
        NMRFxColor color = (NMRFxColor) colorTable.get(name.toLowerCase());
        if (color == null) {
            color = NMRFxColor.fromRGBCode(name);
        }
        if (color == null) {
            color = NMRFxColor.BLACK;
        }
        return color;
    }

    public static void initColorTable() {
        colorTable = new Hashtable();
        iColorTable = new Hashtable();
        colorTable.put("alice blue", new NMRFxColor(240, 248, 255));
        colorTable.put("aliceblue", new NMRFxColor(240, 248, 255));
        colorTable.put("antique white", new NMRFxColor(250, 235, 215));
        colorTable.put("antiquewhite", new NMRFxColor(250, 235, 215));
        colorTable.put("antiquewhite1", new NMRFxColor(255, 239, 219));
        colorTable.put("antiquewhite2", new NMRFxColor(238, 223, 204));
        colorTable.put("antiquewhite3", new NMRFxColor(205, 192, 176));
        colorTable.put("antiquewhite4", new NMRFxColor(139, 131, 120));
        colorTable.put("aquamarine", new NMRFxColor(127, 255, 212));
        colorTable.put("aquamarine2", new NMRFxColor(118, 238, 198));
        colorTable.put("aquamarine3", new NMRFxColor(102, 205, 170));
        colorTable.put("aquamarine4", new NMRFxColor(69, 139, 116));
        colorTable.put("azure2", new NMRFxColor(224, 238, 238));
        colorTable.put("azure3", new NMRFxColor(193, 205, 205));
        colorTable.put("azure4", new NMRFxColor(131, 139, 139));
        colorTable.put("azure", new NMRFxColor(240, 255, 255));
        colorTable.put("beige", new NMRFxColor(245, 245, 220));
        colorTable.put("bisque2", new NMRFxColor(238, 213, 183));
        colorTable.put("bisque3", new NMRFxColor(205, 183, 158));
        colorTable.put("bisque4", new NMRFxColor(139, 125, 107));
        colorTable.put("bisque", new NMRFxColor(255, 228, 196));
        colorTable.put("black", new NMRFxColor(0, 0, 0));
        colorTable.put("blanched almond", new NMRFxColor(255, 235, 205));
        colorTable.put("blanchedalmond", new NMRFxColor(255, 235, 205));
        colorTable.put("blue violet", new NMRFxColor(138, 43, 226));
        colorTable.put("blue2", new NMRFxColor(0, 0, 238));
        colorTable.put("blue3", new NMRFxColor(0, 0, 205));
        colorTable.put("blue4", new NMRFxColor(0, 0, 139));
        colorTable.put("blue", new NMRFxColor(0, 0, 255));
        colorTable.put("blueviolet", new NMRFxColor(138, 43, 226));
        colorTable.put("brown1", new NMRFxColor(255, 64, 64));
        colorTable.put("brown2", new NMRFxColor(238, 59, 59));
        colorTable.put("brown3", new NMRFxColor(205, 51, 51));
        colorTable.put("brown4", new NMRFxColor(139, 35, 35));
        colorTable.put("brown", new NMRFxColor(165, 42, 42));
        colorTable.put("burlywood1", new NMRFxColor(255, 211, 155));
        colorTable.put("burlywood2", new NMRFxColor(238, 197, 145));
        colorTable.put("burlywood3", new NMRFxColor(205, 170, 125));
        colorTable.put("burlywood4", new NMRFxColor(139, 115, 85));
        colorTable.put("burlywood", new NMRFxColor(222, 184, 135));
        colorTable.put("cadet blue", new NMRFxColor(95, 158, 160));
        colorTable.put("cadetblue", new NMRFxColor(95, 158, 160));
        colorTable.put("cadetblue1", new NMRFxColor(152, 245, 255));
        colorTable.put("cadetblue2", new NMRFxColor(142, 229, 238));
        colorTable.put("cadetblue3", new NMRFxColor(122, 197, 205));
        colorTable.put("cadetblue4", new NMRFxColor(83, 134, 139));
        colorTable.put("chartreuse", new NMRFxColor(127, 255, 0));
        colorTable.put("chartreuse2", new NMRFxColor(118, 238, 0));
        colorTable.put("chartreuse3", new NMRFxColor(102, 205, 0));
        colorTable.put("chartreuse4", new NMRFxColor(69, 139, 0));
        colorTable.put("chocolate", new NMRFxColor(210, 105, 30));
        colorTable.put("chocolate1", new NMRFxColor(255, 127, 36));
        colorTable.put("chocolate2", new NMRFxColor(238, 118, 33));
        colorTable.put("chocolate3", new NMRFxColor(205, 102, 29));
        colorTable.put("chocolate4", new NMRFxColor(139, 69, 19));
        colorTable.put("coral", new NMRFxColor(255, 127, 80));
        colorTable.put("coral1", new NMRFxColor(255, 114, 86));
        colorTable.put("coral2", new NMRFxColor(238, 106, 80));
        colorTable.put("coral3", new NMRFxColor(205, 91, 69));
        colorTable.put("coral4", new NMRFxColor(139, 62, 47));
        colorTable.put("cornflower blue", new NMRFxColor(100, 149, 237));
        colorTable.put("cornflowerblue", new NMRFxColor(100, 149, 237));
        colorTable.put("cornsilk", new NMRFxColor(255, 248, 220));
        colorTable.put("cornsilk2", new NMRFxColor(238, 232, 205));
        colorTable.put("cornsilk3", new NMRFxColor(205, 200, 177));
        colorTable.put("cornsilk4", new NMRFxColor(139, 136, 120));
        colorTable.put("cyan", new NMRFxColor(0, 255, 255));
        colorTable.put("cyan2", new NMRFxColor(0, 238, 238));
        colorTable.put("cyan3", new NMRFxColor(0, 205, 205));
        colorTable.put("cyan4", new NMRFxColor(0, 139, 139));
        colorTable.put("dark blue", new NMRFxColor(0, 0, 139));
        colorTable.put("dark cyan", new NMRFxColor(0, 139, 139));
        colorTable.put("dark goldenrod", new NMRFxColor(184, 134, 11));
        colorTable.put("dark gray", new NMRFxColor(169, 169, 169));
        colorTable.put("dark green", new NMRFxColor(0, 100, 0));
        colorTable.put("dark grey", new NMRFxColor(169, 169, 169));
        colorTable.put("dark khaki", new NMRFxColor(189, 183, 107));
        colorTable.put("dark magenta", new NMRFxColor(139, 0, 139));
        colorTable.put("dark olive green", new NMRFxColor(85, 107, 47));
        colorTable.put("dark orange", new NMRFxColor(255, 140, 0));
        colorTable.put("dark orchid", new NMRFxColor(153, 50, 204));
        colorTable.put("dark red", new NMRFxColor(139, 0, 0));
        colorTable.put("dark salmon", new NMRFxColor(233, 150, 122));
        colorTable.put("dark sea green", new NMRFxColor(143, 188, 143));
        colorTable.put("dark slate blue", new NMRFxColor(72, 61, 139));
        colorTable.put("dark slate gray", new NMRFxColor(47, 79, 79));
        colorTable.put("dark slate grey", new NMRFxColor(47, 79, 79));
        colorTable.put("dark turquoise", new NMRFxColor(0, 206, 209));
        colorTable.put("dark violet", new NMRFxColor(148, 0, 211));
        colorTable.put("darkblue", new NMRFxColor(0, 0, 139));
        colorTable.put("darkcyan", new NMRFxColor(0, 139, 139));
        colorTable.put("darkgoldenrod", new NMRFxColor(184, 134, 11));
        colorTable.put("darkgoldenrod1", new NMRFxColor(255, 185, 15));
        colorTable.put("darkgoldenrod2", new NMRFxColor(238, 173, 14));
        colorTable.put("darkgoldenrod3", new NMRFxColor(205, 149, 12));
        colorTable.put("darkgoldenrod4", new NMRFxColor(139, 101, 8));
        colorTable.put("darkgray", new NMRFxColor(169, 169, 169));
        colorTable.put("darkgreen", new NMRFxColor(0, 100, 0));
        colorTable.put("darkgrey", new NMRFxColor(169, 169, 169));
        colorTable.put("darkkhaki", new NMRFxColor(189, 183, 107));
        colorTable.put("darkmagenta", new NMRFxColor(139, 0, 139));
        colorTable.put("darkolivegreen", new NMRFxColor(85, 107, 47));
        colorTable.put("darkolivegreen1", new NMRFxColor(202, 255, 112));
        colorTable.put("darkolivegreen2", new NMRFxColor(188, 238, 104));
        colorTable.put("darkolivegreen3", new NMRFxColor(162, 205, 90));
        colorTable.put("darkolivegreen4", new NMRFxColor(110, 139, 61));
        colorTable.put("darkorange", new NMRFxColor(255, 140, 0));
        colorTable.put("darkorange1", new NMRFxColor(255, 127, 0));
        colorTable.put("darkorange2", new NMRFxColor(238, 118, 0));
        colorTable.put("darkorange3", new NMRFxColor(205, 102, 0));
        colorTable.put("darkorange4", new NMRFxColor(139, 69, 0));
        colorTable.put("darkorchid", new NMRFxColor(153, 50, 204));
        colorTable.put("darkorchid1", new NMRFxColor(191, 62, 255));
        colorTable.put("darkorchid2", new NMRFxColor(178, 58, 238));
        colorTable.put("darkorchid3", new NMRFxColor(154, 50, 205));
        colorTable.put("darkorchid4", new NMRFxColor(104, 34, 139));
        colorTable.put("darkred", new NMRFxColor(139, 0, 0));
        colorTable.put("darksalmon", new NMRFxColor(233, 150, 122));
        colorTable.put("darkseagreen", new NMRFxColor(143, 188, 143));
        colorTable.put("darkseagreen1", new NMRFxColor(193, 255, 193));
        colorTable.put("darkseagreen2", new NMRFxColor(180, 238, 180));
        colorTable.put("darkseagreen3", new NMRFxColor(155, 205, 155));
        colorTable.put("darkseagreen4", new NMRFxColor(105, 139, 105));
        colorTable.put("darkslateblue", new NMRFxColor(72, 61, 139));
        colorTable.put("darkslategray", new NMRFxColor(47, 79, 79));
        colorTable.put("darkslategray1", new NMRFxColor(151, 255, 255));
        colorTable.put("darkslategray2", new NMRFxColor(141, 238, 238));
        colorTable.put("darkslategray3", new NMRFxColor(121, 205, 205));
        colorTable.put("darkslategray4", new NMRFxColor(82, 139, 139));
        colorTable.put("darkslategrey", new NMRFxColor(47, 79, 79));
        colorTable.put("darkturquoise", new NMRFxColor(0, 206, 209));
        colorTable.put("darkviolet", new NMRFxColor(148, 0, 211));
        colorTable.put("deep pink", new NMRFxColor(255, 20, 147));
        colorTable.put("deep sky blue", new NMRFxColor(0, 191, 255));
        colorTable.put("deeppink", new NMRFxColor(255, 20, 147));
        colorTable.put("deeppink2", new NMRFxColor(238, 18, 137));
        colorTable.put("deeppink3", new NMRFxColor(205, 16, 118));
        colorTable.put("deeppink4", new NMRFxColor(139, 10, 80));
        colorTable.put("deepskyblue", new NMRFxColor(0, 191, 255));
        colorTable.put("deepskyblue2", new NMRFxColor(0, 178, 238));
        colorTable.put("deepskyblue3", new NMRFxColor(0, 154, 205));
        colorTable.put("deepskyblue4", new NMRFxColor(0, 104, 139));
        colorTable.put("dim gray", new NMRFxColor(105, 105, 105));
        colorTable.put("dim grey", new NMRFxColor(105, 105, 105));
        colorTable.put("dimgray", new NMRFxColor(105, 105, 105));
        colorTable.put("dimgrey", new NMRFxColor(105, 105, 105));
        colorTable.put("dodger blue", new NMRFxColor(30, 144, 255));
        colorTable.put("dodgerblue", new NMRFxColor(30, 144, 255));
        colorTable.put("dodgerblue2", new NMRFxColor(28, 134, 238));
        colorTable.put("dodgerblue3", new NMRFxColor(24, 116, 205));
        colorTable.put("dodgerblue4", new NMRFxColor(16, 78, 139));
        colorTable.put("firebrick", new NMRFxColor(178, 34, 34));
        colorTable.put("firebrick1", new NMRFxColor(255, 48, 48));
        colorTable.put("firebrick2", new NMRFxColor(238, 44, 44));
        colorTable.put("firebrick3", new NMRFxColor(205, 38, 38));
        colorTable.put("firebrick4", new NMRFxColor(139, 26, 26));
        colorTable.put("floral white", new NMRFxColor(255, 250, 240));
        colorTable.put("floralwhite", new NMRFxColor(255, 250, 240));
        colorTable.put("forest green", new NMRFxColor(34, 139, 34));
        colorTable.put("forestgreen", new NMRFxColor(34, 139, 34));
        colorTable.put("gainsboro", new NMRFxColor(220, 220, 220));
        colorTable.put("ghost white", new NMRFxColor(248, 248, 255));
        colorTable.put("ghostwhite", new NMRFxColor(248, 248, 255));
        colorTable.put("gold", new NMRFxColor(255, 215, 0));
        colorTable.put("gold2", new NMRFxColor(238, 201, 0));
        colorTable.put("gold3", new NMRFxColor(205, 173, 0));
        colorTable.put("gold4", new NMRFxColor(139, 117, 0));
        colorTable.put("goldenrod", new NMRFxColor(218, 165, 32));
        colorTable.put("goldenrod1", new NMRFxColor(255, 193, 37));
        colorTable.put("goldenrod2", new NMRFxColor(238, 180, 34));
        colorTable.put("goldenrod3", new NMRFxColor(205, 155, 29));
        colorTable.put("goldenrod4", new NMRFxColor(139, 105, 20));
        colorTable.put("gray", new NMRFxColor(190, 190, 190));
        colorTable.put("gray0", new NMRFxColor(0, 0, 0));
        colorTable.put("gray1", new NMRFxColor(3, 3, 3));
        colorTable.put("gray10", new NMRFxColor(26, 26, 26));
        colorTable.put("gray100", new NMRFxColor(255, 255, 255));
        colorTable.put("gray11", new NMRFxColor(28, 28, 28));
        colorTable.put("gray12", new NMRFxColor(31, 31, 31));
        colorTable.put("gray13", new NMRFxColor(33, 33, 33));
        colorTable.put("gray14", new NMRFxColor(36, 36, 36));
        colorTable.put("gray15", new NMRFxColor(38, 38, 38));
        colorTable.put("gray16", new NMRFxColor(41, 41, 41));
        colorTable.put("gray17", new NMRFxColor(43, 43, 43));
        colorTable.put("gray18", new NMRFxColor(46, 46, 46));
        colorTable.put("gray19", new NMRFxColor(48, 48, 48));
        colorTable.put("gray2", new NMRFxColor(5, 5, 5));
        colorTable.put("gray20", new NMRFxColor(51, 51, 51));
        colorTable.put("gray21", new NMRFxColor(54, 54, 54));
        colorTable.put("gray22", new NMRFxColor(56, 56, 56));
        colorTable.put("gray23", new NMRFxColor(59, 59, 59));
        colorTable.put("gray24", new NMRFxColor(61, 61, 61));
        colorTable.put("gray25", new NMRFxColor(64, 64, 64));
        colorTable.put("gray26", new NMRFxColor(66, 66, 66));
        colorTable.put("gray27", new NMRFxColor(69, 69, 69));
        colorTable.put("gray28", new NMRFxColor(71, 71, 71));
        colorTable.put("gray29", new NMRFxColor(74, 74, 74));
        colorTable.put("gray3", new NMRFxColor(8, 8, 8));
        colorTable.put("gray30", new NMRFxColor(77, 77, 77));
        colorTable.put("gray31", new NMRFxColor(79, 79, 79));
        colorTable.put("gray32", new NMRFxColor(82, 82, 82));
        colorTable.put("gray33", new NMRFxColor(84, 84, 84));
        colorTable.put("gray34", new NMRFxColor(87, 87, 87));
        colorTable.put("gray35", new NMRFxColor(89, 89, 89));
        colorTable.put("gray36", new NMRFxColor(92, 92, 92));
        colorTable.put("gray37", new NMRFxColor(94, 94, 94));
        colorTable.put("gray38", new NMRFxColor(97, 97, 97));
        colorTable.put("gray39", new NMRFxColor(99, 99, 99));
        colorTable.put("gray4", new NMRFxColor(10, 10, 10));
        colorTable.put("gray40", new NMRFxColor(102, 102, 102));
        colorTable.put("gray41", new NMRFxColor(105, 105, 105));
        colorTable.put("gray42", new NMRFxColor(107, 107, 107));
        colorTable.put("gray43", new NMRFxColor(110, 110, 110));
        colorTable.put("gray44", new NMRFxColor(112, 112, 112));
        colorTable.put("gray45", new NMRFxColor(115, 115, 115));
        colorTable.put("gray46", new NMRFxColor(117, 117, 117));
        colorTable.put("gray47", new NMRFxColor(120, 120, 120));
        colorTable.put("gray48", new NMRFxColor(122, 122, 122));
        colorTable.put("gray49", new NMRFxColor(125, 125, 125));
        colorTable.put("gray5", new NMRFxColor(13, 13, 13));
        colorTable.put("gray50", new NMRFxColor(127, 127, 127));
        colorTable.put("gray51", new NMRFxColor(130, 130, 130));
        colorTable.put("gray52", new NMRFxColor(133, 133, 133));
        colorTable.put("gray53", new NMRFxColor(135, 135, 135));
        colorTable.put("gray54", new NMRFxColor(138, 138, 138));
        colorTable.put("gray55", new NMRFxColor(140, 140, 140));
        colorTable.put("gray56", new NMRFxColor(143, 143, 143));
        colorTable.put("gray57", new NMRFxColor(145, 145, 145));
        colorTable.put("gray58", new NMRFxColor(148, 148, 148));
        colorTable.put("gray59", new NMRFxColor(150, 150, 150));
        colorTable.put("gray6", new NMRFxColor(15, 15, 15));
        colorTable.put("gray60", new NMRFxColor(153, 153, 153));
        colorTable.put("gray61", new NMRFxColor(156, 156, 156));
        colorTable.put("gray62", new NMRFxColor(158, 158, 158));
        colorTable.put("gray63", new NMRFxColor(161, 161, 161));
        colorTable.put("gray64", new NMRFxColor(163, 163, 163));
        colorTable.put("gray65", new NMRFxColor(166, 166, 166));
        colorTable.put("gray66", new NMRFxColor(168, 168, 168));
        colorTable.put("gray67", new NMRFxColor(171, 171, 171));
        colorTable.put("gray68", new NMRFxColor(173, 173, 173));
        colorTable.put("gray69", new NMRFxColor(176, 176, 176));
        colorTable.put("gray7", new NMRFxColor(18, 18, 18));
        colorTable.put("gray70", new NMRFxColor(179, 179, 179));
        colorTable.put("gray71", new NMRFxColor(181, 181, 181));
        colorTable.put("gray72", new NMRFxColor(184, 184, 184));
        colorTable.put("gray73", new NMRFxColor(186, 186, 186));
        colorTable.put("gray74", new NMRFxColor(189, 189, 189));
        colorTable.put("gray75", new NMRFxColor(191, 191, 191));
        colorTable.put("gray76", new NMRFxColor(194, 194, 194));
        colorTable.put("gray77", new NMRFxColor(196, 196, 196));
        colorTable.put("gray78", new NMRFxColor(199, 199, 199));
        colorTable.put("gray79", new NMRFxColor(201, 201, 201));
        colorTable.put("gray8", new NMRFxColor(20, 20, 20));
        colorTable.put("gray80", new NMRFxColor(204, 204, 204));
        colorTable.put("gray81", new NMRFxColor(207, 207, 207));
        colorTable.put("gray82", new NMRFxColor(209, 209, 209));
        colorTable.put("gray83", new NMRFxColor(212, 212, 212));
        colorTable.put("gray84", new NMRFxColor(214, 214, 214));
        colorTable.put("gray85", new NMRFxColor(217, 217, 217));
        colorTable.put("gray86", new NMRFxColor(219, 219, 219));
        colorTable.put("gray87", new NMRFxColor(222, 222, 222));
        colorTable.put("gray88", new NMRFxColor(224, 224, 224));
        colorTable.put("gray89", new NMRFxColor(227, 227, 227));
        colorTable.put("gray9", new NMRFxColor(23, 23, 23));
        colorTable.put("gray90", new NMRFxColor(229, 229, 229));
        colorTable.put("gray91", new NMRFxColor(232, 232, 232));
        colorTable.put("gray92", new NMRFxColor(235, 235, 235));
        colorTable.put("gray93", new NMRFxColor(237, 237, 237));
        colorTable.put("gray94", new NMRFxColor(240, 240, 240));
        colorTable.put("gray95", new NMRFxColor(242, 242, 242));
        colorTable.put("gray96", new NMRFxColor(245, 245, 245));
        colorTable.put("gray97", new NMRFxColor(247, 247, 247));
        colorTable.put("gray98", new NMRFxColor(250, 250, 250));
        colorTable.put("gray99", new NMRFxColor(252, 252, 252));
        colorTable.put("green yellow", new NMRFxColor(173, 255, 47));
        colorTable.put("green2", new NMRFxColor(0, 238, 0));
        colorTable.put("green3", new NMRFxColor(0, 205, 0));
        colorTable.put("green4", new NMRFxColor(0, 139, 0));
        colorTable.put("green", new NMRFxColor(0, 255, 0));
        colorTable.put("greenyellow", new NMRFxColor(173, 255, 47));
        colorTable.put("grey", new NMRFxColor(190, 190, 190));
        colorTable.put("grey0", new NMRFxColor(0, 0, 0));
        colorTable.put("grey1", new NMRFxColor(3, 3, 3));
        colorTable.put("grey10", new NMRFxColor(26, 26, 26));
        colorTable.put("grey100", new NMRFxColor(255, 255, 255));
        colorTable.put("grey11", new NMRFxColor(28, 28, 28));
        colorTable.put("grey12", new NMRFxColor(31, 31, 31));
        colorTable.put("grey13", new NMRFxColor(33, 33, 33));
        colorTable.put("grey14", new NMRFxColor(36, 36, 36));
        colorTable.put("grey15", new NMRFxColor(38, 38, 38));
        colorTable.put("grey16", new NMRFxColor(41, 41, 41));
        colorTable.put("grey17", new NMRFxColor(43, 43, 43));
        colorTable.put("grey18", new NMRFxColor(46, 46, 46));
        colorTable.put("grey19", new NMRFxColor(48, 48, 48));
        colorTable.put("grey2", new NMRFxColor(5, 5, 5));
        colorTable.put("grey20", new NMRFxColor(51, 51, 51));
        colorTable.put("grey21", new NMRFxColor(54, 54, 54));
        colorTable.put("grey22", new NMRFxColor(56, 56, 56));
        colorTable.put("grey23", new NMRFxColor(59, 59, 59));
        colorTable.put("grey24", new NMRFxColor(61, 61, 61));
        colorTable.put("grey25", new NMRFxColor(64, 64, 64));
        colorTable.put("grey26", new NMRFxColor(66, 66, 66));
        colorTable.put("grey27", new NMRFxColor(69, 69, 69));
        colorTable.put("grey28", new NMRFxColor(71, 71, 71));
        colorTable.put("grey29", new NMRFxColor(74, 74, 74));
        colorTable.put("grey3", new NMRFxColor(8, 8, 8));
        colorTable.put("grey30", new NMRFxColor(77, 77, 77));
        colorTable.put("grey31", new NMRFxColor(79, 79, 79));
        colorTable.put("grey32", new NMRFxColor(82, 82, 82));
        colorTable.put("grey33", new NMRFxColor(84, 84, 84));
        colorTable.put("grey34", new NMRFxColor(87, 87, 87));
        colorTable.put("grey35", new NMRFxColor(89, 89, 89));
        colorTable.put("grey36", new NMRFxColor(92, 92, 92));
        colorTable.put("grey37", new NMRFxColor(94, 94, 94));
        colorTable.put("grey38", new NMRFxColor(97, 97, 97));
        colorTable.put("grey39", new NMRFxColor(99, 99, 99));
        colorTable.put("grey4", new NMRFxColor(10, 10, 10));
        colorTable.put("grey40", new NMRFxColor(102, 102, 102));
        colorTable.put("grey41", new NMRFxColor(105, 105, 105));
        colorTable.put("grey42", new NMRFxColor(107, 107, 107));
        colorTable.put("grey43", new NMRFxColor(110, 110, 110));
        colorTable.put("grey44", new NMRFxColor(112, 112, 112));
        colorTable.put("grey45", new NMRFxColor(115, 115, 115));
        colorTable.put("grey46", new NMRFxColor(117, 117, 117));
        colorTable.put("grey47", new NMRFxColor(120, 120, 120));
        colorTable.put("grey48", new NMRFxColor(122, 122, 122));
        colorTable.put("grey49", new NMRFxColor(125, 125, 125));
        colorTable.put("grey5", new NMRFxColor(13, 13, 13));
        colorTable.put("grey50", new NMRFxColor(127, 127, 127));
        colorTable.put("grey51", new NMRFxColor(130, 130, 130));
        colorTable.put("grey52", new NMRFxColor(133, 133, 133));
        colorTable.put("grey53", new NMRFxColor(135, 135, 135));
        colorTable.put("grey54", new NMRFxColor(138, 138, 138));
        colorTable.put("grey55", new NMRFxColor(140, 140, 140));
        colorTable.put("grey56", new NMRFxColor(143, 143, 143));
        colorTable.put("grey57", new NMRFxColor(145, 145, 145));
        colorTable.put("grey58", new NMRFxColor(148, 148, 148));
        colorTable.put("grey59", new NMRFxColor(150, 150, 150));
        colorTable.put("grey6", new NMRFxColor(15, 15, 15));
        colorTable.put("grey60", new NMRFxColor(153, 153, 153));
        colorTable.put("grey61", new NMRFxColor(156, 156, 156));
        colorTable.put("grey62", new NMRFxColor(158, 158, 158));
        colorTable.put("grey63", new NMRFxColor(161, 161, 161));
        colorTable.put("grey64", new NMRFxColor(163, 163, 163));
        colorTable.put("grey65", new NMRFxColor(166, 166, 166));
        colorTable.put("grey66", new NMRFxColor(168, 168, 168));
        colorTable.put("grey67", new NMRFxColor(171, 171, 171));
        colorTable.put("grey68", new NMRFxColor(173, 173, 173));
        colorTable.put("grey69", new NMRFxColor(176, 176, 176));
        colorTable.put("grey7", new NMRFxColor(18, 18, 18));
        colorTable.put("grey70", new NMRFxColor(179, 179, 179));
        colorTable.put("grey71", new NMRFxColor(181, 181, 181));
        colorTable.put("grey72", new NMRFxColor(184, 184, 184));
        colorTable.put("grey73", new NMRFxColor(186, 186, 186));
        colorTable.put("grey74", new NMRFxColor(189, 189, 189));
        colorTable.put("grey75", new NMRFxColor(191, 191, 191));
        colorTable.put("grey76", new NMRFxColor(194, 194, 194));
        colorTable.put("grey77", new NMRFxColor(196, 196, 196));
        colorTable.put("grey78", new NMRFxColor(199, 199, 199));
        colorTable.put("grey79", new NMRFxColor(201, 201, 201));
        colorTable.put("grey8", new NMRFxColor(20, 20, 20));
        colorTable.put("grey80", new NMRFxColor(204, 204, 204));
        colorTable.put("grey81", new NMRFxColor(207, 207, 207));
        colorTable.put("grey82", new NMRFxColor(209, 209, 209));
        colorTable.put("grey83", new NMRFxColor(212, 212, 212));
        colorTable.put("grey84", new NMRFxColor(214, 214, 214));
        colorTable.put("grey85", new NMRFxColor(217, 217, 217));
        colorTable.put("grey86", new NMRFxColor(219, 219, 219));
        colorTable.put("grey87", new NMRFxColor(222, 222, 222));
        colorTable.put("grey88", new NMRFxColor(224, 224, 224));
        colorTable.put("grey89", new NMRFxColor(227, 227, 227));
        colorTable.put("grey9", new NMRFxColor(23, 23, 23));
        colorTable.put("grey90", new NMRFxColor(229, 229, 229));
        colorTable.put("grey91", new NMRFxColor(232, 232, 232));
        colorTable.put("grey92", new NMRFxColor(235, 235, 235));
        colorTable.put("grey93", new NMRFxColor(237, 237, 237));
        colorTable.put("grey94", new NMRFxColor(240, 240, 240));
        colorTable.put("grey95", new NMRFxColor(242, 242, 242));
        colorTable.put("grey96", new NMRFxColor(245, 245, 245));
        colorTable.put("grey97", new NMRFxColor(247, 247, 247));
        colorTable.put("grey98", new NMRFxColor(250, 250, 250));
        colorTable.put("grey99", new NMRFxColor(252, 252, 252));
        colorTable.put("honeydew", new NMRFxColor(240, 255, 240));
        colorTable.put("honeydew2", new NMRFxColor(224, 238, 224));
        colorTable.put("honeydew3", new NMRFxColor(193, 205, 193));
        colorTable.put("honeydew4", new NMRFxColor(131, 139, 131));
        colorTable.put("hot pink", new NMRFxColor(255, 105, 180));
        colorTable.put("hotpink", new NMRFxColor(255, 105, 180));
        colorTable.put("hotpink1", new NMRFxColor(255, 110, 180));
        colorTable.put("hotpink2", new NMRFxColor(238, 106, 167));
        colorTable.put("hotpink3", new NMRFxColor(205, 96, 144));
        colorTable.put("hotpink4", new NMRFxColor(139, 58, 98));
        colorTable.put("indian red", new NMRFxColor(205, 92, 92));
        colorTable.put("indianred", new NMRFxColor(205, 92, 92));
        colorTable.put("indianred1", new NMRFxColor(255, 106, 106));
        colorTable.put("indianred2", new NMRFxColor(238, 99, 99));
        colorTable.put("indianred3", new NMRFxColor(205, 85, 85));
        colorTable.put("indianred4", new NMRFxColor(139, 58, 58));
        colorTable.put("ivory", new NMRFxColor(255, 255, 240));
        colorTable.put("ivory2", new NMRFxColor(238, 238, 224));
        colorTable.put("ivory3", new NMRFxColor(205, 205, 193));
        colorTable.put("ivory4", new NMRFxColor(139, 139, 131));
        colorTable.put("khaki", new NMRFxColor(240, 230, 140));
        colorTable.put("khaki1", new NMRFxColor(255, 246, 143));
        colorTable.put("khaki2", new NMRFxColor(238, 230, 133));
        colorTable.put("khaki3", new NMRFxColor(205, 198, 115));
        colorTable.put("khaki4", new NMRFxColor(139, 134, 78));
        colorTable.put("lavender", new NMRFxColor(230, 230, 250));
        colorTable.put("lavender blush", new NMRFxColor(255, 240, 245));
        colorTable.put("lavenderblush", new NMRFxColor(255, 240, 245));
        colorTable.put("lavenderblush2", new NMRFxColor(238, 224, 229));
        colorTable.put("lavenderblush3", new NMRFxColor(205, 193, 197));
        colorTable.put("lavenderblush4", new NMRFxColor(139, 131, 134));
        colorTable.put("lawn green", new NMRFxColor(124, 252, 0));
        colorTable.put("lawngreen", new NMRFxColor(124, 252, 0));
        colorTable.put("lemon chiffon", new NMRFxColor(255, 250, 205));
        colorTable.put("lemonchiffon", new NMRFxColor(255, 250, 205));
        colorTable.put("lemonchiffon2", new NMRFxColor(238, 233, 191));
        colorTable.put("lemonchiffon3", new NMRFxColor(205, 201, 165));
        colorTable.put("lemonchiffon4", new NMRFxColor(139, 137, 112));
        colorTable.put("light blue", new NMRFxColor(173, 216, 230));
        colorTable.put("light coral", new NMRFxColor(240, 128, 128));
        colorTable.put("light cyan", new NMRFxColor(224, 255, 255));
        colorTable.put("light goldenrod", new NMRFxColor(238, 221, 130));
        colorTable.put("light goldenrod yellow", new NMRFxColor(250, 250, 210));
        colorTable.put("light gray", new NMRFxColor(211, 211, 211));
        colorTable.put("light green", new NMRFxColor(144, 238, 144));
        colorTable.put("light grey", new NMRFxColor(211, 211, 211));
        colorTable.put("light pink", new NMRFxColor(255, 182, 193));
        colorTable.put("light salmon", new NMRFxColor(255, 160, 122));
        colorTable.put("light sea green", new NMRFxColor(32, 178, 170));
        colorTable.put("light sky blue", new NMRFxColor(135, 206, 250));
        colorTable.put("light slate blue", new NMRFxColor(132, 112, 255));
        colorTable.put("light slate gray", new NMRFxColor(119, 136, 153));
        colorTable.put("light slate grey", new NMRFxColor(119, 136, 153));
        colorTable.put("light steel blue", new NMRFxColor(176, 196, 222));
        colorTable.put("light yellow", new NMRFxColor(255, 255, 224));
        colorTable.put("lightblue", new NMRFxColor(173, 216, 230));
        colorTable.put("lightblue1", new NMRFxColor(191, 239, 255));
        colorTable.put("lightblue2", new NMRFxColor(178, 223, 238));
        colorTable.put("lightblue3", new NMRFxColor(154, 192, 205));
        colorTable.put("lightblue4", new NMRFxColor(104, 131, 139));
        colorTable.put("lightcoral", new NMRFxColor(240, 128, 128));
        colorTable.put("lightcyan", new NMRFxColor(224, 255, 255));
        colorTable.put("lightcyan2", new NMRFxColor(209, 238, 238));
        colorTable.put("lightcyan3", new NMRFxColor(180, 205, 205));
        colorTable.put("lightcyan4", new NMRFxColor(122, 139, 139));
        colorTable.put("lightgoldenrod", new NMRFxColor(238, 221, 130));
        colorTable.put("lightgoldenrod1", new NMRFxColor(255, 236, 139));
        colorTable.put("lightgoldenrod2", new NMRFxColor(238, 220, 130));
        colorTable.put("lightgoldenrod3", new NMRFxColor(205, 190, 112));
        colorTable.put("lightgoldenrod4", new NMRFxColor(139, 129, 76));
        colorTable.put("lightgoldenrodyellow", new NMRFxColor(250, 250, 210));
        colorTable.put("lightgray", new NMRFxColor(211, 211, 211));
        colorTable.put("lightgreen", new NMRFxColor(144, 238, 144));
        colorTable.put("lightgrey", new NMRFxColor(211, 211, 211));
        colorTable.put("lightpink", new NMRFxColor(255, 182, 193));
        colorTable.put("lightpink1", new NMRFxColor(255, 174, 185));
        colorTable.put("lightpink2", new NMRFxColor(238, 162, 173));
        colorTable.put("lightpink3", new NMRFxColor(205, 140, 149));
        colorTable.put("lightpink4", new NMRFxColor(139, 95, 101));
        colorTable.put("lightsalmon", new NMRFxColor(255, 160, 122));
        colorTable.put("lightsalmon2", new NMRFxColor(238, 149, 114));
        colorTable.put("lightsalmon3", new NMRFxColor(205, 129, 98));
        colorTable.put("lightsalmon4", new NMRFxColor(139, 87, 66));
        colorTable.put("lightseagreen", new NMRFxColor(32, 178, 170));
        colorTable.put("lightskyblue", new NMRFxColor(135, 206, 250));
        colorTable.put("lightskyblue1", new NMRFxColor(176, 226, 255));
        colorTable.put("lightskyblue2", new NMRFxColor(164, 211, 238));
        colorTable.put("lightskyblue3", new NMRFxColor(141, 182, 205));
        colorTable.put("lightskyblue4", new NMRFxColor(96, 123, 139));
        colorTable.put("lightslateblue", new NMRFxColor(132, 112, 255));
        colorTable.put("lightslategray", new NMRFxColor(119, 136, 153));
        colorTable.put("lightslategrey", new NMRFxColor(119, 136, 153));
        colorTable.put("lightsteelblue", new NMRFxColor(176, 196, 222));
        colorTable.put("lightsteelblue1", new NMRFxColor(202, 225, 255));
        colorTable.put("lightsteelblue2", new NMRFxColor(188, 210, 238));
        colorTable.put("lightsteelblue3", new NMRFxColor(162, 181, 205));
        colorTable.put("lightsteelblue4", new NMRFxColor(110, 123, 139));
        colorTable.put("lightyellow", new NMRFxColor(255, 255, 224));
        colorTable.put("lightyellow2", new NMRFxColor(238, 238, 209));
        colorTable.put("lightyellow3", new NMRFxColor(205, 205, 180));
        colorTable.put("lightyellow4", new NMRFxColor(139, 139, 122));
        colorTable.put("lime green", new NMRFxColor(50, 205, 50));
        colorTable.put("limegreen", new NMRFxColor(50, 205, 50));
        colorTable.put("linen", new NMRFxColor(250, 240, 230));
        colorTable.put("magenta", new NMRFxColor(255, 0, 255));
        colorTable.put("magenta2", new NMRFxColor(238, 0, 238));
        colorTable.put("magenta3", new NMRFxColor(205, 0, 205));
        colorTable.put("magenta4", new NMRFxColor(139, 0, 139));
        colorTable.put("maroon", new NMRFxColor(176, 48, 96));
        colorTable.put("maroon1", new NMRFxColor(255, 52, 179));
        colorTable.put("maroon2", new NMRFxColor(238, 48, 167));
        colorTable.put("maroon3", new NMRFxColor(205, 41, 144));
        colorTable.put("maroon4", new NMRFxColor(139, 28, 98));
        colorTable.put("medium aquamarine", new NMRFxColor(102, 205, 170));
        colorTable.put("medium blue", new NMRFxColor(0, 0, 205));
        colorTable.put("medium orchid", new NMRFxColor(186, 85, 211));
        colorTable.put("medium purple", new NMRFxColor(147, 112, 219));
        colorTable.put("medium sea green", new NMRFxColor(60, 179, 113));
        colorTable.put("medium slate blue", new NMRFxColor(123, 104, 238));
        colorTable.put("medium spring green", new NMRFxColor(0, 250, 154));
        colorTable.put("medium turquoise", new NMRFxColor(72, 209, 204));
        colorTable.put("medium violet red", new NMRFxColor(199, 21, 133));
        colorTable.put("mediumaquamarine", new NMRFxColor(102, 205, 170));
        colorTable.put("mediumblue", new NMRFxColor(0, 0, 205));
        colorTable.put("mediumorchid", new NMRFxColor(186, 85, 211));
        colorTable.put("mediumorchid1", new NMRFxColor(224, 102, 255));
        colorTable.put("mediumorchid2", new NMRFxColor(209, 95, 238));
        colorTable.put("mediumorchid3", new NMRFxColor(180, 82, 205));
        colorTable.put("mediumorchid4", new NMRFxColor(122, 55, 139));
        colorTable.put("mediumpurple", new NMRFxColor(147, 112, 219));
        colorTable.put("mediumpurple1", new NMRFxColor(171, 130, 255));
        colorTable.put("mediumpurple2", new NMRFxColor(159, 121, 238));
        colorTable.put("mediumpurple3", new NMRFxColor(137, 104, 205));
        colorTable.put("mediumpurple4", new NMRFxColor(93, 71, 139));
        colorTable.put("mediumseagreen", new NMRFxColor(60, 179, 113));
        colorTable.put("mediumslateblue", new NMRFxColor(123, 104, 238));
        colorTable.put("mediumspringgreen", new NMRFxColor(0, 250, 154));
        colorTable.put("mediumturquoise", new NMRFxColor(72, 209, 204));
        colorTable.put("mediumvioletred", new NMRFxColor(199, 21, 133));
        colorTable.put("midnight blue", new NMRFxColor(25, 25, 112));
        colorTable.put("midnightblue", new NMRFxColor(25, 25, 112));
        colorTable.put("mint cream", new NMRFxColor(245, 255, 250));
        colorTable.put("mintcream", new NMRFxColor(245, 255, 250));
        colorTable.put("misty rose", new NMRFxColor(255, 228, 225));
        colorTable.put("mistyrose", new NMRFxColor(255, 228, 225));
        colorTable.put("mistyrose2", new NMRFxColor(238, 213, 210));
        colorTable.put("mistyrose3", new NMRFxColor(205, 183, 181));
        colorTable.put("mistyrose4", new NMRFxColor(139, 125, 123));
        colorTable.put("moccasin", new NMRFxColor(255, 228, 181));
        colorTable.put("navajo white", new NMRFxColor(255, 222, 173));
        colorTable.put("navajowhite", new NMRFxColor(255, 222, 173));
        colorTable.put("navajowhite2", new NMRFxColor(238, 207, 161));
        colorTable.put("navajowhite3", new NMRFxColor(205, 179, 139));
        colorTable.put("navajowhite4", new NMRFxColor(139, 121, 94));
        colorTable.put("navy", new NMRFxColor(0, 0, 128));
        colorTable.put("navy blue", new NMRFxColor(0, 0, 128));
        colorTable.put("navyblue", new NMRFxColor(0, 0, 128));
        colorTable.put("old lace", new NMRFxColor(253, 245, 230));
        colorTable.put("oldlace", new NMRFxColor(253, 245, 230));
        colorTable.put("olive drab", new NMRFxColor(107, 142, 35));
        colorTable.put("olivedrab", new NMRFxColor(107, 142, 35));
        colorTable.put("olivedrab1", new NMRFxColor(192, 255, 62));
        colorTable.put("olivedrab2", new NMRFxColor(179, 238, 58));
        colorTable.put("olivedrab3", new NMRFxColor(154, 205, 50));
        colorTable.put("olivedrab4", new NMRFxColor(105, 139, 34));
        colorTable.put("orange", new NMRFxColor(255, 165, 0));
        colorTable.put("orange red", new NMRFxColor(255, 69, 0));
        colorTable.put("orange2", new NMRFxColor(238, 154, 0));
        colorTable.put("orange3", new NMRFxColor(205, 133, 0));
        colorTable.put("orange4", new NMRFxColor(139, 90, 0));
        colorTable.put("orangered", new NMRFxColor(255, 69, 0));
        colorTable.put("orangered2", new NMRFxColor(238, 64, 0));
        colorTable.put("orangered3", new NMRFxColor(205, 55, 0));
        colorTable.put("orangered4", new NMRFxColor(139, 37, 0));
        colorTable.put("orchid", new NMRFxColor(218, 112, 214));
        colorTable.put("orchid1", new NMRFxColor(255, 131, 250));
        colorTable.put("orchid2", new NMRFxColor(238, 122, 233));
        colorTable.put("orchid3", new NMRFxColor(205, 105, 201));
        colorTable.put("orchid4", new NMRFxColor(139, 71, 137));
        colorTable.put("pale goldenrod", new NMRFxColor(238, 232, 170));
        colorTable.put("pale green", new NMRFxColor(152, 251, 152));
        colorTable.put("pale turquoise", new NMRFxColor(175, 238, 238));
        colorTable.put("pale violet red", new NMRFxColor(219, 112, 147));
        colorTable.put("palegoldenrod", new NMRFxColor(238, 232, 170));
        colorTable.put("palegreen", new NMRFxColor(152, 251, 152));
        colorTable.put("palegreen1", new NMRFxColor(154, 255, 154));
        colorTable.put("palegreen2", new NMRFxColor(144, 238, 144));
        colorTable.put("palegreen3", new NMRFxColor(124, 205, 124));
        colorTable.put("palegreen4", new NMRFxColor(84, 139, 84));
        colorTable.put("paleturquoise", new NMRFxColor(175, 238, 238));
        colorTable.put("paleturquoise1", new NMRFxColor(187, 255, 255));
        colorTable.put("paleturquoise2", new NMRFxColor(174, 238, 238));
        colorTable.put("paleturquoise3", new NMRFxColor(150, 205, 205));
        colorTable.put("paleturquoise4", new NMRFxColor(102, 139, 139));
        colorTable.put("palevioletred", new NMRFxColor(219, 112, 147));
        colorTable.put("palevioletred1", new NMRFxColor(255, 130, 171));
        colorTable.put("palevioletred2", new NMRFxColor(238, 121, 159));
        colorTable.put("palevioletred3", new NMRFxColor(205, 104, 137));
        colorTable.put("palevioletred4", new NMRFxColor(139, 71, 93));
        colorTable.put("papaya whip", new NMRFxColor(255, 239, 213));
        colorTable.put("papayawhip", new NMRFxColor(255, 239, 213));
        colorTable.put("peach puff", new NMRFxColor(255, 218, 185));
        colorTable.put("peachpuff", new NMRFxColor(255, 218, 185));
        colorTable.put("peachpuff2", new NMRFxColor(238, 203, 173));
        colorTable.put("peachpuff3", new NMRFxColor(205, 175, 149));
        colorTable.put("peachpuff4", new NMRFxColor(139, 119, 101));
        colorTable.put("peru", new NMRFxColor(205, 133, 63));
        colorTable.put("pink", new NMRFxColor(255, 192, 203));
        colorTable.put("pink1", new NMRFxColor(255, 181, 197));
        colorTable.put("pink2", new NMRFxColor(238, 169, 184));
        colorTable.put("pink3", new NMRFxColor(205, 145, 158));
        colorTable.put("pink4", new NMRFxColor(139, 99, 108));
        colorTable.put("plum", new NMRFxColor(221, 160, 221));
        colorTable.put("plum1", new NMRFxColor(255, 187, 255));
        colorTable.put("plum2", new NMRFxColor(238, 174, 238));
        colorTable.put("plum3", new NMRFxColor(205, 150, 205));
        colorTable.put("plum4", new NMRFxColor(139, 102, 139));
        colorTable.put("powder blue", new NMRFxColor(176, 224, 230));
        colorTable.put("powderblue", new NMRFxColor(176, 224, 230));
        colorTable.put("purple", new NMRFxColor(160, 32, 240));
        colorTable.put("purple1", new NMRFxColor(155, 48, 255));
        colorTable.put("purple2", new NMRFxColor(145, 44, 238));
        colorTable.put("purple3", new NMRFxColor(125, 38, 205));
        colorTable.put("purple4", new NMRFxColor(85, 26, 139));
        colorTable.put("red", new NMRFxColor(255, 0, 0));
        colorTable.put("red2", new NMRFxColor(238, 0, 0));
        colorTable.put("red3", new NMRFxColor(205, 0, 0));
        colorTable.put("red4", new NMRFxColor(139, 0, 0));
        colorTable.put("rosy brown", new NMRFxColor(188, 143, 143));
        colorTable.put("rosybrown", new NMRFxColor(188, 143, 143));
        colorTable.put("rosybrown1", new NMRFxColor(255, 193, 193));
        colorTable.put("rosybrown2", new NMRFxColor(238, 180, 180));
        colorTable.put("rosybrown3", new NMRFxColor(205, 155, 155));
        colorTable.put("rosybrown4", new NMRFxColor(139, 105, 105));
        colorTable.put("royal blue", new NMRFxColor(65, 105, 225));
        colorTable.put("royalblue", new NMRFxColor(65, 105, 225));
        colorTable.put("royalblue1", new NMRFxColor(72, 118, 255));
        colorTable.put("royalblue2", new NMRFxColor(67, 110, 238));
        colorTable.put("royalblue3", new NMRFxColor(58, 95, 205));
        colorTable.put("royalblue4", new NMRFxColor(39, 64, 139));
        colorTable.put("saddle brown", new NMRFxColor(139, 69, 19));
        colorTable.put("saddlebrown", new NMRFxColor(139, 69, 19));
        colorTable.put("salmon", new NMRFxColor(250, 128, 114));
        colorTable.put("salmon1", new NMRFxColor(255, 140, 105));
        colorTable.put("salmon2", new NMRFxColor(238, 130, 98));
        colorTable.put("salmon3", new NMRFxColor(205, 112, 84));
        colorTable.put("salmon4", new NMRFxColor(139, 76, 57));
        colorTable.put("sandy brown", new NMRFxColor(244, 164, 96));
        colorTable.put("sandybrown", new NMRFxColor(244, 164, 96));
        colorTable.put("sea green", new NMRFxColor(46, 139, 87));
        colorTable.put("seagreen", new NMRFxColor(46, 139, 87));
        colorTable.put("seagreen1", new NMRFxColor(84, 255, 159));
        colorTable.put("seagreen2", new NMRFxColor(78, 238, 148));
        colorTable.put("seagreen3", new NMRFxColor(67, 205, 128));
        colorTable.put("seagreen4", new NMRFxColor(46, 139, 87));
        colorTable.put("seashell", new NMRFxColor(255, 245, 238));
        colorTable.put("seashell2", new NMRFxColor(238, 229, 222));
        colorTable.put("seashell3", new NMRFxColor(205, 197, 191));
        colorTable.put("seashell4", new NMRFxColor(139, 134, 130));
        colorTable.put("sienna", new NMRFxColor(160, 82, 45));
        colorTable.put("sienna1", new NMRFxColor(255, 130, 71));
        colorTable.put("sienna2", new NMRFxColor(238, 121, 66));
        colorTable.put("sienna3", new NMRFxColor(205, 104, 57));
        colorTable.put("sienna4", new NMRFxColor(139, 71, 38));
        colorTable.put("sky blue", new NMRFxColor(135, 206, 235));
        colorTable.put("skyblue", new NMRFxColor(135, 206, 235));
        colorTable.put("skyblue1", new NMRFxColor(135, 206, 255));
        colorTable.put("skyblue2", new NMRFxColor(126, 192, 238));
        colorTable.put("skyblue3", new NMRFxColor(108, 166, 205));
        colorTable.put("skyblue4", new NMRFxColor(74, 112, 139));
        colorTable.put("slate blue", new NMRFxColor(106, 90, 205));
        colorTable.put("slate gray", new NMRFxColor(112, 128, 144));
        colorTable.put("slate grey", new NMRFxColor(112, 128, 144));
        colorTable.put("slateblue", new NMRFxColor(106, 90, 205));
        colorTable.put("slateblue1", new NMRFxColor(131, 111, 255));
        colorTable.put("slateblue2", new NMRFxColor(122, 103, 238));
        colorTable.put("slateblue3", new NMRFxColor(105, 89, 205));
        colorTable.put("slateblue4", new NMRFxColor(71, 60, 139));
        colorTable.put("slategray", new NMRFxColor(112, 128, 144));
        colorTable.put("slategray1", new NMRFxColor(198, 226, 255));
        colorTable.put("slategray2", new NMRFxColor(185, 211, 238));
        colorTable.put("slategray3", new NMRFxColor(159, 182, 205));
        colorTable.put("slategray4", new NMRFxColor(108, 123, 139));
        colorTable.put("slategrey", new NMRFxColor(112, 128, 144));
        colorTable.put("snow", new NMRFxColor(255, 250, 250));
        colorTable.put("snow2", new NMRFxColor(238, 233, 233));
        colorTable.put("snow3", new NMRFxColor(205, 201, 201));
        colorTable.put("snow4", new NMRFxColor(139, 137, 137));
        colorTable.put("spring green", new NMRFxColor(0, 255, 127));
        colorTable.put("springgreen", new NMRFxColor(0, 255, 127));
        colorTable.put("springgreen2", new NMRFxColor(0, 238, 118));
        colorTable.put("springgreen3", new NMRFxColor(0, 205, 102));
        colorTable.put("springgreen4", new NMRFxColor(0, 139, 69));
        colorTable.put("steel blue", new NMRFxColor(70, 130, 180));
        colorTable.put("steelblue", new NMRFxColor(70, 130, 180));
        colorTable.put("steelblue1", new NMRFxColor(99, 184, 255));
        colorTable.put("steelblue2", new NMRFxColor(92, 172, 238));
        colorTable.put("steelblue3", new NMRFxColor(79, 148, 205));
        colorTable.put("steelblue4", new NMRFxColor(54, 100, 139));
        colorTable.put("tan", new NMRFxColor(210, 180, 140));
        colorTable.put("tan1", new NMRFxColor(255, 165, 79));
        colorTable.put("tan2", new NMRFxColor(238, 154, 73));
        colorTable.put("tan3", new NMRFxColor(205, 133, 63));
        colorTable.put("tan4", new NMRFxColor(139, 90, 43));
        colorTable.put("thistle", new NMRFxColor(216, 191, 216));
        colorTable.put("thistle1", new NMRFxColor(255, 225, 255));
        colorTable.put("thistle2", new NMRFxColor(238, 210, 238));
        colorTable.put("thistle3", new NMRFxColor(205, 181, 205));
        colorTable.put("thistle4", new NMRFxColor(139, 123, 139));
        colorTable.put("tomato", new NMRFxColor(255, 99, 71));
        colorTable.put("tomato2", new NMRFxColor(238, 92, 66));
        colorTable.put("tomato3", new NMRFxColor(205, 79, 57));
        colorTable.put("tomato4", new NMRFxColor(139, 54, 38));
        colorTable.put("turquoise", new NMRFxColor(64, 224, 208));
        colorTable.put("turquoise1", new NMRFxColor(0, 245, 255));
        colorTable.put("turquoise2", new NMRFxColor(0, 229, 238));
        colorTable.put("turquoise3", new NMRFxColor(0, 197, 205));
        colorTable.put("turquoise4", new NMRFxColor(0, 134, 139));
        colorTable.put("violet", new NMRFxColor(238, 130, 238));
        colorTable.put("violet red", new NMRFxColor(208, 32, 144));
        colorTable.put("violetred", new NMRFxColor(208, 32, 144));
        colorTable.put("violetred1", new NMRFxColor(255, 62, 150));
        colorTable.put("violetred2", new NMRFxColor(238, 58, 140));
        colorTable.put("violetred3", new NMRFxColor(205, 50, 120));
        colorTable.put("violetred4", new NMRFxColor(139, 34, 82));
        colorTable.put("wheat", new NMRFxColor(245, 222, 179));
        colorTable.put("wheat1", new NMRFxColor(255, 231, 186));
        colorTable.put("wheat2", new NMRFxColor(238, 216, 174));
        colorTable.put("wheat3", new NMRFxColor(205, 186, 150));
        colorTable.put("wheat4", new NMRFxColor(139, 126, 102));
        colorTable.put("white", new NMRFxColor(255, 255, 255));
        colorTable.put("white smoke", new NMRFxColor(245, 245, 245));
        colorTable.put("whitesmoke", new NMRFxColor(245, 245, 245));
        colorTable.put("yellow", new NMRFxColor(255, 255, 0));
        colorTable.put("yellow green", new NMRFxColor(154, 205, 50));
        colorTable.put("yellow2", new NMRFxColor(238, 238, 0));
        colorTable.put("yellow3", new NMRFxColor(205, 205, 0));
        colorTable.put("yellow4", new NMRFxColor(139, 139, 0));
        colorTable.put("yellowgreen", new NMRFxColor(154, 205, 50));

        Enumeration e = colorTable.keys();
        NMRFxColor color = null;
        String keyName = null;

        while (e.hasMoreElements()) {
            keyName = (String) e.nextElement();

            if (!keyName.equals("grey0") && !keyName.equals("gray0")) {
                color = (NMRFxColor) colorTable.get(keyName);
                iColorTable.put(color, keyName);
            }
        }
    }
}
