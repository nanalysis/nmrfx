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

import org.nmrfx.processor.math.Vec;

public class MeasureData {

    final double min;
    final double max;
    final double sum;
    final double start;
    final double end;
    final double startPPM;
    final double endPPM;
    final double minPoint;
    final double maxPoint;
    final double minCorrection;
    final double maxCorrection;
    final double sumCorrection;
    double scale = 1.0;

    public MeasureData(Vec vector, double min, double max, double minPoint, double maxPoint, double sum, double start, double end, double minCorrection, double maxCorrection, double sumCorrection) {
        this.min = min;
        this.max = max;
        this.minPoint = minPoint;
        this.maxPoint = maxPoint;
        this.sum = sum;
        this.start = start;
        this.end = end;
        this.minCorrection = minCorrection;
        this.maxCorrection = maxCorrection;
        this.sumCorrection = sumCorrection;
        this.startPPM = vector.pointToPPM(start);
        this.endPPM = vector.pointToPPM(end);
    }

    public String toString() {
        return max / scale + " " + sum / scale;
    }

    public double getStart() {
        return start;
    }

    public double getEnd() {
        return end;
    }

    public double getStartPPM() {
        return startPPM;
    }

    public double getEndPPM() {
        return endPPM;
    }

    public double getSum() {
        return sum / scale;
    }

    public double getMin() {
        return min / scale;
    }

    public double getMax() {
        return max / scale;
    }

    public double getCorrectedSum() {
        return (sum - sumCorrection) / scale;
    }

    public double getCorrectedMin() {
        return (min - minCorrection) / scale;
    }

    public double getCorrectedMax() {
        return (max - maxCorrection) / scale;
    }

    public void setScale(double value) {
        scale = value;
    }
}
