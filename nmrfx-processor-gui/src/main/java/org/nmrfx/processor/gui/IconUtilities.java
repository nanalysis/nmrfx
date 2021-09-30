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

 /*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.glyphfont.GlyphFont;
import org.controlsfx.glyphfont.GlyphFontRegistry;

/**
 *
 * @author brucejohnson
 */
public class IconUtilities {

    static {
        // Register a custom default font
        GlyphFontRegistry.register("icomoon", SpecAttrWindowController.class.getResourceAsStream("/images/icomoon.ttf"), 16);
    }

    private static GlyphFont icoMoon = GlyphFontRegistry.font("icomoon");

    public static final char IM_COGS = '\ue995';
    public static final char IM_PAINT_FORMAT = '\ue90c';
    public static final char IM_HOME = '\ue900';
    public static final char IM_MOVE_UP = '\uea46';
    public static final char IM_KEYBOARD = '\ue955';
    public static final char IM_ARROW_UP_LEFT = '\uea31';
    public static final char IM_PASTE = '\ue92d';
    public static final char IM_CLOCK = '\ue94e';
    public static final char IM_STATS_DOTS = '\ue99b';
    public static final char IM_ARROW_UP_RIGHT = '\uea33';
    public static final char IM_STATS_BARS = '\ue99c';
    public static final char IM_WRENCH = '\ue991';
    public static final char IM_ARROW_LEFT = '\uea38';
    public static final char IM_FLOPPY_DISK = '\ue962';
    public static final char IM_PIE_CHART = '\ue99a';
    public static final char IM_ARROW_DOWN = '\uea36';
    public static final char IM_ARROW_RIGHT = '\uea34';
    public static final char IM_ATTACHMENT = '\ue9cd';
    public static final char IM_SPINNER9 = '\ue982';
    public static final char IM_BIN = '\ue9ac';
    public static final char IM_LOCK = '\ue98f';
    public static final char IM_BIN2 = '\ue9ad';
    public static final char IM_QUESTION = '\uea09';
    public static final char IM_MINUS = '\uea0b';
    public static final char IM_COG = '\ue994';
    public static final char IM_FOLDER_OPEN = '\ue930';
    public static final char IM_HISTORY = '\ue94d';
    public static final char IM_FOLDER_DOWNLOAD = '\ue933';
    public static final char IM_ENLARGE2 = '\ue98b';
    public static final char IM_FOLDER_PLUS = '\ue931';
    public static final char IM_COPY = '\ue92c';
    public static final char IM_MAGIC_WAND = '\ue997';
    public static final char IM_ZOOM_OUT = '\ue988';
    public static final char IM_REDO2 = '\ue968';
    public static final char IM_SEARCH = '\ue986';
    public static final char IM_CANCEL_CIRCLE = '\uea0d';
    public static final char IM_FILES_EMPTY = '\ue925';
    public static final char IM_ARROW_DOWN_LEFT = '\uea37';
    public static final char IM_FILE_TEXT = '\ue922';
    public static final char IM_PRINTER = '\ue954';
    public static final char IM_SHRINK2 = '\ue98c';
    public static final char IM_UNDO2 = '\ue967';
    public static final char IM_FOLDER_MINUS = '\ue932';
    public static final char IM_PENCIL = '\ue905';
    public static final char IM_MOVE_DOWN = '\uea47';
    public static final char IM_PLUS = '\uea0a';
    public static final char IM_INFO = '\uea0c';
    public static final char IM_FOLDER = '\ue92f';
    public static final char IM_STOP = '\uea17';
    public static final char IM_ENLARGE = '\ue989';
    public static final char IM_FILE_EMPTY = '\ue924';
    public static final char IM_ZOOM_IN = '\ue987';
    public static final char IM_FOLDER_UPLOAD = '\ue934';
    public static final char IM_SHRINK = '\ue98a';
    public static final char IM_ARROW_UP = '\uea32';
    public static final char IM_UNLOCKED = '\ue990';
    public static final char IM_ARROW_DOWN_RIGHT = '\uea35';

    public static Glyph create(char fontChar) {
        return icoMoon.create(fontChar);
    }

}
