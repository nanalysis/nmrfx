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

import java.util.Arrays;

/**
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class BasicApodization extends Apodization implements Invertible {

    private static final double END = 1.0;
    final double lb;
    final double gb;
    final double sbOffset;
    final boolean lbOn;
    final boolean gmOn;
    final boolean sbOn;
    final boolean sbSqOn;
    final double c;
    final int apodSize;

    public BasicApodization(boolean lbOn, double lb, boolean gmOn, double gm, boolean sbOn, boolean sbSqOn,
                            double sbOffset,
                            double c, int apodSize) {
        this(lbOn, lb, gmOn, gm, sbOn, sbSqOn, sbOffset, c, apodSize, false);
    }

    public BasicApodization(boolean lbOn, double lb, boolean gmOn, double gb, boolean sbOn, boolean sbSqOn,
                            double sbOffset,
                            double c, int apodSize, boolean inverse) {
        this.lbOn = lbOn;
        this.gmOn = gmOn;
        this.sbOn = sbOn;
        this.sbSqOn = sbSqOn;
        this.sbOffset = sbOffset;
        this.gb = gb;
        this.lb = lb;
        this.c = c;
        this.apodSize = apodSize;
        this.invertOp = inverse;
    }

    @Override
    public BasicApodization eval(Vec vector) throws ProcessingException {
        apodize(vector);
        return this;
    }

    public void apodize(Vec vector) {
        vector.makeApache();
        int size = Math.min(this.apodSize, vector.getSize());
        if (size == 0) {
            size = vector.getSize();
        }

        if (apodVec == null || vector.getSize() != apodVec.length) {
            resize(size);
            int vStart = vector.getStart();
            initApod(vStart);
            Arrays.fill(apodVec, 1.0);

            double start = sbOffset * Math.PI;
            double delta = ((END - sbOffset) * Math.PI) / (size - vStart - 1);
            double dwellTime = vector.dwellTime;
            if (sbOn) {
                applySB(size, vStart, start, delta);
            }
            if (lbOn) {
                applyLB(size, vStart, dwellTime);
            }
            if (gmOn) {
                applyGM(size, vStart, dwellTime);

            }
            apodVec[vStart] *= c;
        }
        if (invertOp) {
            invertApod(vector);
        } else {
            applyApod(vector);
        }
    }

    private void applyGM(int size, int vStart, double dwellTime) {
        for (int i = vStart; i < size; i++) {
            int deltaPos = i - vStart;
            double ga = 0.6 * Math.PI * gb;
            double t = deltaPos * dwellTime;
            apodVec[i] *= Math.exp(-(ga * t * t));
        }
    }

    private void applyLB(int size, int vStart, double dwellTime) {
        for (int i = vStart; i < size; i++) {
            int deltaPos = i - vStart;
            double e = Math.PI * lb;
            double t = deltaPos * dwellTime;
            apodVec[i] *= Math.exp(-e * t);
        }
    }

    private void applySB(int size, int vStart, double start, double delta) {
        if (sbSqOn) {
            double power = 2.0;
            for (int i = vStart; i < size; i++) {
                int deltaPos = i - vStart;
                apodVec[i] *= Math.pow(Math.sin(start + (deltaPos * delta)), power);
            }
        } else {
            for (int i = vStart; i < size; i++) {
                int deltaPos = i - vStart;
                apodVec[i] *= Math.sin(start + (deltaPos * delta));
            }
        }
    }
}
