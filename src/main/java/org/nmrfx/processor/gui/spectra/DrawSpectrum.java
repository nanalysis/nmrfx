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

import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.DatasetAttributes.AXMODE;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.DoubleBinaryOperator;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import org.apache.commons.math3.complex.Complex;
import javafx.geometry.Bounds;
import javafx.scene.control.Button;
import javafx.scene.shape.StrokeLineCap;
import org.nmrfx.processor.gui.PolyChart.DISDIM;

/**
 *
 * @author brucejohnson
 */
public class DrawSpectrum {

    static final double degtorad = Math.PI / 180.0;
    NMRAxis[] axes;
    private boolean useThread = true;
    private SpectrumViewParameters viewPar = new SpectrumViewParameters();
    private SpectrumColorParameters colorPar = new SpectrumColorParameters();
    static Color[] gradColors = new Color[0];
    GraphicsContext g2;
    List<DatasetAttributes> dataAttrList = Collections.synchronizedList(new ArrayList<>());
    final Canvas canvas;
    DrawTask drawTask;
    AXMODE[] axModes;
    DISDIM disDim = DISDIM.TwoD;
    double[][] xy = new double[2][];
    int nPoints = 0;
    int iChunk = 0;
    int rowIndex = -1;

    public DrawSpectrum(NMRAxis[] axes, Canvas canvas) {
        this.axes = axes;
        this.canvas = canvas;
        if (canvas != null) {
            g2 = canvas.getGraphicsContext2D();
        }
        drawTask = new DrawTask(this);
    }

    public void setController(FXMLController controller) {
        Button cancelButton = controller.getCancelButton();
        cancelButton.setOnAction(actionEvent -> {
            ((Service) drawTask.worker).cancel();
        });
        cancelButton.disableProperty().bind(((Service) drawTask.worker).stateProperty().isNotEqualTo(Task.State.RUNNING));
    }

    public void setDisDim(DISDIM disDim) {
        this.disDim = disDim;
    }

    public void setAxes(NMRAxis[] axes) {
        this.axes = axes;
    }

    public void drawSpectrum(ArrayList<DatasetAttributes> dataGenerators, AXMODE[] axModes,
            boolean pick) {

        if (pick) {
            return;
        }
        this.axModes = new AXMODE[axModes.length];
        for (int i = 0; i < axModes.length; i++) {
            this.axModes[i] = axModes[i];
        }
        dataAttrList.clear();
        dataAttrList.addAll(dataGenerators);

        ((Service) drawTask.worker).restart();
    }

    public static float[] getLevels(DatasetAttributes fileData) {
        int nLevels = fileData.getNlevels();
        double clm = fileData.getClm();

        float[] levels = new float[nLevels];
        levels[0] = (float) fileData.levelProperty().get();
        for (int i = 1; i < nLevels; i++) {
            levels[i] = (float) (levels[i - 1] * clm);
        }
        return levels;
    }

    AXMODE[] getAxModes() {
        AXMODE[] modes = new AXMODE[axModes.length];
        for (int i = 0; i < modes.length; i++) {
            modes[i] = axModes[i];
        }
        return modes;
    }

    NMRAxis[] getAxes() {
        NMRAxis[] tempAxes = new NMRAxis[axes.length];
        for (int i = 0; i < axes.length; i++) {
            tempAxes[i] = axes[i];
        }
        return tempAxes;
    }

    private static class DrawTask {

        public Worker<Integer> worker;

        GraphicsContext g2;
        DrawSpectrum drawSpectrum;
        List<DatasetAttributes> dataAttrList;
        AXMODE[] axModes;
        NMRAxis[] axes;
        float[] levels;

        private DrawTask(DrawSpectrum drawSpectrum) {
            this.drawSpectrum = drawSpectrum;
            g2 = drawSpectrum.g2;
            worker = new Service<Integer>() {
                @Override
                protected Task createTask() {
                    return new Task<Integer>() {
                        @Override
                        protected Integer call() {
                            try {
                                dataAttrList = new ArrayList<>();
                                dataAttrList.addAll(drawSpectrum.dataAttrList);
                                for (DatasetAttributes fileData : dataAttrList) {
                                    levels = getLevels(fileData);
                                    axModes = drawSpectrum.getAxModes();
                                    axes = drawSpectrum.getAxes();
                                    drawNow(this, fileData);
                                }
                            } catch (Exception e) {
                                System.out.println("error " + e.getMessage());
                            }
                            return 0;
                        }

                        @Override
                        protected void cancelled() {
                        }

                        @Override
                        protected void succeeded() {
                        }

                    };
                }
            };
        }

        void drawNow(Task task, DatasetAttributes fileData) throws IOException {
            int nDrawLevels = 1;
//        long startTime = System.currentTimeMillis();
            double[] offset = {0, 0};
            fileData.mChunk = -1;
            do {
                if (task.isCancelled()) {
                    break;
                }
                int iChunk = fileData.mChunk + 1;
                final Contour[] contours = new Contour[2];
                contours[0] = new Contour();
                contours[1] = new Contour();
                if (drawSpectrum.getContours(fileData, contours, iChunk, offset, levels)) {
                    for (int iPosNeg = 0; iPosNeg < 2; iPosNeg++) {
                        if ((iPosNeg == 0) && !fileData.getPosDrawOn()) {
                            continue;
                        } else if ((iPosNeg == 1) && !fileData.getNegDrawOn()) {
                            continue;
                        }
                        if (contours[iPosNeg].getLineCount() != 0) {
                            final int jPosNeg = iPosNeg;
//                            System.out.println("chunk " + iChunk);

                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
//                                    System.out.println("draw");

                                    g2.setGlobalAlpha(1.0);
                                    g2.setLineCap(StrokeLineCap.BUTT);
                                    g2.setEffect(null);
                                    if (jPosNeg == 0) {
                                        g2.setLineWidth(fileData.posLineWidthProperty().get());
                                        g2.setStroke(fileData.getPosColor());
                                    } else {
                                        g2.setLineWidth(fileData.negLineWidthProperty().get());
                                        g2.setStroke(fileData.getNegColor());
                                    }
                                    for (int iLevel = 0; iLevel < nDrawLevels; iLevel++) {
                                        drawSpectrum.drawContours(fileData, axModes, contours[jPosNeg], iLevel, g2);
                                    }
                                }
                            });

                        }
                    }
                } else {
                    break;
                }

            } while (true);

        }
    }

    boolean getContours(DatasetAttributes fileData, Contour[] contours, int iChunk, double[] offset, float[] levels) throws IOException {
        StringBuffer chunkLabel = new StringBuffer();
        chunkLabel.setLength(0);
        int[][] apt = new int[fileData.getDataset().getNDim()][2];
        int fileStatus = fileData.getMatrixRegion(iChunk, viewPar.mode, apt,
                offset, chunkLabel);
        if (fileStatus != 0) {
            return false;
        }
        float[][] z = null;

        try {
            z = fileData.Matrix2(fileData.mChunk, chunkLabel.toString(), apt);
        } catch (IOException ioE) {
            throw ioE;
        }
        if (z == null) {
            return false;
        }

        for (int iPosNeg = 0; iPosNeg < 2; iPosNeg++) {
            if ((iPosNeg == 0) && !fileData.getPosDrawOn()) {
                continue;
            } else if ((iPosNeg == 1) && !fileData.getNegDrawOn()) {
                continue;
            }
            contours[iPosNeg].setLineCount(0);

            if (checkLevels(z, iPosNeg, levels)) {
                if (contours[iPosNeg] != null) {
                    if (!contours[iPosNeg].contour(levels, z)) {
                        contours[iPosNeg].xOffset = offset[0] + fileData.ptd[0][0];
                        contours[iPosNeg].yOffset = offset[1] + fileData.ptd[1][0];
                    }
                }
            }
        }
        return true;
    }

    private void drawContours(DatasetAttributes dataGenerator, AXMODE[] axModes, Contour contours, final int coordIndex, GraphicsContext g2) {
        int lineCount = contours.getLineCount(coordIndex);
        float scale = Contour.getScaleFac() / Short.MAX_VALUE;
        double cxOffset = contours.xOffset;
        double cyOffset = contours.yOffset;
        g2.beginPath();
        Dataset dataset = dataGenerator.getDataset();
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

            g2.moveTo(x1, y1);
            g2.lineTo(x2, y2);
        }
        g2.stroke();
    }

    private boolean checkLevels(float[][] z, int iPosNeg, float[] levels) {
        int ny = z.length;
        int nx = z[0].length;
        boolean ok = false;

        if (iPosNeg == 1) {
            for (int jj = 0; jj < ny; jj++) {
                for (int ii = 0; ii < nx; ii++) {
                    z[jj][ii] = -z[jj][ii];
                }
            }
        }

        for (int jj = 0; jj < ny; jj++) {
            for (int ii = 0; ii < nx; ii++) {
                if (z[jj][ii] > levels[0]) {
                    ok = true;

                    break;
                }
            }

            if (ok) {
                break;
            }
        }

        return ok;
    }

    static void setColorGradient(final int nLevels, final boolean refresh, final Color color1, final Color color2) {
        if (refresh || (gradColors.length != nLevels)) {
            double hue1 = color1.getHue();
            double brightness1 = color1.getBrightness();
            double saturation1 = color1.getSaturation();
            double hue2 = color2.getHue();
            double brightness2 = color2.getBrightness();
            double saturation2 = color2.getSaturation();
            gradColors = new Color[nLevels];
            for (int iColor = 0; iColor < nLevels; iColor++) {
                double f = ((double) iColor) / (nLevels - 1);
                double h = hue1 + (hue2 - hue1) * f;
                double s = saturation1 + (saturation2 - saturation1) * f;
                double b = brightness1 + (brightness2 - brightness1) * f;
                gradColors[iColor] = Color.hsb(h, s, b);
            }
        }
    }

    public void drawSlice(DatasetAttributes datasetAttr, SliceAttributes sliceAttr, int orientation, double slicePosX, double slicePosY, Bounds bounds, double ph0, double ph1) {
        int sliceDim = orientation;
        Vec sliceVec = new Vec(32, false);
        boolean drawReal = datasetAttr.getDrawReal();
        boolean offsetTracking = sliceAttr.getOffsetTracking();
        try {
            datasetAttr.getSlice(sliceVec, sliceDim, slicePosX, slicePosY);
            double level = datasetAttr.levelProperty().get();
            double scale = -sliceAttr.getScaleValue() / level;
            //System.out.println(orientation + " " + slicePosX + " " + slicePosY);
            if (sliceDim == 0) {
                double offset;
                if (offsetTracking) {
                    offset = axes[1].getDisplayPosition(slicePosY);
                } else {
                    offset = bounds.getHeight() * (1.0 - sliceAttr.getOffsetYValue());
                }
                drawVector(sliceVec, orientation, 0, AXMODE.PPM, drawReal, ph0, ph1, null,
                        (index, intensity) -> axes[0].getDisplayPosition(index),
                        (index, intensity) -> intensity * scale + offset, false);
            } else {
                double offset;
                if (offsetTracking) {
                    offset = axes[0].getDisplayPosition(slicePosX);
                } else {
                    offset = bounds.getWidth() * sliceAttr.getOffsetXValue();
                }
                drawVector(sliceVec, orientation, 0, AXMODE.PPM, drawReal, ph0, ph1, null,
                        (index, intensity) -> -intensity * scale + offset,
                        (index, intensity) -> axes[1].getDisplayPosition(index), false);
            }
        } catch (IOException ioE) {
            System.out.println(ioE.getMessage());
        }
    }

    public double[][] getXY() {
        return xy;
    }

    public int getNPoints() {
        return nPoints;
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public void setToLastChunk(DatasetAttributes dataAttributes) {
        iChunk = dataAttributes.getLastChunk(0);
    }

    public boolean draw1DSpectrum(DatasetAttributes dataAttributes, int orientation, AXMODE axMode, double ph0, double ph1, Path bcPath) {
        Vec specVec = new Vec(32);
        boolean drawReal = dataAttributes.getDrawReal();
        boolean offsetMode = true;
        Dataset dataset = dataAttributes.getDataset();
        if (dataset.getVec() != null) {
            specVec = dataset.getVec();
            iChunk = -1;
        } else {
            offsetMode = false;
            try {
                int iDim = 0;
                rowIndex = dataAttributes.getRowIndex(iDim, iChunk);
                System.out.println("row in " + rowIndex);
                if (!dataAttributes.Vector(specVec, iChunk--)) {
                    return false;
                }
            } catch (IOException ioE) {
                return false;
            }
        }
        double level = dataAttributes.levelProperty().get();
        double height = axes[1].getHeight();
        double scale = -height / 10.0 / level;
        double offset = height * (1.0 - dataAttributes.offsetProperty().doubleValue());
        drawVector(specVec, orientation, 0, axMode, drawReal, ph0, ph1, bcPath,
                (index, intensity) -> axes[0].getDisplayPosition(index),
                (index, intensity) -> intensity * scale + offset, offsetMode);

        if (iChunk < 0) {
            return false;
        }
        return true;

    }

    private int vecIndexer(Vec vec, double position) {
        int point = vec.refToPt(position);
        return point;
    }

    public void drawVector(Vec vec, int orientation, int dataOffset, AXMODE axMode, boolean drawReal, double ph0, double ph1, Path bcPath, DoubleBinaryOperator xFunction, DoubleBinaryOperator yFunction, boolean offsetVec) {
        int size = vec.getSize();
        double phase1Delta = ph1 / (size - 1);
        NMRAxis indexAxis = orientation == PolyChart.HORIZONTAL ? axes[0] : axes[1];

        int vecStartPoint;
        int vecEndPoint;
        double indexAxisDelta;
        if (offsetVec) {
            vecStartPoint = axMode.getIndex(vec, indexAxis.getLowerBound());
            vecEndPoint = axMode.getIndex(vec, indexAxis.getUpperBound());
            indexAxisDelta = axMode.getIncrement(vec, indexAxis.getLowerBound(), indexAxis.getUpperBound());
        } else {
            vecStartPoint = vec.getSize() - 1;
            vecEndPoint = 0;
            dataOffset = 0;
            indexAxisDelta = (indexAxis.getLowerBound() - indexAxis.getUpperBound()) / vecStartPoint;
        }
        double dValue = indexAxis.getLowerBound();

//        System.out.printf("%d %.5f %.5f %d %d %.4f %.4f\n", orientation, indexAxis.getLowerBound(), indexAxis.getUpperBound(), vecStartPoint, vecEndPoint, dValue, indexAxisDelta);
        if (vecStartPoint > vecEndPoint) {
            int hold = vecStartPoint;
            vecStartPoint = vecEndPoint;
            vecEndPoint = hold;
            dValue = indexAxis.getUpperBound();
        }
        nPoints = drawVectoreCore(vec, dataOffset, drawReal, ph0, ph1, xy, bcPath, xFunction, yFunction, offsetVec, vecStartPoint, vecEndPoint, size, dValue, phase1Delta, indexAxisDelta);
    }

    public static int drawVector(Vec vec, NMRAxisIO xAxis, NMRAxisIO yAxis, AXMODE axMode, double[][] xy) {
        int dataOffset = 0;
        NMRAxisIO indexAxis = xAxis;

        int vecStartPoint;
        int vecEndPoint;
        double indexAxisDelta;
        boolean offsetVec = true;
        if (offsetVec) {
            vecStartPoint = axMode.getIndex(vec, indexAxis.getLowerBound());
            vecEndPoint = axMode.getIndex(vec, indexAxis.getUpperBound());
            indexAxisDelta = axMode.getIncrement(vec, indexAxis.getLowerBound(), indexAxis.getUpperBound());
        } else {
            vecStartPoint = vec.getSize() - 1;
            vecEndPoint = 0;
            dataOffset = 0;
            indexAxisDelta = (indexAxis.getLowerBound() - indexAxis.getUpperBound()) / vecStartPoint;
        }
        double dValue = indexAxis.getLowerBound();

        //System.out.printf("%d %.5f %.5f %d %d %.4f %.4f\n", 0, indexAxis.getLowerBound(), indexAxis.getUpperBound(), vecStartPoint, vecEndPoint, dValue, indexAxisDelta);
        if (vecStartPoint > vecEndPoint) {
            int hold = vecStartPoint;
            vecStartPoint = vecEndPoint;
            vecEndPoint = hold;
            dValue = indexAxis.getUpperBound();
        }
        DoubleBinaryOperator xFunction = (index, intensity) -> xAxis.getDisplayPosition(index);
        DoubleBinaryOperator yFunction = (index, intensity) -> yAxis.getDisplayPosition(intensity);
        boolean drawReal = true;
        double ph0 = 0.0;
        double ph1 = 0.0;
        int size = vec.getSize();
        double phase1Delta = ph1 / (size - 1);
        Path bcPath = null;

        int nPoints = drawVectoreCore(vec, dataOffset, drawReal, ph0, ph1, xy, bcPath, xFunction, yFunction, offsetVec, vecStartPoint, vecEndPoint, size, dValue, phase1Delta, indexAxisDelta);
        return nPoints;
    }

    private static int drawVectoreCore(Vec vec, int dataOffset, boolean drawReal, double ph0, double ph1, double[][] xyValues, Path bcPath, DoubleBinaryOperator xFunction, DoubleBinaryOperator yFunction, boolean offsetVec, int start, int end, int size, double dValue, double dDelta, double delta) {

        if ((start - dataOffset) < 0) {
            start = dataOffset;
        }
        if (end > ((size + dataOffset) - 1)) {
            end = (size + dataOffset) - 1;
        }
        int incr = (end - start) / 2048;
        if (incr < 1) {
            incr = 1;
        }
        if ((start - dataOffset) < 0) {
            start = dataOffset;
        }
        if (end > ((size + dataOffset) - 1)) {
            end = (size + dataOffset) - 1;
        }
        //System.out.println((start - dataOffset) + " " + (end - dataOffset) + " " + dValue + " " + (dValue + delta * (end - start)));
        if (((Math.abs(ph0) > 1.0e-6) || (Math.abs(ph1) > 1.0e-6)) && !vec.isComplex()) {
            vec.hft();
        }
        double dValueHold = dValue;
        int nPoints = 0;
        if (incr != 1) {
            double[] ve = new double[end - start + 1];
            for (int i = start; i <= end; i++) {
                double p = ph0 + i * dDelta;
                if (vec.isComplex()) {
                    Complex cmpPhas = new Complex(Math.cos(p * degtorad), -Math.sin(p * degtorad));
                    Complex phasedValue = vec.getComplex(i - dataOffset).multiply(cmpPhas);
                    if (drawReal) {
                        ve[i - start] = phasedValue.getReal();
                    } else {
                        ve[i - start] = phasedValue.getImaginary();
                    }
                } else {
                    ve[i - start] = vec.getReal(i - dataOffset);
                }
            }
            nPoints = speedSpectrum(ve, start, start, end, dValue, delta, incr, xyValues, xFunction, yFunction);
        } else {
            nPoints = end - start + 1;
            if ((xyValues[0] == null) || (xyValues[0].length < nPoints)) {
                xyValues[0] = new double[nPoints];
                xyValues[1] = new double[nPoints];
            }
            int iLine = 0;
            for (int i = start; i <= end; i++) {
                double intensity;
                if (vec.isComplex()) {
                    double p = ph0 + i * dDelta;
                    Complex cmpPhas = new Complex(Math.cos(p * degtorad), -Math.sin(p * degtorad));
                    Complex phasedValue = vec.getComplex(i - dataOffset).multiply(cmpPhas);
                    if (drawReal) {
                        intensity = phasedValue.getReal();
                    } else {
                        intensity = phasedValue.getImaginary();
                    }
                } else {
                    intensity = vec.getReal(i - dataOffset);
                }
                xyValues[0][iLine] = xFunction.applyAsDouble(dValue, intensity);
                xyValues[1][iLine++] = yFunction.applyAsDouble(dValue, intensity);
                dValue += delta;
            }
        }
        if (bcPath != null) {
            boolean[] signalPoints = vec.getSignalRegion();
            dValue = dValueHold;
            if (signalPoints != null) {
                boolean inBase = !signalPoints[start];
                int last = 0;
                for (int i = start; i < end; i++) {
                    double intensity;
                    if (vec.isComplex()) {
                        double p = ph0 + i * dDelta;
                        Complex cmpPhas = new Complex(Math.cos(p * degtorad), -Math.sin(p * degtorad));
                        Complex phasedValue = vec.getComplex(i - dataOffset).multiply(cmpPhas);
                        if (drawReal) {
                            intensity = phasedValue.getReal();
                        } else {
                            intensity = phasedValue.getImaginary();
                        }
                    } else {
                        intensity = vec.getReal(i - dataOffset);
                    }
                    double xValue = xFunction.applyAsDouble(dValue, intensity);
                    double yValue = yFunction.applyAsDouble(dValue, intensity);
                    if (i == start) {
                        if (inBase) {
                            bcPath.getElements().add(new MoveTo(xValue, yValue));
                        }
                    }
                    if (i == (end - 1)) {
                        if (inBase) {
                            bcPath.getElements().add(new LineTo(xValue, yValue));
                        }
                    }
                    if (!signalPoints[i] != inBase) {
                        if (inBase) {
                            bcPath.getElements().add(new LineTo(xValue, yValue));
                            last = i;
                        } else {
                            bcPath.getElements().add(new MoveTo(xValue, yValue));
                        }
                    }
                    if ((i - last) > 10) {
                        if (inBase) {
                            bcPath.getElements().add(new LineTo(xValue, yValue));
                            last = i;
                        }
                    }
                    inBase = !signalPoints[i];
                    dValue += delta;
                }
            }
        }
        return nPoints;

    }

    public void drawVecAnno(DatasetAttributes dataAttributes, int orientation, AXMODE axMode) {
        Dataset dataset = dataAttributes.getDataset();
        nPoints = 0;
        if (dataset.getVec() != null) {
            Vec vec = dataset.getVec();
            NMRAxis indexAxis = orientation == PolyChart.HORIZONTAL ? axes[0] : axes[1];
            int vecStartPoint = axMode.getIndex(vec, indexAxis.getLowerBound());
            int vecEndPoint = axMode.getIndex(vec, indexAxis.getUpperBound());

            if (!vec.freqDomain()) {
                double[] ve = vec.getAnnotation();
                if ((ve != null) && (ve.length <= vec.getSize())) {
                    int annoEnd = vecEndPoint;
                    if (annoEnd >= ve.length) {
                        annoEnd = ve.length - 1;
                    }
                    nPoints = drawScaledLine(ve, vecStartPoint, annoEnd, vecEndPoint);
                }
            }
        }
    }

    public int drawScaledLine(double[] ve, int start, int annoEnd, int end) {
        double width = axes[0].getWidth();
        double height = axes[1].getHeight();
        double delta = width / (end - start);

        return speedSpectrum(ve, 0, start, annoEnd, 0.0, delta, 4, xy, (index, intensity) -> index,
                (index, intensity) -> (1.0 - intensity) * height);

    }

    static int speedSpectrum(double[] ve, int vStart, int start, int end, double dValue, double delta, int nIncr, double[][] xy, DoubleBinaryOperator xFunction, DoubleBinaryOperator yFunction) {
        double minValue = Double.MAX_VALUE;
        double maxValue = Double.NEGATIVE_INFINITY;
        double pxmin;
        double pxmax;
        double iMin = 0;
        double iMax = 0;
        int k = 0;
        int n = ((end - start + 1) / nIncr) * 2 + 8; // fixme approximate
        if ((xy[0] == null) || (xy[0].length < n)) {
            xy[0] = new double[n];
            xy[1] = new double[n];
        }

        xy[0][0] = xFunction.applyAsDouble(dValue, ve[start - vStart]);
        xy[1][0] = yFunction.applyAsDouble(dValue, ve[start - vStart]);
        dValue += delta;
        int iLine = 0;
        for (int i = (start + 1); i < end; i++) {
            double py1 = ve[i - vStart];

            if (py1 < minValue) {
                minValue = py1;
                iMin = dValue;
            }

            if (py1 > maxValue) {
                maxValue = py1;
                iMax = dValue;
            }

            pxmin = iMin;
            pxmax = iMax;

            k++;
            dValue += delta;

            if (k < nIncr) {
                continue;
            }

            k = 0;
            if (pxmin > pxmax) {
                xy[0][iLine] = (xFunction.applyAsDouble(pxmin, minValue));
                xy[1][iLine++] = (yFunction.applyAsDouble(pxmin, minValue));
                xy[0][iLine] = (xFunction.applyAsDouble(pxmax, maxValue));
                xy[1][iLine++] = (yFunction.applyAsDouble(pxmax, maxValue));
            } else {
                xy[0][iLine] = (xFunction.applyAsDouble(pxmax, maxValue));
                xy[1][iLine++] = (yFunction.applyAsDouble(pxmax, maxValue));
                xy[0][iLine] = (xFunction.applyAsDouble(pxmin, minValue));
                xy[1][iLine++] = (yFunction.applyAsDouble(pxmin, minValue));
            }

            minValue = Double.MAX_VALUE;
            maxValue = Double.NEGATIVE_INFINITY;
        }
        xy[0][iLine] = (xFunction.applyAsDouble(dValue, ve[end - vStart]));
        xy[1][iLine++] = (yFunction.applyAsDouble(dValue, ve[end - vStart]));
        return iLine;
    }
}
