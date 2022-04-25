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
import org.nmrfx.processor.processing.ProcessingException;

/**
 * @author johnsonb
 */
public class BasicApodization extends Apodization implements Invertible {

    final double lb;
    final double gb;
    final double sbOffset;
    final double end = 1.0;
    final boolean lbOn;
    final boolean gmOn;
    final boolean sbOn;
    final boolean sbSqOn;
    final double c;
    final int apodSize;

    @Override
    public BasicApodization eval(Vec vector) throws ProcessingException {
        apodize(vector);
        return this;
    }

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

    public void apodize(Vec vector) {
        vector.makeApache();
        int apodSize = Math.min(this.apodSize, vector.getSize());
        if (apodSize == 0) {
            apodSize = vector.getSize();
        }

        if (apodVec == null || vector.getSize() != apodVec.length) {
            resize(apodSize);
            int vStart = vector.getStart();
            initApod(vStart);
            for (int i = 0;i< apodVec.length;i++) {
                apodVec[i] = 1.0;
            }

            double start = sbOffset * Math.PI;
            double delta = ((end - sbOffset) * Math.PI) / (apodSize - vStart - 1);
            double dwellTime = vector.dwellTime;
            if (sbOn) {
                if (sbSqOn) {
                    double power = 2.0;
                    for (int i = vStart; i < apodSize; i++) {
                        double deltaPos = i - vStart;
                        apodVec[i] *= Math.pow(Math.sin(start + (deltaPos * delta)), power);
                    }
                } else {
                    for (int i = vStart; i < apodSize; i++) {
                        double deltaPos = i - vStart;
                        apodVec[i] *= Math.sin(start + (deltaPos * delta));
                    }
                }
            }
            if (lbOn) {
                for (int i = vStart; i < apodSize; i++) {
                    double deltaPos = i - vStart;
                    double e = Math.PI * lb;

                    double t = deltaPos * dwellTime;
                    apodVec[i] *= Math.exp(-e * t);
                }
            }
            if (gmOn) {
                for (int i = vStart; i < apodSize; i++) {
                    double deltaPos = i - vStart;
                    double ga = 0.6 * Math.PI * gb;

                    double t = deltaPos * dwellTime;
                    apodVec[i] *= Math.exp( -(ga * t * t));
                }

            }
            apodVec[vStart] *= c;
        }
        if (invertOp) {
            invertApod(vector);
        } else {
            applyApod(vector);
        }
    }
}
