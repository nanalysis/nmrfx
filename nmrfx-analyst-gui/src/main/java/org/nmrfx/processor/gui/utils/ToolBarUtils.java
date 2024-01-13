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
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.processor.gui.utils;

import javafx.scene.control.ToolBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.nmrfx.annotations.PluginAPI;

/**
 * @author brucejohnson
 */
@PluginAPI("parametric")
public class ToolBarUtils {

    public static void addFiller(ToolBar toolBar, double min, double max) {
        Pane filler = new Pane();
        HBox.setHgrow(filler, Priority.ALWAYS);
        filler.setMinWidth(min);
        filler.setMaxWidth(max);
        toolBar.getItems().add(filler);
    }

    public static Pane makeFiller(double min) {
        return makeFiller(min, Region.USE_COMPUTED_SIZE);
    }

    public static Pane makeFiller(double min, double max) {
        Pane filler = new Pane();
        HBox.setHgrow(filler, Priority.ALWAYS);
        filler.setMinWidth(min);
        filler.setMaxWidth(max);
        return filler;
    }
}
