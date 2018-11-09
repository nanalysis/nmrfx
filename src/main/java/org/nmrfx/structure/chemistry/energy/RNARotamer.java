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

import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.Polymer;
import org.nmrfx.structure.chemistry.Residue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.math3.util.FastMath;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Point3;
import org.python.modules.math;

public class RNARotamer {

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
    // static final String[] atomNames = {"O3'", "P", "O5'", "C5'", "C4'", "C3'", "O3'"};
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
        double[] normDeltas = new double[7];

        public RotamerScore(RNARotamer rotamer, double score, double prob, double[] angles) {
            this(rotamer, score, prob, angles, "");
        }

        public RotamerScore(RNARotamer rotamer, double score, double prob, double[] angles, String message) {
            this.rotamer = rotamer;
            this.score = score;
            this.angles = angles;
            this.message = message;
            this.prob = prob;
        }

        private void calcNormDeltas() {
            for (int i = 0; i < 7; i++) {
                double delta = angles[i] - rotamer.angles[i];
//                double delta = Math.abs(angles[i] - rotamer.angles[i]);

                if (delta > Math.PI) {
                    delta = -(2.0 * Math.PI - delta);
                }
                if (delta < -Math.PI) {
                    delta = 2.0 * Math.PI + delta;
                }
                normDeltas[i] = delta / rotamer.sdev[i];
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
                suiteness = 0.01;
            }
            return suiteness;
        }

        @Override
        public String toString() {
            String result = String.format("%2s S%20s %8.6f %.3f %.2f %50s %6s", rotamer.name, rotamer.suiteName, prob, score, getSuiteness(), formatAngles(angles), message);
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
        //System.out.println(this.name + " " + this.suiteName + " " + this.deltaDeltaGamma);
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

    public final String makeSuiteName(double[] angles) {
        StringBuilder builder = new StringBuilder();
        // m, p, t convention of Lovell et al. (20) for values near 􏰋60°, 􏰏60°, or 180°;
        for (int i = 0; i < 7; i++) {
            double testAngle = angles[i] * toDEG;
            if ((i == 0) || (i == 6)) {
                if (Math.abs(testAngle - 84.0) < 10.0) {
                    builder.append("3'");
                } else if (Math.abs(testAngle - 147.0) < 10.0) {
                    builder.append("2'");
                } else {
                    builder.append('?');
                }
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
                System.out.println("fail " + i + " " + testAngle + " " + ranges[i - 1][0] + " " + ranges[i - 1][1]);
                result = i;
                break;
            }
        }

        return result;
    }

    public static String formatAngles(double[] angles) {
        StringBuilder builder = new StringBuilder();
        for (double angle : angles) {
            builder.append(String.format(" %6.1f", angle * toDEG));
        }
        return builder.toString();
    }

    public static void clear() {
        ROTAMERS.clear();
    }

    public static void add(String name, int n, double... angles) {
        ROTAMERS.put(name, new RNARotamer(name, n, angles));
    }

    public static void validateDetail() {
        for (RNARotamer rotamer : ROTAMERS.values()) {
            ArrayList<RotamerScore> hits = getHits(rotamer.angles);
            for (RotamerScore rScore : hits) {
                System.out.println(rotamer.fraction + " " + rotamer.name + " " + rScore.toString());
            }
        }
    }

    public static void validate() {
        for (RNARotamer rotamer : ROTAMERS.values()) {
            RotamerScore rScore = getBest(rotamer.angles);
            System.out.println(rotamer.fraction + " " + rotamer.name + " " + rScore.toString());
        }
    }

    public static RotamerScore[] getNBest(Polymer polymer, int residueNum, int n) {
        return getNBest(polymer, residueNum, n, null);
    }

    public static RotamerScore[] getNBest(Polymer polymer, int residueNum, int n, EnergyCoords ec) {
        /* getNBest finds n of the best rotamer confirmations and returns a 
           list of rotamer scores containing the type of rotamer and the 
           probability. The function takes the polymer and a residue number.
         */

        RotamerScore[] bestScores = new RotamerScore[n];
        double[] testAngles = RNARotamer.getDihedrals(polymer, residueNum, ec);
        List<RotamerScore> rotamerScores = new ArrayList<>();
        for (RNARotamer rotamer : ROTAMERS.values()) {
            double probability = rotamer.probability(testAngles, new int[]{0, 1, 2, 3, 4, 5, 6}, rotamer.fraction);
            RotamerScore rotScore = new RotamerScore(rotamer, 0.0, probability, testAngles, null);
            rotamerScores.add(rotScore);
        }
        rotamerScores = rotamerScores.stream().sorted(Comparator.comparingDouble(RotamerScore::getProb).reversed()).limit(n).collect(Collectors.toList());
        
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
        bestScores = rotamerScores.toArray(bestScores);
        return bestScores;
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
            score.calcNormDeltas();
            double prob = score.prob;
            if (prob < 10e-200) {
                System.out.println("probability changed");
                prob = 10e-200;
                score.prob = prob;
            }
            totalProb += score.prob;

        }
        return -FastMath.log(totalProb);
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
            sum += FastMath.pow(delta, HPOWER);
        }
        double result = FastMath.pow(sum, 1.0 / HPOWER);
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
            double sdevValue = sdev[index];
            double coeff = 1.0 / (sdevValue * Math.sqrt(2.0 * Math.PI));
            double normalizedDelta = delta / sdevValue;
            double exponent = -(1.0 / 2.0) * normalizedDelta * normalizedDelta;
            double p = coeff * Math.exp(exponent);
            totalProb *= p;
        }
        if (totalProb > 1) {
            totalProb = 1;
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
        double[] angles = new double[suiteAtoms.length];
        if (residueNum > 0) {
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
                    Residue residue = polymer.getResidue(residueNum + delta);
                    Atom atom = residue.getAtom(aName);
                    angleAtoms[j++] = atom;
                    if (j == 3) {
                        atoms[i] = atom;
                    }
                }
                if (ec == null) {
                    angles[i++] = AtomMath.calcDihedral(angleAtoms[0].getPoint(), angleAtoms[1].getPoint(), angleAtoms[2].getPoint(), angleAtoms[3].getPoint());
                } else {
                    angles[i++] = ec.calcDihedral(angleAtoms[0].eAtom, angleAtoms[1].eAtom, angleAtoms[2].eAtom, angleAtoms[3].eAtom);
                }
            }
        }
        return angles;
    }

//O3'     P       O5'     C5'     C4'     C3'     O3'
// d-1     e-1     z-1     a       b       g       d
    public static RotamerScore scoreResidue(Polymer polymer, int residueNum) {
        RotamerScore rotamerScore = null;
        if (residueNum > 0) {
            double[] angles = getDihedrals(polymer, residueNum);
            rotamerScore = bestProb(angles);
        }
        return rotamerScore;
    }

    public static ArrayList<AngleBoundary> getAngleBoundaries(Polymer polymer, String residueNum, String rotamerName, double mul) throws IllegalArgumentException, InvalidMoleculeException {
        RNARotamer rotamer = ROTAMERS.get(rotamerName);
        if (rotamer == null) {
            throw new IllegalArgumentException("Invalid rotamer name \"" + rotamerName + "\"");
        }
        ArrayList<AngleBoundary> boundaries = new ArrayList<>();
        int i = 0;
        for (String[] atomNames : suiteAtoms) {
            double mean = rotamer.angles[i];
            double sdev = sdevs[i];
            double lower = (mean - sdev * mul) * toDEG;
            double upper = (mean + sdev * mul) * toDEG;
            if (lower < -180.0) {
                lower += 360.0;
                upper += 360.0;
            }
            //System.out.printf("%3s %5s %8.3f %8.3f %8.3f %8.3f\n", residueNum, name, mean * toDEG, sdev * toDEG * mul, lower, upper);
            List<Atom> angleAtomNames = new ArrayList<>();
            int j = 0;
            boolean ok = true;
            for (String aName : atomNames) {
                int colonPos = aName.indexOf(':');
                int delta = 0;
                if (colonPos != -1) {
                    String deltaRes = aName.substring(0, colonPos);
                    delta = Integer.valueOf(deltaRes);
                    aName = aName.substring(colonPos + 1);
                }
                Residue residue = polymer.getResidue(residueNum);
                Atom atom = null;
                switch (delta) {
                    case 1:
                        if (residue.next == null) {
                            ok = false;
                        } else {
                            atom = residue.next.getAtom(aName);
                        }
                        break;
                    case -1:
                        if (residue.previous == null) {
                            ok = false;
                        } else {
                            atom = residue.previous.getAtom(aName);
                        }
                        break;
                    default:
                        atom = residue.getAtom(aName);
                        break;
                }
                if (ok) {
                    angleAtomNames.add(atom);
                } else {
                    break;
                }
            }
            if (ok) {
                AngleBoundary angleBoundary = new AngleBoundary(angleAtomNames, lower, upper, 1.0);
                boundaries.add(angleBoundary);
            }
            i++;

        }
        return boundaries;
    }

}
