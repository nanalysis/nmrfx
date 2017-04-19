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
import org.nmrfx.processor.math.VecException;
import org.nmrfx.processor.processing.ProcessingException;
import java.lang.reflect.Field;
import java.util.ArrayList;

/**
 * A subclass of Operation is a function which performs a computation involving a Vec. The expected behavior of an
 * Operation is to be called with an ArrayList<Vec> from a Processor. For single-vector Operations, this will call the
 * eval(Vec vector) method which is overwritten in an Operation implementation.
 *
 * @author johnsonb
 */
public abstract class Operation implements Cloneable {//extends ForkJoinTask<ArrayList<Vec>>{

    protected boolean invertOp = false;

    /**
     * Performs operation on single vector. An operation like combine that takes two vectors does not make sense if
     * called on a single vector so it's not abstract.
     *
     * @param vector
     * @throws ProcessingException
     */
    public abstract Operation eval(Vec vector) throws ProcessingException;

    /**
     * Performs the Operation on multiple vectors. This function is called by the Process with all of the vectors that
     * it receives in one Processing loop.
     *
     * @param vectors
     * @throws ProcessingException
     */
    public Operation eval(ArrayList<Vec> vectors) throws ProcessingException, VecException, IllegalArgumentException {
        for (Vec v : vectors) {
            eval(v);
        }
        return this;
    }

    /**
     *
     * @return Name of the derived class.
     */
    public String getName() {
        String fullName = getClass().getName();
        int splitAt = fullName.lastIndexOf(".") + 1;

        return fullName.substring(splitAt);
    }

    /**
     * Clone the Operation (non-final parameters are discouraged in Operations so that clone doesn't have weird
     * consequences).
     *
     * @return
     */
    public Operation clone() {
        try {
            return (Operation) super.clone();
        } catch (CloneNotSupportedException cnse) {
            cnse.printStackTrace();
            return null;
        }
    }
}
