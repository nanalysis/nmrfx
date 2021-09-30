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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.geometry.VPos;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.Effect;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Affine;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.util.Matrix;

/**
 *
 * @author brucejohnson
 */
public class PDFGraphicsContext implements GraphicsContextInterface {

    PDPageContentStream contentStream;
    PDDocument doc = null;
    String fileName;
    PDFont font = PDType1Font.HELVETICA;
    Font fxFont = Font.font("Helvetica");
    Color fill = Color.BLACK;
    Color stroke = Color.BLACK;
    float fontSize = 12;
    TextAlignment textAlignment = TextAlignment.LEFT;
    VPos textBaseline = VPos.BASELINE;
    float pageWidth;
    float pageHeight;
    float border = 36.0f;
    double canvasWidth;
    double canvasHeight;
    double scaleX;
    double scaleY;
    boolean landScape = false;
    Matrix matrix = new Matrix();
    GCCache cache = new GCCache();
    boolean nativeCoords = false;

    class GCCache {

        double fontSize = 12;
        String fontFamilyName = "Helvetica";
        Color fill = Color.BLACK;
        Color stroke = Color.BLACK;
        PDFont font;
        double lineWidth = 1.0;
        String clipPath = "";
        TextAlignment textAlignment = TextAlignment.LEFT;
        VPos textBaseline = VPos.BASELINE;
        Matrix matrix = null;
        List<Object> transforms = new ArrayList<>();

        void save(PDFGraphicsContext pdfGC) {
            this.fontSize = pdfGC.fontSize;
            this.font = pdfGC.font;
            this.fill = pdfGC.fill;
            this.stroke = pdfGC.stroke;
            //  this.clipPath = pdfGC.clipPath;
            this.textAlignment = pdfGC.textAlignment;
            this.textBaseline = pdfGC.textBaseline;
            this.matrix = pdfGC.matrix == null ? null : pdfGC.matrix.clone();
            //this.transforms.clear();
            //this.transforms.addAll(pdfGC.transforms);
        }

        void restore(PDFGraphicsContext pdfGC) {
            pdfGC.fontSize = (float) fontSize;
            pdfGC.font = font;
            pdfGC.fill = fill;
            pdfGC.stroke = stroke;
            //pdfGC.clipPath = clipPath;
            pdfGC.textAlignment = textAlignment;
            pdfGC.textBaseline = textBaseline;
            pdfGC.matrix = matrix == null ? null : matrix.clone();
            try {
                pdfGC.contentStream.transform(matrix);
            } catch (IOException ex) {
            }
            // pdfGC.transforms.clear();
            // pdfGC.transforms.addAll(transforms);

        }

    }

    public void create(boolean landScape, double width, double height, String fileName) throws GraphicsIOException {
        // the document
        this.landScape = landScape;
        this.fileName = fileName;
        doc = new PDDocument();
        InputStream iStream = PDFGraphicsContext.class.getResourceAsStream("/LiberationSans-Regular.ttf");
        if (iStream == null) {
            System.out.println("null stream");
        } else {
            try {
                font = PDType0Font.load(doc, iStream);
            } catch (IOException ex) {
            }
        }

        try {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDRectangle pageSize = page.getMediaBox();
            pageWidth = pageSize.getWidth();
            pageHeight = pageSize.getHeight();
            canvasWidth = width;
            canvasHeight = height;
            scaleX = (pageHeight - 2.0f * border) / canvasWidth;
            scaleY = (pageWidth - 2.0f * border) / canvasHeight;
            if (scaleX > scaleY) {
                scaleX = scaleY;
            } else {
                scaleY = scaleX;
            }
            contentStream = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.OVERWRITE, false);
            // add the rotation using the current transformation matrix
            // including a translation of pageWidth to use the lower left corner as 0,0 reference
            if (landScape) {
                page.setRotation(90);
                contentStream.transform(new Matrix(0, 1, -1, 0, pageWidth, 0));
            }
        } catch (IOException ioE) {
            throw new GraphicsIOException(ioE.getMessage());
        }
    }

    public void nativeCoords(boolean state) {
        this.nativeCoords = state;
    }

    private float getTextAnchor(String text) {
        try {
            float width = font.getStringWidth(text) / 1000.0f * fontSize;
            switch (textAlignment) {
                case CENTER:
                    return width * 0.5f;
                case LEFT:
                    return 0.0f;
                case RIGHT:
                    return width;
                default:
                    return 0.0f;
            }
        } catch (IOException ex) {
            return 0.0f;
        }
    }

    private float getTextDY() {
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
        return (float) (dYf * fontSize);
    }

    public void startText() {
        try {
            contentStream.setFont(font, fontSize);
            contentStream.beginText();
        } catch (IOException ioE) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ioE);
        }
    }

    public void showText(String message, float startX, float startY) throws GraphicsIOException {
        try {
            contentStream.newLineAtOffset(startX, startY);
            contentStream.showText(message);
        } catch (IOException ioE) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ioE);
        }

    }

    public void endText() throws GraphicsIOException {
        try {
            contentStream.endText();
        } catch (IOException ioE) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ioE);
        }
    }

    private float tX(double x) {
        return nativeCoords ? (float) x : (float) (scaleX * x) + border;
    }

    private float tY(double y) {
        return nativeCoords ? (float) y : (float) (pageWidth - (scaleY * y)) - border;
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
    }

    @Override
    public void bezierCurveTo(double xc1, double yc1, double xc2, double yc2, double x1, double y1) {
        try {
            contentStream.curveTo(tX(xc1), tY(yc1), tX(xc2), tY(yc2), tX(x1), tY(y1));
        } catch (IOException ex) {
        }
    }

    @Override
    public void clearRect(double x, double y, double w, double h) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void clip() {
        try {
            contentStream.clip();
        } catch (IOException ex) {
        }
    }

    @Override
    public void closePath() {
        try {
            contentStream.closePath();
        } catch (IOException ex) {
        }
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
            contentStream.fill();
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void fillArc(double x, double y, double w, double h, double startAngle, double arcExtent, ArcType closure) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void fillOval(double x, double y, double w, double h) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void fillPolygon(double[] xPoints, double[] yPoints, int nPoints) {
        try {
            contentStream.moveTo(tX(xPoints[0]), tY(yPoints[0]));
            for (int i = 1; i < nPoints; i++) {
                contentStream.lineTo(tX(xPoints[i]), tY(yPoints[i]));
            }
            contentStream.fill();
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void fillRect(double x, double y, double w, double h) {
        try {
            contentStream.moveTo(tX(x), tY(y));
            contentStream.lineTo(tX(x), tY(y + h));
            contentStream.lineTo(tX(x + w), tY(y + h));
            contentStream.lineTo(tX(x + w), tY(y));
            contentStream.lineTo(tX(x), tY(y));
            contentStream.fill();
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void fillRoundRect(double x, double y, double w, double h, double arcWidth, double arcHeight) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void fillText(String text, double x, double y) {
        float dY = getTextDY();
        float dX = getTextAnchor(text);
        try {
            startText();
            showText(text, tX(x) - dX, tY(y) - dY);
            endText();
        } catch (GraphicsIOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void fillText(String text, double x, double y, double maxWidth) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Effect getEffect(Effect e) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Paint getFill() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public FillRule getFillRule() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Font getFont() {
        return fxFont;
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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
        try {
            contentStream.lineTo(tX(x1), tY(y1));
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void moveTo(double x0, double y0) {
        try {
            contentStream.moveTo(tX(x0), tY(y0));
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void quadraticCurveTo(double xc, double yc, double x1, double y1) {
        try {
            contentStream.curveTo1((float) xc, (float) yc, (float) x1, (float) y1);
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void rect(double x, double y, double w, double h) {
        try {
            contentStream.moveTo(tX(x), tY(y));
            contentStream.lineTo(tX(x), tY(y + h));
            contentStream.lineTo(tX(x + w), tY(y + h));
            contentStream.lineTo(tX(x + w), tY(y));
            contentStream.lineTo(tX(x), tY(y));
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void restore() {
        try {
            contentStream.restoreGraphicsState();
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void rotate(double degrees) {
        Matrix rotMat = new Matrix();
        rotMat.rotate(Math.toRadians(-degrees));
        try {
            contentStream.transform(rotMat);
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void save() {
        try {
            contentStream.saveGraphicsState();
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void scale(double x, double y) {
        matrix.scale((float) x, (float) y);
        try {
            contentStream.transform(matrix);
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void setEffect(Effect e) {
    }

    @Override
    public void setFill(Paint p) {
        fill = (Color) p;
        int r = (int) (255 * fill.getRed());
        int g = (int) (255 * fill.getGreen());
        int b = (int) (255 * fill.getBlue());
        try {
            contentStream.setNonStrokingColor(r, g, b);
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void setFillRule(FillRule fillRule) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setFont(Font fxfont) {
        this.fxFont = fxfont;
//        switch (fxfont.getFamily().toUpperCase()) {
//            case "HELVETICA":
//                font = PDType1Font.HELVETICA;
//                break;
//            case "COURIER":
//                font = PDType1Font.COURIER;
//                break;
//            default:
//                font = PDType1Font.HELVETICA;
//        }
        fontSize = (float) Math.round(fxfont.getSize() * scaleX);
        try {
            contentStream.setFont(font, fontSize);
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void setFontSmoothingType(FontSmoothingType fontsmoothing) {
        // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
        int pdCap;
        if (null == cap) {
            pdCap = 0;
        } else {
            switch (cap) {
                case ROUND:
                    pdCap = 1;
                    break;
                case SQUARE:
                    pdCap = 2;
                    break;
                default:
                    pdCap = 0;
                    break;
            }
        }
        try {
            contentStream.setLineCapStyle(pdCap);
        } catch (IOException ex) {
        }
    }

    @Override
    public void setLineDashes(double... dashes) {
        // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setLineDashOffset(double dashOffset) {
        // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setLineJoin(StrokeLineJoin join) {
        //  throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setLineWidth(double lw) {
        try {
            contentStream.setLineWidth((float) lw);
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void setMiterLimit(double ml) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setStroke(Paint p) {
        Color color = (Color) p;
        int r = (int) (255 * color.getRed());
        int g = (int) (255 * color.getGreen());
        int b = (int) (255 * color.getBlue());
        try {
            contentStream.setStrokingColor(r, g, b);
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setTransform(double mxx, double myx, double mxy, double myy, double mxt, double myt) {
        Matrix m = new Matrix((float) mxx, (float) myx, (float) mxy, (float) myy, (float) mxt, (float) myt);
        try {
            contentStream.transform(m);
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void stroke() {
        try {
            contentStream.stroke();
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void strokeArc(double x, double y, double w, double h, double startAngle, double arcExtent, ArcType closure) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void strokeLine(double x1, double y1, double x2, double y2) {
        try {
            contentStream.moveTo(tX(x1), tY(y1));
            contentStream.lineTo(tX(x2), tY(y2));
            contentStream.stroke();
        } catch (IOException ioE) {
        }
    }

    public void strokeLineNoTrans(double x1, double y1, double x2, double y2) {
        try {
            contentStream.moveTo((float) x1, (float) y1);
            contentStream.lineTo((float) x2, (float) y2);
            contentStream.stroke();
        } catch (IOException ioE) {
        }
    }

    @Override
    public void strokeOval(double x, double y, double w, double h) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void strokePolygon(double[] xPoints, double[] yPoints, int nPoints) {
        try {
            contentStream.moveTo(tX(xPoints[0]), tY(yPoints[0]));
            for (int i = 1; i < nPoints; i++) {
                contentStream.lineTo(tX(xPoints[i]), tY(yPoints[i]));
            }
            contentStream.stroke();
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void strokePolyline(double[] x, double[] y, int n) {
        try {
            contentStream.moveTo(tX(x[0]), tY(y[0]));
            for (int i = 1; i < n; i++) {
                contentStream.lineTo(tX(x[i]), tY(y[i]));
            }
            contentStream.stroke();
        } catch (IOException ioE) {
        }
    }

    @Override
    public void strokeRect(double x, double y, double w, double h) {
        try {
            contentStream.moveTo(tX(x), tY(y));
            contentStream.lineTo(tX(x), tY(y + h));
            contentStream.lineTo(tX(x + w), tY(y + h));
            contentStream.lineTo(tX(x + w), tY(y));
            contentStream.lineTo(tX(x), tY(y));
            contentStream.stroke();
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void strokeRoundRect(double x, double y, double w, double h, double arcWidth, double arcHeight) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void strokeText(String text, double x, double y) {
        try {
            startText();
            showText(text, tX(x), tY(y));
            endText();
        } catch (GraphicsIOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void strokeText(String text, double x, double y, double maxWidth) {
        // fixme not using maxWidth
        try {
            startText();
            showText(text, tX(x), tY(y));
            endText();
        } catch (GraphicsIOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
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
        Matrix translate = new Matrix();
        translate.translate(tX(x), tY(y));
        // translate.translate((float) (scaleX * x), pageWidth - border - (float) (scaleY * y));
        try {
            contentStream.transform(translate);
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void saveFile() throws GraphicsIOException {
        try {
            contentStream.close();

            doc.save(fileName);

            if (doc != null) {
                doc.close();

            }
        } catch (IOException ioE) {
            throw new GraphicsIOException(ioE.getMessage());
        }
    }

}
