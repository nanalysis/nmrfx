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
package org.nmrfx.processor.gui.graphicsio;

import java.util.ArrayList;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 *
 * @author brucejohnson
 */
public interface GraphicsIO {

    public void create(boolean landScape, String fileName) throws GraphicsIOException;

    public void drawText(String text, double x, double y) throws GraphicsIOException;

    public void drawText(String text, double x, double y, String anchor, double rotate) throws GraphicsIOException;

    public void drawLine(double x1, double y1, double x2, double y2) throws GraphicsIOException;

    public void drawPolyLine(double[] x, double[] y) throws GraphicsIOException;

    public void drawPolyLine(ArrayList<Double> values) throws GraphicsIOException;

    public void drawPolyLines(ArrayList<Double> values) throws GraphicsIOException;

    public void setFont(Font font) throws GraphicsIOException;

    public void setStroke(Color color) throws GraphicsIOException;

    public void setFill(Color color) throws GraphicsIOException;

    public void setLineWidth(double width) throws GraphicsIOException;

    public void drawRect(double x, double y, double w, double h) throws GraphicsIOException;

    public void saveFile() throws GraphicsIOException;

    public void clipRect(double x, double y, double w, double h) throws GraphicsIOException;

    public double getWidth();

    public double getHeight();

}
