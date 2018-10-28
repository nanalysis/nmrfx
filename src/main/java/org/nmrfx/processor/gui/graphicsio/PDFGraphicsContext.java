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
package org.nmrfx.processor.gui.graphicsio;

import java.io.IOException;
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
    float fontSize = 12;
    float pageWidth;
    float pageHeight;
    boolean landScape = false;
    Matrix matrix = new Matrix();

    public void create(boolean landScape, double width, double height, String fileName) throws GraphicsIOException {
        // the document
        this.landScape = landScape;
        this.fileName = fileName;
        doc = new PDDocument();
        try {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDRectangle pageSize = page.getMediaBox();
            pageWidth = pageSize.getWidth();
            pageHeight = pageSize.getHeight();
            contentStream = new PDPageContentStream(doc, page, false, false);
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
        return (float) x;
    }

    private float tY(double y) {
        return (float) (pageWidth - y);
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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void clearRect(double x, double y, double w, double h) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void clip() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void closePath() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
            contentStream.lineTo((float) x1, (float) y1);
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void moveTo(double x0, double y0) {
        try {
            contentStream.moveTo((float) x0, (float) y0);
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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void rotate(double degrees) {
        matrix.rotate(degrees);
        try {
            contentStream.transform(matrix);
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void save() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setFill(Paint p) {
        Color color = (Color) p;
        int r = (int) (255 * color.getRed());
        int g = (int) (255 * color.getGreen());
        int b = (int) (255 * color.getBlue());
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
        switch (fxfont.getFamily().toUpperCase()) {
            case "HELVETICA":
                font = PDType1Font.HELVETICA;
                break;
            case "COURIER":
                font = PDType1Font.COURIER;
                break;
            default:
                font = PDType1Font.HELVETICA;
        }
        fontSize = (float) fxfont.getSize();
        try {
            contentStream.setFont(font, fontSize);
        } catch (IOException ex) {
            Logger.getLogger(PDFGraphicsContext.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void setFontSmoothingType(FontSmoothingType fontsmoothing) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setGlobalAlpha(double alpha) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setGlobalBlendMode(BlendMode op) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setLineCap(StrokeLineCap cap) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setTextBaseline(VPos baseline) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
    public void strokeLine(double x1, double y1, double x2, double y2) throws GraphicsIOException {
        try {
            contentStream.moveTo(tX(x1), tY(y1));
            contentStream.lineTo(tX(x2), tY(y2));
            contentStream.stroke();
        } catch (IOException ioE) {
            throw new GraphicsIOException(ioE.getMessage());
        }
    }

    @Override
    public void strokeOval(double x, double y, double w, double h) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void strokePolygon(double[] xPoints, double[] yPoints, int nPoints) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void strokePolyline(double[] x, double[] y, int n) throws GraphicsIOException {
        try {
            contentStream.moveTo(tX(x[0]), tY(y[0]));
            for (int i = 1; i < n; i++) {
                contentStream.lineTo(tX(x[i]), tY(y[i]));
            }
            contentStream.stroke();
        } catch (IOException ioE) {
            throw new GraphicsIOException(ioE.getMessage());
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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
