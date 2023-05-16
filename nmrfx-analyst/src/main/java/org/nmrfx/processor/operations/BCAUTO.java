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

import java.util.ArrayList;

/**
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class BCAUTO extends Operation {

    private final double ratio;

    public BCAUTO(double ratio) {
        this.ratio = ratio;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        int vecSize = vector.getSize();
        int winSize = 16;
        if (winSize > vecSize) {
            throw new OperationException("regions: error in winSize");
        }

        boolean[] isInSignalRegion;

        ArrayList<Integer> positions = Util.idBaseLineBySDev(vector, winSize, ratio);
        isInSignalRegion = Util.getSignalRegion(vecSize, positions);
        vector.setSignalRegion(isInSignalRegion);
        double lambda = 5000;
        int order = 1;
        boolean baselineMode = false;
        vector.bcWhit(lambda, order, baselineMode);
        return this;
    }
}
