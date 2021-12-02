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

module org.nmrfx.analyst.gui {
    exports org.nmrfx.analyst.gui;
    requires org.nmrfx.core;
    requires org.nmrfx.processor.gui;
    requires org.nmrfx.processor;
    requires org.nmrfx.structure;
    requires org.nmrfx.analyst;
    requires org.nmrfx.utils;
    requires org.comdnmr;
    requires org.comdnmr.gui;
    requires javafx.graphics;
    requires javafx.controls;
    requires javafx.base;
    requires javafx.fxml;
    requires java.logging;
    requires org.controlsfx.controls;
    requires org.apache.commons.lang3;
    requires jython.slim;
    requires nsmenufx;

}
