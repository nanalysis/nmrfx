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
package org.nmrfx.processor.operations;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

import java.util.ArrayList;

/**
 * Median Baseline Correction.
 *
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class BcMed extends Operation {

    private final double winFrac;
    private final boolean wrapIt;

    public BcMed(double frac, boolean wrapIt) {
        this.winFrac = frac;
        this.wrapIt = wrapIt;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        int newLoc;
        int nextLoc;
        int prevLoc;
        int first;
        int last;
        int iiFirst;
        int size = vector.getSize();
        //vector must be real 
        if (vector.isComplex()) {
            vector.makeReal();
        }

        double[] vec = vector.rvec;

        if (wrapIt) {
            first = 0;
            last = size;
        } else {
            first = 1;
            last = size - 1;
        }

        int i = 0;
        int nExtreme = 0;
        double[] veBase = new double[size];

        ArrayList<Double> dalExtreme = new ArrayList<>();
        ArrayList<Integer> ialExtreme = new ArrayList<>();

        for (int j = first; j < last; j++) {
            prevLoc = j - 1;
            nextLoc = j + 1;

            if (nextLoc >= size) {
                nextLoc = 0;
            }

            if (prevLoc < 0) {
                prevLoc = size - 1;
            }

            if (((vec[j] > vec[prevLoc]) && (vec[j] > vec[nextLoc]))
                    || ((vec[j] < vec[prevLoc]) && (vec[j] < vec[nextLoc]))) {
                dalExtreme.add(vec[j]);
                ialExtreme.add(j);
                nExtreme++;
            }
        }
        int winSize = (int) (nExtreme * winFrac);
        winSize = ((winSize + 1) / 2) * 2;

        DescriptiveStatistics dStat = new DescriptiveStatistics(winSize);
        if (wrapIt) {
            for (int j = 0; j < (winSize / 2); j++) {
                dStat.addValue(dalExtreme.get(nExtreme - (winSize / 2) + j));
            }
            for (int j = 0; j < (winSize / 2); j++) {
                dStat.addValue(dalExtreme.get(j));
            }
        } else {
            for (int j = 0; j < winSize; j++) {
                dStat.addValue(dalExtreme.get(j));
            }
        }

        if (wrapIt) {
            first = 0;
            last = nExtreme;
            newLoc = (winSize / 2) + 1;
        } else {
            first = (winSize / 2);
            last = size - (winSize / 2) + 1;
            newLoc = winSize;
        }

        i = 0;
        iiFirst = 0;

        double rMed = 0.0;
        for (int j = 0; j < nExtreme; j++) {
            rMed = dStat.getPercentile(50.0);
            int iiExtreme = j + winSize / 2;
            if (iiExtreme >= nExtreme) {
                iiExtreme = nExtreme - 1;
            }
            int iiLast = ialExtreme.get(iiExtreme);
            for (int ii = iiFirst; ii < iiLast; ii++) {
                veBase[ii] = rMed;
            }
            iiFirst = iiLast;
            dStat.addValue(dalExtreme.get(newLoc));
            newLoc++;
            if (newLoc >= nExtreme) {
                if (wrapIt) {
                    newLoc = 0;
                } else {
                    break;
                }
            }
        }

        for (int ii = iiFirst; ii < size; ii++) {
            veBase[ii] = rMed;
        }

        for (int j = 0; j < size; j++) {
            vec[j] -= veBase[j];
        }

        return this;
    }

}
