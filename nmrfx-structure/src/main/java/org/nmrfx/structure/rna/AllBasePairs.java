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
/**
 *
 */
package org.nmrfx.structure.rna;

import java.io.InputStream;
import java.util.*;

public class AllBasePairs {

    public int type;
    public String res1;
    public String res2;
    BPConstraint[] bpConstraints;

    private final static Map<String, AllBasePairs> bpMap = new HashMap<>();
    private final static List<AllBasePairs> basePairs = new ArrayList<>();

    public AllBasePairs(int type, String res1, String res2, BPConstraint[] bpConstraints) {
        this.res1 = res1;
        this.res2 = res2;
        this.type = type;
        this.bpConstraints = bpConstraints;
    }

    public static class BPConstraint {

        final String[][] atomNames;
        final double lower;
        final double upper;
        final double lowerHeavy;
        final double upperHeavy;

        public BPConstraint(String[][] atomNames, double lower, double upper, double lowerHeavy, double upperHeavy) {
            this.atomNames = atomNames;
            this.lower = lower;
            this.upper = upper;
            this.lowerHeavy = lowerHeavy;
            this.upperHeavy = upperHeavy;
        }

        public String[][] getAtomNames() {
            return atomNames;
        }

        public double getLower() {
            return lower;
        }

        public double getUpper() {
            return upper;
        }

        public double getLowerHeavy() {
            return lowerHeavy;
        }

        public double getUpperHeavy() {
            return upperHeavy;
        }

        public String toString() {
            StringBuilder sBuilder = new StringBuilder();
            sBuilder.append(String.format("%4.1f ", lower));
            sBuilder.append(String.format("%4.1f ", upper));
            sBuilder.append(String.format("%4.1f ", lowerHeavy));
            sBuilder.append(String.format("%4.1f ", upperHeavy));
            for (int i = 0; i < atomNames.length; i++) {
                sBuilder.append(atomNames[i][0]).append(" ");
                sBuilder.append(atomNames[i][1]);
                if (i < atomNames.length - 1) {
                    sBuilder.append(" ");
                }
            }
            return sBuilder.toString();
        }
    }

    public BPConstraint[] getBPConstraints() {
        return bpConstraints;
    }

    @Override
    public String toString() {
        return type + ", " + res1 + ", " + res2;
    }

    static String dnaToRNA(String res) {
        if (res.length() == 2) {
            res = res.substring(1, 2);
        }
        if (res.equals("T")) {
            res = "U";
        }
        return res;

    }
    public static AllBasePairs getBasePair(int type, String res1, String res2) {
        if (bpMap.isEmpty()) {
            loadBasePairs();
        }
        String strType = String.valueOf(type);
        res1 = dnaToRNA(res1);
        res2 = dnaToRNA(res2);
        return bpMap.get(strType + res1 + res2);
    }

    public static List<AllBasePairs> getBasePairs() {
        if (basePairs.isEmpty()) {
            loadBasePairs();
        }
        return basePairs;
    }

    static void loadBasePairs() {
        bpMap.clear();
        basePairs.clear();
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        InputStream istream = cl.getResourceAsStream("data/basepair.txt");
        Scanner inputStream = new Scanner(istream);
        while (inputStream.hasNextLine()) {
            String data = inputStream.nextLine();
            if (!data.isEmpty()) {
                String[] arrOfStr = data.split("\t");
                if (arrOfStr.length >= 1) {
                    int type = Integer.parseInt(arrOfStr[0]);
                    String res1 = arrOfStr[1];
                    String res2 = arrOfStr[2];
                    int nPairs = (arrOfStr.length - 3) / 3;
                    BPConstraint[] bpConstraints = new BPConstraint[nPairs];
                    int firstindex = 3;
                    int secondindex = 4;
                    for (int i = 0; i < nPairs; i++) {
                        String[] atomNames1 = arrOfStr[firstindex].split("/");
                        String[] atomNames2 = arrOfStr[secondindex].split("/");
                        int nCombo = atomNames1.length * atomNames2.length;
                        String[][] atomNames = new String[nCombo][2];
                        int iCombo = 0;
                        for (String atomName1 : atomNames1) {
                            for (String atomName2 : atomNames2) {
                                atomNames[iCombo][0] = atomName1;
                                atomNames[iCombo][1] = atomName2;
                                iCombo++;
                            }
                        }

                        String[] restraints = arrOfStr[secondindex + 1].split("/");
                        String lowerALim = restraints[0];
                        String upperALim = restraints[1];
                        String lowerPLim = restraints[2];
                        String upperPLim = restraints[3];
                        double lower = Double.parseDouble(lowerALim);
                        double upper = Double.parseDouble(upperALim);
                        double heavyLower = Double.parseDouble(lowerPLim);
                        double heavyUpper = Double.parseDouble(upperPLim);
                        bpConstraints[i] = new BPConstraint(atomNames, lower, upper, heavyLower, heavyUpper);
                        firstindex += 3;
                        secondindex += 3;
                    }
                    String pair = type + res1 + res2;
                    AllBasePairs bp = new AllBasePairs(type, res1, res2, bpConstraints);
                    bpMap.put(pair, bp);
                    basePairs.add(bp);
                }
            }
        }
    }

}
