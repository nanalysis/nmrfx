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
package org.nmrfx.processor.operations;

/**
 * @author johnsonb
 */
public class BracketMinimizer {

    private double gridStart = 0.0;
    private double gridEnd = 0.0;
    private double gridDelta = 0.0;
    private double tol = 0.1;

    public BracketMinimizer(double gridStart, double gridEnd, double gridDelta, double tol) {
        this.gridStart = gridStart;
        this.gridEnd = gridEnd;
        this.gridDelta = gridDelta;
        this.tol = tol;
        initialize();
    }

    void initialize() {
    }

    double getScore(double value) {
        return 0.0;
    }

    public double minimize() {
        double minValue = 0.0;
        double value = 0.0;
        double minScore = Double.MAX_VALUE;
        value = gridStart;
        while (value < gridEnd) {
            double score = getScore(value);
            if (score < minScore) {
                minScore = score;
                minValue = value;
            }
            value += gridDelta;
        }

        double x1 = 0.0;
        double x2 = 0.0;
        double f1 = 0.0;
        double f2 = 0.0;
        double r = 0.3819660;
        double c = 1.0 - r;
        double ax = minValue - gridDelta;
        double bx = minValue;
        double cx = minValue + gridDelta;
        double x0 = ax;
        double x3 = cx;

        if (Math.abs(cx - bx) > Math.abs(bx - ax)) {
            x1 = bx;
            x2 = bx + (c * (cx - bx));
        } else {
            x2 = bx;
            x1 = bx - (c * (bx - ax));
        }

        f1 = getScore(x1);
        f2 = getScore(x2);

        while (Math.abs(x3 - x0) > 1.0) {
            if (f2 < f1) {
                x0 = x1;
                x1 = x2;
                x2 = (r * x1) + (c * x3);
                f1 = f2;
                f2 = getScore(x2);
            } else {
                x3 = x2;
                x2 = x1;
                x1 = (r * x2) + (c * x0);
                f2 = f1;
                f1 = getScore(x1);
            }
        }

        if (f1 < f2) {
            minValue = x1;
        } else {
            minValue = x2;
        }
        return minValue;
    }
}
