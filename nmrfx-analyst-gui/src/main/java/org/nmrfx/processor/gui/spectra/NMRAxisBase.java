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
package org.nmrfx.processor.gui.spectra;

import javafx.util.StringConverter;
import javafx.util.converter.NumberStringConverter;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author brucejohnson
 */
public class NMRAxisBase {

    StringConverter<Number> defaultFormatter = new NumberStringConverter();
    DecimalFormat df = new DecimalFormat();
    boolean reverse = false;
    double mul = 1.0;
    double inc;
    int nMinor = 4;

    public NMRAxisBase() {
    }

    public void setReverse(boolean state) {
        reverse = state;
    }

    public boolean isReversed() {
        return reverse;
    }

    protected List<Number> calculateTickValues(double length, Object range) {
        final double[] rangeProps = (double[]) range;
        final double lowerBound = rangeProps[0];
        final double upperBound = rangeProps[1];
        final double tickUnit = rangeProps[2];
        return calculateTickValues(length, lowerBound, upperBound, tickUnit);
    }

    public List<Number> calculateTickValues(double length, double lowerBound, double upperBound, double tickUnit) {
        calculateSpacing(lowerBound, upperBound, length);
        calculateLabelFormat(df, lowerBound, upperBound);
        return getTics(lowerBound, upperBound);
    }

    public String getTickMarkLabel(Object value) {
        return df.format(value);
    }

    private List<Number> getTics(double pmin, double pmax) {
        double fullInc = mul * inc;
        double minorInc = fullInc / nMinor;
        double tic;
        List<Number> tickValues = new ArrayList<>();
        if (Math.abs(Math.floor((pmin + minorInc) / minorInc) * minorInc - pmin) < 1.0e-8) {
            tic = pmin;
        } else {
            tic = Math.floor((pmin + minorInc * 0.999) / minorInc) * minorInc;
        }
        while (tic <= pmax) {
            if (Math.abs(((Math.round(tic / fullInc)) * fullInc) - tic) < (Math.abs(minorInc / 10))) {
                tickValues.add(tic);
            } else {
                // minorTick
            }

            tic += minorInc;

        }
        // if you don't reverse it not all ticks are displayed
        if (reverse) {
            Collections.sort(tickValues, Collections.reverseOrder());
        }
        return tickValues;

    }

    private boolean calculateSpacing(final double min, final double max, final double delPix) {
        double ticDelta = 0.0;
        int ntic = 1;
        double delp = Math.abs(max - min);
        if (ticDelta > 1.0e-6) {
            mul = 1.0;
            inc = ticDelta;
        } else {
            int n = 0;
            mul = 1.0;
            inc = 1.0;
            nMinor = 4;

            do {
                n++;
                inc = 1.0;
                nMinor = 4;
                ntic = (int) (delp / (mul * inc));
                int npix = (int) ((delPix / delp) * (mul * inc));

                if (npix > 200) {
                    mul = mul * 0.1;

                    continue;
                }

                if ((npix >= 80) && (npix <= 200)) {
                    break;
                }

                inc = 2.0;
                nMinor = 4;
                ntic = (int) (delp / (mul * inc));
                npix = (int) ((delPix / delp) * (mul * inc));

                if ((npix >= 80) && (npix <= 200)) {
                    break;
                }

                inc = 4.0;
                nMinor = 4;
                ntic = (int) (delp / (mul * inc));
                npix = (int) ((delPix / delp) * (mul * inc));

                if ((npix >= 80) && (npix <= 200)) {
                    break;
                }

                mul = mul * 8;
            } while (n < 10);

            if (n == 10) {
                return false;
            }

            if (ntic > 9) {
                if (inc == 1.0) {
                    inc = 2.0;
                    nMinor = 4;
                } else if (inc == 2.0) {
                    inc = 5.0;
                    nMinor = 5;
                } else {
                    inc = 1.0;
                    nMinor = 4;
                    mul = mul * 10.0;
                }
            }

            if (ntic < 2) {
                if (inc == 1.0) {
                    inc = 5.0;
                    nMinor = 5;
                    mul = mul / 10.0;
                } else if (inc == 2.0) {
                    inc = 1.0;
                    nMinor = 4;
                } else {
                    inc = 2.0;
                    nMinor = 4;
                }
            }
        }
        return true;
    }

    /**
     * @param df
     */
    private void calculateLabelFormat(final DecimalFormat df, double pmin, double pmax) {
        int labprec = (int) ((-Math.log(mul) / Math.log(10.0)) + 0.5) + 1;

        if (labprec < 0) {
            labprec = 0;
        }

        int labmax = (int) ((Math.abs(pmax) > Math.abs(pmin)) ? Math.abs(pmax)
                : Math.abs(pmin));
        int labwid = (int) ((Math.log(labmax) / Math.log(10.0)) + 1);

        if (labwid < 0) {
            labwid = 0;
        }

        if (labprec > 0) {
            labwid = labwid + labprec + 2;
        }

        df.setMinimumFractionDigits(labprec);
        df.setMaximumFractionDigits(labprec);
    }

}
