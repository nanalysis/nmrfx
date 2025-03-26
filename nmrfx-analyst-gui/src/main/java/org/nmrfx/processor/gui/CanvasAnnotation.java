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
package org.nmrfx.processor.gui;

import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.paint.Color;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.processor.gui.spectra.ChartMenu;

/**
 * @author Bruce Johnson
 */
public interface CanvasAnnotation {
    double HANDLE_WIDTH = 10;

    enum POSTYPE {
        PIXEL {
            @Override
            public double transform(double v, double[] b, double[] w) {
                return v + b[0];
            }

            @Override
            public double itransform(double v, double[] b, double[] w) {
                return v - b[0];
            }

        },
        FRACTION {
            @Override
            public double transform(double v, double[] b, double[] w) {
                return v * (b[1] - b[0]) + b[0];

            }

            @Override
            public double itransform(double v, double[] b, double[] w) {
                return (v - b[0]) / (b[1] - b[0]);
            }

        },
        WORLD {
            @Override
            public double transform(double v, double[] b, double[] w) {
                double f = (v - w[0]) / (w[1] - w[0]);
                return f * (b[1] - b[0]) + b[0];
            }

            @Override
            public double itransform(double v, double[] b, double[] w) {
                double f = (v - b[0]) / (b[1] - b[0]);
                return f * (w[1] - w[0]) + w[0];
            }
        };

        public abstract double transform(double v, double[] bounds, double[] world);

        public abstract double itransform(double v, double[] bounds, double[] world);

        public double move(double v, double dp, double[] b, double[] w) {
            double vp = transform(v, b, w);
            return itransform(vp + dp, b, w);
        }

    }

    void draw(GraphicsContextInterface gC, double[][] bounds, double[][] world);

    boolean hit(double x, double y, boolean selectMode);

    default void move(double[] start, double[] pos) {
    }

    default void move(double[][] bounds, double[][] world, double[] start, double[] pos) {
    }

    default ChartMenu getMenu() {
        return null;
    }

    default boolean getClipInAxes() {
        return false;
    }

    static double getHOffset(Pos pos) {
        return switch (pos.getHpos()) {
            case LEFT -> HANDLE_WIDTH;
            case RIGHT -> -HANDLE_WIDTH;
            case CENTER -> -HANDLE_WIDTH / 2;
        };
    }

    static double getVOffset(Pos pos) {
        return switch (pos.getVpos()) {
            case TOP -> HANDLE_WIDTH;
            case CENTER -> -HANDLE_WIDTH / 2;
            case BOTTOM, BASELINE -> -HANDLE_WIDTH;
        };
    }

    static double getHandleWidth() {
        return HANDLE_WIDTH;
    }

    default void drawHandle(GraphicsContextInterface gC, double x, double y, Pos pos) {
        gC.setStroke(Color.ORANGE);
        double hOffset = getHOffset(pos);
        double vOffset = getVOffset(pos);
        gC.strokeRect(x + hOffset, y + vOffset, HANDLE_WIDTH, HANDLE_WIDTH);
    }

    POSTYPE getXPosType();

    POSTYPE getYPosType();

    void setXPosType(POSTYPE yPosType);
    void setYPosType(POSTYPE yPosType);
    /**
     * Get the separation limit between two handles converted to POSTYPE.
     *
     * @param bounds The bounds.
     * @param world  The bounds in world units.
     * @return The converted handle width value.
     */
    default double getHandleSeparationLimit(double[][] bounds, double[][] world) {
        double width = switch (getXPosType()) {
            case PIXEL -> HANDLE_WIDTH;
            case FRACTION -> HANDLE_WIDTH / (bounds[0][1] - bounds[0][0]);
            case WORLD -> HANDLE_WIDTH / (world[0][1] - world[0][0]);
        };
        return width * 2;
    }

    void drawHandles(GraphicsContextInterface gC);

    boolean isSelected();

    boolean isSelectable();

    int hitHandle(double x, double y);

    default boolean hitHandle(double x, double y, Pos pos, double handleX, double handleY) {
        double hOffset = getHOffset(pos);
        double vOffset = getVOffset(pos);
        Rectangle2D rect = new Rectangle2D(handleX + hOffset, handleY + vOffset, HANDLE_WIDTH, HANDLE_WIDTH);
        return rect.contains(x, y);
    }
    void updateXPosType(CanvasAnnotation.POSTYPE type, double[] bounds, double[] world);
    void updateYPosType(CanvasAnnotation.POSTYPE type, double[] bounds, double[] world);

}
