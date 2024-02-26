/*
 * NMRFx Structure : A Program for Calculating Structures
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
package org.nmrfx.structure.chemistry.energy;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.chemistry.constraints.AngleConstraint;
import org.nmrfx.structure.chemistry.Molecule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class RNARotamer {

    private static final Logger log = LoggerFactory.getLogger(RNARotamer.class);

    public static String SUITE_FORMAT = " %6.1f %5.1f";
    private static Atom[][] Atom;

    final double[] angles;
    final double[] sdev;
    final String name;
    final String suiteName;
    final String deltaDeltaGamma;
    final int nSamples;
    double fraction = 0.0;
    static double toRAD = Math.PI / 180.0;
    static double toDEG = 180.0 / Math.PI;
    static final double WIDTH = Math.PI / 10;
    public static double HPOWER = 3.0;
    static final int[] subsetIndices = {1, 2, 3, 4};
    static final int[] indices = {0, 1, 2, 3, 4, 5, 6};
    static Atom[] atoms = {null, null, null, null, null, null, null};
    static final int NPREVIOUS = 1;

    static final String[] DELTAP_ATOMS = {"-1:C5'", "-1:C4'", "-1:C3'", "-1:O3'"};
    static final String[] EPSILON_ATOMS = {"-1:C4'", "-1:C3'", "-1:O3'", "P"};
    static final String[] ZETA_ATOMS = {"-1:C3'", "-1:O3'", "P", "O5'"};
    static final String[] ALPHA_ATOMS = {"-1:O3'", "P", "O5'", "C5'"};
    static final String[] BETA_ATOMS = {"P", "O5'", "C5'", "C4'"};
    static final String[] GAMMA_ATOMS = {"O5'", "C5'", "C4'", "C3'"};
    static final String[] DELTA_ATOMS = {"C5'", "C4'", "C3'", "O3'"};
    static final String[][] suiteAtoms = {DELTAP_ATOMS, EPSILON_ATOMS, ZETA_ATOMS, ALPHA_ATOMS, BETA_ATOMS, GAMMA_ATOMS, DELTA_ATOMS};
    static final double[] sdevs = new double[suiteAtoms.length];
    static final double[] halfWidths = new double[suiteAtoms.length];

    static final LinkedHashMap<String, RNARotamer> ROTAMERS = new LinkedHashMap<>();

    static final String[] PRESET_ATOMS = {"P", "O5'", "C5'", "C4'", "C3'", "O3'"};

    // d-1, e-1,z-1,a,b,g,d
    static {
        ROTAMERS.put("1a", new RNARotamer("1a", 4637, 81, 4, -148, 10, -71, 7, -65, 8, 174, 8, 54, 6, 81, 3));
        ROTAMERS.put("1m", new RNARotamer("1m", 15, 84, 5, -142, 16, -68, 15, -68, 16, -138, 12, 58, 10, 86, 7));
        ROTAMERS.put("1L", new RNARotamer("1L", 14, 86, 4, -115, 6, -92, 13, -56, 8, 138, 4, 62, 10, 79, 5));
        ROTAMERS.put("&a", new RNARotamer("&a", 33, 82, 5, -169, 7, -95, 6, -64, 9, -178, 10, 51, 7, 82, 5));
        ROTAMERS.put("7a", new RNARotamer("7a", 36, 83, 4, -143, 23, -138, 14, -57, 9, 161, 15, 49, 6, 82, 3));
        ROTAMERS.put("3a", new RNARotamer("3a", 25, 85, 4, -144, 24, 173, 14, -71, 12, 164, 16, 46, 7, 85, 6));
        ROTAMERS.put("9a", new RNARotamer("9a", 19, 83, 2, -150, 15, 121, 13, -71, 12, 157, 23, 49, 6, 81, 3));
        ROTAMERS.put("1g", new RNARotamer("1g", 78, 81, 3, -141, 8, -69, 9, 167, 8, 160, 16, 51, 5, 85, 3));
        ROTAMERS.put("7d", new RNARotamer("7d", 16, 84, 4, -121, 16, -103, 12, 70, 10, 170, 23, 53, 6, 85, 3));
        ROTAMERS.put("3d", new RNARotamer("3d", 20, 85, 4, -116, 15, -156, 15, 66, 19, -179, 23, 55, 6, 86, 4));
        ROTAMERS.put("5d", new RNARotamer("5d", 14, 80, 4, -158, 7, 63, 14, 68, 12, 143, 30, 50, 7, 83, 2));
        ROTAMERS.put("1e", new RNARotamer("1e", 42, 81, 3, -159, 8, -79, 6, -111, 9, 83, 11, 168, 6, 86, 4));
        ROTAMERS.put("1c", new RNARotamer("1c", 275, 80, 3, -163, 9, -69, 10, 153, 12, -166, 12, 179, 10, 84, 3));
        ROTAMERS.put("1f", new RNARotamer("1f", 20, 81, 2, -157, 14, -66, 11, 172, 11, 139, 13, 176, 10, 84, 3));
        ROTAMERS.put("5j", new RNARotamer("5j", 12, 87, 7, -136, 23, 80, 15, 67, 9, 109, 10, 176, 6, 84, 4));
        ROTAMERS.put("1b", new RNARotamer("1b", 168, 84, 4, -145, 10, -71, 10, -60, 9, 177, 12, 58, 7, 145, 7));
        ROTAMERS.put("1[", new RNARotamer("1[", 52, 83, 4, -140, 10, -71, 10, -63, 8, -138, 9, 54, 7, 144, 8));
        ROTAMERS.put("3b", new RNARotamer("3b", 14, 85, 3, -134, 18, 168, 17, -67, 15, 178, 22, 49, 5, 148, 3));
        ROTAMERS.put("1z", new RNARotamer("1z", 12, 83, 3, -154, 18, -82, 19, -164, 14, 162, 25, 51, 5, 145, 5));
        ROTAMERS.put("5z", new RNARotamer("5z", 42, 83, 3, -154, 5, 53, 7, 164, 5, 148, 10, 50, 5, 148, 4));
        ROTAMERS.put("7p", new RNARotamer("7p", 27, 84, 3, -123, 24, -140, 15, 68, 12, -160, 30, 54, 7, 146, 6));
        ROTAMERS.put("1t", new RNARotamer("1t", 7, 81, 3, -161, 20, -71, 8, 180, 17, -165, 14, 178, 9, 147, 5));
        ROTAMERS.put("5q", new RNARotamer("5q", 6, 82, 8, -155, 6, 69, 14, 63, 9, 115, 17, 176, 6, 146, 4));
        ROTAMERS.put("1o", new RNARotamer("1o", 13, 84, 4, -143, 17, -73, 15, -63, 7, -135, 39, -66, 7, 151, 13));
        ROTAMERS.put("7r", new RNARotamer("7r", 16, 85, 4, -127, 13, -112, 19, 63, 13, -178, 27, -64, 4, 150, 7));
        ROTAMERS.put("2a", new RNARotamer("2a", 126, 145, 8, -100, 12, -71, 18, -72, 13, -167, 17, 53, 7, 84, 5));
        ROTAMERS.put("4a", new RNARotamer("4a", 12, 146, 7, -100, 15, 170, 14, -62, 19, 170, 34, 51, 8, 84, 5));
        ROTAMERS.put("0a", new RNARotamer("0a", 29, 149, 7, -137, 11, 139, 25, -75, 11, 158, 20, 48, 6, 84, 4));
        ROTAMERS.put("#a", new RNARotamer("#a", 16, 148, 3, -168, 5, 146, 6, -71, 7, 151, 12, 42, 4, 85, 3));
        ROTAMERS.put("4g", new RNARotamer("4g", 18, 148, 8, -103, 14, 165, 21, -155, 14, 165, 15, 49, 7, 83, 4));
        ROTAMERS.put("6g", new RNARotamer("6g", 16, 145, 7, -97, 18, 80, 16, -156, 29, -170, 23, 58, 5, 85, 7));
        ROTAMERS.put("8d", new RNARotamer("8d", 24, 149, 6, -89, 10, -119, 17, 62, 10, 176, 23, 54, 4, 87, 3));
        ROTAMERS.put("4d", new RNARotamer("4d", 9, 150, 6, -110, 26, -172, 7, 80, 20, -162, 20, 61, 8, 89, 4));
        ROTAMERS.put("6d", new RNARotamer("6d", 18, 147, 6, -119, 23, 89, 16, 59, 14, 161, 23, 52, 7, 83, 4));
        ROTAMERS.put("2h", new RNARotamer("2h", 17, 148, 4, -99, 8, -70, 12, -64, 10, 177, 17, 176, 14, 87, 4));
        ROTAMERS.put("4n", new RNARotamer("4n", 9, 144, 7, -133, 14, -156, 14, 74, 12, -143, 20, -166, 9, 81, 3));
        ROTAMERS.put("0i", new RNARotamer("0i", 6, 149, 2, -85, 20, 100, 13, 81, 11, -112, 12, -178, 3, 83, 2));
        ROTAMERS.put("6n", new RNARotamer("6n", 18, 150, 6, -92, 11, 85, 8, 64, 5, -169, 8, 177, 9, 86, 5));
        ROTAMERS.put("6j", new RNARotamer("6j", 9, 142, 8, -116, 28, 66, 15, 72, 8, 122, 22, -178, 6, 84, 3));
        ROTAMERS.put("2[", new RNARotamer("2[", 40, 146, 8, -101, 16, -69, 17, -68, 12, -150, 21, 54, 7, 148, 7));
        ROTAMERS.put("4b", new RNARotamer("4b", 27, 145, 7, -115, 20, 163, 13, -66, 6, 172, 14, 46, 6, 146, 6));
        ROTAMERS.put("0b", new RNARotamer("0b", 14, 148, 4, -112, 20, 112, 14, -85, 17, 165, 16, 57, 12, 146, 6));
        ROTAMERS.put("4p", new RNARotamer("4p", 13, 150, 10, -100, 16, -146, 19, 72, 13, -152, 27, 57, 14, 148, 4));
        ROTAMERS.put("6p", new RNARotamer("6p", 39, 146, 7, -102, 21, 90, 15, 68, 12, 173, 18, 56, 8, 148, 4));
        ROTAMERS.put("4s", new RNARotamer("4s", 8, 150, 2, -112, 16, 170, 12, -82, 13, 84, 7, 176, 6, 148, 2));
        ROTAMERS.put("2o", new RNARotamer("2o", 12, 147, 6, -104, 15, -64, 16, -73, 4, -165, 26, -66, 7, 150, 3));
        countSamples();
    }

    static RNARotamer OUTLIER = new RNARotamer("--");

    public static class RotamerScore {

        RNARotamer rotamer;
        double score;
        double prob;
        double[] angles;
        String message;
        double[] normDeltas;
        double[] deltas;

        public RotamerScore(RNARotamer rotamer, double score, double prob, double[] angles) {
            this(rotamer, score, prob, angles, "");
        }

        public RotamerScore(RNARotamer rotamer, double score, double prob, double[] angles, String message) {
            this.rotamer = rotamer;
            this.score = score;
            this.angles = angles;
            this.message = message;
            this.prob = prob;
            if (rotamer.angles != null) {
                normDeltas = new double[7];
                deltas = new double[7];
                calcNormDeltas();
            }
        }

        private void calcNormDeltas() {
            if (angles != null) {
                for (int i = 0; i < 7; i++) {
                    double delta = angles[i] - rotamer.angles[i];

                    if (delta > Math.PI) {
                        delta = -(2.0 * Math.PI - delta);
                    }
                    if (delta < -Math.PI) {
                        delta = 2.0 * Math.PI + delta;
                    }
                    normDeltas[i] = delta / rotamer.sdev[i];
                    deltas[i] = delta;
                }
            }
        }

        public String getName() {
            return rotamer.name;
        }

        public double getScore() {
            return score;
        }

        public double getProb() {
            return prob;
        }

        public double getSuiteness() {
            double suiteness = (Math.cos(Math.PI * Math.min(score, 1.0)) + 1.0) / 2.0;
            if (suiteness < 0.01) {
                if (rotamer == OUTLIER) {
                    suiteness = 0.0;
                } else {
                    suiteness = 0.01;
                }
            }
            return suiteness;
        }

        @Override
        public String toString() {
            String result = String.format("%2s %20s %8.6f %.3f %.2f %50s %6s", rotamer.name, rotamer.suiteName, prob, score, getSuiteness(), RNARotamer.formatAngles(angles), message);
            return result;
        }

        public String report() {
            String pucker2 = getPucker(Math.toDegrees(angles[6]));

            String result = String.format("%2s %4.2f %2s %92s", rotamer.name, getSuiteness(), pucker2, formatAngles(angles, deltas));
            return result;
        }

    }

    public RNARotamer(String name) {
        this.name = name;
        angles = null;
        sdev = null;
        suiteName = "----";
        deltaDeltaGamma = "";
        nSamples = 0;

    }

    public RNARotamer(String name, int nSamples, double... angles) {
        this.name = name;
        if (angles.length != 14) {
            throw new IllegalArgumentException("Must specify 7 angles,sdevs");
        }
        this.angles = new double[angles.length / 2];
        this.sdev = new double[angles.length / 2];
        for (int i = 0; i < (angles.length / 2); i++) {
            this.angles[i] = angles[i * 2] * toRAD;
            this.sdev[i] = angles[i * 2 + 1] * toRAD;
        }
        this.suiteName = makeSuiteName(this.angles);
        this.deltaDeltaGamma = getDeltaDeltaGamma(this.angles);
        this.nSamples = nSamples;
    }

    static void countSamples() {
        int MAXCOUNT = 150;
        HashMap<String, Integer> counts = new HashMap<>();
        for (RNARotamer rotamer : ROTAMERS.values()) {
            if (!counts.containsKey(rotamer.deltaDeltaGamma)) {
                counts.put(rotamer.deltaDeltaGamma, 0);
            }
            Integer count = counts.get(rotamer.deltaDeltaGamma);
            int sCount = rotamer.nSamples;
            if (sCount > MAXCOUNT) {
                sCount = MAXCOUNT;
            }
            count += sCount;
            counts.put(rotamer.deltaDeltaGamma, count);
        }
        for (RNARotamer rotamer : ROTAMERS.values()) {
            Integer count = counts.get(rotamer.deltaDeltaGamma);
            int sCount = rotamer.nSamples;
            if (sCount > MAXCOUNT) {
                sCount = MAXCOUNT;
            }
            double frac = ((double) sCount) / count;
            rotamer.fraction = frac;
        }
        double[] sumSdev = new double[suiteAtoms.length];
        for (RNARotamer rotamer : ROTAMERS.values()) {
            for (int i = 0; i < sumSdev.length; i++) {
                sumSdev[i] += rotamer.sdev[i];
            }

        }
        for (int i = 0; i < sumSdev.length; i++) {
            sdevs[i] = sumSdev[i] / ROTAMERS.size();
            halfWidths[i] = sdevs[i] * 3.0 + 15.0 * toRAD;
        }

    }

    public static String getPucker(double angleDegrees) {
        final String pucker;
        if (Math.abs(angleDegrees - 84.0) < 10.0) {
            pucker = "3'";
        } else if (Math.abs(angleDegrees - 147.0) < 10.0) {
            pucker = "2'";
        } else {
            pucker = "?";

        }
        return pucker;
    }

    public final String makeSuiteName(double[] angles) {
        StringBuilder builder = new StringBuilder();
        // m, p, t convention of Lovell et al. (20) for values near 􏰋60°, 􏰏60°, or 180°;
        for (int i = 0; i < 7; i++) {
            double testAngle = angles[i] * toDEG;
            if ((i == 0) || (i == 6)) {
                builder.append(getPucker(testAngle));
            } else if (i == 1) {
                if (Math.abs(testAngle + 130.0) < 41.0) {
                    builder.append("e");
                } else {
                    builder.append("?");
                }
            } else {

                double angle = -60;
                String[] types = {"m", "p", "t"};
                String type = "";
                for (int j = 0; j < 3; j++) {
                    double delta = Math.abs(testAngle - angle);
                    if (delta > 180.0) {
                        delta = 360.0 - delta;
                    }
                    if (delta < 40.0) {
                        type = types[j];
                    }
                    angle += 120.0;
                }
                if (type.equals("")) {
                    type = String.format("%.0f", testAngle);
                }
                builder.append(type);
            }
            builder.append(" ");

        }

        return builder.toString().trim();
    }

    static String getMPT(double angle) {
        double testAngle = angle * toDEG;
        String name = "";
        if ((testAngle >= 20.0) && (testAngle <= 95.0)) {
            name = "p";
        } else if ((testAngle >= 140.0) && (testAngle <= 180.0)) {
            name = "t";
        } else if ((testAngle >= -180.0) && (testAngle <= -145.0)) {
            name = "t";
        } else if ((testAngle >= -100.0) && (testAngle <= -25.0)) {
            name = "m";
        }
        return name;
    }

    static String getDelta(double angle) {
        double testAngle = angle * toDEG;
        String name = "";
        if ((testAngle >= 55.0) && (testAngle <= 110)) {
            name = "3";
        } else if ((testAngle >= 120.0) && (testAngle <= 175)) {
            name = "2";
        }
        return name;
    }

    public static String getDeltaDeltaGamma(double[] angles) {
        StringBuilder builder = new StringBuilder();
        String[] results = new String[3];
        results[0] = getDelta(angles[0]);
        results[1] = getDelta(angles[6]);
        results[2] = getMPT(angles[5]);
        boolean ok = true;
        for (int i = 0; i < 3; i++) {
            if (results[i].equals("")) {
                ok = false;
            }
            builder.append(results[i]);
        }
        if (ok) {
            return builder.toString();
        } else {
            return "-" + builder.toString();
        }
    }

    public static int triage(double[] angles) {
        int result = -1;
        // epsilon
        double[][] ranges = {
                {-50.0, 155.0}, // epsilon
                {-25.0, 25.0}, // zeta
                {-25.0, 25.0}, //alpha
                {-70.0, 50.0} // beta
        };
        for (int i = 1; i < 5; i++) {
            double testAngle = angles[i] * toDEG;
            if ((testAngle >= ranges[i - 1][0]) && (testAngle <= ranges[i - 1][1])) {
                result = i;
                break;
            }
        }

        return result;
    }

    public static String formatAngles(double[] angles) {
        StringBuilder builder = new StringBuilder();
        for (double angle : angles) {
            builder.append(String.format(" %6.1f", Math.toDegrees(angle)));
        }
        return builder.toString();
    }

    public static String formatAngles(double[] angles, double[] deltas) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < angles.length; i++) {
            var angle = angles[i];
            double delta = 0.0;
            if (deltas != null) {
                delta = deltas[i];
            }
            builder.append(String.format(SUITE_FORMAT, Math.toDegrees(angle), Math.toDegrees(delta)));
        }
        return builder.toString();
    }

    public static void clear() {
        ROTAMERS.clear();
    }

    public static void add(String name, int n, double... angles) {
        ROTAMERS.put(name, new RNARotamer(name, n, angles));
    }

    public static RotamerScore[] getNBest(Polymer polymer, int residueNum, int n) {
        return getNBest(polymer, residueNum, n, null);
    }

    public static RotamerScore[] getNBest(Polymer polymer, int residueNum, int n, EnergyCoords ec) {
        Residue residue = polymer.getResidue(residueNum);
        return getNBest(residue, n, ec);
    }

    public static RotamerScore[] getNBest(Residue residue, int n, EnergyCoords ec) {
        /* getNBest finds n of the best rotamer confirmations and returns a 
           list of rotamer scores containing the type of rotamer and the 
           probability. The function takes the polymer and a residue number.
         */

        double[] testAngles = RNARotamer.getDihedrals(residue, ec);
        if (testAngles == null) {
            return new RotamerScore[0];
        }
        List<RotamerScore> rotamerScores = new ArrayList<>();
        for (RNARotamer rotamer : ROTAMERS.values()) {
            double probability = rotamer.probability(testAngles, new int[]{0, 1, 2, 3, 4, 5, 6}, rotamer.fraction);
            RotamerScore rotScore = new RotamerScore(rotamer, 0.0, probability, testAngles, null);
            rotamerScores.add(rotScore);
        }
        rotamerScores = rotamerScores.stream().filter(rScore -> (rScore.getProb() > 1.0e-16)).sorted(Comparator.comparingDouble(RotamerScore::getProb).reversed()).limit(n).collect(Collectors.toList());

        // The commented out code may be a bit faster but less legible. 
//        for (RNARotamer rotamer : ROTAMERS.values()) {
//            double probability = rotamer.probability(testAngles, new int[]{1, 2, 3, 4, 5, 6}, rotamer.fraction);
//            // If the last element in the list is null or if the last elements probability is less than the calculated probability
        // the bestScores array should be edited. If not, we should just continue.
//            if (!(bestScores[n - 1] == null || probability > bestScores[n - 1].prob)) {
//                continue;
//            }
//            RotamerScore rotScore = new RotamerScore(rotamer, 0.0, probability, testAngles, null);
//            for (int i = 0; i < n; i++) {
//                Double storedProb = bestScores[i] == null ? null : bestScores[i].prob;
//                if (storedProb == null) {
//                    bestScores[i] = rotScore;
//                    break;
//                } else if (probability > storedProb) {
//                    for (int j = n - 1; j > i; j--) {
//                        bestScores[j] = bestScores[j - 1];
//                    }
//                    bestScores[i] = rotScore;
//                    break;
//                }
//            }
//        }
        RotamerScore[] bestScores = new RotamerScore[rotamerScores.size()];
        bestScores = rotamerScores.toArray(bestScores);
        return bestScores;
    }

    public static RotamerScore getBest(Polymer polymer, int residueNum, EnergyCoords ec) {
        Residue residue = polymer.getResidue(residueNum);
        return getBest(residue, ec);

    }

    public static RotamerScore getBest(Residue residue, EnergyCoords ec) {
        /* getNBest finds n of the best rotamer confirmations and returns a 
           list of rotamer scores containing the type of rotamer and the 
           probability. The function takes the polymer and a residue number.
         */

        double[] testAngles = RNARotamer.getDihedrals(residue, ec);
        if (testAngles == null) {
            return null;
        }
        RotamerScore bestRotamer = null;
        double best = 0.0;
        for (RNARotamer rotamer : ROTAMERS.values()) {
            double probability = rotamer.probability(testAngles, new int[]{0, 1, 2, 3, 4, 5, 6}, rotamer.fraction);
            if (probability > best) {
                bestRotamer = new RotamerScore(rotamer, 0.0, probability, testAngles, null);
                best = probability;
            }

        }
        return bestRotamer;
    }

    public static double calcEnergy(Polymer polymer, int residueNum) {
        /* calcRotamerEnergy calculates the rotamer energy of index residueNum.
           This defaults to using  three possible rotamer configureations.
         */
        return calcEnergy(polymer, residueNum, 3);
    }

    public static double calcEnergy(Polymer polymer, int residueNum, int n) {
        /* This calcRotamerEnergy can use n possible rotamer configurations.*/
        RotamerScore[] scores = getNBest(polymer, residueNum, n);
        return calcEnergy(scores);
    }

    public static double calcEnergy(RotamerScore[] scores) {
        /* calcRotamerEnergy takes a list of RotamerScore objects and computes
           an energy based on the probabilities stored in each RotamerScore 
           object. 
         */
        double totalProb = 0;
        for (RotamerScore score : scores) {
            if (score == null) {
                continue;
            }
            score.calcNormDeltas();
            double prob = score.prob;
            if (prob < 10e-200) {
                System.out.println("probability changed");
                prob = 10e-200;
                score.prob = prob;
            }
            totalProb += score.prob;

        }
        return -Math.log(totalProb);
    }

    public static Map<Integer, Double> calcDerivs(RotamerScore[] scores, double rotEnergy) {
        int i = 0;
        Map<Integer, Double> derivMap = new HashMap<>();
        double eRotEnergy = Math.exp(rotEnergy);
        for (i = 0; i < 7; i++) {
            double sum = 0;
            for (int j = 0; j < scores.length; j++) {
                sum += (scores[j].prob * scores[j].normDeltas[i] * (1.0 / scores[j].rotamer.sdev[i]));
            }
            double deriv = eRotEnergy * sum;
            if ((i == 0) || (i == 6)) {
                deriv /= 2;  // avoid double counting delta
            }
            int angleIndex = atoms[i].aAtom;
            derivMap.put(angleIndex, deriv);
        }

        return derivMap;
    }

    public double score(double[] testAngles, int[] indices, double[] halfWidths) {
        if (testAngles.length != angles.length) {
            throw new IllegalArgumentException("Must specify " + angles.length + " angles");
        }
        double sum = 0.0;
        for (int index : indices) {
            double delta = Math.abs(testAngles[index] - angles[index]);
            if (delta > Math.PI) {
                delta = 2.0 * Math.PI - delta;
            }
            delta /= halfWidths[index];
            sum += Math.pow(delta, HPOWER);
        }
        double result = Math.pow(sum, 1.0 / HPOWER);
        return result;
    }

    public double probability(double[] testAngles, int[] indices, double prior) {
        if (testAngles.length != angles.length) {
            throw new IllegalArgumentException("Must specify " + angles.length + " angles");
        }
        double totalProb = prior;
        for (int index : indices) {
            double delta = Math.abs(testAngles[index] - angles[index]);
            if (delta > Math.PI) {
                delta = 2.0 * Math.PI - delta;
            }
            double sdevValue = sdevs[index];
            double p = (1.0 / (sdevValue * Math.sqrt(2.0 * Math.PI))) * Math.exp(-(delta * delta) / (2.0 * sdevValue * sdevValue)) + 1.0e-3;
            totalProb *= p;
        }
        if (totalProb < 1.0e-15) {
            totalProb = 1.0e-15;
        }
        if (totalProb > 1000.0) {
            totalProb = 1000.0;
        }
        return totalProb;
    }

    public static RotamerScore getBest(double[] angles) {
        ArrayList<RotamerScore> hits = getHits(angles);
        double highestProb = 0.0;
        RotamerScore bestRotamerScore = null;
        for (RotamerScore rScore : hits) {
            if (rScore.prob > highestProb) {
                highestProb = rScore.prob;
                bestRotamerScore = rScore;

            }
        }
        return bestRotamerScore;
    }

    public static ArrayList<RotamerScore> getHits(double[] angles) {
        double best = Double.MAX_VALUE;
        RNARotamer bestRotamer = null;
        ArrayList<RotamerScore> hits = new ArrayList<>();
        for (RNARotamer rotamer : ROTAMERS.values()) {
            double score = rotamer.score(angles, indices, halfWidths);
            if (score < 1.0) {
                double prior = rotamer.fraction;
                double prob = rotamer.probability(angles, subsetIndices, prior);
                RotamerScore rotScore = new RotamerScore(rotamer, score, prob, angles);
                hits.add(rotScore);
            }
            if (score < best) {
                best = score;
                bestRotamer = rotamer;
            }
        }
        return hits;
    }

    public static RotamerScore bestProb(double[] angles) {
        double best = 0.0;
        RNARotamer bestRotamer = null;
        String ddG = getDeltaDeltaGamma(angles);
        if (ddG.startsWith("-")) {
            return new RotamerScore(OUTLIER, 10.0, 0.0, angles, ddG);
        }
        int triageResult = triage(angles);
        if (triageResult > -1) {
            return new RotamerScore(OUTLIER, 10.0, 0.0, angles, "" + triageResult);
        }
        ArrayList<RNARotamer> group = new ArrayList<>();
        for (RNARotamer rotamer : ROTAMERS.values()) {
            if (rotamer.deltaDeltaGamma.equals(ddG)) {
                group.add(rotamer);
            }

        }
        double bestScore = 1.0;
        for (RNARotamer rotamer : group) {
            double score = rotamer.score(angles, indices, halfWidths);
            if (score < 1.0) {
                double prior = rotamer.fraction;
                double prob = rotamer.probability(angles, subsetIndices, prior);
                if (prob > best) {
                    best = prob;
                    bestRotamer = rotamer;
                    bestScore = score;
                }
            }
        }
        if (bestRotamer == null) {
            return new RotamerScore(OUTLIER, bestScore, 0.0, angles, "score");
        }

        return new RotamerScore(bestRotamer, bestScore, best, angles);
    }

    public static double[] getDihedrals(Polymer polymer, int residueNum) {
        return getDihedrals(polymer, residueNum, null);
    }

    public static double[] getDihedrals(Polymer polymer, int residueNum, EnergyCoords ec) {
        Residue residue = polymer.getResidue(residueNum);
        return getDihedrals(residue, ec);
    }

    public static double[] getDihedrals(Residue residue, EnergyCoords ec) {
        double[] angles = new double[suiteAtoms.length];
        if (residue.previous != null) {
            int i = 0;
            for (String[] atomNames : suiteAtoms) {
                Atom[] angleAtoms = new Atom[4];
                int j = 0;
                for (String aName : atomNames) {
                    int colonPos = aName.indexOf(':');
                    int delta = 0;
                    if (colonPos != -1) {
                        String deltaRes = aName.substring(0, colonPos);
                        delta = Integer.valueOf(deltaRes);
                        aName = aName.substring(colonPos + 1);
                    }
                    Atom atom = delta == -1 ? residue.previous.getAtom(aName)
                            : residue.getAtom(aName);
                    if (atom == null) {
                        return null;
                    }
                    angleAtoms[j] = atom;
                    if (j == 2) {
                        if (!atom.rotActive) {
                            return null;
                        }
                        atoms[i] = atom;
                    }
                    j++;
                }
                if (ec == null) {
                    for (Atom atom : angleAtoms) {
                        if (atom.getPoint() == null) {
                            return null;
                        }
                    }
                    angles[i] = AtomMath.calcDihedral(angleAtoms[0].getPoint(), angleAtoms[1].getPoint(), angleAtoms[2].getPoint(), angleAtoms[3].getPoint());
                } else {
                    angles[i] = ec.calcDihedral(angleAtoms[0].eAtom, angleAtoms[1].eAtom, angleAtoms[2].eAtom, angleAtoms[3].eAtom);
                }
                if (angles[i] < -Math.PI) {
                    angles[i] = 2.0 * Math.PI + angles[i];
                } else if (angles[i] > Math.PI) {
                    angles[i] = angles[i] - 2.0 * Math.PI;
                }
                i++;
            }
        }
        return angles;
    }

    public static void setDihedrals(Residue residue, String suiteName, boolean doFreeze) {
        setDihedrals(residue, suiteName, 0.0, doFreeze);
    }

    /**
     * Set the dihedral angles for the specified residue based on angles for the
     * specified suite.
     *
     * @param residue   Set angles in this residue
     * @param suiteName Get the angles from the suite with this name
     */
    public static void setDihedrals(Residue residue, String suiteName) {
        setDihedrals(residue, suiteName, 0.0, false);
    }

    /**
     * Set the dihedral angles for the specified residue based on angles for the
     * specified suite.
     *
     * @param residue   Set angles in this residue
     * @param suiteName Get the angles from the suite with this name
     * @param sdev      Vary the angle from the suite value by a random number chosen
     *                  from a Gaussian distribution with a standard deviation of sdev
     * @param doFreeze  If true, freeze the angle so it is not adjusted during
     *                  refinement
     */
    public static void setDihedrals(Residue residue, String suiteName, double sdev, boolean doFreeze) {
        if (residue == null) {
            throw new IllegalArgumentException("Residue is null. Unable to set Dihedrals.");
        }
        RNARotamer rotamer = ROTAMERS.get(suiteName);
        int j = 0;
        sdev = Math.toRadians(sdev);
        for (String[] atomNames : suiteAtoms) {
            if (j == 0) {
                j++;
                continue;
            }
            String aName = atomNames[3];
            int colonPos = aName.indexOf(':');
            int delta = 0;
            if (colonPos != -1) {
                String deltaRes = aName.substring(0, colonPos);
                delta = Integer.valueOf(deltaRes);
                aName = aName.substring(colonPos + 1);
            }
            Residue applyResidue = delta < 0 ? residue.previous : residue;
            if (applyResidue != null) {
                Atom atom = applyResidue.getAtom(aName);
                if (atom != null) {
                    double angle = rotamer.angles[j];
                    if (sdev > 1.0e-5) {
                        angle += CmaesRefinement.DEFAULT_RANDOMGENERATOR.nextGaussian() * sdev;
                    }
                    System.out.println(atom.getFullName() + " " + Math.toDegrees(angle));
                    atom.setDihedral(Math.toDegrees(angle));
                    if (doFreeze) {
                        atom.parent.setRotActive(false);
                    }
                }
            }
            j++;
        }
        residue.molecule.genCoords(false);
    }

    /**
     * Set the dihedral angles for the specified residue based on angles for the
     * specified suite.
     *
     * @param residue  Set angles in this residue
     * @param angles   Am array of angle values to set. The angles are set on the
     *                 atoms named in the PRESET_ATOMS array.
     * @param sdev     Vary the angle from the suite value by a random number chosen
     *                 from a Gaussian distribution with a standard deviation of sdev
     * @param doFreeze If true, freeze the angle so it is not adjusted during
     *                 refinement
     */
    public static void setDihedrals(Residue residue, double[] angles, double sdev, boolean doFreeze) {
        setDihedrals(residue, PRESET_ATOMS, angles, sdev, doFreeze);
    }

    /**
     * Set the dihedral angles for the specified residue based on angles for the
     * specified suite.
     *
     * @param residue   Set angles in this residue
     * @param atomNames The atoms whose angles are set. This specifies the name
     *                  of the daughter atom of the rotatable bond. That is, the atom name here
     *                  is atom D, for the torsion A-B-C-D (rotation around bond B-C)
     * @param angles    Am array of angle values to set. The angles are set on the
     *                  atoms named in atomNames.
     * @param sdev      Vary the angle from the suite value by a random number chosen
     *                  from a Gaussian distribution with a standard deviation of sdev
     * @param doFreeze  If true, freeze the angle so it is not adjusted during
     *                  refinement
     */
    public static void setDihedrals(Residue residue, String[] atomNames, double[] angles, double sdev, boolean doFreeze) {
        int j = 0;
        sdev = Math.toRadians(sdev);
        for (String atomName : atomNames) {
            int colonPos = atomName.indexOf(':');
            int delta = 0;
            if (colonPos != -1) {
                String deltaRes = atomName.substring(0, colonPos);
                delta = Integer.valueOf(deltaRes);
                atomName = atomName.substring(colonPos + 1);
            }
            Residue applyResidue = delta < 0 ? residue.previous : residue;
            if (applyResidue != null) {
                Atom atom = applyResidue.getAtom(atomName);
                if (atom != null) {
                    double angle = Math.toRadians(angles[j]); // Converted to radians
                    if (sdev != 0.0) {
                        angle += CmaesRefinement.DEFAULT_RANDOMGENERATOR.nextGaussian() * sdev;
                    }
                    atom.setDihedral(Math.toDegrees(angle));
                    if (doFreeze) {
                        atom.parent.setRotActive(false);
                    }
                }
            }
            j++;
        }
        residue.molecule.genCoords(false);
    }

    /**
     * Set the dihedral angles for the specified residue based on angles for the
     * specified suite.
     *
     * @param residue  Set angles in this residue
     * @param angleMap A map with atomNames as key and angles (in degrees) as
     *                 values. The atomNames (unlike the other setDihedrals methods) specify
     *                 atom C, for the torsion A-B-C-D (rotation around bond B-C).
     * @param sdev     Vary the angle from the suite value by a random number chosen
     *                 from a Gaussian distribution with a standard deviation of sdev
     * @param doFreeze If true, freeze the angle so it is not adjusted during
     *                 refinement
     */
    public static void setDihedrals(Residue residue, Map<String, Double> angleMap, double sdev, boolean doFreeze) {
        int j = 0;
        for (String atomKey : angleMap.keySet()) {
            String atomName = atomKey;
            int colonPos = atomName.indexOf(':');
            int delta = 0;
            if (colonPos != -1) {
                String deltaRes = atomName.substring(0, colonPos);
                delta = Integer.valueOf(deltaRes);
                atomName = atomName.substring(colonPos + 1);
            }
            Residue applyResidue = delta < 0 ? residue.previous : residue;
            if (applyResidue != null) {
                Atom atom = applyResidue.getAtom(atomName);
                if (atom != null) {
                    Atom daughterAtom = atom.daughterAtom;
                    if (daughterAtom != null) {
                        if (angleMap.containsKey(atomKey)) {
                            double angleDeg = angleMap.get(atomKey);
                            if (sdev > 1.0e-5) {
                                angleDeg += CmaesRefinement.DEFAULT_RANDOMGENERATOR.nextGaussian() * sdev;
                            }
                            daughterAtom.setDihedral(angleDeg);
                            if (doFreeze) {
                                atom.setRotActive(false);
                            }
                        } else {
                            System.out.println("No atom " + atomName + " " + applyResidue.getName() + " " + angleMap.toString());

                        }
                    } else {
                        System.out.println(residue.toString() + " no daugh " + atomName);
                    }
                } else {
                    System.out.println(residue.toString() + " no " + atomName);
                }
            }
            j++;
        }
        residue.molecule.genCoords(false);
    }

    //O3'     P       O5'     C5'     C4'     C3'     O3'
// d-1     e-1     z-1     a       b       g       d
    public static RotamerScore scoreResidue(Polymer polymer, int residueNum) {
        Residue residue = polymer.getResidue(residueNum);
        return scoreResidue(residue);
    }

    public static RotamerScore scoreResidue(Residue residue) {
        RotamerScore rotamerScore = null;
        if (residue.previous != null) {
            double[] angles = getDihedrals(residue, null);
            if (angles != null) {
                rotamerScore = bestProb(angles);
            }
        }
        return rotamerScore;
    }

    public static ArrayList<AngleConstraint> getAngleBoundaries(Polymer polymer, String residueNum, String rotamerName, double mul) throws IllegalArgumentException, InvalidMoleculeException {
        RNARotamer rotamer = ROTAMERS.get(rotamerName);
        if (rotamer == null) {
            throw new IllegalArgumentException("Invalid rotamer name \"" + rotamerName + "\"");
        }
        ArrayList<AngleConstraint> boundaries = new ArrayList<>();
        int i = 0;
        for (String[] atomNames : suiteAtoms) {
            if (i == 0) {
                i++;
                continue;
            }
            double mean = rotamer.angles[i];
            double sdev = sdevs[i];
            double lower = (mean - sdev * mul) * toDEG;
            double upper = (mean + sdev * mul) * toDEG;
            if (lower < -180.0) {
                lower += 360.0;
                upper += 360.0;
            }
            List<Atom> angleAtoms = new ArrayList<>();
            int j = 0;
            boolean ok = true;
            int iAtom = 0;
            Atom[] angleAtomArray = new Atom[4];
            for (iAtom = 3; iAtom >= 0; iAtom--) {
                String aName = atomNames[iAtom];
                int colonPos = aName.indexOf(':');
                int delta = 0;
                if (colonPos != -1) {
                    String deltaRes = aName.substring(0, colonPos);
                    delta = Integer.valueOf(deltaRes);
                    aName = aName.substring(colonPos + 1);
                }
                Residue residue = polymer.getResidue(residueNum);
                Atom atom;
                if (iAtom == 3) {
                    atom = residue.getAtom(aName);
                } else {
                    atom = angleAtomArray[iAtom + 1].parent;
                }
                if (atom == null) {
                    ok = false;
                    break;
                } else {
                    String thisName = atom.getName();
                    if (!aName.equals(thisName)) {
                        if (thisName.startsWith("X")) {
                            thisName = thisName.substring(1);
                            if (!aName.equals(thisName)) {
                                System.out.println("skip " + iAtom + " " + atom.getFullName() + " " + atomNames[iAtom]);
                                ok = false;
                                break;
                            }
                        }
                    }

                    angleAtomArray[iAtom] = atom;
                }
            }
            if (ok) {
                for (Atom atom : angleAtomArray) {
                    angleAtoms.add(atom);
                }
                try {
                    AngleConstraint angleBoundary = new AngleConstraint(angleAtoms, lower, upper, 1.0);
                    boundaries.add(angleBoundary);
                } catch (IllegalArgumentException iaE) {
                    System.out.println(iaE.getMessage());

                }
            }
            i++;

        }
        return boundaries;
    }

    public static List<Atom>[] getLinkAtoms(Molecule molecule, boolean skipEnd) {
        List<Atom> angleAtoms = molecule.getAngleAtoms();

        int nSuite = 10;
        String[] linkAtoms = new String[nSuite];
        for (int j = 1; j < suiteAtoms.length; j++) {
            linkAtoms[j - 1] = suiteAtoms[j][3];
        }
        linkAtoms[6] = "C1'";
        linkAtoms[7] = "ring1";
        linkAtoms[8] = "ring2GA";
        linkAtoms[9] = "ring2CU";
        int nVars = angleAtoms.size() + suiteAtoms.length - 1;
        List<Atom>[] updateAtoms = new ArrayList[nSuite + 1];
        Set<Atom> fixAtoms = new HashSet<>();
        for (Polymer polymer : molecule.getPolymers()) {
            if (polymer.isRNA()) {
                for (Residue residue : polymer.getResidues()) {
                    boolean helixEnd = false;
                    if ((residue.next != null) && (residue.next.pairedTo == null)) {
                        helixEnd = true;
                    }

                    if ((residue.previous != null) && (residue.previous.pairedTo == null)) {
                        helixEnd = true;
                    }
                    if ((residue.pairedTo != null) && (residue.pairedTo.previous != null) && (residue.pairedTo.previous.pairedTo == null)) {
                        helixEnd = true;
                    }
                    if ((residue.pairedTo != null) && (residue.pairedTo.next != null) && (residue.pairedTo.next.pairedTo == null)) {
                        helixEnd = true;
                    }
                    if ((residue.pairedTo != null) && (!helixEnd || (!skipEnd && residue.iRes < residue.pairedTo.iRes))) {
                        int j = 0;
                        for (String aName : linkAtoms) {
                            boolean ok = true;
                            if (aName.equals("ring1")) {
                                if (residue.getName().equals("G") || residue.getName().equals("A")) {
                                    aName = "N9";
                                } else {
                                    aName = "N1";
                                }
                            }
                            if (aName.equals("ring2GA")) {
                                if (residue.getName().equals("G") || residue.getName().equals("A")) {
                                    aName = "C8";
                                } else {
                                    ok = false;
                                }
                            }
                            if (aName.equals("ring2CU")) {
                                if (residue.getName().equals("C") || residue.getName().equals("U")) {
                                    aName = "C2";
                                } else {
                                    ok = false;
                                }
                            }
                            if (ok) {
                                int colonPos = aName.indexOf(':');
                                int delta = 0;
                                if (colonPos != -1) {
                                    String deltaRes = aName.substring(0, colonPos);
                                    delta = Integer.valueOf(deltaRes);
                                    aName = aName.substring(colonPos + 1);
                                }
                                Residue applyResidue = delta < 0 ? residue.previous : residue;
                                if (updateAtoms[j] == null) {
                                    updateAtoms[j] = new ArrayList<>();
                                }
                                Atom atom = applyResidue.getAtom(aName);
                                if (atom != null) {
                                    updateAtoms[j].add(atom);
                                    fixAtoms.add(atom);
                                }
                            }
                            j++;
                        }
                    }
                }
            }
        }
        updateAtoms[nSuite] = new ArrayList<>();
        for (Atom atom : angleAtoms) {
            if (!fixAtoms.contains(atom.daughterAtom)) {
                updateAtoms[nSuite].add(atom.daughterAtom);
            }
        }
        for (List<Atom> updateSet : updateAtoms) {
            for (Atom atom : updateSet) {
                if (atom != null) {
                    System.out.print(atom.getShortName() + "  ");
                }
            }
            System.out.println("");
        }
        return updateAtoms;
    }
}
