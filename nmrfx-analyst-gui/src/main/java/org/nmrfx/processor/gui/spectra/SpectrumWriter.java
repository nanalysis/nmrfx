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
package org.nmrfx.processor.gui.spectra;

import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.gui.spectra.DatasetAttributes.AXMODE;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.graphicsio.GraphicsIO;
import org.nmrfx.graphicsio.GraphicsIOException;
import org.nmrfx.graphicsio.PDFWriter;
import org.nmrfx.graphicsio.SVGWriter;
import org.nmrfx.math.VecBase;
import org.nmrfx.processor.datasets.Dataset;

/**
 *
 * @author brucejohnson
 */
public class SpectrumWriter {

    static double ticSize = 10;

    private static GraphicsIO getGraphicsIO(String fileType) throws GraphicsIOException {
        GraphicsIO gIO = null;
        switch (fileType) {
            case "pdf":
                gIO = new PDFWriter();
                break;
            case "svg":
                gIO = new SVGWriter();
                break;
            default:
                throw new GraphicsIOException(("Invalid filetype " + fileType));
        }
        return gIO;
    }

    public static void writeVec(DatasetAttributes datasetAttributes, double width, double height, Vec[] vecs, String fileName, NMRAxisLimits[] axes, AXMODE[] axModes, String fileType) throws GraphicsIOException {
        GraphicsIO gIO = getGraphicsIO(fileType);
        gIO.create(true, width, height, fileName);

        write(gIO, datasetAttributes, vecs, axes, axModes);
        gIO.saveFile();
    }

    public static void printVec(DatasetAttributes datasetAttributes, VecBase[] vecs, NMRAxisLimits[] axes, AXMODE[] axModes) throws GraphicsIOException {
        GraphicsIO gIO = new PDFWriter();
        gIO.create(true, null);
        write(gIO, datasetAttributes, vecs, axes, axModes);
        printGraphics(gIO);
    }

    public static void write(GraphicsIO writer, DatasetAttributes datasetAttributes, VecBase[] vecs, NMRAxisLimits[] axes, AXMODE[] axModes) throws GraphicsIOException {

        double pageWidth = writer.getWidth();
        double pageHeight = writer.getHeight();
        double leftBorder = 100;
        double bottomBorder = 100;

        double height = pageHeight - bottomBorder - bottomBorder;
        double width = pageWidth - leftBorder - leftBorder;

        NMRAxisIO xAxis = new NMRAxisIO(axes[0].getLowerBound(), axes[0].getUpperBound(), leftBorder, leftBorder + width);
        xAxis.setReverse(axes[0].getReverse());
        xAxis.setLabel(axes[0].getLabel());
        NMRAxisIO yAxis = new NMRAxisIO(axes[1].getLowerBound(), axes[1].getUpperBound(), bottomBorder + height, bottomBorder);
        NMRAxisIO[] axes2 = {xAxis, yAxis};
        drawHorizontalAxis(writer, xAxis, yAxis.getStart());

        writer.clipRect(xAxis.getStart(), yAxis.getStart(), width, height);
        double[][] xy = new double[2][];
        for (VecBase vec : vecs) {
            int nPoints = DrawSpectrum.drawVector(vec, xAxis, yAxis, axModes[0], xy, true);
            writer.setLineWidth(datasetAttributes.getPosWidth());
            writer.drawPolyLine(xy[0], xy[1], nPoints);
        }
    }

    public static void printNDSpectrum(DrawSpectrum drawSpectrum) throws IOException, GraphicsIOException {
        GraphicsIO gIO = new PDFWriter();
        gIO.create(true, null);
        writeNDSpectrum(gIO, drawSpectrum);
        printGraphics(gIO);
    }

    public static void writeNDSpectrum(DrawSpectrum drawSpectrum, double width, double height, String fileName, String fileType) throws IOException, GraphicsIOException {
        GraphicsIO gIO = getGraphicsIO(fileType);
        gIO.create(true, width, height, fileName);
        writeNDSpectrum(gIO, drawSpectrum);
        gIO.saveFile();
    }

    public static void writeNDSpectrum(GraphicsIO writer, DrawSpectrum drawSpectrum) throws IOException, GraphicsIOException {
        double[] lineWidth = new double[2];
        AXMODE[] axModes = drawSpectrum.getAxModes();
        NMRAxis[] axes = drawSpectrum.getAxes();

        double pageWidth = writer.getWidth();
        double pageHeight = writer.getHeight();
        double leftBorder = 100;
        double bottomBorder = 100;

        double height = pageHeight - bottomBorder - bottomBorder;
        double width = pageWidth - leftBorder - leftBorder;

        NMRAxisIO xAxis = new NMRAxisIO(axes[0].getLowerBound(), axes[0].getUpperBound(), leftBorder, leftBorder + width);
        xAxis.setReverse(true);
        xAxis.setLabel(axes[0].getLabel());
        NMRAxisIO yAxis = new NMRAxisIO(axes[1].getLowerBound(), axes[1].getUpperBound(), bottomBorder + height, bottomBorder);
        yAxis.setReverse(true);
        yAxis.setLabel(axes[1].getLabel());
        NMRAxisIO[] axes2 = {xAxis, yAxis};
        int nDrawLevels = 1;
        double[] offset = {0, 0};
        drawHorizontalAxis(writer, xAxis, yAxis.getStart());
        drawVerticalAxis(writer, yAxis, xAxis.getStart());
        writer.drawLine(xAxis.getStart(), yAxis.getEnd(), xAxis.getEnd(), yAxis.getEnd());
        writer.drawLine(xAxis.getEnd(), yAxis.getEnd(), xAxis.getEnd(), yAxis.getStart());
        writer.clipRect(xAxis.getStart(), yAxis.getStart(), width, height);
        for (DatasetAttributes fileData : drawSpectrum.dataAttrList) {
            lineWidth[0] = fileData.posWidthProperty().get();
            lineWidth[1] = fileData.negWidthProperty().get();
            float[] levels = DrawSpectrum.getLevels(fileData);
            fileData.updateBounds(axModes, axes, drawSpectrum.disDim);
            fileData.mChunk = -1;
            do {
                int iChunk = fileData.mChunk + 1;
                double[][] pix = DrawSpectrum.getPix(axes, fileData);
                final Contour[] contours = new Contour[2];
                contours[0] = new Contour(fileData.ptd, pix);
                contours[1] = new Contour(fileData.ptd, pix);
                if (drawSpectrum.getContours(fileData, contours, iChunk, offset, levels)) {
                    for (int iPosNeg = 0; iPosNeg < 2; iPosNeg++) {
                        if ((iPosNeg == 0) && !fileData.getPos()) {
                            continue;
                        } else if ((iPosNeg == 1) && !fileData.getNeg()) {
                            continue;
                        }
                        if (contours[iPosNeg].getLineCount() != 0) {
                            final int jPosNeg = iPosNeg;

                            writer.setLineWidth(lineWidth[jPosNeg]);
                            if (jPosNeg == 0) {
                                writer.setStroke(fileData.getPosColor());
                            } else {
                                writer.setStroke(fileData.getNegColor());
                            }
                            for (int iLevel = 0; iLevel < nDrawLevels; iLevel++) {
                                drawContours(fileData, axModes, contours[jPosNeg], iLevel, writer, axes2);
                            }

                        }
                    }
                } else {
                    break;
                }

            } while (true);
        }
    }

    private static void drawContours(DatasetAttributes dataGenerator, AXMODE[] axModes, Contour contours, final int coordIndex, GraphicsIO g2, NMRAxisLimits[] axes) throws GraphicsIOException {
        int lineCount = contours.getLineCount(coordIndex);
        float scale = Contour.getScaleFac() / Short.MAX_VALUE;
        double cxOffset = contours.xOffset;
        double cyOffset = contours.yOffset;
        ArrayList<Double> polyLines = new ArrayList<>();
        DatasetBase dataset = dataGenerator.getDataset();
        for (int iLine = 0; iLine < lineCount; iLine += 4) {
            double xPoint1 = scale * contours.coords[coordIndex][iLine] + cxOffset;
            double xPoint2 = scale * contours.coords[coordIndex][iLine + 2] + cxOffset;
            double yPoint1 = scale * contours.coords[coordIndex][iLine + 1] + cyOffset;
            double yPoint2 = scale * contours.coords[coordIndex][iLine + 3] + cyOffset;
            xPoint1 = dataset.pointToPPM(dataGenerator.dim[0], xPoint1);
            xPoint2 = dataset.pointToPPM(dataGenerator.dim[0], xPoint2);
            yPoint1 = dataset.pointToPPM(dataGenerator.dim[1], yPoint1);
            yPoint2 = dataset.pointToPPM(dataGenerator.dim[1], yPoint2);

            double x1 = axes[0].getDisplayPosition(xPoint1);
            double x2 = axes[0].getDisplayPosition(xPoint2);
            double y1 = axes[1].getDisplayPosition(yPoint1);
            double y2 = axes[1].getDisplayPosition(yPoint2);
            polyLines.add(x1);
            polyLines.add(y1);
            polyLines.add(x2);
            polyLines.add(y2);
        }
        g2.drawPolyLines(polyLines);
    }

    private static void drawHorizontalAxis(GraphicsIO writer, NMRAxisIO axis, double border) throws GraphicsIOException {
        double leftBorder = axis.getStart();
        double width = axis.getEnd() - axis.getStart();
        writer.drawLine(leftBorder, border, leftBorder + width, border);

        List<Number> tics = axis.calculateTickValues(width, axis.getLowerBound(), axis.getUpperBound(), width);
        for (Number tic : tics) {
            double x = axis.getDisplayPosition(tic);
            String ticString = axis.getTickMarkLabel(tic);
            double y1 = border;
            double y2 = border + ticSize;
            writer.drawLine(x, y1, x, y2);
            writer.drawText(ticString, x, y2 - 2, "n", 0.0);
        }
        String label = axis.getLabel();
        if (label.length() != 0) {
            double labelTop = border + ticSize + 12.0;
            writer.drawText(label, leftBorder + width / 2, labelTop, "n", 0.0);
        }

    }

    private static void drawVerticalAxis(GraphicsIO writer, NMRAxisIO axis, double border) throws GraphicsIOException {
        double bottomBorder = axis.getStart();
        double height = axis.getStart() - axis.getEnd();
        writer.drawLine(border, axis.getStart(), border, axis.getEnd());

        List<Number> tics = axis.calculateTickValues(height, axis.getLowerBound(), axis.getUpperBound(), height);
        int ticStringLen = 0;
        for (Number tic : tics) {
            String ticString = axis.getTickMarkLabel(tic);
            if (ticString.length() > ticStringLen) {
                ticStringLen = ticString.length();
            }
            double y = axis.getDisplayPosition(tic);
            double x1 = border;
            double x2 = border - ticSize;
            writer.drawLine(x1, y, x2, y);
            writer.drawText(ticString, x2 - 2, y, "e", 0.0);
        }
        String label = axis.getLabel();
        if (label.length() != 0) {
            double labelRight = border - ticSize - ticStringLen * 12.0;
            writer.drawText(label, labelRight, bottomBorder - height / 2, "s", 90.0);
        }
    }

    public static void printGraphics(GraphicsIO gIO) {
        if (gIO instanceof PDFWriter) {
            PDFWriter writer = (PDFWriter) gIO;
            PDPageContentStream contentStream = writer.getContentStream();
            PDDocument doc = writer.getDocument();
            try {
                contentStream.close();
            } catch (IOException ioE) {
                System.out.println(ioE.getMessage());
                return;
            }
        }
    }
}
