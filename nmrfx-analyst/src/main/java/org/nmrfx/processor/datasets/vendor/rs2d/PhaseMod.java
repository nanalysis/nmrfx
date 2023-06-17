/*
 * NMRFx: A Program for Processing NMR Data
 * Copyright (C) 2004-2022 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.processor.datasets.vendor.rs2d;

/**
 * Possible values for PHASE_MOD parameter in RS2D data
 */
public enum PhaseMod {
    NONE(true, "ft", "sep", new double[0], 1),
    STATES(true, "negate", "hyper-r", new double[]{1, 0, 0, 0, 0, 0, 1, 0}, 2),
    TPPI(false, "rft", "real", new double[0], 1),
    STATES_TPPI(true, "negate", "hyper", new double[]{1, 0, 0, 0, 0, 0, 1, 0}, 2),
    ECHO_ANTIECHO(true, "ft", "echo-antiecho", new double[]{1, 0, -1, 0, 0, -1, 0, -1}, 2),
    QF(true, "ft", "sep", new double[0], 1),
    QSEQ(false, "rft", "real", new double[0], 2);

    private final boolean complex;
    private final String ftType;
    private final String symbolicCoefs;
    private final int groupSize;
    private final double[] coefs;

    PhaseMod(boolean complex, String ftType, String symbolicCoefs, double[] coefs, int groupSize) {
        this.complex = complex;
        this.ftType = ftType;
        this.symbolicCoefs = symbolicCoefs;
        this.coefs = coefs;
        this.groupSize = groupSize;
    }

    public boolean isComplex() {
        return complex;
    }

    public int getGroupSize() {
        return groupSize;
    }

    public String getFtType() {
        return ftType;
    }

    public String getSymbolicCoefs() {
        return symbolicCoefs;
    }

    public double[] getCoefs() {
        return coefs;
    }

    /**
     * Get the PhaseMod directly from its name
     *
     * @param name the phase mod name
     * @return the PhaseMod enum value.
     */
    public static PhaseMod fromName(String name) {
        String normalized = normalize(name);
        return valueOf(normalized);
    }

    /**
     * Guess the PhaseMod from the acquisition mode. Acquisition mode is an obsolote param, that can still be found
     * in older RS2D data, before 2021.12.
     *
     * @param acqMode the acquisition mode
     * @return the PhaseMod enum value.
     */
    public static PhaseMod fromAcquisitionMode(String acqMode) {
        String normalized = normalize(acqMode);
        switch (normalized) {
            case "COMPLEX":
                return STATES;
            case "TPPI":
                return TPPI;
            case "COMPLEX_TPPI":
                return STATES_TPPI;
            case "PHASE_MODULATION":
                return NONE; //XXX Not sure about this one, does it really exist?
            case "ECHO_ANTIECHO":
                return ECHO_ANTIECHO;
            default:
                return NONE;
        }
    }

    private static String normalize(String s) {
        return s.toUpperCase()
                .replace("-", "_")
                .replace(" ", "_");
    }
}