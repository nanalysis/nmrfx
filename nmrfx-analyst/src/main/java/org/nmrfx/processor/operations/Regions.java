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
public class Regions extends Operation {

    private final int winSize;
    private final int minBase;
    private final double ratio;
    // fixme what about clone
    private final ArrayList<Double> realPoints;
    private ArrayList<Integer> points;
    private final boolean invert;
    private final String type;
    private final String mode;

    public Regions(ArrayList<Double> realPoints, String type, boolean invert) {
        this.realPoints = realPoints;
        this.points = null;
        this.type = type;
        this.invert = invert;
        this.mode = "";
        this.winSize = 0;
        this.minBase = 0;
        this.ratio = 0;
    }

    public Regions(ArrayList<Integer> points, boolean invert) {
        this.points = points;
        this.realPoints = null;
        this.type = "";
        this.invert = invert;
        this.mode = "";
        this.winSize = 0;
        this.minBase = 0;
        this.ratio = 0;
    }

    public Regions(String mode, int winSize, int minBase, double ratio) {
        this.realPoints = null;
        this.points = null;
        this.type = "";
        this.invert = false;
        this.mode = mode;
        this.winSize = winSize;
        this.minBase = minBase;
        this.ratio = ratio;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        int vecSize = vector.getSize();
        if ((winSize < 0) || (winSize > vecSize)) {
            throw new OperationException("regions: error in winSize");
        }

        ArrayList<Integer> positions = null;
        if (points != null) {
            positions = Util.getBasePoints(vector, points, invert);
        } else if (realPoints != null) {
            positions = Util.getBasePoints(vector, realPoints, type, invert);
        }

        boolean[] isInSignalRegion;
        if ((positions == null) || (positions.isEmpty())) {
            if (mode.equals("sdev")) {
                positions = Util.idBaseLineBySDev(vector, winSize, ratio);
                isInSignalRegion = Util.getSignalRegion(vecSize, positions);
            } else if (mode.equals("cwtd")) {
                isInSignalRegion = Util.getSignalRegionByCWTD(vector, winSize, minBase, ratio, IDBaseline2.ThreshMode.SDEV);
            } else {
                isInSignalRegion = Util.getSignalRegionByCWTD(vector, winSize, minBase, ratio, IDBaseline2.ThreshMode.FRACTION);
            }
        } else {
            isInSignalRegion = Util.getSignalRegion(vecSize, positions);
        }
        vector.setSignalRegion(isInSignalRegion);
        return this;
    }

}
