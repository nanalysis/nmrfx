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
package org.nmrfx.processor.math;

/**
 * @author brucejohnson
 */
public class VecBaseline {

    Vec vec;

    public VecBaseline(Vec vec) {
        this.vec = vec;
    }

    public boolean[] idBaseline2(int minSize, int[] limits, double ratio) {
        if (vec.isComplex()) {
            throw new IllegalArgumentException("idBaseline2: vector complex");
        }
        double threshold = Double.MAX_VALUE;
        int lastBaseline = -1;
        while (true) {
            double sum = 0.0;
            int nBaseline = 0;
            for (int i = 0; i < vec.getSize(); i++) {
                double value = vec.rvec[i];
                if (value < threshold) {
                    nBaseline++;
                    sum += value;
                }
            }
            double mean = sum / nBaseline;
            double sumSq = 0.0;
            for (int i = 0; i < vec.getSize(); i++) {
                double value = vec.rvec[i];
                if (value < threshold) {
                    double delta = vec.rvec[i] - mean;
                    sumSq += delta * delta;
                }
            }
            double sdev = Math.sqrt(sumSq / nBaseline);
            if (nBaseline == lastBaseline) {
                break;
            }
            lastBaseline = nBaseline;
            threshold = mean + ratio * sdev;
        }
        boolean[] nonBaseLinePts = new boolean[vec.getSize()];
        int first = 0;
        for (int i = minSize; i < (vec.getSize() - minSize - 1); i++) {
            boolean nonBase = false;
            for (int j = -minSize; j <= minSize; j++) {
                if (vec.rvec[i + j] > threshold) {
                    nonBase = true;
                    break;
                }
            }
            if (nonBase && (vec.rvec[i] > (20.0 * threshold))) {
                if ((i > (vec.getSize() / 16)) && (first == 0)) {
                    first = i;
                }
            }
            nonBaseLinePts[i] = nonBase;
        }
        for (int j = 0; j < minSize; j++) {
            nonBaseLinePts[j] = true;
            nonBaseLinePts[vec.getSize() - j - 1] = true;
        }
        first = minSize + 1;
        int last = vec.getSize() - minSize;
        limits[0] = first;
        limits[1] = last;
        return nonBaseLinePts;
    }

}
