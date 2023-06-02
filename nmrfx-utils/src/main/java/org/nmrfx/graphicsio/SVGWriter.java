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

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;

/**
 * @author brucejohnson
 */

/*

<?xml version="1.0" encoding="utf-8" ?>
<svg baseProfile="tiny" height="100%" version="1.2" width="100%" xmlns="http://www.w3.org/2000/svg" xmlns:ev="http://www.w3.org/2001/xml-events" xmlns:xlink="http://www.w3.org/1999/xlink"><defs />

<g font-family="Verdana" font-size="45"><text fill="red" x="0" y="0.2"><tspan fill="green">A</tspan><tspan fill="red">B</tspan><tspan fill="blue">C</tspan></text></g></svg>
 */
public class SVGWriter implements GraphicsIO {

    StringBuilder doc = new StringBuilder();
    double pageWidth = 1024;
    double pageHeight = 1024;
    double fontSize = 12;
    String fontFamilyName = "Helvetica";
    Color fill = Color.BLACK;
    Color stroke = Color.BLACK;
    Font font = Font.getDefault();
    double lineWidth = 1.0;
    StringBuilder sBuilder = new StringBuilder();
    FileOutputStream stream;
    XMLStreamWriter writer;
    String clipPath = "";

    @Override
    public void create(boolean landScape, String fileName) throws GraphicsIOException {
        create(landScape, 1024, 1024, fileName);
    }

    @Override
    public void create(boolean landScape, double width, double height, String fileName) throws GraphicsIOException {
        try {
            this.pageWidth = width;
            this.pageHeight = height;
            XMLOutputFactory factory = XMLOutputFactory.newFactory();
            try {
                stream = new FileOutputStream(fileName);
            } catch (FileNotFoundException ex) {
                throw new GraphicsIOException(ex.getMessage());
            }
            try {
                writer = factory.createXMLStreamWriter(stream);
            } catch (XMLStreamException ex) {
                throw new GraphicsIOException(ex.getMessage());
            }
            writer.writeStartDocument();
            writer.writeCharacters("\n");
            writer.writeStartElement("svg");
            writer.writeAttribute("baseProfile", "tiny");
            writer.writeAttribute("width", format(pageWidth));
            writer.writeAttribute("height", format(pageHeight));
            writer.writeAttribute("xmlns", "http://www.w3.org/2000/svg");

            writer.writeCharacters("\n");
        } catch (XMLStreamException ex) {
            throw new GraphicsIOException(ex.getMessage());
        }

    }

    @Override
    public void drawText(String text, double x, double y, String anchor, double rotate) throws GraphicsIOException {
        showCenteredText(text, x, y, anchor, rotate);
    }

    public void showCenteredText(String text, double x, double y, String anchor, double rotate) throws GraphicsIOException, IllegalArgumentException {
        int aLen = anchor.length();
        //  <text fill="black" x="295.7" y="708.0" text-anchor="middle" dy="14">8.0</text>
        String textAnchor = "start";
        double dYf = 0.0;
        if (aLen > 0) {
            switch (anchor) {
                case "nw":
                    textAnchor = "start";
                    dYf = 1.0;
                    break;
                case "n":
                    textAnchor = "middle";
                    dYf = 1.0;
                    break;
                case "ne":
                    textAnchor = "end";
                    dYf = 1.0;
                    break;
                case "e":
                    textAnchor = "end";
                    dYf = 0.5;
                    break;
                case "se":
                    textAnchor = "end";
                    dYf = 0.0;
                    break;
                case "s":
                    textAnchor = "middle";
                    dYf = 0.0;
                    break;
                case "sw":
                    textAnchor = "start";
                    dYf = 0.0;
                    break;
                case "w":
                    textAnchor = "start";
                    dYf = 0.5;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid anchor \"" + anchor + "\"");
            }
        }

        try {
            writer.writeStartElement("text");
            writer.writeAttribute("fill", "black");
            writer.writeAttribute("x", format(x));
            writer.writeAttribute("y", format(y));
            writer.writeAttribute("text-anchor", textAnchor);
            writer.writeAttribute("dy", format(dYf * fontSize));
            if (rotate != 0.0) {
                String transform = String.format("rotate(%f,%f,%f)", -rotate, x, y);
                writer.writeAttribute("transform", transform);
            }
            writer.writeCharacters(text);
            writer.writeEndElement();
            writer.writeCharacters("\n");
        } catch (XMLStreamException ex) {
            throw new GraphicsIOException(ex.getMessage());
        }

    }

    @Override
    public void drawLine(double x1, double y1, double x2, double y2) throws GraphicsIOException {
        // <line x1="0"  y1="10" x2="0"   y2="100" style="stroke:#006600;"/>
        try {
            writer.writeEmptyElement("line");
            writer.writeAttribute("x1", format(x1));
            writer.writeAttribute("y1", format(y1));
            writer.writeAttribute("x2", format(x2));
            writer.writeAttribute("y2", format(y2));
            writer.writeAttribute("style", "stroke:black;");
            writer.writeCharacters("\n");
        } catch (XMLStreamException ex) {
            throw new GraphicsIOException(ex.getMessage());
        }
    }

    @Override
    public void drawPolyLine(double[] x, double[] y, int n) throws GraphicsIOException {
        //<polyline points="0,0  30,0  15,30" style="stroke:#006600;"/>   
        StringBuilder pointBuilder = new StringBuilder();
        for (int i = 0; i < n; i++) {
            pointBuilder.append(format(x[i]));
            pointBuilder.append(',');
            pointBuilder.append(format(y[i]));
            pointBuilder.append(' ');
        }
        try {
            writer.writeEmptyElement("polyline");
            writer.writeAttribute("points", pointBuilder.toString());
            writer.writeAttribute("style", getStyle());
            writer.writeCharacters("\n");
        } catch (XMLStreamException ex) {
            throw new GraphicsIOException(ex.getMessage());
        }
    }

    @Override
    public void drawPolyLines(ArrayList<Double> values) throws GraphicsIOException {
        StringBuilder pointBuilder = new StringBuilder();
        for (int i = 0; i < values.size(); i += 4) {
            pointBuilder.append('M');
            pointBuilder.append(format(values.get(i)));
            pointBuilder.append(',');
            pointBuilder.append(format(values.get(i + 1)));
            pointBuilder.append(' ');
            pointBuilder.append('L');
            pointBuilder.append(format(values.get(i + 2)));
            pointBuilder.append(',');
            pointBuilder.append(format(values.get(i + 3)));
            pointBuilder.append(' ');
        }
        try {
            writer.writeEmptyElement("path");
            writer.writeAttribute("d", pointBuilder.toString());
            writer.writeAttribute("style", getStyle());

            writer.writeCharacters("\n");
        } catch (XMLStreamException ex) {
            throw new GraphicsIOException(ex.getMessage());
        }
    }

    @Override
    public void setFont(Font font) throws GraphicsIOException {
        this.font = font;
    }

    @Override
    public void setStroke(Color color) throws GraphicsIOException {
        stroke = color;

    }

    @Override
    public void setFill(Color color) throws GraphicsIOException {
        fill = color;
    }

    @Override
    public void setLineWidth(double width) throws GraphicsIOException {
        lineWidth = width;
    }

    @Override
    public void saveFile() throws GraphicsIOException {
        try {
            writer.writeEndDocument();
            writer.flush();
            writer.close();
        } catch (XMLStreamException ex) {
            throw new GraphicsIOException(ex.getMessage());
        }
    }

    @Override
    public void clipRect(double x, double y, double w, double h) throws GraphicsIOException {
        try {
            writer.writeStartElement("defs");
            writer.writeStartElement("clipPath");
            writer.writeAttribute("id", "clipPath1");
            writer.writeStartElement("rect");
            writer.writeAttribute("x", String.valueOf(x));
            writer.writeAttribute("y", String.valueOf(y));
            writer.writeAttribute("height", String.valueOf(h));
            writer.writeAttribute("width", String.valueOf(w));
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeEndElement();

            clipPath = "clip-path: url(#clipPath1);";
        } catch (XMLStreamException ex) {
            throw new GraphicsIOException(ex.getMessage());
        }
    }

    @Override
    public double getWidth() {

        return pageWidth;
    }

    @Override
    public double getHeight() {
        return pageHeight;
    }

    private String format(double value) {
        return String.format("%.2f", value);
    }

    private String getStyle() {
        StringBuilder builder = new StringBuilder();
        builder.append("stroke: ");
        builder.append("black");
        builder.append(';');
        builder.append("fill: ");
        builder.append("none");
        builder.append(';');
        builder.append("stroke-width: ");
        builder.append(format(lineWidth));
        builder.append(';');
        if (clipPath.length() != 0) {
            builder.append(clipPath);
        }
        return builder.toString();

    }
}
