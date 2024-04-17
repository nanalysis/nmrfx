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

import org.apache.commons.math3.complex.Complex;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.math.VecUtil;
import org.nmrfx.processor.processing.ProcessingException;

/**
 * Zero Bruker baseline and assorted algorithms.
 *
 * @author bfetler
 */
@PythonAPI("pyproc")
public class Bz extends Operation {

    private final String alg;
    private double groupDelay;
    private final double mult;   // vector mult
    private final double phase;  // phase for mult
    private final double mult2;  // 2nd point mult

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        try {
            bz(vector);
        } catch (IllegalArgumentException iae) {
            throw new ProcessingException(iae.getMessage());
        }
        return this;
    }

    /**
     * Create operation for Bruker baseline correction.
     *
     * @throws ProcessingException
     */
    public Bz(String alg, double delay, double mult, double phase, double mult2)
            throws ProcessingException {
        this.alg = alg;
        this.groupDelay = delay;  // usually 67.98 or so
        this.mult = -mult;
        this.phase = Math.PI * phase / 180.0;
        this.mult2 = mult2;
    }

    private void bz(Vec vector) throws IllegalArgumentException {
        Vec stub;
        int size = vector.getSize();
        // fixme why have in constructor
        groupDelay = vector.getGroupDelay();  // overrides constructor
        int start = (int) (groupDelay + 0.5);    // usually 67.98 or so
        switch (alg) {
            case "dspph":
                dspph(vector, groupDelay);
                break;
            case "negate":
                if (vector.isComplex() && vector.useApache()) {
                    VecUtil.negate(vector.getCvec());
                } else {
                    throw new IllegalArgumentException("BZ: negate not implemented on non-complex data");
                }
                break;
            case "conj":
                if (vector.isComplex() && vector.useApache()) {
                    VecUtil.conjugate(vector.getCvec());
                } else {
                    throw new IllegalArgumentException("BZ: cannot conjugate non-complex data");
                }
                break;
            case "chop":  // remove precharge points
                System.out.println("chop: size=" + size + " start=" + start);
                chop(vector, start, size);
                vector.setGroupDelay(0.0);
                break;
            case "stub":
            case "stubstart":  // stub of fid start
                System.out.println("stubstart: size=" + size + " start=" + start);
                stub = stubVec(vector, start, 0);
                stub.copy(vector);
                break;
            case "stubend":  // stub of fid end
                dspph(vector, groupDelay);
                System.out.println("stubend: size=" + size + " start=" + start + " offset=" + (size - start));
                stub = stubVec(vector, start, size - start);
                stub.copy(vector);
                break;
            case "start":  // subtract start
                System.out.println("start: size=" + size + " start=" + start + "  ");
                stub = stubVec(vector, start, 0);
                stub.reverse();
                multByFactor(stub);
                chop(vector, start, size);
                vector.add(stub);
                vector.setGroupDelay(0.0);
                break;
            case "end":  // subtract end
                dspph(vector, groupDelay);
                System.out.println("end: size=" + size + " start=" + start + " offset=" + (size - start));
                stub = stubVec(vector, start, size - start);
                stub.reverse();
                multByFactor(stub);
                chop(vector, 0, size - start);
                vector.add(stub);
                vector.setGroupDelay(0.0);
                break;
            case "ph":  // phase, ft,hft,ift
                vector.fixWithPhasedHFT(0.5 * phase * 180.0 / Math.PI);
                break;
            case "showsim":  // show Bruker FID distortion simulation
                vector.showBrukerFilterSim();
                break;
            case "sim":
            default:
                vector.fixWithBrukerFilter(mult, phase);
                break;
        }
    }

    private Vec stubVec(Vec vector, int size, int offset) {
        Vec stub = new Vec(size, vector.isComplex());
        for (int i = 0; i < size; i++) {
            stub.set(i, vector.getComplex(offset + i));
        }
        return stub;
    }

    private void chop(Vec vector, int start, int size) {
        size = size - start;
        vector.trim(start, size);
    }

    // usually used on stubVec
    private void multByFactor(Vec vector) throws IllegalArgumentException {
        if (vector.isComplex()) {
            int size = vector.getSize();
            Complex factor = new Complex(mult * Math.cos(phase), mult * Math.sin(phase));
            for (int i = 0; i < size; i++) {
                vector.multiply(i, factor);
            }
            vector.multiply(1, mult2, 0.0);
        }
    }

    private void dspph(Vec vector, double shiftPoints) {
        vector.fft();
        vector.phase(0.0, -360.0 * shiftPoints, false, false);  // oldStyle is false
        vector.ifft();
        vector.setGroupDelay(0.0);
    }

}
