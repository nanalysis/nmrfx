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
package org.nmrfx.graphicsio;

import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.util.Matrix;

import java.io.IOException;
import java.util.ArrayList;

/**
 * This is an example of how to create a page with a landscape orientation.
 */
public class PDFWriter implements GraphicsIO {

    PDPageContentStream contentStream;
    PDDocument doc = null;
    String fileName;
    PDFont font = PDType1Font.HELVETICA;
    float fontSize = 12;
    float pageWidth;
    float pageHeight;
    boolean landScape = false;

    /**
     * Constructor.
     */
    public PDFWriter() {
        super();
    }

    public void create(boolean landScape, String fileName) throws GraphicsIOException {

    }

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

    public PDPageContentStream getContentStream() {
        return contentStream;
    }

    public PDDocument getDocument() {
        return doc;
    }

    public double getWidth() {
        if (landScape) {
            return pageHeight;
        } else {
            return pageWidth;
        }
    }

    public double getHeight() {
        if (landScape) {
            return pageWidth;
        } else {
            return pageHeight;
        }
    }

    private float tX(double x) {
        return (float) x;
    }

    private float tY(double y) {
        return (float) (pageWidth - y);
    }

    public void drawText(String message, double startX, double startY, String anchor, double rotate) throws GraphicsIOException {
        try {
            startText();
            showCenteredText(message, startX, startY, anchor, rotate);
            endText();
        } catch (Exception ioE) {
            throw new GraphicsIOException(ioE.getMessage());
        }
    }

    public void setFont(Font fxfont) {
        font = switch (fxfont.getFamily().toUpperCase()) {
            case "HELVETICA" -> PDType1Font.HELVETICA;
            case "COURIER" -> PDType1Font.COURIER;
            default -> PDType1Font.HELVETICA;
        };
        fontSize = (float) fxfont.getSize();

    }

    public void startText() throws GraphicsIOException {
        try {
            contentStream.setFont(font, fontSize);
            contentStream.beginText();
        } catch (IOException ioE) {
            throw new GraphicsIOException(ioE.getMessage());
        }
    }

    public void showCenteredText(String message, double startX, double startY, String anchor, double rotate) throws GraphicsIOException, IllegalArgumentException {
        try {
            int aLen = anchor.length();
            double xFrac = 0.0;
            double yFrac = 0.0;
            if (aLen > 0) {
                switch (anchor) {
                    case "nw" -> {
                        xFrac = 0.0;
                        yFrac = 1.0;
                    }
                    case "n" -> {
                        xFrac = 0.5;
                        yFrac = 1.0;
                    }
                    case "ne" -> {
                        xFrac = 1.0;
                        yFrac = 1.0;
                    }
                    case "e" -> {
                        xFrac = 1.0;
                        yFrac = 0.5;
                    }
                    case "se" -> {
                        xFrac = 1.0;
                        yFrac = 0.0;
                    }
                    case "s" -> {
                        xFrac = 0.5;
                        yFrac = 0.0;
                    }
                    case "sw" -> {
                        xFrac = 0.0;
                        yFrac = 0.0;
                    }
                    case "w" -> {
                        xFrac = 0.0;
                        yFrac = 0.5;
                    }
                    default -> throw new IllegalArgumentException("Invalid anchor \"" + anchor + "\"");
                }
            }
            font = PDType1Font.HELVETICA;
            float fontSize = 12;
            float stringWidth = font.getStringWidth(message) * fontSize / 1000f;
            PDFontDescriptor pdfFontDescriptor = font.getFontDescriptor();
            float stringHeight = pdfFontDescriptor.getCapHeight() * fontSize / 1000f;
            float xOffset = -stringWidth * (float) xFrac;
            float yOffset = -stringHeight * (float) yFrac;

            contentStream.newLineAtOffset(tX(startX + xOffset), tY(startY - yOffset));
            if (rotate != 0.0) {
                Matrix matrix = new Matrix();
                matrix.translate(tX(startX), tY(startY));
                matrix.rotate(rotate * Math.PI / 180.0);
                matrix.translate(xOffset, -yOffset);
                contentStream.setTextMatrix(matrix);
            }

            contentStream.showText(message);
        } catch (IOException ioE) {
            throw new GraphicsIOException(ioE.getMessage());
        }

    }

    public void endText() throws GraphicsIOException {
        try {
            contentStream.endText();
        } catch (IOException ioE) {
            throw new GraphicsIOException(ioE.getMessage());
        }
    }

    public void drawLine(double startX, double startY, double endX, double endY) throws GraphicsIOException {
        try {
            contentStream.moveTo(tX(startX), tY(startY));
            contentStream.lineTo(tX(endX), tY(endY));
            contentStream.stroke();
        } catch (IOException ioE) {
            throw new GraphicsIOException(ioE.getMessage());
        }
    }

    public void clipRect(double startX, double startY, double width, double height) throws GraphicsIOException {
        try {
            contentStream.addRect(tX(startX), tY(startY), (float) width, (float) height);
            contentStream.clip();
        } catch (IOException ioE) {
            throw new GraphicsIOException(ioE.getMessage());
        }
    }

    public void drawPolyLines(ArrayList<Double> values) throws GraphicsIOException {
        try {
            int n = values.size();
            for (int i = 0; i < n; i += 4) {
                contentStream.moveTo(tX(values.get(i)), tY(values.get(i + 1)));
                contentStream.lineTo(tX(values.get(i + 2)), tY(values.get(i + 3)));
            }
            contentStream.stroke();
        } catch (IOException ioE) {
            throw new GraphicsIOException(ioE.getMessage());
        }
    }

    public void drawPolyLine(double[] x, double[] y, int n) throws GraphicsIOException {
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

    public void setLineWidth(double value) throws GraphicsIOException {
        try {
            contentStream.setLineWidth((float) value);
        } catch (IOException ioE) {
            throw new GraphicsIOException(ioE.getMessage());
        }
    }

    public void setFill(Color color) throws GraphicsIOException {
        try {
            int r = (int) (255 * color.getRed());
            int g = (int) (255 * color.getGreen());
            int b = (int) (255 * color.getBlue());
            contentStream.setNonStrokingColor(r, g, b);
        } catch (IOException ioE) {
            throw new GraphicsIOException(ioE.getMessage());
        }

    }

    public void setStroke(Color color) throws GraphicsIOException {
        try {
            int r = (int) (255 * color.getRed());
            int g = (int) (255 * color.getGreen());
            int b = (int) (255 * color.getBlue());
            contentStream.setStrokingColor(r, g, b);
        } catch (IOException ioE) {
            throw new GraphicsIOException(ioE.getMessage());
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
