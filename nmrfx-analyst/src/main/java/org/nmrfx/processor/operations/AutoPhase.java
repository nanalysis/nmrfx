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

import com.google.common.util.concurrent.AtomicDouble;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

/**
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class AutoPhase extends Operation {

    public static final AtomicDouble lastPh0 = new AtomicDouble(0.0);
    public static final AtomicDouble lastPh1 = new AtomicDouble(0.0);

    private final boolean firstOrder;
    private final boolean maxMode;
    private final int winSize;
    private final double ratio;
    private final int mode;
    private final double ph1Limit;
    private final double negativePenalty;


    public AutoPhase(boolean firstOrder, boolean maxMode, int winSize, double ratio, int mode, double ph1Limit, double negativePenalty) {
        this.firstOrder = firstOrder;
        this.maxMode = maxMode;
        this.winSize = winSize;
        this.ratio = ratio;
        this.mode = mode;
        this.ph1Limit = ph1Limit;
        this.negativePenalty = negativePenalty;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        phase(vector);

        return this;
    }

    public void phase(Vec vector) {
        if (maxMode) {
            double phase = vector.autoPhaseByMax();
            vector.phase(phase, 0.0, false, false);
            lastPh0.set(phase);
        } else {
            double[] phases = vector.autoPhase(firstOrder, winSize, ratio, mode, ph1Limit, negativePenalty);
            vector.phase(phases[0], phases[1], false, false);
            lastPh0.set(phases[0]);
            lastPh1.set(phases[1]);
        }
    }

}
