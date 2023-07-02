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

import javafx.scene.ImageCursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.controlsfx.glyphfont.GlyphFontRegistry;
import org.nmrfx.analyst.gui.AnalystApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author brucejohnson
 */
public class IconUtilities {
    private static final Logger log = LoggerFactory.getLogger(IconUtilities.class);

    static {
        // Register a custom default font
        GlyphFontRegistry.register("icomoon", IconUtilities.class.getResourceAsStream("/images/icomoon.ttf"), 16);
    }

    private IconUtilities() {
    }

    public static ImageView getIcon(String name) {
        Image imageIcon = new Image("/images/" + name + ".png", false);
        ImageView imageView = new ImageView(imageIcon);
        try {
            double size = Double.parseDouble(AnalystApp.ICON_SIZE_STR.replaceAll("[^\\d.]", ""));
            imageView.setFitHeight(size);
            imageView.setFitWidth(size);
        } catch (NumberFormatException e) {
            log.warn("Unable to set icon size.");
        }
        return imageView;
    }

    public static ImageCursor getCursor(String name, int x, int y) {
        Image image = new Image("/images/" + name + ".png", false);
        return new ImageCursor(image, x, y);
    }
}
