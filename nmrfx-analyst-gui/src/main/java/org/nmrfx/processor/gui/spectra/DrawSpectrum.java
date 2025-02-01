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

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import org.apache.commons.math3.complex.Complex;
import org.nmrfx.chart.Axis;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.graphicsio.GraphicsContextProxy;
import org.nmrfx.graphicsio.GraphicsIOException;
import org.nmrfx.math.VecBase;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.PolyChartAxes;
import org.nmrfx.processor.gui.spectra.DatasetAttributes.AXMODE;
import org.nmrfx.processor.math.Vec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleBinaryOperator;

/**
 * @author brucejohnson
 */
public class DrawSpectrum {
    private static final Logger log = LoggerFactory.getLogger(DrawSpectrum.class);
    private static final double DEG_TO_RAD = Math.PI / 180.0;
    private static final long MAX_TIME = 2000;

    private static final ExecutorService CONTOUR_GENERATION_EXECUTOR = Executors.newFixedThreadPool(30);
    private static final ExecutorService CONTOUR_DRAWING_EXECUTOR = Executors.newFixedThreadPool(100);
    private static boolean cancelled = false;

    static {
        ((ThreadPoolExecutor) CONTOUR_DRAWING_EXECUTOR).setKeepAliveTime(10, TimeUnit.SECONDS);
    }

    private final List<DatasetAttributes> dataAttrList = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong contourDrawingRequestId = new AtomicLong(0);
    private final ArrayBlockingQueue<ContourDrawingRequest> contourQueue = new ArrayBlockingQueue<>(4);
    private final ContourGenerationService contoursGeneration;
    private final ContoursDrawingService contoursDrawing;
    private final double[][] xy = new double[2][];
    private final PolyChartAxes axes;
    private final GraphicsContextInterface g2;
    private double stackWidth = 0.0;
    private double stackY = 0.0;
    private int nPoints = 0;
    private int iChunk = 0;
    private int rowIndex = -1;
    private long startTime = 0;
    private Rectangle clipRect = null;

    public DrawSpectrum(PolyChartAxes axes, GraphicsContextProxy graphics) {
        this.axes = axes;
        g2 = graphics;
        contoursGeneration = new ContourGenerationService(this);
        contoursDrawing = new ContoursDrawingService(this);
    }

    public void setupHaltButton(Button button) {
        button.setOnAction(actionEvent -> {
            cancelled = true;
            contoursGeneration.cancel();
            contoursDrawing.cancel();
        });
        button.disableProperty().bind(contoursGeneration.stateProperty().isNotEqualTo(Worker.State.RUNNING));
    }

    public void setClipRect(double x, double y, double width, double height) {
        clipRect = new Rectangle(x, y, width, height);
    }

    public void drawSpectrum(ArrayList<DatasetAttributes> dataGenerators, boolean pick) {
        cancelled = false;

        if (pick) {
            return;
        }
        contourDrawingRequestId.incrementAndGet();
        contoursGeneration.cancel();
        contoursDrawing.cancel();
        Executor makeExec = contoursGeneration.getExecutor();
        Executor drawExec = contoursDrawing.getExecutor();
        if (makeExec instanceof ThreadPoolExecutor tPool) {
            tPool.purge();
        }
        if (drawExec instanceof ThreadPoolExecutor tPool) {
            tPool.purge();
        }
        dataAttrList.clear();
        dataAttrList.addAll(dataGenerators);
        contourQueue.clear();
        startTime = System.currentTimeMillis();

        contoursGeneration.restart();
        contoursDrawing.restart();
    }

    public void clearThreads() {
        contoursGeneration.cancel();
        contoursDrawing.cancel();
    }

    public boolean drawSpectrumImmediate(GraphicsContextInterface g2I, ArrayList<DatasetAttributes> dataGenerators) {
        cancelled = false;

        dataAttrList.clear();
        dataAttrList.addAll(dataGenerators);
        contourQueue.clear();
        startTime = System.currentTimeMillis();
        boolean finished = false;
        try {
            finished = drawNow(g2I);
        } catch (IOException ex) {
            log.warn(ex.getMessage(), ex);
        }

        return finished;
    }

    private PolyChartAxes getAxes() {
        // 2023-06-01: Previous implementation was making a (shallow) copy of the backing array.
        // We may have to reintroduce this copy if we notice concurrent access bugs when drawing contours while changing axes
        return axes;
    }

    private boolean drawNow(GraphicsContextInterface g2I) throws IOException {
        for (DatasetAttributes fileData : dataAttrList) {
            float[] levels = getLevels(fileData);

            double[] offset = {0, 0};
            fileData.mChunk = -1;
            float[][] z = null;
            do {
                long currentTime = System.currentTimeMillis();
                if ((g2I instanceof GraphicsContextProxy) && ((currentTime - startTime) > MAX_TIME)) {
                    return false;

                }
                int iChunk = fileData.mChunk + 1;
                double[][] pix = getPix(axes.getX(), axes.getY(), fileData);
                final Contour[] contours = new Contour[2];
                contours[0] = new Contour(fileData.ptd, pix);
                contours[1] = new Contour(fileData.ptd, pix);
                try {
                    z = getData(fileData, iChunk, offset, z);
                    if (z != null) {
                        double xOff = offset[0];
                        double yOff = offset[1];
                        int[][] cells = new int[z.length][z[0].length];
                        for (int iPosNeg = 0; iPosNeg < 2; iPosNeg++) {
                            Contour contour = contours[iPosNeg];
                            if (!setContext(contour, fileData, iPosNeg, iChunk)) {
                                continue;
                            }
                            contour.xOffset = xOff;
                            contour.yOffset = yOff;
                            float sign = iPosNeg == 0 ? 1.0f : -1.0f;
                            for (float level : levels) {
                                if (!checkLevels(z, iPosNeg, sign * level)) {
                                    break;
                                }
                                if (!contour.marchSquares(sign * level, z, cells)) {
                                    contour.drawSquares(g2I);
                                } else {
                                    break;
                                }
                            }
                        }
                    } else {
                        break;
                    }
                } catch (GraphicsIOException ex) {
                    throw new IOException(ex.getMessage());
                }
            } while (true);
        }
        return true;
    }

    public void clip(GraphicsContextInterface gC) {
        try {
            gC.beginPath();
            Rectangle r = clipRect;
            gC.rect(r.getX(), r.getY(), r.getWidth(), r.getHeight());
            gC.clip();
            gC.beginPath();
        } catch (Exception ex) {
            log.warn(ex.getMessage(), ex);
        }
    }

    public void drawProjection(DatasetAttributes projectionDatasetAttributes, DatasetAttributes datasetAttr, int orientation) {
        int sliceDim = orientation;
        Vec sliceVec = new Vec(32, false);
        boolean drawReal = datasetAttr.getDrawReal();
        try {
            datasetAttr.getProjection((Dataset) projectionDatasetAttributes.getDataset(), sliceVec, sliceDim);
            double lvlMult = projectionDatasetAttributes.getLvl();
            if (sliceDim == 0) {
                double offset = axes.getX().getYOrigin() - axes.getY().getHeight() * 1.005;
                drawVector(sliceVec, orientation, 0, AXMODE.PPM, drawReal, 0.0, 0.0, null,
                        (index, intensity) -> axes.getX().getDisplayPosition(index),
                        (index, intensity) -> -intensity / lvlMult + offset, false);
            } else {
                double offset = axes.getX().getXOrigin() + axes.getX().getWidth() * 1.005;
                drawVector(sliceVec, orientation, 0, AXMODE.PPM, drawReal, 0.0, 0.0, null,
                        (index, intensity) -> intensity / lvlMult + offset,
                        (index, intensity) -> axes.getY().getDisplayPosition(index), false);
            }
        } catch (IOException ioE) {
            log.warn(ioE.getMessage(), ioE);
        }
    }

    public void drawSlice(DatasetAttributes datasetAttr, SliceAttributes sliceAttr, int orientation, double slicePosX, double slicePosY, double ph0, double ph1) {
        int sliceDim = orientation;
        Vec sliceVec = new Vec(32, false);
        boolean drawReal = datasetAttr.getDrawReal();
        boolean offsetTracking = sliceAttr.getOffsetTracking();
        try {
            datasetAttr.getSlice(sliceVec, sliceDim, slicePosX, slicePosY);
            double level = datasetAttr.lvlProperty().get();
            double scale = -sliceAttr.getScaleValue() / level;
            if (sliceDim == 0) {
                double offset;
                if (offsetTracking) {
                    offset = axes.getY().getDisplayPosition(slicePosY);
                } else {
                    offset = axes.getX().getYOrigin() - axes.getY().getHeight() * sliceAttr.getOffsetYValue();
                }
                drawVector(sliceVec, orientation, 0, AXMODE.PPM, drawReal, ph0, ph1, null,
                        (index, intensity) -> axes.getX().getDisplayPosition(index),
                        (index, intensity) -> intensity * scale + offset, false);
            } else {
                double offset;
                if (offsetTracking) {
                    offset = axes.getX().getDisplayPosition(slicePosX);
                } else {
                    offset = axes.getX().getXOrigin() + axes.getX().getWidth() * sliceAttr.getOffsetXValue();
                }
                drawVector(sliceVec, orientation, 0, AXMODE.PPM, drawReal, ph0, ph1, null,
                        (index, intensity) -> -intensity * scale + offset,
                        (index, intensity) -> axes.getY().getDisplayPosition(index), false);
            }
        } catch (IOException ioE) {
            log.warn(ioE.getMessage(), ioE);
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

    public boolean draw1DSpectrum(DatasetAttributes dataAttributes, double firstLvl, double firstOffset,
                                  int i1D, int n1D, int orientation, AXMODE axMode,
                                  double ph0, double ph1, Path bcPath) {
        VecBase specVec = new Vec(32);
        boolean drawReal = dataAttributes.getDrawReal();
        boolean offsetMode = true;
        DatasetBase dataset = dataAttributes.getDataset();
        if (dataset.getVec() != null) {
            specVec = dataset.getVec();
            iChunk = -1;
        } else {
            offsetMode = false;
            try {
                int iDim = 0;
                rowIndex = dataAttributes.getRowIndex(iDim, iChunk);
                if (!dataAttributes.Vector(specVec, iChunk--)) {
                    return false;
                }
            } catch (IOException ioE) {
                return false;
            }
        }
        double[] offsets = getOffset(dataAttributes, firstOffset, i1D, n1D);
        double lvlMult = dataAttributes.getLvl() / firstLvl;
        drawVector(specVec, orientation, 0, axMode, drawReal, ph0, ph1, bcPath,
                (index, intensity) -> axes.getX().getDisplayPosition(index) + offsets[0],
                (index, intensity) -> axes.getY().getDisplayPosition(intensity / lvlMult) - offsets[1], offsetMode);

        return iChunk >= 0;
    }

    private double getOffsetFraction(int i1D, int n1D) {
        double fraction = 0.0;
        if (n1D > 1) {
            fraction = (n1D - i1D - 1.0) / (n1D - 1.0);
        }
        return fraction;
    }

    public void setStackWidth(double value) {
        stackWidth = value;
    }

    public void setStackY(double value) {
        stackY = Math.min(1.00, Math.max(0.0, value));
    }

    public double[] getOffset(DatasetAttributes dataAttributes, double firstOffset, int i1D, int n1D) {
        double height = axes.getY().getHeight();
        double mapOffset = height * dataAttributes.getMapOffset(rowIndex);
        double dOffset = dataAttributes.getOffset();
        double dataOffset = height * (dOffset - firstOffset);
        double fraction = getOffsetFraction(i1D, n1D);
        double delta = height * fraction * stackY;
        if (n1D > 0) {
            delta *= (1.0 - firstOffset) * (n1D - 1.0) / n1D;
        }
        double yOffset = dataOffset + mapOffset + delta;
        double xOffset = stackWidth * fraction;
        return new double[]{xOffset, yOffset};
    }

    public Optional<Double> draw1DIntegrals(DatasetAttributes dataAttributes,
                                            double ppm1, double ppm2, double[] offsets,
                                            double integralMax, double low, double high) {
        Vec specVec = new Vec(32);
        Optional<Double> result = Optional.empty();
        try {
            if (!dataAttributes.getIntegralVec(specVec, iChunk + 1, ppm1, ppm2, offsets)) {
                System.out.println("no  vec int");
                return result;
            }
        } catch (IOException ioE) {
            log.warn(ioE.getMessage(), ioE);
            return result;
        }
        Double integralValue = specVec.getReal(specVec.getSize() - 1);
        result = Optional.of(integralValue);
        double height = axes.getY().getHeight();
        double yOrigin = axes.getY().getYOrigin();
        drawSubVector(specVec, 0,
                (index, intensity) -> axes.getX().getDisplayPosition(index),
                (index, intensity) -> yOrigin - height + (1.0 - high) * height + (high - low) * height * (1.0 - (intensity / integralMax)), ppm1, ppm2);
        return result;
    }

    private void drawSubVector(Vec vec, int dataOffset,
                               DoubleBinaryOperator xFunction, DoubleBinaryOperator yFunction, double ppm1, double ppm2) {
        int size = vec.getSize();

        double indexAxisDelta = (ppm1 - ppm2) / vec.getSize();
        double dValue = ppm2;

        nPoints = drawVectorCore(vec, dataOffset, true, 0.0, 0.0, xy, null, xFunction,
                yFunction, 0, vec.getSize() - 1, size, dValue, 0.0, indexAxisDelta);
    }

    private void drawVector(VecBase vec, int orientation, int dataOffset, AXMODE axMode, boolean drawReal, double ph0, double ph1, Path bcPath, DoubleBinaryOperator xFunction, DoubleBinaryOperator yFunction, boolean offsetVec) {
        int size = vec.getSize();
        double phase1Delta = ph1 / (size - 1);
        Axis indexAxis = orientation == PolyChart.HORIZONTAL ? axes.getX() : axes.getY();

        int vecStartPoint;
        int vecEndPoint;
        double indexAxisDelta;
        if (offsetVec) {
            vecStartPoint = axMode.getIndex(vec, indexAxis.getLowerBound());
            vecEndPoint = axMode.getIndex(vec, indexAxis.getUpperBound());
            indexAxisDelta = axMode.getIncrement(vec, indexAxis.getLowerBound(), indexAxis.getUpperBound());
        } else if (indexAxis.isReversed()) {
            vecStartPoint = vec.getSize() - 1;
            vecEndPoint = 0;
            dataOffset = 0;
            indexAxisDelta = (indexAxis.getLowerBound() - indexAxis.getUpperBound()) / vecStartPoint;
        } else {
            vecStartPoint = 0;
            vecEndPoint = vec.getSize() - 1;
            dataOffset = 0;
            indexAxisDelta = (indexAxis.getUpperBound() - indexAxis.getLowerBound()) / vecEndPoint;
        }
        double dValue = indexAxis.getLowerBound();

        if (vecStartPoint > vecEndPoint) {
            int hold = vecStartPoint;
            vecStartPoint = vecEndPoint;
            vecEndPoint = hold;
            dValue = indexAxis.getUpperBound();
        }
        nPoints = drawVectorCore(vec, dataOffset, drawReal, ph0, ph1, xy, bcPath, xFunction, yFunction, vecStartPoint, vecEndPoint, size, dValue, phase1Delta, indexAxisDelta);
    }

    public void drawVecAnno(DatasetAttributes dataAttributes, int orientation, AXMODE axMode) {
        DatasetBase dataset = dataAttributes.getDataset();
        nPoints = 0;
        if (dataset.getVec() != null) {
            VecBase vec = dataset.getVec();
            Axis indexAxis = orientation == PolyChart.HORIZONTAL ? axes.getX() : axes.getY();
            int vecStartPoint = axMode.getIndex(vec, indexAxis.getLowerBound());
            int vecEndPoint = axMode.getIndex(vec, indexAxis.getUpperBound());

            if (!vec.freqDomain()) {
                double[] ve = null;
                if (vec instanceof Vec) {
                    ve = ((Vec) vec).getAnnotation();
                }
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

    private int drawScaledLine(double[] ve, int start, int annoEnd, int end) {
        double width = axes.getX().getWidth();
        double height = axes.getY().getHeight();
        double delta = width / (end - start);
        double xOrigin = axes.getX().getXOrigin();
        double yOrigin = axes.getY().getYOrigin();

        return drawFullLine(ve, start, annoEnd, 0.0, delta, xy,
                (index, intensity) -> xOrigin + index,
                (index, intensity) -> yOrigin - intensity * (height - 1) + 1);

    }

    private int drawFullLine(double[] ve, int start, int end, double dValue, double delta, double[][] xy, DoubleBinaryOperator xFunction, DoubleBinaryOperator yFunction) {
        nPoints = 0;
        int maxPoints = end - start + 1;
        if ((xy[0] == null) || (xy[0].length < maxPoints)) {
            xy[0] = new double[maxPoints];
            xy[1] = new double[maxPoints];
        }
        int iLine = 0;
        for (int i = start; i <= end; i++) {
            double intensity = ve[i];
            if (intensity != Double.MAX_VALUE) {
                xy[0][iLine] = xFunction.applyAsDouble(dValue, intensity);
                xy[1][iLine++] = yFunction.applyAsDouble(dValue, intensity);
            }
            dValue += delta;
        }
        nPoints = iLine;
        return iLine;
    }

    public Optional<IntegralHit> drawActiveRegion(GraphicsContextInterface g2, DatasetAttributes datasetAttr, DatasetRegion region) throws GraphicsIOException {
        return drawActiveRegion(g2, datasetAttr, region, false, false, 0, 0);
    }

    public Optional<IntegralHit> hitRegion(DatasetAttributes datasetAttr, DatasetRegion region, boolean controls, double pickX, double pickY) {
        Optional<IntegralHit> result;
        try {
            result = drawActiveRegion(null, datasetAttr, region, true, controls, pickX, pickY);
        } catch (GraphicsIOException ex) {
            result = Optional.empty();
        }
        return result;
    }

    private Optional<IntegralHit> drawActiveRegion(GraphicsContextInterface g2, DatasetAttributes datasetAttr, DatasetRegion region, boolean pick, boolean pickControls, double pickX, double pickY) throws GraphicsIOException {
        Optional<IntegralHit> result = Optional.empty();
        double rx2 = region.getRegionStart(0);
        double rx1 = region.getRegionEnd(0);
        double ryB1 = region.getRegionStartIntensity(0);
        double ryB2 = region.getRegionEndIntensity(0);
        double ry1;
        double ry2;
        if (region.getNDims() > 1) {
            ry1 = region.getRegionStart(1);
            ry2 = region.getRegionEnd(1);
        } else {
            ry2 = axes.getY().getUpperBound();
            ry1 = axes.getY().getLowerBound();
        }

        double px1 = axes.getX().getDisplayPosition(rx1);
        double py1 = axes.getY().getDisplayPosition(ry1);

        double px2 = axes.getX().getDisplayPosition(rx2);
        double py2 = axes.getY().getDisplayPosition(ry1);

        double pxb1 = axes.getX().getDisplayPosition(rx1);
        double pyb1 = axes.getY().getDisplayPosition(ryB1);

        double pxb2 = axes.getX().getDisplayPosition(rx2);
        double pyb2 = axes.getY().getDisplayPosition(ryB2);

        double pxb1p = axes.getX().getDisplayPosition(rx1);
        double pyb1p = axes.getY().getDisplayPosition(ryB1) - 25;
        double pxb2p = axes.getX().getDisplayPosition(rx2);
        double pyb2p = axes.getY().getDisplayPosition(ryB2) - 25;

        if ((px2 - px1) < 2) {
            px1 = px1 - 1;
            px2 = px2 + 1;
        }

        double px3 = axes.getX().getDisplayPosition(rx2);
        double py3 = axes.getY().getDisplayPosition(ry2);

        double px4 = axes.getX().getDisplayPosition(rx1);
        double py4 = axes.getY().getDisplayPosition(ry2);

        if ((px4 - px3) < 2) {
            px3 = px3 - 1;
            px4 = px4 + 1;
        }

        if (pick) {
            int minDelta = Integer.MAX_VALUE;
            int iMin = -1;
            if (!pickControls) {
                if ((pickX > pxb1) && (pickX < pxb2)) {
                    minDelta = 0;
                    iMin = 0;

                }
            } else {
                int[] deltas = new int[4];
                int delP1 = (int) (Math.abs(pickX - pxb1));
                int delP2 = (int) (Math.abs(pickY - pyb1));
                deltas[0] = delP1 + delP2;
                delP1 = (int) (Math.abs(pickX - pxb2));
                delP2 = (int) (Math.abs(pickY - pyb2));
                deltas[1] = delP1 + delP2;
                delP1 = (int) (Math.abs(pickX - pxb1p));
                delP2 = (int) (Math.abs(pickY - pyb1p));
                deltas[2] = delP1 + delP2;
                delP1 = (int) (Math.abs(pickX - pxb2p));
                delP2 = (int) (Math.abs(pickY - pyb2p));
                deltas[3] = delP1 + delP2;
                int iValue = 0;
                for (int delta : deltas) {
                    if (delta < minDelta) {
                        minDelta = delta;
                        iMin = iValue;
                    }
                    iValue++;
                }
            }
            double pickJiggle = 10.0;
            if (minDelta < pickJiggle) {
                result = Optional.of(new IntegralHit(datasetAttr, region, iMin + 1));
            } else if ((pickX > (pxb1 + pickJiggle)) && (pickX < (pxb2 - pickJiggle))) {
                double f = (pickX - pxb1) / (pxb2 - pxb1);
                int yVal = (int) (pyb1 + f * (pyb2 - pyb1));
                int delta1 = (int) Math.abs(pickY - yVal);
                if (delta1 < pickJiggle) {
                    result = Optional.of(new IntegralHit(datasetAttr, region, 0));
                }
            }
        } else {
            g2.setStroke(Color.GREEN);

            g2.strokeLine(px1, py1 - 5, px4, py4 + 5);
            g2.strokeLine(px2, py2 - 5, px3, py3 + 5);
            g2.strokeLine(pxb1, pyb1, pxb2, pyb2);

            drawHandleV(g2, pxb1, pyb1);
            drawHandleV(g2, pxb2, pyb2);
            drawHandleH(g2, pxb1p, pyb1p);
            drawHandleH(g2, pxb2p, pyb2p);
        }

        return result;
    }

    private void drawHandleV(GraphicsContextInterface g2, double x, double y) {
        int handleSize = 6;
        int handleSize2 = 9;
        int halfHandleSize = handleSize / 2;

        g2.beginPath();
        g2.moveTo(x, y - handleSize2);
        g2.lineTo(x, y + handleSize2);

        g2.moveTo(x, y - handleSize2);
        g2.lineTo(x - halfHandleSize, y - handleSize);
        g2.moveTo(x, y - handleSize2);
        g2.lineTo(x + halfHandleSize, y - handleSize);

        g2.moveTo(x, y + handleSize2);
        g2.lineTo(x - halfHandleSize, y + handleSize);
        g2.moveTo(x, y + handleSize2);
        g2.lineTo(x + halfHandleSize, y + handleSize);

        g2.moveTo(x - halfHandleSize, y - halfHandleSize);
        g2.lineTo(x + halfHandleSize, y - halfHandleSize);
        g2.lineTo(x + halfHandleSize, y + halfHandleSize);
        g2.lineTo(x - halfHandleSize, y + halfHandleSize);
        g2.lineTo(x - halfHandleSize, y - halfHandleSize);

        g2.setFill(Color.WHITE);
        g2.fill();
        g2.setLineWidth(2);
        g2.setStroke(Color.BLACK);
        g2.stroke();
    }

    private void drawHandleH(GraphicsContextInterface g2, double x, double y) {
        int handleSize = 6;
        int handleSize2 = 9;
        int halfHandleSize = handleSize / 2;

        g2.beginPath();
        g2.moveTo(x - handleSize, y);
        g2.lineTo(x + handleSize, y);

        g2.moveTo(x - handleSize2, y);
        g2.lineTo(x - handleSize, y - halfHandleSize);
        g2.moveTo(x - handleSize2, y);
        g2.lineTo(x - handleSize, y + halfHandleSize);

        g2.moveTo(x + handleSize2, y);
        g2.lineTo(x + handleSize, y - halfHandleSize);
        g2.moveTo(x + handleSize2, y);
        g2.lineTo(x + handleSize, y + halfHandleSize);

        g2.moveTo(x - halfHandleSize, y - halfHandleSize);
        g2.lineTo(x + halfHandleSize, y - halfHandleSize);
        g2.lineTo(x + halfHandleSize, y + halfHandleSize);
        g2.lineTo(x - halfHandleSize, y + halfHandleSize);
        g2.lineTo(x - halfHandleSize, y - halfHandleSize);

        g2.setFill(Color.WHITE);
        g2.fill();
        g2.setLineWidth(2);
        g2.setStroke(Color.BLACK);
        g2.stroke();
    }

    private static float[] getLevels(DatasetAttributes fileData) {
        int nLevels = fileData.getNlvls();
        double clm = fileData.getClm();

        float[] levels = new float[nLevels];
        levels[0] = (float) fileData.lvlProperty().get();
        for (int i = 1; i < nLevels; i++) {
            levels[i] = (float) (levels[i - 1] * clm);
        }
        return levels;
    }

    private static double[][] getPix(Axis xAxis, Axis yAxis, DatasetAttributes dataAttr) {
        DatasetBase dataset = dataAttr.getDataset();
        double xPoint1 = dataset.pointToPPM(dataAttr.dim[0], dataAttr.ptd[0][0]);
        double xPoint2 = dataset.pointToPPM(dataAttr.dim[0], dataAttr.ptd[0][1]);
        double yPoint1 = dataset.pointToPPM(dataAttr.dim[1], dataAttr.ptd[1][0]);
        double yPoint2 = dataset.pointToPPM(dataAttr.dim[1], dataAttr.ptd[1][1]);
        double[][] pix = new double[2][2];
        pix[0][0] = xAxis.getDisplayPosition(xPoint1);
        pix[0][1] = xAxis.getDisplayPosition(xPoint2);
        pix[1][0] = yAxis.getDisplayPosition(yPoint1);
        pix[1][1] = yAxis.getDisplayPosition(yPoint2);
        return pix;
    }

    private static float[][] getData(DatasetAttributes dataAttr, int iChunk, double[] offset, float[][] z) throws IOException {
        StringBuffer chunkLabel = new StringBuffer();
        chunkLabel.setLength(0);
        int[][] apt = new int[dataAttr.getDataset().getNDim()][2];
        int fileStatus = dataAttr.getMatrixRegion(iChunk, 2048, 0, apt,
                offset, chunkLabel);
        if (fileStatus != 0) {
            return null;
        }
        return dataAttr.readMatrix(dataAttr.mChunk, chunkLabel.toString(), apt, z);
    }

    private static boolean setContext(Contour contour, DatasetAttributes dataAttr, int iPosNeg, int iChunk) throws GraphicsIOException {
        final boolean ok;
        if (iPosNeg == 0) {
            ok = dataAttr.getPos();
        } else {
            ok = dataAttr.getNeg();
        }
        int index = -1;
        if (!dataAttr.drawList.isEmpty()) {
            index = dataAttr.getDrawListIndex(iChunk);
        }
        double widthScale = dataAttr.isSelected(index) ? 3.0 : 1.0;
        if (ok) {
            if (iPosNeg == 0) {
                Color posColor = dataAttr.getPosColor(index);
                contour.setAttributes(dataAttr.posWidthProperty().get() * widthScale, posColor);
            } else {
                Color negColor = dataAttr.getNegColor();
                contour.setAttributes(dataAttr.negWidthProperty().get() * widthScale, negColor);
            }
        }
        return ok;
    }

    private static boolean checkLevels(float[][] z, int iPosNeg, float level) {
        int ny = z.length;
        int nx = z[0].length;
        boolean ok = false;

        for (int jj = 0; jj < ny; jj++) {
            for (int ii = 0; ii < nx; ii++) {
                if ((iPosNeg == 0) && (z[jj][ii] > level)) {
                    ok = true;
                    break;
                } else if ((iPosNeg == 1) && (z[jj][ii] < level)) {
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

    private static int drawVectorCore(VecBase vec, int dataOffset, boolean drawReal,
                                      double ph0, double ph1, double[][] xyValues, Path bcPath, DoubleBinaryOperator xFunction,
                                      DoubleBinaryOperator yFunction, int start, int end, int size,
                                      double dValue, double dDelta, double delta) {

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
        if (((Math.abs(ph0) > 1.0e-6) || (Math.abs(ph1) > 1.0e-6)) && !vec.isComplex()) {
            if (vec instanceof Vec) {
                ((Vec) vec).hft();
            }
        }
        double dValueHold = dValue;
        int nPoints = 0;
        if (incr != 1) {
            double[] ve = new double[end - start + 1];
            for (int i = start; i <= end; i++) {
                double p = ph0 + i * dDelta;
                if (vec.isComplex()) {
                    Complex cmpPhas = new Complex(Math.cos(p * DEG_TO_RAD), -Math.sin(p * DEG_TO_RAD));
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
            nPoints = 0;
            int maxPoints = end - start + 1;
            if ((xyValues[0] == null) || (xyValues[0].length < maxPoints)) {
                xyValues[0] = new double[maxPoints];
                xyValues[1] = new double[maxPoints];
            }
            int iLine = 0;
            for (int i = start; i <= end; i++) {
                double intensity;
                if (vec.isComplex()) {
                    double p = ph0 + i * dDelta;
                    Complex cmpPhas = new Complex(Math.cos(p * DEG_TO_RAD), -Math.sin(p * DEG_TO_RAD));
                    Complex phasedValue = vec.getComplex(i - dataOffset).multiply(cmpPhas);
                    if (drawReal) {
                        intensity = phasedValue.getReal();
                    } else {
                        intensity = phasedValue.getImaginary();
                    }
                } else {
                    intensity = vec.getReal(i - dataOffset);
                }
                if (intensity != Double.MAX_VALUE) {
                    xyValues[0][iLine] = xFunction.applyAsDouble(dValue, intensity);
                    xyValues[1][iLine++] = yFunction.applyAsDouble(dValue, intensity);
                }
                dValue += delta;
            }
            nPoints = iLine;
        }
        if (bcPath != null) {
            boolean[] signalPoints = null;
            if (vec instanceof Vec) {
                signalPoints = ((Vec) vec).getSignalRegion();
            }
            dValue = dValueHold;
            if (signalPoints != null) {
                boolean inBase = !signalPoints[start];
                int last = 0;
                for (int i = start; i < end; i++) {
                    double intensity = 0.0;
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
                    if (signalPoints[i] == inBase) {
                        if (inBase) {
                            bcPath.getElements().add(new LineTo(xValue, yValue));
                            last = i;
                        } else {
                            bcPath.getElements().add(new MoveTo(xValue, yValue));
                        }
                    }
                    if ((i - last) > 10) {
                        if (inBase) {
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

    private static int speedSpectrum(double[] ve, int vStart, int start, int end, double dValue, double delta, int nIncr, double[][] xy, DoubleBinaryOperator xFunction, DoubleBinaryOperator yFunction) {
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
            if (py1 == Double.MAX_VALUE) {
                dValue += delta;
                continue;
            }

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

    /**
     * A way to store a contour for asynchronous drawing. The contour is stored in a queue when computed, while an asynchronous tasks is polling the queue to actually draw them.
     * The job identifier is used to avoid drawing contours generated for a previous drawing request.
     *
     * @param contour   the contour to draw asynchronously
     * @param requestId the job identifier for this drawing request
     */
    private record ContourDrawingRequest(Contour contour, long requestId) {
    }

    /**
     * The service responsible for computing contours and adding them to the shared queue.
     */
    private static class ContourGenerationService extends Service<Void> {
        private final DrawSpectrum drawSpectrum;
        private List<DatasetAttributes> dataAttrList;
        private PolyChartAxes axes;
        private boolean done = false;

        private ContourGenerationService(DrawSpectrum drawSpectrum) {
            this.drawSpectrum = drawSpectrum;
            setExecutor(CONTOUR_GENERATION_EXECUTOR);
        }

        @Override
        protected Task<Void> createTask() {
            return new Task<>() {
                @Override
                protected Void call() {
                    try {
                        done = false;
                        dataAttrList = new ArrayList<>();
                        dataAttrList.addAll(drawSpectrum.dataAttrList);
                        for (DatasetAttributes fileData : dataAttrList) {
                            axes = drawSpectrum.getAxes();
                            generateDrawingRequests(this, fileData);
                            if (done) {
                                break;
                            }
                        }
                    } catch (IOException e) {
                        log.warn(e.getMessage(), e);
                    }
                    return null;
                }
            };
        }

        private void generateDrawingRequests(Task<Void> task, DatasetAttributes fileData) throws IOException {
            float[] levels = getLevels(fileData);
            double[] offset = {0, 0};
            fileData.mChunk = -1;
            float[][] z = null;

            do {
                if (task.isCancelled()) {
                    done = true;
                    break;
                }
                int iChunk = fileData.mChunk + 1;
                double[][] pix = getPix(axes.getX(), axes.getY(), fileData);

                try {
                    z = getData(fileData, iChunk, offset, z);
                    if (z != null) {
                        double xOff = offset[0];
                        double yOff = offset[1];

                        for (int iPosNeg = 0; iPosNeg < 2; iPosNeg++) {
                            float sign = iPosNeg == 0 ? 1.0f : -1.0f;
                            for (float level : levels) {
                                if (!checkLevels(z, iPosNeg, sign * level)) {
                                    break;
                                }
                                Contour contour = new Contour(fileData.ptd, pix);
                                if (!setContext(contour, fileData, iPosNeg, iChunk)) {
                                    continue;
                                }
                                contour.xOffset = xOff;
                                contour.yOffset = yOff;

                                int[][] cells = new int[z.length][z[0].length];
                                if (!contour.marchSquares(sign * level, z, cells)) {
                                    try {
                                        ContourDrawingRequest request = new ContourDrawingRequest(contour, drawSpectrum.contourDrawingRequestId.get());
                                        drawSpectrum.contourQueue.put(request);
                                    } catch (InterruptedException ex) {
                                        done = true;
                                        return;
                                    }
                                } else {
                                    break;
                                }
                            }
                        }
                    } else {
                        break;
                    }
                } catch (GraphicsIOException ex) {
                    throw new IOException(ex.getMessage());
                }
            } while (true);
        }
    }

    /**
     * The service responsible for polling contour drawing request from the queue and actually drawing them.
     */
    private static class ContoursDrawingService extends Service<Void> {
        private final DrawSpectrum drawSpectrum;

        private ContoursDrawingService(DrawSpectrum drawSpectrum) {
            this.drawSpectrum = drawSpectrum;
            setExecutor(CONTOUR_DRAWING_EXECUTOR);
        }

        @Override
        protected Task<Void> createTask() {
            return new Task<>() {
                @Override
                protected Void call() {
                    try {
                        drawAllContours(this);
                    } catch (Exception e) {
                        log.warn(e.getMessage(), e);
                    }
                    return null;
                }
            };
        }

        private void drawAllContours(Task<Void> task) {
            boolean interrupted = false;
            try {
                while (true) {
                    if (task.isCancelled()) {
                        break;
                    }
                    if (Thread.currentThread().isInterrupted()) {
                        interrupted = true;
                        break;
                    }
                    if (DrawSpectrum.cancelled) {
                        return;
                    }

                    try {
                        if (!drawNextContour()) {
                            break;
                        }
                    } catch (ExecutionException ex) {
                        log.warn(ex.getMessage(), ex);
                    } catch (InterruptedException ex) {
                        interrupted = true;
                        break;
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        /**
         * Poll a contour from the queue, draw it on JavaFX's thread, and wait until termination.
         *
         * @return false if the queue is empty
         */
        private boolean drawNextContour() throws InterruptedException, ExecutionException {
            ContourDrawingRequest queuedContour = drawSpectrum.contourQueue.poll(10, TimeUnit.SECONDS);
            if (queuedContour == null) {
                return false;
            }

            Fx.runOnFxThreadAndWait(() -> drawSquares(drawSpectrum, queuedContour));
            return true;
        }

        private static void drawSquares(DrawSpectrum drawSpectrum, ContourDrawingRequest request) {
            if (DrawSpectrum.cancelled || request.requestId != drawSpectrum.contourDrawingRequestId.get()) {
                return;
            }

            try {
                request.contour.drawSquares(drawSpectrum.g2);
            } catch (Exception ex) {
                log.warn("Exception while drawing square", ex);
            }
        }
    }
}
