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

import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

import java.util.ArrayList;
import java.util.Map;

/**
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class Measure extends Operation {

    final String key;
    final Map map;

    public Measure(Map map, String key) {
        this.map = map;
        this.key = key;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        boolean[] isInSignalRegion = vector.getSignalRegion();
        ArrayList<MeasureData> measures = new ArrayList<>();
        if (isInSignalRegion != null) {
            int size = vector.getSize();
            boolean wasNotInSignalRegion = true;
            double startValue = 0.0;
            double endValue = 0.0;
            double max = Double.NEGATIVE_INFINITY;
            double min = Double.MAX_VALUE;
            double start = 0.0;
            double end = 0.0;
            double sum = 0.0;
            double minPoint = 0.0;
            double maxPoint = 0.0;
            for (int i = 0; i < size; i++) {
                if (isInSignalRegion[i]) {
                    double currentValue = vector.getReal(i);
                    sum += currentValue;

                    if (currentValue > max) {
                        maxPoint = i;
                        max = currentValue;
                    }
                    if (currentValue < min) {
                        minPoint = i;
                        min = currentValue;
                    }

                    if (wasNotInSignalRegion) {
                        start = i;
                        startValue = currentValue;
                    } else {
                        end = i;
                        endValue = currentValue;
                    }
                    wasNotInSignalRegion = false;
                    if (i == size - 1) {
                        MeasureData value = processRegion(vector, sum, startValue, endValue, start, end, min, max, minPoint, maxPoint);
                        measures.add(value);
                    }
                } else {
                    if (wasNotInSignalRegion) {
                    } else {
                        MeasureData value = processRegion(vector, sum, startValue, endValue, start, end, min, max, minPoint, maxPoint);
                        measures.add(value);
                        sum = 0.0;
                        max = Double.NEGATIVE_INFINITY;
                        min = Double.MAX_VALUE;
                    }
                    wasNotInSignalRegion = true;
                }
            }
        }
        if (map != null) {
            int iPoint = 0;
            int[][] pt = vector.getPt();
            if ((pt != null) && (pt.length > 1)) {
                iPoint = pt[1][0];
            }
            map.put(key + iPoint, measures);
        }

        return this;
    }

    MeasureData processRegion(Vec vector, double sum, double startValue, double endValue, double start, double end, double min, double max, double minPoint, double maxPoint) {
        double fMin = (minPoint - start) / (end - start);
        double fMax = (maxPoint - start) / (end - start);
        double minCorrection = fMin * (endValue - startValue) + startValue;
        double maxCorrection = fMax * (endValue - startValue) + startValue;
        double sumCorrection = (endValue + startValue) / 2 * (end - start);

        MeasureData result = new MeasureData(vector, min, max, minPoint, maxPoint, sum, start, end, minCorrection, maxCorrection, sumCorrection);
        return result;
    }
}
