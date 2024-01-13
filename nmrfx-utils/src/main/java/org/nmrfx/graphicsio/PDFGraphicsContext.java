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
import javafx.scene.effect.Effect;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author brucejohnson
 */
public class PDFGraphicsContext implements GraphicsContextInterface {
    private static final Logger log = LoggerFactory.getLogger(PDFGraphicsContext.class);

    PDPageContentStream contentStream;
    PDDocument doc = null;
    String fileName;
    PDFont font = PDType1Font.HELVETICA;
    Font fxFont = Font.font("Helvetica");
    Color fill = Color.BLACK;
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
    boolean nativeCoords = false;

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
                log.warn(ex.getMessage(), ex);
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
            return switch (textAlignment) {
                case CENTER -> width * 0.5f;
                case LEFT -> 0.0f;
                case RIGHT -> width;
                default -> 0.0f;
            };
        } catch (IOException ex) {
            return 0.0f;
        }
    }

    private float getTextDY() {
        double dYf = switch (textBaseline) {
            case BASELINE, BOTTOM -> 0.0;
            case TOP -> 1.0;
            case CENTER -> 0.5;
        };
        return (float) (dYf * fontSize);
    }

    public void startText() {
        try {
            contentStream.setFont(font, fontSize);
            contentStream.beginText();
        } catch (IOException ioE) {
            log.error(ioE.getMessage(), ioE);
        }
    }

    public void showText(String message, float startX, float startY) throws GraphicsIOException {
        try {
            contentStream.newLineAtOffset(startX, startY);
            contentStream.showText(message);
        } catch (IOException ioE) {
            log.error(ioE.getMessage(), ioE);
        }
    }

    public void endText() throws GraphicsIOException {
        try {
            contentStream.endText();
        } catch (IOException ioE) {
            log.error(ioE.getMessage(), ioE);
        }
    }

    private float tX(double x) {
        return nativeCoords ? (float) x : (float) (scaleX * x) + border;
    }

    private float tY(double y) {
        return nativeCoords ? (float) y : (float) (pageWidth - (scaleY * y)) - border;
    }

    @Override
    public void beginPath() {
    }

    @Override
    public void bezierCurveTo(double xc1, double yc1, double xc2, double yc2, double x1, double y1) {
        try {
            contentStream.curveTo(tX(xc1), tY(yc1), tX(xc2), tY(yc2), tX(x1), tY(y1));
        } catch (IOException ex) {
            log.warn(ex.getMessage(), ex);
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
            log.warn(ex.getMessage(), ex);
        }
    }

    @Override
    public void closePath() {
        try {
            contentStream.closePath();
        } catch (IOException ex) {
            log.warn(ex.getMessage(), ex);
        }
    }

    @Override
    public void fill() {
        try {
            contentStream.fill();
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void fillOval(double x, double y, double w, double h) {
        doOval(x, y, w, h, true);
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
            log.error(ex.getMessage(), ex);
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
            log.error(ex.getMessage(), ex);
        }
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
            log.error(ex.getMessage(), ex);
        }
    }

    @Override
    public Paint getFill() {
        return fill;
    }

    @Override
    public Font getFont() {
        return fxFont;
    }

    @Override
    public double getLineWidth() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Paint getStroke() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Affine getTransform() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void lineTo(double x1, double y1) {
        try {
            contentStream.lineTo(tX(x1), tY(y1));
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void moveTo(double x0, double y0) {
        try {
            contentStream.moveTo(tX(x0), tY(y0));
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
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
            log.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void restore() {
        try {
            contentStream.restoreGraphicsState();
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void rotate(double degrees) {
        Matrix rotMat = new Matrix();
        rotMat.rotate(Math.toRadians(-degrees));
        try {
            contentStream.transform(rotMat);
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void save() {
        try {
            contentStream.saveGraphicsState();
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
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
            log.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void setFont(Font fxfont) {
        this.fxFont = fxfont;
        fontSize = (float) Math.round(fxfont.getSize() * scaleX);
        try {
            contentStream.setFont(font, fontSize);
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void setGlobalAlpha(double alpha) {
    }

    @Override
    public void setLineCap(StrokeLineCap cap) {
        int pdCap;
        if (null == cap) {
            pdCap = 0;
        } else {
            pdCap = switch (cap) {
                case ROUND -> 1;
                case SQUARE -> 2;
                default -> 0;
            };
        }
        try {
            contentStream.setLineCapStyle(pdCap);
        } catch (IOException ex) {
            log.warn(ex.getMessage(), ex);
        }
    }

    @Override
    public void setLineDashes(double... dashes) {
        float[] floatDashes;
        if (dashes == null) {
            floatDashes = new float[0];
        } else {
            floatDashes = new float[dashes.length];
            for (int i = 0; i < dashes.length; i++) {
                floatDashes[i] = (float) dashes[i];
            }
        }
        try {
            contentStream.setLineDashPattern(floatDashes, (float) 0.0);
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void setLineWidth(double lw) {
        try {
            contentStream.setLineWidth((float) lw);
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
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
            log.error(ex.getMessage(), ex);
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
    public void stroke() {
        try {
            contentStream.stroke();
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void strokeLine(double x1, double y1, double x2, double y2) {
        try {
            contentStream.moveTo(tX(x1), tY(y1));
            contentStream.lineTo(tX(x2), tY(y2));
            contentStream.stroke();
        } catch (IOException ioE) {
            log.warn(ioE.getMessage(), ioE);
        }
    }

    @Override
    public void strokeOval(double x, double y, double w, double h) {
        doOval(x, y, w, h, false);
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
            log.error(ex.getMessage(), ex);
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
            log.warn(ioE.getMessage(), ioE);
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
            log.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void strokeText(String text, double x, double y) {
        try {
            startText();
            showText(text, tX(x), tY(y));
            endText();
        } catch (GraphicsIOException ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void translate(double x, double y) {
        Matrix translate = new Matrix();
        translate.translate(tX(x), tY(y));
        try {
            contentStream.transform(translate);
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
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

    void doOval(
            double x, double y, double w, double h, boolean fill) {

        final float k = 0.552284749831f;
        float cx = tX(x + w / 2.0);
        float cy = tY(y + h / 2.0);
        float rx =  Math.abs(tX(x) - cx);
        float ry =  Math.abs(tY(y) - cy);

        try {
            contentStream.moveTo(cx - rx, cy);
            contentStream.curveTo(cx - rx, cy + k * ry, cx - k * rx, cy + ry, cx, cy + ry);
            contentStream.curveTo(cx + k * rx, cy + ry, cx + rx, cy + k * ry, cx + rx, cy);
            contentStream.curveTo(cx + rx, cy - k * ry, cx + k * rx, cy - ry, cx, cy - ry);
            contentStream.curveTo(cx - k * rx, cy - ry, cx - rx, cy - k * ry, cx - rx, cy);
            if (fill) {
                contentStream.fill();
            } else {
                contentStream.stroke();
            }
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
     }
}
