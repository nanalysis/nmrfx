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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
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
import javafx.scene.shape.Rectangle;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.graphicsio.GraphicsContextProxy;
import org.nmrfx.graphicsio.GraphicsIOException;
import org.nmrfx.processor.datasets.DatasetRegion;
import org.nmrfx.processor.gui.PolyChart.DISDIM;

/**
 *
 * @author brucejohnson
 */
public class DrawSpectrum {

    static public boolean MARCH_MODE = true;

    static final double degtorad = Math.PI / 180.0;
    static final long MAX_TIME = 2000;
    NMRAxis[] axes;
    private boolean useThread = true;
    private SpectrumViewParameters viewPar = new SpectrumViewParameters();
    private SpectrumColorParameters colorPar = new SpectrumColorParameters();
    static Color[] gradColors = new Color[0];
    GraphicsContextInterface g2;
    List<DatasetAttributes> dataAttrList = Collections.synchronizedList(new ArrayList<>());
    final Canvas canvas;
    DrawTask makeContours;
    DrawContours drawContours;
    AXMODE[] axModes;
    DISDIM disDim = DISDIM.TwoD;
    double[][] xy = new double[2][];
    int nPoints = 0;
    int iChunk = 0;
    int rowIndex = -1;
    private static boolean cancelled = false;
    ArrayBlockingQueue<DrawObject> contourQueue = new ArrayBlockingQueue<>(4);
    volatile long jobCount = 0;
    private long startTime = 0;
    private long lastPlotTime = 0;
    Rectangle clipRect = null;

    public DrawSpectrum(NMRAxis[] axes, Canvas canvas) {
        this.axes = axes;
        this.canvas = canvas;
        if (canvas != null) {
            GraphicsContext g2C = canvas.getGraphicsContext2D();
            g2 = new GraphicsContextProxy(g2C);
        }
        makeContours = new DrawTask(this);
        drawContours = new DrawContours(this);
    }

    public void setController(FXMLController controller) {
        Button cancelButton = controller.getCancelButton();
        cancelButton.setOnAction(actionEvent -> {
            cancelled = true;
            ((Service) makeContours.worker).cancel();
            ((Service) drawContours.worker).cancel();
        });
        cancelButton.disableProperty().bind(((Service) makeContours.worker).stateProperty().isNotEqualTo(Task.State.RUNNING));
    }

    public void setDisDim(DISDIM disDim) {
        this.disDim = disDim;
    }

    public void setAxes(NMRAxis[] axes) {
        this.axes = axes;
    }

    public void setClipRect(Rectangle rect) {
        clipRect = new Rectangle(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
    }

    public void setClipRect(double x, double y, double width, double height) {
        clipRect = new Rectangle(x, y, width, height);
    }

    synchronized void setLastPlotTime() {
        lastPlotTime = System.currentTimeMillis() - startTime;
    }

    public long getLastPlotTime() {
        return lastPlotTime;
    }

    public void drawSpectrum(ArrayList<DatasetAttributes> dataGenerators, AXMODE[] axModes,
            boolean pick) {
        cancelled = false;

        if (pick) {
            return;
        }
        jobCount++;
        ((Service) makeContours.worker).cancel();
        ((Service) drawContours.worker).cancel();
        this.axModes = new AXMODE[axModes.length];
        System.arraycopy(axModes, 0, this.axModes, 0, axModes.length);
        dataAttrList.clear();
        dataAttrList.addAll(dataGenerators);
        contourQueue.clear();
        lastPlotTime = 0;
        startTime = System.currentTimeMillis();
        ((Service) makeContours.worker).restart();
        ((Service) drawContours.worker).restart();
    }

    public boolean drawSpectrumImmediate(GraphicsContextInterface g2I, ArrayList<DatasetAttributes> dataGenerators, AXMODE[] axModes) {
        cancelled = false;

        this.axModes = new AXMODE[axModes.length];
        System.arraycopy(axModes, 0, this.axModes, 0, axModes.length);
        dataAttrList.clear();
        dataAttrList.addAll(dataGenerators);
        contourQueue.clear();
        startTime = System.currentTimeMillis();
        boolean finished = false;
        try {
            finished = drawNow(g2I);
        } catch (IOException ex) {
        }
        lastPlotTime = 0;

        return finished;
    }

    public static float[] getLevels(DatasetAttributes fileData) {
        int nLevels = fileData.getNlvls();
        double clm = fileData.getClm();

        float[] levels = new float[nLevels];
        levels[0] = (float) fileData.lvlProperty().get();
        for (int i = 1; i < nLevels; i++) {
            levels[i] = (float) (levels[i - 1] * clm);
        }
        return levels;
    }

    AXMODE[] getAxModes() {
        AXMODE[] modes = new AXMODE[axModes.length];
        System.arraycopy(axModes, 0, modes, 0, modes.length);
        return modes;
    }

    NMRAxis[] getAxes() {
        NMRAxis[] tempAxes = new NMRAxis[axes.length];
        System.arraycopy(axes, 0, tempAxes, 0, axes.length);
        return tempAxes;
    }

    private static class DrawObject {

        Contour contour;
        DatasetAttributes dataAttr;
        long count;

        DrawObject(DatasetAttributes dataAttr, Contour contour, long count) {
            this.contour = contour;
            this.dataAttr = dataAttr;
            this.count = count;
        }
    }

    private static class DrawTask {

        public Worker<Integer> worker;

        DrawSpectrum drawSpectrum;
        List<DatasetAttributes> dataAttrList;
        AXMODE[] axModes;
        NMRAxis[] axes;
        float[] levels;
        int nRunning = 0;
        boolean done = false;

        private DrawTask(DrawSpectrum drawSpectrum) {
            this.drawSpectrum = drawSpectrum;
            worker = new Service<Integer>() {
                @Override
                protected Task createTask() {
                    return new Task<Integer>() {
                        @Override
                        protected Integer call() {
                            try {
                                done = false;
                                dataAttrList = new ArrayList<>();
                                dataAttrList.addAll(drawSpectrum.dataAttrList);
                                for (DatasetAttributes fileData : dataAttrList) {
                                    levels = getLevels(fileData);
                                    axModes = drawSpectrum.getAxModes();
                                    axes = drawSpectrum.getAxes();
                                    drawNow(this, fileData);
                                    if (done) {
                                        break;
                                    }
                                }
                            } catch (IOException e) {
                                System.out.println("error " + e.getMessage());
                                e.printStackTrace();
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
            float[] levels = getLevels(fileData);
            double[] offset = {0, 0};
            //MARCH_MODE = iMode == 0 ? false : true;
            //fileData.posColorProperty().set(iMode == 0 ? Color.BLACK : Color.RED);
            fileData.mChunk = -1;
            float[][] z = null;
            do {
                if (task.isCancelled()) {
                    done = true;
                    break;
                }
                long currentTime = System.currentTimeMillis();
//                System.out.println(fileData.mChunk + " " + (currentTime - startTime));
                int iChunk = fileData.mChunk + 1;
                double[][] pix = getPix(axes, fileData);

                try {
                    z = getData(fileData, iChunk, offset, z);
                    if (z != null) {
                        double xOff = fileData.pt[0][0] - fileData.ptd[0][0];
                        double yOff = fileData.pt[1][0] - fileData.ptd[1][0];
                        for (int iPosNeg = 0; iPosNeg < 2; iPosNeg++) {
                            float sign = iPosNeg == 0 ? 1.0f : -1.0f;
                            for (float level : levels) {
                                if (!checkLevels(z, iPosNeg, sign * level)) {
                                    break;
                                }
                                Contour contour = new Contour(fileData.ptd, pix);
                                if (!setContext(contour, fileData, iPosNeg)) {
                                    continue;
                                }
                                contour.xOffset = xOff;
                                contour.yOffset = yOff;

                                int[][] cells = new int[z.length][z[0].length];
                                if (!contour.marchSquares(sign * level, z, cells)) {
                                    try {
                                        DrawObject drawObject = new DrawObject(fileData, contour, drawSpectrum.jobCount);
                                        drawSpectrum.contourQueue.put(drawObject);
                                    } catch (InterruptedException ex) {
                                        done = true;
                                        return;
                                    }
                                    // contour.drawSquares(g2, xOff, yOff);
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

    private static class DrawContours {

        DrawSpectrum drawSpectrum;
        public Worker<Integer> worker;

        private DrawContours(DrawSpectrum drawSpectrum) {
            this.drawSpectrum = drawSpectrum;
            worker = new Service<Integer>() {
                @Override
                protected Task createTask() {
                    return new Task<Integer>() {
                        @Override
                        protected Integer call() {
                            try {
                                drawAllContours(this);
                            } catch (Exception e) {
                                e.printStackTrace();

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

        public int drawContourObject(DrawObject drawObject) throws InterruptedException, ExecutionException {
            GraphicsContextInterface g2 = drawSpectrum.g2;
            FutureTask<Integer> future = new FutureTask(() -> {
                return drawSquares(drawSpectrum, drawObject, g2);
            });
            Platform.runLater(future);
            Integer value;
            try {
                value = future.get();
            } catch (InterruptedException iE) {
                value = 0;
            }
            return value;
        }

        public void drawAllContours(Task task) {
//            while (!drawSpectrum.makeContours.done || !drawSpectrum.contourQueue.isEmpty()) {
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
                    if (drawSpectrum.cancelled) {
                        return;
                    }

                    DrawObject drawObject = null;

                    try {
                        drawObject = drawSpectrum.contourQueue.take();
                        try {
                            drawContourObject(drawObject);
                        } catch (ExecutionException ex) {
                            ex.printStackTrace();
                        }
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
//            System.out.println("done " + drawSpectrum.makeContours.done + " " + drawSpectrum.contourQueue.isEmpty());
        }

    }

    public boolean drawNow(GraphicsContextInterface g2I) throws IOException {
        for (DatasetAttributes fileData : dataAttrList) {
            float[] levels = getLevels(fileData);

            double[] offset = {0, 0};
            //MARCH_MODE = iMode == 0 ? false : true;
            //fileData.posColorProperty().set(iMode == 0 ? Color.BLACK : Color.RED);
            fileData.mChunk = -1;
            float[][] z = null;
            do {
                long currentTime = System.currentTimeMillis();
//                System.out.println(fileData.mChunk + " " + (currentTime - startTime));
                if ((g2I instanceof GraphicsContextProxy) && ((currentTime - startTime) > MAX_TIME)) {
                    return false;

                }
                int iChunk = fileData.mChunk + 1;
                double[][] pix = getPix(axes, fileData);
                final Contour[] contours = new Contour[2];
                contours[0] = new Contour(fileData.ptd, pix);
                contours[1] = new Contour(fileData.ptd, pix);
                try {
                    z = getData(fileData, iChunk, offset, z);
                    if (z != null) {
                        double xOff = offset[0] + fileData.ptd[0][0];
                        double yOff = offset[1] + fileData.ptd[1][0];
                        int[][] cells = new int[z.length][z[0].length];
                        for (int iPosNeg = 0; iPosNeg < 2; iPosNeg++) {
                            Contour contour = contours[iPosNeg];
                            if ((contour == null) || !setContext(contour, fileData, iPosNeg)) {
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

    void drawGrid(DatasetAttributes dataAttr, GraphicsContextInterface g2) throws GraphicsIOException {
        int x0 = dataAttr.pt[0][0];
        int x1 = dataAttr.pt[0][1];
        int y0 = dataAttr.pt[1][0];
        int y1 = dataAttr.pt[1][1];
        g2.setLineWidth(1);
        g2.setStroke(Color.BLACK);
        Dataset dataset = dataAttr.getDataset();
        for (int i = x0; i <= x1; i++) {
            for (int j = y0; j <= y1; j++) {
                double xP = dataset.pointToPPM(dataAttr.dim[0], i);
                double yP = dataset.pointToPPM(dataAttr.dim[1], j);
                double px1 = axes[0].getDisplayPosition(xP);
                double py1 = axes[1].getDisplayPosition(yP);
                g2.strokeLine(px1 - 1, py1, px1 + 1, py1);

            }
        }

    }

    public void clip(GraphicsContextInterface gC) {
        try {
            g2.beginPath();
            Rectangle r = clipRect;
            g2.rect(r.getX(), r.getY(), r.getWidth(), r.getHeight());
            g2.clip();
            g2.beginPath();
        } catch (GraphicsIOException ex) {

        }

    }

    static int drawSquares(DrawSpectrum drawSpectrum, DrawObject drawObject, GraphicsContextInterface g2) {
        int result = 0;
        if (cancelled) {
            return result;
        }
        if (drawObject.count < drawSpectrum.jobCount) {
            return result;
        }
        //g2.save();
        result = 1;
        try {
            //  drawSpectrum.clip(g2);
            drawObject.contour.drawSquares(g2);
        } catch (GraphicsIOException ex) {
            result = 0;
        } finally {
            //g2.restore();
            return result;
        }

    }

    static double[][] getPix(NMRAxis[] axes, DatasetAttributes dataAttr) {
        Dataset dataset = dataAttr.getDataset();
        double xPoint1 = dataset.pointToPPM(dataAttr.dim[0], dataAttr.ptd[0][0]);
        double xPoint2 = dataset.pointToPPM(dataAttr.dim[0], dataAttr.ptd[0][1]);
        double yPoint1 = dataset.pointToPPM(dataAttr.dim[1], dataAttr.ptd[1][0]);
        double yPoint2 = dataset.pointToPPM(dataAttr.dim[1], dataAttr.ptd[1][1]);
        double[][] pix = new double[2][2];
        pix[0][0] = axes[0].getDisplayPosition(xPoint1);
        pix[0][1] = axes[0].getDisplayPosition(xPoint2);
        pix[1][0] = axes[1].getDisplayPosition(yPoint1);
        pix[1][1] = axes[1].getDisplayPosition(yPoint2);
        return pix;
    }

    static float[][] getData(DatasetAttributes dataAttr, int iChunk, double[] offset, float[][] z) throws IOException {
        StringBuffer chunkLabel = new StringBuffer();
        chunkLabel.setLength(0);
        int[][] apt = new int[dataAttr.getDataset().getNDim()][2];
        int fileStatus = dataAttr.getMatrixRegion(iChunk, 2048, 0, apt,
                offset, chunkLabel);
        if (fileStatus != 0) {
            return null;
        }
        try {
            z = dataAttr.readMatrix(dataAttr.mChunk, chunkLabel.toString(), apt, z);
        } catch (IOException ioE) {
            throw ioE;
        }
        return z;
    }

    static boolean setContext(Contour contour, DatasetAttributes dataAttr, int iPosNeg) throws GraphicsIOException {
        final boolean ok;
        if (iPosNeg == 0) {
            ok = dataAttr.getPos();
        } else {
            ok = dataAttr.getNeg();
        }
        if (ok) {
            if (iPosNeg == 0) {
                contour.setAttributes(dataAttr.posWidthProperty().get(), dataAttr.getPosColor());
            } else {
                contour.setAttributes(dataAttr.negWidthProperty().get(), dataAttr.getNegColor());
            }
        }
        return ok;

    }

    boolean getMarchingSquares(DatasetAttributes fileData, float[][] z,
            int[][] cells, Contour contour, double[] offset, float level, GraphicsContextInterface g2) throws IOException, GraphicsIOException {
        double xOff = offset[0] + fileData.ptd[0][0];
        double yOff = offset[1] + fileData.ptd[1][0];

        if (!contour.marchSquares(level, z, cells)) {
            contour.xOffset = xOff;
            contour.yOffset = yOff;
            contour.drawSquares(g2);

        }
        return true;
    }

    boolean getContours(DatasetAttributes fileData, Contour[] contours, int iChunk, double[] offset, float[] levels) throws IOException {
        StringBuffer chunkLabel = new StringBuffer();
        chunkLabel.setLength(0);
        int[][] apt = new int[fileData.getDataset().getNDim()][2];
        int fileStatus = fileData.getMatrixRegion(iChunk, 64, 0, apt,
                offset, chunkLabel);
        if (fileStatus != 0) {
            return false;
        }
        float[][] z = null;

        try {
            z = fileData.readMatrix(fileData.mChunk, chunkLabel.toString(), apt, null);
        } catch (IOException ioE) {
            throw ioE;
        }
        if (z == null) {
            return false;
        }

        for (int iPosNeg = 0; iPosNeg < 2; iPosNeg++) {
            if ((iPosNeg == 0) && !fileData.getPos()) {
                continue;
            } else if ((iPosNeg == 1) && !fileData.getNeg()) {
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

    private void genContourPath(DatasetAttributes dataGenerator, AXMODE[] axModes, Contour contours, final int coordIndex, GraphicsContextInterface g2) throws GraphicsIOException {
        int lineCount = contours.getLineCount(coordIndex);
        float scale = Contour.getScaleFac() / Short.MAX_VALUE;
        double cxOffset = contours.xOffset;
        double cyOffset = contours.yOffset;
        if (lineCount != 0) {
            g2.beginPath();
            Dataset dataset = dataGenerator.getDataset();
            for (int iLine = 0; iLine < lineCount; iLine += 4) {
                if (cancelled) {
                    System.out.println("can response1");
                    break;
                }
                double xPoint1 = scale * contours.coords[coordIndex][iLine] + cxOffset;
                double xPoint2 = scale * contours.coords[coordIndex][iLine + 2] + cxOffset;
                double yPoint1 = scale * contours.coords[coordIndex][iLine + 1] + cyOffset;
                double yPoint2 = scale * contours.coords[coordIndex][iLine + 3] + cyOffset;
//                if (MARCH_MODE) {
//                    System.out.println("pt " + xPoint1 + " " + yPoint1 + " " + xPoint2 + " " + yPoint2);
//                }
                xPoint1 = dataset.pointToPPM(dataGenerator.dim[0], xPoint1);
                xPoint2 = dataset.pointToPPM(dataGenerator.dim[0], xPoint2);
                yPoint1 = dataset.pointToPPM(dataGenerator.dim[1], yPoint1);
                yPoint2 = dataset.pointToPPM(dataGenerator.dim[1], yPoint2);
//                if (MARCH_MODE) {
//                    System.out.println("pp " + xPoint1 + " " + yPoint1 + " " + xPoint2 + " " + yPoint2);
//                }

                double x1 = axes[0].getDisplayPosition(xPoint1);
                double x2 = axes[0].getDisplayPosition(xPoint2);
                double y1 = axes[1].getDisplayPosition(yPoint1);
                double y2 = axes[1].getDisplayPosition(yPoint2);

                g2.moveTo(x1, y1);
                g2.lineTo(x2, y2);
            }
            g2.stroke();
        }
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
            double level = datasetAttr.lvlProperty().get();
            double scale = -sliceAttr.getScaleValue() / level;
            //System.out.println(orientation + " " + slicePosX + " " + slicePosY);
            if (sliceDim == 0) {
                double offset;
                if (offsetTracking) {
                    offset = axes[1].getDisplayPosition(slicePosY);
                } else {
                    offset = axes[0].getYOrigin() - axes[1].getHeight() * sliceAttr.getOffsetYValue();
                }
                drawVector(sliceVec, orientation, 0, AXMODE.PPM, drawReal, ph0, ph1, null,
                        (index, intensity) -> axes[0].getDisplayPosition(index),
                        (index, intensity) -> intensity * scale + offset, false, false);
            } else {
                double offset;
                if (offsetTracking) {
                    offset = axes[0].getDisplayPosition(slicePosX);
                } else {
                    offset = axes[0].getXOrigin() + axes[0].getWidth() * sliceAttr.getOffsetXValue();
                }
                drawVector(sliceVec, orientation, 0, AXMODE.PPM, drawReal, ph0, ph1, null,
                        (index, intensity) -> -intensity * scale + offset,
                        (index, intensity) -> axes[1].getDisplayPosition(index), false, false);
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
                if (!dataAttributes.Vector(specVec, iChunk--)) {
                    return false;
                }
            } catch (IOException ioE) {
                return false;
            }
        }
        double height = axes[1].getHeight();
        double scale = axes[1].getScale();
        double offset = height * dataAttributes.getMapOffset(rowIndex);
        drawVector(specVec, orientation, 0, axMode, drawReal, ph0, ph1, bcPath,
                (index, intensity) -> axes[0].getDisplayPosition(index),
                (index, intensity) -> axes[1].getDisplayPosition(intensity) - offset, offsetMode, false);

        if (iChunk < 0) {
            return false;
        }
        return true;

    }

    public double[] getRegionAsArray(Dataset dataset) {
        Set<DatasetRegion> regions = dataset.getRegions();
        double[] ppms = null;
        if (regions != null) {
            ppms = new double[regions.size() * 2];

            int i = 0;
            for (DatasetRegion region : regions) {
                ppms[i++] = region.getRegionStart(0);
                ppms[i++] = region.getRegionEnd(0);
            }
        }
        return ppms;
    }

    public double[] getOffsetsAsArray(Dataset dataset) {
        Set<DatasetRegion> regions = dataset.getRegions();
        double[] offsets = null;
        if (regions != null) {
            offsets = new double[regions.size() * 2];
            int i = 0;
            for (DatasetRegion region : regions) {
                offsets[i++] = region.getRegionStartIntensity(0);
                offsets[i++] = region.getRegionEndIntensity(0);
            }
        }
        return offsets;
    }

    public Optional<Double> draw1DIntegrals(DatasetAttributes dataAttributes, int orientation,
            AXMODE axMode, double ppm1, double ppm2, double[] offsets) {
        Vec specVec = new Vec(32);
        Optional<Double> result = Optional.empty();
        boolean drawReal = dataAttributes.getDrawReal();
        try {
            if (!dataAttributes.getIntegralVec(specVec, iChunk + 1, ppm1, ppm2, offsets)) {
                System.out.println("no  vec int");
                return result;
            }
        } catch (IOException ioE) {
            ioE.printStackTrace();
            return result;
        }
        Double integralValue = specVec.getReal(specVec.getSize() - 1);
        result = Optional.of(integralValue);
        double scale = dataAttributes.getIntegralScale();
        drawSubVector(specVec, orientation, 0, axMode,
                (index, intensity) -> axes[0].getDisplayPosition(index),
                (index, intensity) -> axes[1].getDisplayPosition(intensity / scale), ppm1, ppm2);

        return result;
    }

    private int vecIndexer(Vec vec, double position) {
        int point = vec.refToPt(position);
        return point;
    }

    public void drawSubVector(Vec vec, int orientation, int dataOffset, AXMODE axMode,
            DoubleBinaryOperator xFunction, DoubleBinaryOperator yFunction, double ppm1, double ppm2) {
        int size = vec.getSize();
        NMRAxis indexAxis = orientation == PolyChart.HORIZONTAL ? axes[0] : axes[1];

        double indexAxisDelta = (ppm1 - ppm2) / vec.getSize();
        double dValue = ppm2;

        nPoints = drawVectoreCore(vec, dataOffset, true, 0.0, 0.0, xy, null, xFunction,
                yFunction, true, 0, vec.getSize() - 1, size, dValue, 0.0, indexAxisDelta, true);
    }

    public void drawVector(Vec vec, int orientation, int dataOffset, AXMODE axMode, boolean drawReal, double ph0, double ph1, Path bcPath, DoubleBinaryOperator xFunction, DoubleBinaryOperator yFunction, boolean offsetVec, boolean integralMode) {
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
        nPoints = drawVectoreCore(vec, dataOffset, drawReal, ph0, ph1, xy, bcPath, xFunction, yFunction, offsetVec, vecStartPoint, vecEndPoint, size, dValue, phase1Delta, indexAxisDelta, integralMode);
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

        int nPoints = drawVectoreCore(vec, dataOffset, drawReal, ph0, ph1, xy, bcPath, xFunction, yFunction, offsetVec, vecStartPoint, vecEndPoint, size, dValue, phase1Delta, indexAxisDelta, false);
        return nPoints;
    }

    private static int drawVectoreCore(Vec vec, int dataOffset, boolean drawReal,
            double ph0, double ph1, double[][] xyValues, Path bcPath, DoubleBinaryOperator xFunction,
            DoubleBinaryOperator yFunction, boolean offsetVec, int start, int end, int size,
            double dValue, double dDelta, double delta, boolean integralMode) {

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
                if (intensity != Double.MAX_VALUE) {
                    xyValues[0][iLine] = xFunction.applyAsDouble(dValue, intensity);
                    xyValues[1][iLine++] = yFunction.applyAsDouble(dValue, intensity);
                }
                dValue += delta;
            }
            nPoints = iLine;
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
                    intensity = 0.0;
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
                            //bcPath.getElements().add(new LineTo(xValue, yValue));
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

    public void drawRegion(DatasetAttributes dataAttributes, int orientation, AXMODE axMode) {
        Dataset dataset = dataAttributes.getDataset();
        Set<DatasetRegion> regions = dataset.getRegions();
        for (DatasetRegion region : regions) {

        }
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

    public Optional<IntegralHit> drawActiveRegion(GraphicsContextInterface g2, DatasetAttributes datasetAttr, DatasetRegion region) throws GraphicsIOException {
        return drawActiveRegion(g2, datasetAttr, region, false, 0, 0);
    }

    public Optional<IntegralHit> hitRegion(DatasetAttributes datasetAttr, DatasetRegion region, double pickX, double pickY) {
        Optional<IntegralHit> result;
        try {
            result = drawActiveRegion(null, datasetAttr, region, true, pickX, pickY);
        } catch (GraphicsIOException ex) {
            result = Optional.empty();
        }
        return result;
    }

    public Optional<IntegralHit> drawActiveRegion(GraphicsContextInterface g2, DatasetAttributes datasetAttr, DatasetRegion region, boolean pick, double pickX, double pickY) throws GraphicsIOException {
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
            ry2 = axes[1].getUpperBound();
            ry1 = axes[1].getLowerBound();
        }

        double px1 = axes[0].getDisplayPosition(rx1);
        double py1 = axes[1].getDisplayPosition(ry1);

        double px2 = axes[0].getDisplayPosition(rx2);
        double py2 = axes[1].getDisplayPosition(ry1);

        double pxb1 = axes[0].getDisplayPosition(rx1);
        double pyb1 = axes[1].getDisplayPosition(ryB1);

        double pxb2 = axes[0].getDisplayPosition(rx2);
        double pyb2 = axes[1].getDisplayPosition(ryB2);

        double pxb1p = axes[0].getDisplayPosition(rx1);
        double pyb1p = axes[1].getDisplayPosition(ryB1) - 25;
        double pxb2p = axes[0].getDisplayPosition(rx2);
        double pyb2p = axes[1].getDisplayPosition(ryB2) - 25;

        if ((px2 - px1) < 2) {
            px1 = px1 - 1;
            px2 = px2 + 1;
        }

        double px3 = axes[0].getDisplayPosition(rx2);
        double py3 = axes[1].getDisplayPosition(ry2);

        double px4 = axes[0].getDisplayPosition(rx1);
        double py4 = axes[1].getDisplayPosition(ry2);

        if ((px4 - px3) < 2) {
            px3 = px3 - 1;
            px4 = px4 + 1;
        }

        if (pick) {
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
            int minDelta = Integer.MAX_VALUE;
            int iMin = -1;
            int iValue = 0;
            for (int delta : deltas) {
                if (delta < minDelta) {
                    minDelta = delta;
                    iMin = iValue;
                }
                iValue++;
            }
            double pickJiggle = 2.0;
            double minPickDel = 2.0;
            if ((minDelta < pickJiggle) && (minDelta < minPickDel)) {
                minPickDel = minDelta;
                result = Optional.of(new IntegralHit(datasetAttr, region, iMin + 1));
            } else if ((pickX > (pxb1 + pickJiggle)) && (pickX < (pxb2 - pickJiggle))) {
                double f = (1.0 * pickX - pxb1) / (pxb2 - pxb1);
                int yVal = (int) (pyb1 + f * (pyb2 - pyb1));
                int delta1 = (int) Math.abs(pickY - yVal);
                if (delta1 < pickJiggle) {
                    minPickDel = delta1;
                    result = Optional.of(new IntegralHit(datasetAttr, region, 0));
                }
            }
        } else {
            //g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            //g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
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

    void drawHandleV(GraphicsContextInterface g2, double x, double y) throws GraphicsIOException {
        int handleSize = 6;
        int handleSize2 = 9;

        g2.beginPath();
        g2.moveTo(x, y - handleSize2);
        g2.lineTo(x, y + handleSize2);

        g2.moveTo(x, y - handleSize2);
        g2.lineTo(x - handleSize / 2, y - handleSize);
        g2.moveTo(x, y - handleSize2);
        g2.lineTo(x + handleSize / 2, y - handleSize);

        g2.moveTo(x, y + handleSize2);
        g2.lineTo(x - handleSize / 2, y + handleSize);
        g2.moveTo(x, y + handleSize2);
        g2.lineTo(x + handleSize / 2, y + handleSize);

        g2.moveTo(x - handleSize / 2, y - handleSize / 2);
        g2.lineTo(x + handleSize / 2, y - handleSize / 2);
        g2.lineTo(x + handleSize / 2, y + handleSize / 2);
        g2.lineTo(x - handleSize / 2, y + handleSize / 2);
        g2.lineTo(x - handleSize / 2, y - handleSize / 2);

        g2.setFill(Color.WHITE);
        g2.fill();
        g2.setLineWidth(2);
        g2.setStroke(Color.BLACK);
        g2.stroke();
    }

    void drawHandleH(GraphicsContextInterface g2, double x, double y) throws GraphicsIOException {
        int handleSize = 6;
        int handleSize2 = 9;
        g2.beginPath();
        g2.moveTo(x - handleSize, y);
        g2.lineTo(x + handleSize, y);

        g2.moveTo(x - handleSize2, y);
        g2.lineTo(x - handleSize, y - handleSize / 2);
        g2.moveTo(x - handleSize2, y);
        g2.lineTo(x - handleSize, y + handleSize / 2);

        g2.moveTo(x + handleSize2, y);
        g2.lineTo(x + handleSize, y - handleSize / 2);
        g2.moveTo(x + handleSize2, y);
        g2.lineTo(x + handleSize, y + handleSize / 2);

        g2.moveTo(x - handleSize / 2, y - handleSize / 2);
        g2.lineTo(x + handleSize / 2, y - handleSize / 2);
        g2.lineTo(x + handleSize / 2, y + handleSize / 2);
        g2.lineTo(x - handleSize / 2, y + handleSize / 2);
        g2.lineTo(x - handleSize / 2, y - handleSize / 2);

        g2.setFill(Color.WHITE);
        g2.fill();
        g2.setLineWidth(2);
        g2.setStroke(Color.BLACK);
        g2.stroke();
    }

}
