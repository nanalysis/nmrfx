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
package org.nmrfx.structure.chemistry.predict;

import java.util.HashMap;
import java.util.Map;
import org.nmrfx.chemistry.Residue;

/**
 *
 * @author brucejohnson
 */
public class ResidueProperties {

    private final static Map<String, Double> SCALES = new HashMap<>();
    private static final double LIMIT = 16.0;

    static {
        SCALES.put("N", 0.627);
        SCALES.put("C", 0.310);
        SCALES.put("CB", 0.219);
        SCALES.put("CA", 0.281);
        SCALES.put("H", 0.102);
        SCALES.put("HA", 0.0566);
        SCALES.put("HB", 0.0546);
    }

    /**
     * Calculate CheZOD score as a measure of disorder based on chemical shifts
     * of the residue. Random coil values should be set up before calling this
     * method. Nielsen & Mulder Frontiers in Molecular Biosciences February 2016
     * | Volume 3 | Article 4
     *
     * @param centerResidue calculate score for this residue
     * @param ppmSet index of ppm set to get experimental shifts from
     * @param refSet index of ppm set to get random coil values from
     * @return the CheZOD score
     */
    public static double calcZIDR(Residue centerResidue,
            int ppmSet, int refSet) {
        Residue prevResidue = centerResidue.previous;
        Residue nextResidue = centerResidue.next;
        int resNum = centerResidue.getResNum();

        Residue[] residues = {prevResidue, centerResidue, nextResidue};

        int n = 0;
        double chiSq = 0.0;
        for (var residue : residues) {
            if (residue == null) {
                continue;
            }
            for (var scaleEntry : SCALES.entrySet()) {
                var atom = residue.getAtom(scaleEntry.getKey());
                if (atom != null) {
                    var ppmV = atom.getPPM(ppmSet);
                    if ((ppmV != null) && ppmV.isValid()) {
                        var refPPMV = atom.getRefPPM(refSet);
                        if ((refPPMV != null) && refPPMV.isValid()) {
                            double delta = (ppmV.getValue() - refPPMV.getValue()) / scaleEntry.getValue();
                            chiSq += Math.min(delta * delta, LIMIT);
                            n++;
                        }
                    }
                }
            }
        }
        return normChiSq(chiSq, n);
    }

    // L. Canal/Computational Statistics & Data Analysis 48 (2005) 803–808
    /**
     * Transform a chi-squared distributed number to a normally distributed
     * number using the method of L. Canal, Computational Statistics & Data
     * Analysis 48 (2005) 803–808
     *
     * @param chiSq the chiSq value to transform
     * @param n the number of observations
     * @return a score that is approximately normally distributed.
     */
    public static double normChiSq(double chiSq, int n) {
        var p = chiSq / n;
        var lNorm = Math.pow(p, 1.0 / 6.0) - 0.5 * Math.pow(p, 1.0 / 3.0) + 1.0 / 3.0 * Math.sqrt(p);
        var expValue = 5.0 / 6.0 - 1.0 / (9 * n) - 7.0 / (648 * n * n) + 25.0 / (2187 * Math.pow(n, 3));  // equation 1
        var sdev = Math.sqrt(1.0 / (18.0 * n) + 1.0 / (162.0 * n * n) - 37.0 / (11664.0 * Math.pow(n, 3)));  // equation 2

        var normalized = (lNorm - expValue) / sdev;

        return normalized;
    }

}
