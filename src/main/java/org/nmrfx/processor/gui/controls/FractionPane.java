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
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.scene.transform.Transform;

/**
 *
 * @author Bruce Johnson
 */
public class FractionPane extends Pane {

    public enum ORIENTATION {
        HORIZONTAL, VERTICAL, GRID;
    }
    ORIENTATION orient = null;
    LayoutControlPane controlPane;
    static int[] nRowDefaults = {1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 2, 3, 3, 3, 4};
    int setRows = -1;

    public FractionPane() {
        super();
//        getChildrenUnmodifiable().addListener((ListChangeListener.Change<? extends Node> c) -> {
//            updateLayoutIndicators();
//        });
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

    public void updateLayout(int nRows) {
        setRows = nRows;
        orient = ORIENTATION.GRID;
        if (nRows == 1) {
            orient = ORIENTATION.HORIZONTAL;
        }
        layoutChildren();
    }

    public void updateLayout(ORIENTATION newOrient) {
        this.orient = newOrient;
        layoutChildren();
    }

    public boolean setOrientation(ORIENTATION newOrient) {
        setRows = -1;

        int nChildren = getChildrenUnmodifiable().size();
        if ((nChildren < 2) || (orient == null)) {
            orient = newOrient;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void layoutChildren() {
        Bounds bounds = getLayoutBounds();
        double width = bounds.getWidth();
        double height = bounds.getHeight();
        int nChildren = getChildrenUnmodifiable().size();
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

        double delX = 0.0;
        double delY = 0.0;
        double itemWidth = 1.0;
        double itemHeight = 1.0;
        if ((orient == null) || (orient == ORIENTATION.HORIZONTAL)) {
            delX = width / nChildren;
            delY = 0.0;
            itemWidth = delX;
            itemHeight = height;
        } else if (orient == ORIENTATION.VERTICAL) {
            delX = 0.0;
            delY = height / nChildren;
            itemHeight = delY;
            itemWidth = width;
        } else {
            delX = width / nCols;
            itemWidth = delX;
            itemHeight = height / nRows;
            delY = 0.0;
        }

        double x = 0.0;
        double y = 0.0;
        int iChild = 0;
        for (Node node : getChildrenUnmodifiable()) {
            node.resizeRelocate(x, y, itemWidth, itemHeight);
            x += delX;
            y += delY;
            iChild++;
            if (orient == ORIENTATION.GRID) {
                if ((iChild % nCols) == 0) {
                    x = 0.0;
                    y += itemHeight;
                }
            }
        }

    }

    public void setControlPane(LayoutControlPane pane) {
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
