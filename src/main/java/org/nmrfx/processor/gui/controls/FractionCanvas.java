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
package org.nmrfx.processor.gui.controls;

import java.util.function.Function;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.scene.transform.Transform;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;

/**
 *
 * @author Bruce Johnson
 */
public class FractionCanvas extends Pane {

    public enum ORIENTATION {
        HORIZONTAL, VERTICAL, GRID;
    }
    ORIENTATION orient = null;
    FXMLController controller;
    LayoutControlCanvas controlPane;
    static int[] nRowDefaults = {1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 2, 3, 3, 3, 4};
    int setRows = -1;
    private int currentRows = 1;
    private int currentCols = 1;
    ObservableList<PolyChart> charts;
    final Canvas canvas;

    public FractionCanvas(FXMLController controller, Canvas canvas, ObservableList<PolyChart> charts) {
        this.controller = controller;
        this.charts = charts;
        this.canvas = canvas;
        layoutBoundsProperty().addListener((ObservableValue<? extends Bounds> arg0, Bounds arg1, Bounds arg2) -> {
            if (arg2.getWidth() == 0 || arg2.getHeight() == 0) {
                return;
            }
            requestLayout();
        });
    }

    public static ORIENTATION getOrientation(String name) {
        name = name.toUpperCase();
        if ("HORIZONTAL".startsWith(name)) {
            return ORIENTATION.HORIZONTAL;
        } else if ("VERTICAL".startsWith(name)) {
            return ORIENTATION.VERTICAL;
        } else if ("GRID".startsWith(name)) {
            return ORIENTATION.GRID;
        } else {
            throw new IllegalArgumentException("Invalid orientation: " + name);
        }
    }

    public void setRows(int nRows) {
        setRows = nRows;
        orient = ORIENTATION.GRID;
        if (nRows == 1) {
            orient = ORIENTATION.HORIZONTAL;
        }
    }

    public void updateLayout(ORIENTATION newOrient) {
        this.orient = newOrient;
        layoutChildren();
    }

    public boolean setOrientation(ORIENTATION newOrient, boolean force) {
        setRows = -1;

        int nChildren = charts.size();
        if (force || (nChildren < 2) || (orient == null)) {
            orient = newOrient;
            return true;
        } else {
            return false;
        }
    }

    public void addChart(int pos, PolyChart chart) {
        charts.add(pos, chart);
    }

    public void addChart(PolyChart chart) {
        charts.add(chart);
    }

    public int getCurrentRows() {
        return currentRows;
    }

    public int getCurrentCols() {
        return currentCols;
    }

    public int getRows() {
        int nChildren = charts.size();
        int nRows = 4;
        if (setRows != -1) {
            nRows = setRows;
            if ((nChildren > 1) && (nRows == nChildren)) {
                orient = ORIENTATION.VERTICAL;
            }
        } else {
            if (nChildren < nRowDefaults.length) {
                nRows = nRowDefaults[nChildren];
            }
        }
        if (nRows < 1) {
            nRows = 1;
        }
        return nRows;
    }

    public int getColumns() {
        int nChildren = charts.size();
        int nRows = 4;
        if (setRows != -1) {
            nRows = setRows;
            if ((nChildren > 1) && (nRows == nChildren)) {
                orient = ORIENTATION.VERTICAL;
            }
        } else {
            if (nChildren < nRowDefaults.length) {
                nRows = nRowDefaults[nChildren];
            }
        }
        if (nRows < 1) {
            nRows = 1;
        }
        int nCols = nChildren / nRows;
        nCols = nCols * nRows < nChildren ? nCols + 1 : nCols;
        if (nCols < 1) {
            nCols = 1;
        }
        return nCols;
    }

    public void removeChild(PolyChart node) {
        int nRows = getRows();
        int nCols = getColumns();
        charts.remove(node);
        int nChildren = charts.size();

        if (setRows != -1) {
            int lastChild = nChildren - 1;
            if (lastChild < 0) {
                lastChild = 0;
            }
            int calcRows = (lastChild / nCols) + 1;
            setRows = Math.min(nRows, calcRows);
        }
    }

    @Override
    public void layoutChildren() {
        Bounds bounds = getLayoutBounds();
        double width = bounds.getWidth();
        double height = bounds.getHeight();
        canvas.setWidth(width);
        canvas.setHeight(height);
        GraphicsContext gC = canvas.getGraphicsContext2D();
        gC.clearRect(0, 0, width, height);

        int nChildren = charts.size();
        int nRows = getRows();
        int nCols = getColumns();

        if ((orient == null) || (orient == ORIENTATION.HORIZONTAL)) {
            nCols = nChildren;
            nRows = 1;
        } else if (orient == ORIENTATION.VERTICAL) {
            nRows = nChildren;
            nCols = 1;
        } else {
        }
        currentRows = nRows;
        currentCols = nCols;
        double[][] bordersGrid = controller.prepareChildren(nRows, nCols);
        double sumX = 0.0;
        double sumY = 0.0;
        double sumPPMX = 0.0;
        double sumPPMY = 0.0;
        for (int i = 0; i < bordersGrid[0].length; i++) {
            sumX += bordersGrid[0][i] + bordersGrid[1][i];
            sumPPMX += bordersGrid[4][i];
        }
        for (int i = 0; i < bordersGrid[2].length; i++) {
            sumY += bordersGrid[2][i] + bordersGrid[3][i];
            sumPPMY += bordersGrid[5][i];
        }

        double deltaX = (width - sumX);
        double deltaY = (height - sumY);

//        System.out.println("layout " + nRows + " " + nCols + " " + deltaX + " " + sumX + " " + width
//                + " " + deltaY + " " + sumY + " " + height);
        double[][] offsets = new double[2][];
        offsets[0] = new double[nCols];
        offsets[1] = new double[nRows];

        for (int i = 0; i < bordersGrid[0].length - 1; i++) {
            double fX = bordersGrid[4][i] / sumPPMX;
            offsets[0][i + 1] = offsets[0][i] + bordersGrid[0][i] + deltaX * fX + bordersGrid[1][i];
        }
        for (int i = 0; i < bordersGrid[2].length - 1; i++) {
            double fY = bordersGrid[5][i] / sumPPMY;
            offsets[1][i + 1] = offsets[1][i] + bordersGrid[2][i] + deltaY * fY + bordersGrid[3][i];
        }
        int iChild = 0;

        iChild = 0;
        for (PolyChart node : charts) {
            int iRow = iChild / nCols;
            int iCol = iChild % nCols;
            double x = offsets[0][iCol];
            double y = offsets[1][iRow];
            double fX = bordersGrid[4][iCol] / sumPPMX;
            double fY = bordersGrid[5][iRow] / sumPPMY;
            double itemWidth = bordersGrid[0][iCol] + deltaX * fX + bordersGrid[1][iCol];
            double itemHeight = bordersGrid[2][iRow] + deltaY * fY + bordersGrid[3][iRow];

//            System.out.printf("%2d %2d %2d %6.1f %6.1f %6.1f %6.1f %6.1f %6.1f\n",
//                    iChild, iRow, iCol, x, y, itemWidth, itemHeight, offsets[0][iCol], offsets[1][iRow]);
            node.resizeRelocate(x, y, itemWidth, itemHeight);
            iChild++;
        }
        for (PolyChart chart : charts) {
            chart.refresh();
        }
//        // children.redrawChildren();
    }

    public void setControlPane(LayoutControlCanvas pane) {
        controlPane = pane;
    }

    Point2D getLocal(double x, double y) {
        Transform transform = getLocalToSceneTransform();
        Point2D result = null;
        try {
            Transform inverseTrans = transform.createInverse();
            result = inverseTrans.transform(x, y);
        } catch (NonInvertibleTransformException ex) {
            result = new Point2D(0.0, 0.0);
        }

        return result;
    }

    Point2D getFraction(double x, double y) {
        Transform transform = getLocalToSceneTransform();
        Point2D result = null;
        try {
            Transform inverseTrans = transform.createInverse();
            Point2D point = inverseTrans.transform(x, y);
            double width = getWidth();
            double height = getHeight();
            double fx = point.getX() / width;
            double fy = point.getY() / height;
            result = new Point2D(fx, fy);

        } catch (NonInvertibleTransformException ex) {
            result = new Point2D(0.0, 0.0);
        }

        return result;
    }

    public void mouseDrag(MouseEvent e) {
        double x = e.getSceneX();
        double y = e.getSceneY();
        Point2D pt = getLocal(x, y);
        controlPane.getRectangle(pt);
    }

    public void mousePressed(MouseEvent e) {
        double x = e.getSceneX();
        double y = e.getSceneY();
        Point2D pt = getLocal(x, y);
        controlPane.getRectangle(pt);
        controlPane.setVisible(true);
    }

    public void mouseDragRelease(MouseEvent e, Function<Integer, Integer> f) {
        double x = e.getSceneX();
        double y = e.getSceneY();
        Point2D pt = getLocal(x, y);
        int iRect = controlPane.getRectangle(pt);
        if (iRect != -1) {
            f.apply(iRect);
        }

        controlPane.setVisible(false);
    }

}
