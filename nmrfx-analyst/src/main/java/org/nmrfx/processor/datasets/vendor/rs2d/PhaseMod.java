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

import org.nmrfx.processor.datasets.AcquisitionType;

/**
 * Possible values for PHASE_MOD parameter in RS2D data
 */
public enum PhaseMod {
    NONE(true, "ft", AcquisitionType.SEP, 1),
    STATES(true, "negate", AcquisitionType.HYPER_R, 2),
    TPPI(false, "rft", AcquisitionType.REAL, 1),
    STATES_TPPI(true, "negate", AcquisitionType.HYPER, 2),
    ECHO_ANTIECHO(true, "ft", AcquisitionType.ECHO_ANTIECHO, 2),
    QF(true, "ft", AcquisitionType.SEP, 1),
    QSEQ(false, "rft", AcquisitionType.REAL, 2);

    private final boolean complex;
    private final String ftType;
    private final AcquisitionType acquisitionType;
    private final int groupSize;

    PhaseMod(boolean complex, String ftType, AcquisitionType acquisitionType, int groupSize) {
        this.complex = complex;
        this.ftType = ftType;
        this.acquisitionType = acquisitionType;
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
        return acquisitionType.getLabel();
    }

    public double[] getCoefs() {
        return acquisitionType.getCoefficients();
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
        return switch (normalized) {
            case "COMPLEX" -> STATES;
            case "TPPI" -> TPPI;
            case "COMPLEX_TPPI" -> STATES_TPPI;
            case "PHASE_MODULATION" -> NONE; //XXX Not sure about this one, does it really exist?
            case "ECHO_ANTIECHO" -> ECHO_ANTIECHO;
            default -> NONE;
        };
    }

    private static String normalize(String s) {
        return s.toUpperCase()
                .replace("-", "_")
                .replace(" ", "_");
    }
}