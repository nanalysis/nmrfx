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

import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

/**
 *
 * @author johnsonb
 */
public class Bucket extends Operation {

    private final int nBuckets;

    public Bucket(int buckets) {
        this.nBuckets = buckets;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        boolean complex = vector.isComplex();
        boolean freqDomain = vector.freqDomain();
        int size = vector.getSize();

        if (freqDomain) {
            vector.adjustRef(0, nBuckets);
        }

        if (complex) {
            throw new ProcessingException("bucket: vector must be real");
        }

        if (size < nBuckets) {
            throw new ProcessingException(
                    "bucket: nBuckets must be smaller than size");
        }

        if ((size % nBuckets) != 0) {
            throw new ProcessingException(
                    "bucket: size must be multiple of nBuckets");
        }

        int bucketSize = size / nBuckets;

        int k = 0;
        double bucketVal = 0.0;
        double[] vec = vector.rvec;

        for (int i = 0; i < nBuckets; i++) {
            bucketVal = 0.0;
            k = i * bucketSize;

            for (int j = 0; j < bucketSize; j++) {
                bucketVal += vec[k++];
            }

            vec[i] = bucketVal;
        }

        size = nBuckets;
        return this;
    }

}
