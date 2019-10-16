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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.math.VecCombine;
import org.nmrfx.processor.processing.ProcessingException;

/**
 *
 * @author Bruce Johnson
 */
public class Dept extends DatasetOperation {

    private static final double[][] COEFS = {
        {2.23, 0.23, 0, 0},
        {0, 1, 1, 0},
        {1, 0, 0, -1},
        {0.23, 0, -0.77, 1}};

    private static final double[][] COEFSQ = {
        {2.23, 2.23, 0.23, 0.23, 0, 0, 0, 0},
        {1.0, -1.0, 1.0, -1.0, 1.0, -1.0, 1.0, -1.0},
        {0, 0, 1, 1, 1, 1, 0, 0},
        {1, 1, 0, 0, 0, 0, -1, -1},
        {0.23, 0.23, 0, 0, -0.77, -0.77, 1, 1}};

    public Dept() {
    }

    @Override
    public Operation evalDataset(Dataset dataset) throws ProcessingException {
        deptCombine(dataset);
        return this;
    }

    public void deptCombine(Dataset dataset) throws ProcessingException {
        int size2 = dataset.getSize(1);
        double[][] coefs;
        switch (size2) {
            case 8:
                coefs = COEFSQ;
                break;
            case 4:
                coefs = COEFS;
                break;
            default:
                throw new ProcessingException("Should have 4 or 8 rows");
        }
        int nCoefs = coefs[0].length;
        int nOut = coefs.length;
        int n = dataset.getSize(0);
        Vec[] vecs = new Vec[nCoefs];
        Vec[] outVecs = new Vec[1];
        try {
            for (int i = 0; i < nCoefs; i++) {
                vecs[i] = new Vec(n);
                dataset.readVector(vecs[i], i, 0);
            }
            outVecs[0] = new Vec(n);

            for (int i = 0; i < nOut; i++) {
                VecCombine.comb2(coefs[i], vecs, false, outVecs);
                dataset.writeVector(outVecs[0], i, 0);
            }
        } catch (IOException ex) {
            Logger.getLogger(Dept.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
