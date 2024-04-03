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
import org.nmrfx.math.units.Unit;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

/**
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class Fdss extends Operation {

    // fixme do Unit fields get initialized in clone
    private final boolean autoCenter;
    private final Unit centerUnit;
    private final Unit startUnit;
    private final Unit endUnit;

    @Override
    public Fdss eval(Vec vector) throws ProcessingException {
        fdss(vector);
        return this;
    }

    public Fdss(Unit centerUnit, Unit startUnit, Unit endUnit, boolean autoCenter) {
        this.startUnit = startUnit;
        this.centerUnit = centerUnit;
        this.endUnit = endUnit;
        this.autoCenter = autoCenter;
    }

    private void fdss(Vec vector) throws ProcessingException {
        /*
         * Allowing for the vector size for the vectors in a process
         * to have different sizes requires us to calculate these variables
         * local to the method.
         */
        int icenter = -1;
        int start = -1;
        int end = -1;
        if (centerUnit != null) {
            icenter = (vector.getSize() / 2) + (int) (centerUnit.getDoublePosition(vector));
        }
        if (startUnit != null) {
            start = (int) (startUnit.getDoublePosition(vector));
        }
        if (endUnit != null) {
            end = (int) (endUnit.getDoublePosition(vector));
        }

        boolean autoCenter = this.autoCenter;

        if (icenter < 0) //if not set then give default value
        {
            icenter = vector.getSize() / 2;
        }
        if (start < 0) // if not set then give default value
        {
            start = vector.getSize() / 256;
        }
        if (end < 0) // if not set then give default value
        {
            end = start * 3;
        }
        if (autoCenter) {
            icenter = -1;
        }
        if (start > end) {
            end = start * 3;
        }

        boolean isComplex = true;
        if (!vector.isComplex()) {
            isComplex = false;
            vector.hft();
        }

        FDSolventMinimizer fdMin = new FDSolventMinimizer(vector, -180.0, 170.0, 45.0, 0.1);

        fdMin.icenter = icenter;
        fdMin.start = start;
        fdMin.end = end;

        double phase = fdMin.minimize();

        vector.phase(phase, 0.0, false, true);

        int m = 2 * end + 1;
        double[] w = new double[m + 1];
        double[] z = new double[m + 1];
        double[] y = new double[m + 1];
        double lambda = 5000.0;

        double[] rvec = vector.getRvec();

        for (int i = 0; i < m; i++) {
            w[i + 1] = 1.0;
            y[i + 1] = rvec[icenter - end + i];
        }
        for (int i = (end - start); i < (m - start); i++) {
            w[i + 1] = 0.0;
        }
        int n = 1;
        double[] a = new double[n + 1];
        Util.pascalrow(a, 1);
        Util.asmooth(w, y, z, a, lambda, m, 1);
        for (int i = (end - start); i < (m - start); i++) {
            rvec[icenter - end + i] = z[i + 1];
        }

        vector.hft();

        vector.phase(-phase, 0.0, false, false);

        double sdev = Util.sdev(vector, 16, 4);

        if (!isComplex) {
            vector.makeReal();
        }
    }
}
