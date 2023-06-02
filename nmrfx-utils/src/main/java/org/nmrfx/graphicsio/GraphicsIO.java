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
package org.nmrfx.graphicsio;

import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.ArrayList;

/**
 * @author brucejohnson
 */
public interface GraphicsIO {

    void create(boolean landScape, double width, double height, String fileName) throws GraphicsIOException;

    void create(boolean landScape, String fileName) throws GraphicsIOException;

    void drawText(String text, double x, double y, String anchor, double rotate) throws GraphicsIOException;

    void drawLine(double x1, double y1, double x2, double y2) throws GraphicsIOException;

    void drawPolyLine(double[] x, double[] y, int n) throws GraphicsIOException;

    void drawPolyLines(ArrayList<Double> values) throws GraphicsIOException;

    void setFont(Font font) throws GraphicsIOException;

    void setStroke(Color color) throws GraphicsIOException;

    void setFill(Color color) throws GraphicsIOException;

    void setLineWidth(double width) throws GraphicsIOException;

    void saveFile() throws GraphicsIOException;

    void clipRect(double x, double y, double w, double h) throws GraphicsIOException;

    double getWidth();

    double getHeight();

}
