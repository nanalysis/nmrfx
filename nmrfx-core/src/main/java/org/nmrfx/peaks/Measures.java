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
package org.nmrfx.peaks;

import org.nmrfx.star.STAR3;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * @author Bruce Johnson
 */
public class Measures {

    double[] values;

    public Measures(double[] values) {
        this.values = values.clone();
    }

    public Measures(List<Double> values) {
        this.values = new double[values.size()];
        for (int i = 0; i < this.values.length; i++) {
            this.values[i] = values.get(i);
        }
    }

    public double[] getValues() {
        return values;
    }

    public void writeMeasures(Writer chan) throws IOException {
        if ((values == null) || (values.length == 0)) {
            return;
        }
        String[] loopStrings = {"_Spectral_measure.ID", "_Spectral_measure.value"};
        chan.write("loop_\n");

        for (String loopString : loopStrings) {
            chan.write(loopString + "\n");
        }
        chan.write("\n");
        for (int i = 0;i < values.length;i++) {
            String outStr = String.format("%3d %f\n", i, values[i]);
            chan.write(outStr);
        }
        chan.write(STAR3.STOP + "\n\n");
    }
}
