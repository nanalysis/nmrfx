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
package org.nmrfx.graphicsio;

import javafx.geometry.VPos;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.Effect;
import javafx.scene.image.Image;
import javafx.scene.paint.Paint;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Affine;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 *
 * @author brucejohnson
 */
public class SVGGraphicsContext implements GraphicsContextInterface {

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
    OutputStream stream;
    XMLStreamWriter writer;
    String clipPath = "";
    TextAlignment textAlignment = TextAlignment.LEFT;
    VPos textBaseline = VPos.BASELINE;
    StringBuilder pathBuilder = new StringBuilder();
    Affine transform = null;
    List<Object> transforms = new ArrayList<>();
    boolean clipActive = false;
    int clipIndex = 1;

    GCCache cache = new GCCache();
    private List<Object> ArrayList;

    class Rotate {

        final double value;

        Rotate(double value) {
            this.value = value;
        }
    }

    class Translate {

        final double x;
        final double y;

        Translate(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    class GCCache {

        double fontSize = 12;
        String fontFamilyName = "Helvetica";
        Color fill = Color.BLACK;
        Color stroke = Color.BLACK;
        Font font = Font.getDefault();
        double lineWidth = 1.0;
        String clipPath = "";
        TextAlignment textAlignment = TextAlignment.LEFT;
        VPos textBaseline = VPos.BASELINE;
        Affine transform = null;
        List<Object> transforms = new ArrayList<>();

        void save(SVGGraphicsContext svgGC) {
            this.fontSize = svgGC.fontSize;
            this.fontFamilyName = svgGC.fontFamilyName;
            this.fill = svgGC.fill;
            this.stroke = svgGC.stroke;
            this.clipPath = svgGC.clipPath;
            this.textAlignment = svgGC.textAlignment;
            this.textBaseline = svgGC.textBaseline;
            this.transform = svgGC.transform == null ? null : svgGC.transform.clone();
            this.transforms.clear();
            this.transforms.addAll(svgGC.transforms);
        }

        void restore(SVGGraphicsContext svgGC) {
            svgGC.fontSize = fontSize;
            svgGC.fontFamilyName = fontFamilyName;
            svgGC.fill = fill;
            svgGC.stroke = stroke;
            svgGC.clipPath = clipPath;
            svgGC.textAlignment = textAlignment;
            svgGC.textBaseline = textBaseline;
            svgGC.transform = transform == null ? null : transform.clone();
            svgGC.transforms.clear();
            svgGC.transforms.addAll(transforms);

        }

    }

    public void create(boolean landScape, String fileName) {
        create(landScape, 1024, 1024, fileName);
    }

    public void create(boolean landScape, double width, double height, String fileName) {
        try {
            OutputStream stream = new FileOutputStream(fileName);
            create(landScape, width, height, stream);
        } catch (FileNotFoundException ex) {
        }

    }

    public void create(boolean landScape, double width, double height, OutputStream stream) {
        try {
            this.pageWidth = width;
            this.pageHeight = height;
            XMLOutputFactory factory = XMLOutputFactory.newFactory();
            try {
                writer = factory.createXMLStreamWriter(stream);
            } catch (XMLStreamException ex) {
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
        }

    }

    private Affine getValidTransform() {
        if (transform == null) {
            transform = new Affine();
        }
        return transform;
    }

    private String getTextAnchor() {
        switch (textAlignment) {
            case CENTER:
                return "middle";
            case LEFT:
                return "start";
            case RIGHT:
                return "end";
            default:
                return "start";
        }
    }

    private String getTextDY() {
        double dYf = 0.0;
        switch (textBaseline) {
            case BASELINE:
                dYf = 0.0;
                break;
            case BOTTOM:
                dYf = 0.0;
                break;
            case TOP:
                dYf = 1.0;
                break;
            case CENTER:
                dYf = 0.5;
                break;
        }
        return format(dYf * fontSize);
    }

    private String getTransformText() {
        StringBuilder sBuilder = new StringBuilder();
        //      transform="matrix(1,0,0,1,100,20)"
        for (Object obj : transforms) {
            if (obj instanceof Rotate) {
                double value = ((Rotate) obj).value;
                sBuilder.append("rotate(");
                sBuilder.append(value).append(") ");
            }
            if (obj instanceof Translate) {
                double x = ((Translate) obj).x;
                double y = ((Translate) obj).y;
                sBuilder.append("translate(");
                sBuilder.append(x).append(",");
                sBuilder.append(y).append(") ");
            }
        }
        return sBuilder.toString();
    }

    private String getTransformShape() {
        StringBuilder sBuilder = new StringBuilder();
        //      transform="matrix(1,0,0,1,100,20)"

        sBuilder.append("matrix(");
        sBuilder.append(transform.getMxx()).append(",");
        sBuilder.append(transform.getMyx()).append(",");
        sBuilder.append(transform.getMxy()).append(",");
        sBuilder.append(transform.getMyy()).append(",");
        sBuilder.append(transform.getMxz()).append(",");
        sBuilder.append(transform.getMyz()).append(",");
        sBuilder.append(transform.getTx()).append(",");
        sBuilder.append(transform.getTy()).append(")");
        return sBuilder.toString();
    }

    public void saveFile() {
        try {
            writer.writeEndDocument();
            writer.flush();
            writer.close();
//            stream.flush();
//            stream.close();
        } catch (XMLStreamException ex) {
        }
    }

    public double getWidth() {

        return pageWidth;
    }

    public double getHeight() {
        return pageHeight;
    }

    private String format(double value) {
        return String.format("%.2f", value);
    }

    private String getTextStyle(boolean fillMode) {
//        style="font-family: Arial;
//                 font-size  : 34;
//                 stroke     : #000000;
//                 fill       : #00ff00;
//                "
        StringBuilder builder = new StringBuilder();
        builder.append("stroke: ");
        if (!fillMode) {
            builder.append(toRGBCode(stroke));
        } else {
            builder.append("none");
        }
        builder.append(';');
        builder.append("fill: ");
        if (fillMode) {
            builder.append(toRGBCode(fill));
        } else {
            builder.append("none");
        }
        builder.append(';');
        builder.append("font-size: ");
        builder.append(fontSize);
        builder.append(';');
        return builder.toString();

    }

    private String getStyle(boolean strokeMode, boolean fillMode) {
        StringBuilder builder = new StringBuilder();
        builder.append("stroke: ");
        if (strokeMode) {
            builder.append(toRGBCode(stroke));
        } else {
            builder.append("none");
        }
        builder.append(';');
        builder.append("fill: ");
        if (fillMode) {
            builder.append(toRGBCode(fill));
        } else {
            builder.append("none");
        }
        builder.append(';');
        builder.append("stroke-width: ");
        builder.append(format(lineWidth));
        builder.append(';');
        if (clipPath.length() != 0) {
            builder.append(clipPath);
        }
        return builder.toString();
    }

    public static String toRGBCode(Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255)
        );
    }

    @Override
    public void appendSVGPath(String svgpath) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void applyEffect(Effect e) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void arc(double centerX, double centerY, double radiusX, double radiusY, double startAngle, double length) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void arcTo(double x1, double y1, double x2, double y2, double radius) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void beginPath() {
        pathBuilder.setLength(0);
    }

    @Override
    public void bezierCurveTo(double xc1, double yc1, double xc2, double yc2, double x1, double y1) {
        pathBuilder.append('C');
        pathBuilder.append(format(xc1));
        pathBuilder.append(',');
        pathBuilder.append(format(yc1));
        pathBuilder.append(' ');
        pathBuilder.append(format(xc2));
        pathBuilder.append(',');
        pathBuilder.append(format(yc2));
        pathBuilder.append(' ');
        pathBuilder.append(format(x1));
        pathBuilder.append(',');
        pathBuilder.append(format(y1));
        pathBuilder.append(' ');

    }

    @Override
    public void clearRect(double x, double y, double w, double h) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void clip() {
        try {
            if (clipActive) {
                writer.writeEndElement();
            }
            clipPath += pathBuilder.toString();
            if (clipPath.length() > 0) {
                writer.writeStartElement("defs");
                writer.writeStartElement("clipPath");
                writer.writeAttribute("id", "clipPath" + clipIndex);
                writer.writeStartElement("path");
                writer.writeAttribute("d", clipPath);
                writer.writeEndElement();
                writer.writeEndElement();
                writer.writeEndElement();
                writer.writeStartElement("g");
                writer.writeAttribute("style", "clip-path: url(#clipPath" + clipIndex + ");");
                clipActive = true;
                clipIndex++;
            } else {
                clipActive = false;
            }

        } catch (XMLStreamException ex) {
            System.out.println(ex.getMessage());
        }
    }

    @Override
    public void closePath() {
        pathBuilder.append("Z");
    }

    @Override
    public void drawImage(Image img, double x, double y) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void drawImage(Image img, double x, double y, double w, double h) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void drawImage(Image img, double sx, double sy, double sw, double sh, double dx, double dy, double dw, double dh) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void fill() {
        try {
            writer.writeEmptyElement("path");
            writer.writeAttribute("d", pathBuilder.toString());
            writer.writeAttribute("style", getStyle(false, true));
            writer.writeCharacters("\n");

        } catch (XMLStreamException ex) {
        }
    }

    @Override
    public void fillArc(double x, double y, double w, double h, double startAngle, double arcExtent, ArcType closure) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void fillOval(double x, double y, double w, double h) {
        doOval(x, y, w, h, false, true);
    }

    @Override
    public void fillPolygon(double[] xPoints, double[] yPoints, int nPoints) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void fillRect(double x, double y, double w, double h) {
        doRect(x, y, w, h, false, true);
    }

    @Override
    public void fillRoundRect(double x, double y, double w, double h, double arcWidth, double arcHeight) {
        doRoundRect(x, y, w, h, arcWidth, arcHeight, false, true);
    }

    @Override
    public void fillText(String text, double x, double y) {
        try {
            writer.writeStartElement("text");
            if (!transforms.isEmpty()) {
                writer.writeAttribute("transform", getTransformText());
            }
            writer.writeAttribute("text-anchor", getTextAnchor());
            writer.writeAttribute("dy", getTextDY());

            writer.writeAttribute("style", getTextStyle(true));
            writer.writeAttribute("x", format(x));
            writer.writeAttribute("y", format(y));
            writer.writeCharacters(text);
            writer.writeEndElement();
            writer.writeCharacters("\n");
        } catch (XMLStreamException ex) {
        }
    }

    @Override
    public void fillText(String text, double x, double y, double maxWidth) {
        // fixme doesn't use maxWidth
        try {
            writer.writeStartElement("text");
            if (transform != null) {
                writer.writeAttribute("transform", getTransformText());
            }
            writer.writeAttribute("style", getTextStyle(true));
            writer.writeAttribute("x", format(x));
            writer.writeAttribute("y", format(y));
            writer.writeCharacters(text);
            writer.writeEndElement();
            writer.writeCharacters("\n");
        } catch (XMLStreamException ex) {
        }
    }

    @Override
    public Effect getEffect(Effect e) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Paint getFill() {
        return fill;
    }

    @Override
    public FillRule getFillRule() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Font getFont() {
        return font;
    }

    @Override
    public FontSmoothingType getFontSmoothingType() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double getGlobalAlpha() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public BlendMode getGlobalBlendMode() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public StrokeLineCap getLineCap() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double[] getLineDashes() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double getLineDashOffset() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public StrokeLineJoin getLineJoin() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double getLineWidth() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double getMiterLimit() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Paint getStroke() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public TextAlignment getTextAlign() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public VPos getTextBaseline() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Affine getTransform() {
        return getValidTransform();
    }

    @Override
    public Affine getTransform(Affine xform) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean isPointInPath(double x, double y) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void lineTo(double x1, double y1) {
        pathBuilder.append('L');
        pathBuilder.append(format(x1));
        pathBuilder.append(',');
        pathBuilder.append(format(y1));
        pathBuilder.append(' ');
    }

    @Override
    public void moveTo(double x0, double y0) {
        pathBuilder.append('M');
        pathBuilder.append(format(x0));
        pathBuilder.append(',');
        pathBuilder.append(format(y0));
        pathBuilder.append(' ');
    }

    @Override
    public void quadraticCurveTo(double xc, double yc, double x1, double y1) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void rect(double x, double y, double w, double h) {
        moveTo(x, y);
        lineTo(x + w, y);
        lineTo(x + w, y + h);
        lineTo(x, y + h);
        lineTo(x, y);
    }

    @Override
    public void restore() {
        if (clipActive) {
            try {
                writer.writeEndElement();
            } catch (XMLStreamException ex) {
            }
            clipActive = false;
        }
        pathBuilder.setLength(0);
        cache.restore(this);
    }

    @Override
    public void rotate(double degrees) {
        transforms.add(new Rotate(degrees));
    }

    @Override
    public void save() {
        cache.save(this);
    }

    @Override
    public void scale(double x, double y) {
        getValidTransform().appendScale(x, y);
    }

    @Override
    public void setEffect(Effect e) {
    }

    @Override
    public void setFill(Paint p) {
        fill = (Color) p;
    }

    @Override
    public void setFillRule(FillRule fillRule) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setFont(Font f) {
        this.font = f;
        fontSize = f.getSize();
    }

    @Override
    public void setFontSmoothingType(FontSmoothingType fontsmoothing) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setGlobalAlpha(double alpha) {

    }

    @Override
    public void setGlobalBlendMode(BlendMode op) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setLineCap(StrokeLineCap cap) {
    }

    @Override
    public void setLineDashes(double... dashes) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setLineDashOffset(double dashOffset) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setLineJoin(StrokeLineJoin join) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setLineWidth(double lw) {
        lineWidth = lw;
    }

    @Override
    public void setMiterLimit(double ml) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setStroke(Paint p) {
        stroke = (Color) p;
    }

    @Override
    public void setTextAlign(TextAlignment align) {
        textAlignment = align;
    }

    @Override
    public void setTextBaseline(VPos baseline) {
        textBaseline = baseline;
    }

    @Override
    public void setTransform(Affine xform) {
        transform = xform;
    }

    @Override
    public void setTransform(double mxx, double myx, double mxy, double myy, double mxt, double myt) {
    }

    @Override
    public void stroke() {
        try {
            writer.writeEmptyElement("path");
            writer.writeAttribute("d", pathBuilder.toString());
            writer.writeAttribute("style", getStyle(true, false));
            writer.writeCharacters("\n");

        } catch (XMLStreamException ex) {
        }
    }

    @Override
    public void strokeArc(double x, double y, double w, double h, double startAngle, double arcExtent, ArcType closure) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void strokeLine(double x1, double y1, double x2, double y2) {
        try {
            writer.writeEmptyElement("line");
            writer.writeAttribute("x1", format(x1));
            writer.writeAttribute("y1", format(y1));
            writer.writeAttribute("x2", format(x2));
            writer.writeAttribute("y2", format(y2));
            writer.writeAttribute("style", getStyle(true, false));
            writer.writeCharacters("\n");
        } catch (XMLStreamException ex) {
        }
    }

    @Override
    public void strokeOval(double x, double y, double w, double h) {
        doOval(x, y, w, h, true, false);
    }

    @Override
    public void strokePolygon(double[] xPoints, double[] yPoints, int nPoints) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void strokePolyline(double[] xPoints, double[] yPoints, int nPoints) {
        StringBuilder pointBuilder = new StringBuilder();
        for (int i = 0; i < nPoints; i++) {
            pointBuilder.append(format(xPoints[i]));
            pointBuilder.append(',');
            pointBuilder.append(format(yPoints[i]));
            pointBuilder.append(' ');
        }
        try {
            writer.writeEmptyElement("polyline");
            writer.writeAttribute("points", pointBuilder.toString());
            writer.writeAttribute("style", getStyle(true, false));
            writer.writeCharacters("\n");
        } catch (XMLStreamException ex) {
        }
    }

    @Override
    public void strokeRect(double x, double y, double w, double h) {
        doRect(x, y, w, h, true, false);
    }

    public void doRect(double x, double y, double w, double h, boolean strokeMode, boolean fillMode) {
        String style = getStyle(strokeMode, fillMode);
        try {
            writer.writeEmptyElement("rect");
            writer.writeAttribute("x", format(x));
            writer.writeAttribute("y", format(y));
            writer.writeAttribute("height", format(h));
            writer.writeAttribute("width", format(w));
            writer.writeAttribute("style", style);
            writer.writeCharacters("\n");
        } catch (XMLStreamException ex) {
        }
    }

    /*
            /*
        <ellipse cx="50" cy="50" rx="40" ry="30"
         style="stroke: #ff0000;
               stroke-width: 5;
               fill: none;
        "/>

     */
    public void doOval(double x, double y, double dW, double dH, boolean strokeMode, boolean fillMode) {
        String style = getStyle(strokeMode, fillMode);
        double rW = dW / 2;
        double rH = dH / 2;
        x += rW;
        y += rH;
        try {
            writer.writeEmptyElement("ellipse");
            writer.writeAttribute("cx", format(x));
            writer.writeAttribute("cy", format(y));
            writer.writeAttribute("rx", format(rW));
            writer.writeAttribute("ry", format(rH));
            writer.writeAttribute("style", style);
            writer.writeCharacters("\n");
        } catch (XMLStreamException ex) {
        }
    }

    @Override
    public void strokeRoundRect(double x, double y, double w, double h, double arcWidth, double arcHeight) {
        doRoundRect(x, y, w, h, arcWidth, arcHeight, true, false);
    }

    public void doRoundRect(double x, double y, double w, double h, double arcWidth, double arcHeight, boolean strokeMode, boolean fillMode) {
        String style = getStyle(strokeMode, fillMode);
        try {
            writer.writeEmptyElement("rect");
            writer.writeAttribute("x", format(x));
            writer.writeAttribute("y", format(y));
            writer.writeAttribute("height", format(h));
            writer.writeAttribute("width", format(w));
            writer.writeAttribute("rx", format(arcWidth));
            writer.writeAttribute("ry", format(arcHeight));
            writer.writeAttribute("style", style);
            writer.writeCharacters("\n");
        } catch (XMLStreamException ex) {
        }
    }

    @Override
    public void strokeText(String text, double x, double y) {
        try {
            writer.writeStartElement("text");
            writer.writeAttribute("style", getTextStyle(false));
            writer.writeAttribute("x", format(x));
            writer.writeAttribute("y", format(y));
            writer.writeCharacters(text);
            writer.writeEndElement();
            writer.writeCharacters("\n");
        } catch (XMLStreamException ex) {
        }
    }

    @Override
    public void strokeText(String text, double x, double y, double maxWidth) {
        // fixme doesn't use maxWidth
        try {
            writer.writeStartElement("text");
            writer.writeAttribute("style", getTextStyle(false));
            writer.writeAttribute("x", format(x));
            writer.writeAttribute("y", format(y));
            writer.writeCharacters(text);
            writer.writeEndElement();
            writer.writeCharacters("\n");
        } catch (XMLStreamException ex) {
        }
    }

    @Override
    public void transform(Affine xform) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void transform(double mxx, double myx, double mxy, double myy, double mxt, double myt) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void translate(double x, double y) {
        transforms.add(new Translate(x, y));
    }

}
