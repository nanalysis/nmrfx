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
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.operations;

import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.processor.math.LinearPrediction;
import org.nmrfx.processor.math.Vec;

/**
 * @author michael
 */
@PythonAPI("pyproc")
public class Extend extends Operation {

    private final int fitStart;
    private final int fitEnd;
    private final int ncoef;
    private final int predictStart;
    private final int predictEnd;
    private final int npred;
    private final double threshold;
    private final boolean calculateForward;
    private final boolean calculateBackward;
    private final boolean insertMode;
    private final int mirror;

    /**
     * Extend a Vec using linear prediction.
     *
     * @param fitStart
     * @param fitEnd
     * @param predictStart
     * @param predictEnd
     * @param ncoefgetEntry
     * @param threshold
     * @param mode
     * @param forward
     * @param backward
     * @param insertMode
     * @param mirror
     */
    public Extend(int fitStart, int fitEnd, int predictStart, int predictEnd,
                  int npred, int ncoef, double threshold, boolean backward,
                  boolean forward, boolean insertMode, int mirror) {
        this.fitStart = fitStart;
        this.fitEnd = fitEnd;
        this.predictStart = predictStart;
        this.predictEnd = predictEnd;
        this.npred = npred;
        this.ncoef = ncoef;
        this.threshold = threshold;
        this.calculateForward = forward;
        this.calculateBackward = backward;
        this.insertMode = insertMode;
        this.mirror = mirror;
    }

    public Extend eval(Vec vector) {
        LinearPrediction lp = new LinearPrediction(vector);
        lp.svdPredLP(fitStart, fitEnd, ncoef, threshold, predictStart, predictEnd, npred, calculateBackward,
                calculateForward, insertMode, mirror);
        return this;
    }

}
