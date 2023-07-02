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
 * but WITHOUT ANY WARRANTY ; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.graphicsio;

import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.nmrfx.utils.GUIUtils;

/**
 * @author brucejohnson
 */
public class StyledCanvasText {

    static double drawCurrent(GraphicsContextInterface gC, StringBuilder sBuilder, double x, double y, boolean measure) {
        String subStr = sBuilder.toString();
        if (!measure) {
            gC.fillText(subStr, x, y);
        }
        x += GUIUtils.getTextWidth(subStr, gC.getFont());
        sBuilder.setLength(0);
        return x;
    }

    static double drawSuper(GraphicsContextInterface gC, StringBuilder sBuilder, double x, double y, boolean measure) {
        Font defFont = gC.getFont();
        double fontSize = defFont.getSize();
        Font scriptFont = new Font(fontSize * 0.75);
        gC.setFont(scriptFont);
        x = drawCurrent(gC, sBuilder, x, y, measure);
        x += fontSize * 0.75 * 0.25;
        gC.setFont(defFont);
        return x;
    }

    public static void drawStyledText(GraphicsContextInterface gC, String text, double x, double y) {
        double endX = drawStyledText(gC, text, x, y, true);
        double width = endX - x;
        x -= width / 2.0;
        drawStyledText(gC, text, x, y, false);
    }

    public static double drawStyledText(GraphicsContextInterface gC, String text, double x, double y, boolean measure) {
        if (!text.contains("^") && !text.contains("_")) {
            if (!measure) {
                gC.fillText(text, x, y);
            }
            return x;
        } else {
            gC.setTextAlign(TextAlignment.LEFT);
            int i = 0;
            int len = text.length();
            StringBuilder sBuilder = new StringBuilder();
            double yBase = y;
            Font defFont = gC.getFont();
            double fontSize = defFont.getSize();
            double superDelta = fontSize * 0.25;
            int superLevel = 0;
            int group = 0;
            while (i < len) {
                char ch = text.charAt(i);
                if (ch == '^') {
                    if (sBuilder.length() > 0) {
                        x = drawCurrent(gC, sBuilder, x, y, measure);
                    }
                    y -= superDelta;
                    superLevel++;
                } else if (ch == '_') {
                    if (sBuilder.length() > 0) {
                        x = drawCurrent(gC, sBuilder, x, y, measure);
                    }
                    y += superDelta;
                    superLevel++;
                } else {
                    if (ch == '{') {
                        group++;
                    } else if (ch == '}') {
                        group--;
                        if ((group == 0) && (superLevel > 0)) {
                            x = drawSuper(gC, sBuilder, x, y, measure);
                            y = yBase;
                            superLevel--;
                        } else {
                            x = drawCurrent(gC, sBuilder, x, y, measure);
                        }
                    } else {
                        sBuilder.append(ch);
                        if ((group == 0) && (superLevel > 0)) {
                            x = drawSuper(gC, sBuilder, x, y, measure);
                            y = yBase;
                            superLevel--;
                        }
                    }
                }
                i++;
            }
            if (sBuilder.length() > 0) {
                x = drawCurrent(gC, sBuilder, x, y, measure);
            }
        }
        return x;
    }

}
