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
package org.nmrfx.chart;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.paint.Color;

/**
 *
 * @author brucejohnson
 */
public class DataSeries {

    ObservableList<XYValue> values = FXCollections.observableArrayList();

    String name = "";
    Color fill = Color.BLACK;
    Color stroke = Color.BLACK;
    Symbol symbol = Symbol.CIRCLE;
    double radius = 3;
    boolean radiusInPercent = false;
    boolean fillSymbol = true;
    boolean strokeSymbol = true;
    boolean drawLine = false;

    public DataSeries() {
    }

    public ObservableList<XYValue> getData() {
        return values;
    }

    public void setStroke(Color color) {
        this.stroke = color;
    }

    public void setFill(Color color) {
        this.fill = color;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void add(XYValue value) {
        values.add(value);
    }

}
