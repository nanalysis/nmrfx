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
package org.nmrfx.processor.math;

import org.apache.commons.math3.complex.Complex;

/**
 * @author brucejohnson
 */
public class VecAutoPhase {

    public static double apsl(Vec vec, int icenter, int start, int width,
                              boolean asym) {
        double num;
        double dem;
        double dc;
        double ds;
        double newphase;
        double dcc;
        double dsc;
        double re;
        double im;
        int i;
        if (!vec.isComplex()) {
            throw new IllegalArgumentException("apsl: vector must be complex");
        }

        vec.makeApache();

        Complex[] cvec = vec.cvec;
        int size = vec.getSize();

        if (((icenter - width) < 0) || ((icenter + width) >= size)
                || (start > width)) {
            throw new IllegalArgumentException("apsl: error in parameters");
        }

        num = 0.0;
        dem = 0.0;
        dcc = 0.0;
        dsc = 0.0;

        double dcSum = 0.0;
        double dsSum = 0.0;
        double la = 0.0;
        double ra = 0.0;
        double f;
        double totalValue = 0.0;
        double weightedValues = 0.0;

        for (i = start; i < width; i++) {
            weightedValues += (i * Math.sqrt((cvec[icenter + i].getReal() * cvec[icenter
                    + i].getReal()) + (cvec[icenter + i].getImaginary() * cvec[icenter + i].getImaginary())));
            weightedValues -= (i * Math.sqrt((cvec[icenter - i].getReal() * cvec[icenter
                    - i].getReal()) + (cvec[icenter - i].getImaginary() * cvec[icenter - i].getImaginary())));
            totalValue += Math.sqrt((cvec[icenter + i].getReal() * cvec[icenter + i].getReal())
                    + (cvec[icenter + i].getImaginary() * cvec[icenter + i].getImaginary()));
            totalValue += Math.sqrt((cvec[icenter - i].getReal() * cvec[icenter - i].getReal())
                    + (cvec[icenter - i].getImaginary() * cvec[icenter - i].getImaginary()));
        }

        double offset = weightedValues / totalValue;
        icenter += Math.round(offset);

        for (i = start; i < width; i++) {
            ra += Math.sqrt((cvec[icenter + i].getReal() * cvec[icenter + i].getReal())
                    + (cvec[icenter + i].getImaginary() * cvec[icenter + i].getImaginary()));
            la += Math.sqrt((cvec[icenter - i].getReal() * cvec[icenter - i].getReal())
                    + (cvec[icenter - i].getImaginary() * cvec[icenter - i].getImaginary()));

            dc = cvec[icenter + i].getReal() - cvec[icenter - i].getReal();
            ds = cvec[icenter + i].getImaginary() - cvec[icenter - i].getImaginary();
            dcSum += dc;
            dsSum += ds;
            num = num + (dc * ds);
            dem = dem + ((ds * ds) - (dc * dc));
        }

        newphase = Math.PI + (Math.atan2(2.0 * num, dem) / 2.0);

        for (i = start; i < width; i++) {
            dcc = (dcc
                    + ((Math.cos(newphase) * cvec[icenter + i].getReal())
                    - (Math.sin(newphase) * cvec[icenter + i].getImaginary())))
                    - ((Math.cos(newphase) * cvec[icenter - i].getReal())
                    - (Math.sin(newphase) * cvec[icenter - i].getImaginary()));

            dsc = dsc
                    + ((Math.cos(newphase) * cvec[icenter + i].getImaginary())
                    + (Math.sin(newphase) * cvec[icenter + i].getReal()))
                    + ((Math.cos(newphase) * cvec[icenter - i].getImaginary())
                    + (Math.sin(newphase) * cvec[icenter - i].getReal()));
        }

        re = ((Math.cos(newphase) * cvec[icenter].getReal())
                - (Math.sin(newphase) * cvec[icenter].getImaginary()));
        im = ((Math.cos(newphase) * cvec[icenter].getImaginary())
                + (Math.sin(newphase) * cvec[icenter].getReal()));

        if (re < im) {
            newphase += Math.PI;
        }

        if (asym) {
            if (ra > la) {
                f = la / ra;
                newphase = newphase + Math.atan(1.0 - f);
            } else {
                f = ra / la;
                newphase = newphase - Math.atan(1.0 - f);
            }
        }

        newphase = (newphase * 180.0) / Math.PI;
        return (newphase);
    }

}
