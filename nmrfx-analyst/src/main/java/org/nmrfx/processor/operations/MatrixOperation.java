//MatrixOperation.java
//Simon Hulse
//simonhulse@protonmail.com
//Last Edited: Mon 05 Aug 2024 10:01:05 EDT

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

import java.util.ArrayList;

/**
 * A subclass of Operation is a function which performs a computation involving a Matrix. The expected behavior of an
 * Operation is to be called with an ArrayList<Matrix> from a Processor. For single-matrix Operations, this will call
 * the evalMatrix(Matrix matrix) method which is overwritten in an Operation implementation.
 *
 * @author bfetler
 */
public abstract class MatrixOperation extends Operation {

    @Override
    public Operation eval(Vec vector) {  // dummy eval
        return this;
    }

    public abstract Operation evalMatrix(MatrixType matrix) throws ProcessingException;

    public Operation evalMatrix(ArrayList<MatrixType> matrices) throws ProcessingException {
        for (MatrixType m : matrices) {
            evalMatrix(m);
        }
        return this;
    }

}
