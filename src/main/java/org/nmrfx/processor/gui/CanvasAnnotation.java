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

import javafx.scene.canvas.Canvas;
import org.nmrfx.graphicsio.GraphicsContextInterface;

/**
 *
 * @author Bruce Johnson
 */
public interface CanvasAnnotation {

    public enum POSTYPE {
        PIXEL {
            @Override
            public double transform(double v, double[] b, double[] w) {
                return v;
            }

        },
        FRACTION {
            @Override
            public double transform(double v, double[] b, double[] w) {
                return v * (b[1] - b[0]) + b[0];

            }

        },
        WORLD {
            @Override
            public double transform(double v, double[] b, double[] w) {
                double f = (v - w[0]) / (w[1] - w[0]);
                return f * (b[1] - b[0]) + b[0];
            }
        };

        public abstract double transform(double v, double[] bounds, double[] world);

    };

    public void draw(GraphicsContextInterface gC, double[][] bounds, double[][] world);

    public POSTYPE getXPosType();

    public POSTYPE getYPosType();

}
