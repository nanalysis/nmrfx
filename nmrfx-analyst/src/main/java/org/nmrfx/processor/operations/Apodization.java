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

import org.nmrfx.datasets.MatrixType;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

/**
 * @author johnsonb
 */
public abstract class Apodization extends MatrixOperation {

    protected double[] apodVec;

    protected void initApod(int vStart) {
        for (int i = 0; i < vStart; i++) {
            apodVec[i] = 1.0;
        }
    }

    public Operation evalMatrix(MatrixType matrix) throws ProcessingException {
        return this;
    }

    protected void applyApod(Vec vector) {
        int size2;

        if (apodVec.length < vector.getSize()) {
            size2 = apodVec.length;
        } else {
            size2 = vector.getSize();
        }
        vector.setAnnotation(apodVec);

        if (vector.isComplex()) {
            for (int i = 0; i < size2; i++) {
                vector.set(i, vector.getReal(i) * apodVec[i], vector.getImag(i) * apodVec[i]);
            }

            for (int i = size2; i < vector.getSize(); i++) {
                vector.set(i, 0, 0);
            }
        } else {
            for (int i = 0; i < size2; i++) {
                vector.rvec[i] *= apodVec[i];
            }

            for (int i = size2; i < vector.getSize(); i++) {
                vector.rvec[i] = 0.0;
            }
        }

    }

    // fixme should we check for apodVec value being zero
    protected void invertApod(Vec vector) {
        int size2;

        if (apodVec.length < vector.getSize()) {
            size2 = apodVec.length;
        } else {
            size2 = vector.getSize();
        }

        if (vector.isComplex()) {
            for (int i = 0; i < size2; i++) {
                if (apodVec[i] < 1.0e-8) {
                    throw new ProcessingException("apodVec value < 1.0e-8");
                }
                vector.set(i, vector.getReal(i) / apodVec[i], vector.getImag(i) / apodVec[i]);
            }

            for (int i = size2; i < vector.getSize(); i++) {
                vector.set(i, 0, 0);
            }
        } else {
            for (int i = 0; i < size2; i++) {
                if (apodVec[i] < 1.0e-8) {
                    throw new ProcessingException("apodVec value < 1.0e-8");
                }
                vector.rvec[i] /= apodVec[i];
            }

            for (int i = size2; i < vector.getSize(); i++) {
                vector.rvec[i] = 0.0;
            }
        }

    }

    protected void resize(int size) {
        apodVec = new double[size];
    }

    public void setApodVec(double[] apod) {
        apodVec = apod;
    }
}
