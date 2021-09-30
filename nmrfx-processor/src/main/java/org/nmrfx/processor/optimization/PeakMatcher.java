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

 /*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.optimization;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.StringTokenizer;
import org.apache.commons.math3.special.Erf;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 *
 * @author brucejohnson
 */
public class PeakMatcher {

    final double SQRT2 = Math.sqrt(2.0);
    final double ERRMUL = 1.0;
    final double weightPred = 4.0;
    final double weightMulti = 1.0;
    int[][] bestMatches;

    abstract class Value {

        int index;
        double[] values;
        double[] tvalues;
        int[] atoms;
    }

    class PPMTol {

        final String atomName;
        final double predAvg;
        double peakAvg;
        final double sigma;
        boolean useList = false;
        DescriptiveStatistics stats = new DescriptiveStatistics();
        ArrayList<Double> errorLims = new ArrayList<>();

        PPMTol(final String atomName, final double predAvg, final double sigma) {
            this.atomName = atomName;
            this.predAvg = predAvg;
            this.sigma = sigma;
        }

        double getPPM() {
            if (!useList) {
                return predAvg;
            } else {
                return peakAvg;
            }
        }

        double getSigma() {
            return sigma;
        }

        double getQ(final double x) {
            double q = Math.log(1.0 - Erf.erf(Math.abs(x) / SQRT2));
            return q;
        }

        double getQPred(final double ppm) {
            double x = (ppm - predAvg) / sigma;
            double q = getQ(x);
            double x0 = 1.5;
            double q0 = getQ(x0);
            double QPred = 1.0 + (q / Math.abs(q0));
            return QPred;
        }

        double getQMulti(final double ppm, final double errorLim) {
            double x = (ppm - peakAvg) / errorLim;
            double q = getQ(x);
            double x0 = 2.0;
            double q0 = getQ(x0);
            double QMulti = 1.0 + (q / Math.abs(q0));
            return QMulti;
        }

        double getQMultiSum() {
            double sum = 0.0;
            long n = stats.getN();
            for (int i = 0; i < n; i++) {
                sum += getQMulti(stats.getElement(i), errorLims.get(i) * ERRMUL);
            }
            return sum;
        }

        void addPPM(final double newValue, final double errorLim) {
            long nValues = stats.getN();
            if ((nValues == 0) || (Math.abs(newValue - peakAvg) < errorLim * 1.0)) {
                stats.addValue(newValue);
                errorLims.add(errorLim);
                peakAvg = stats.getMean();
                useList = true;
            }
        }

        void clearPPM() {
            useList = false;
            stats.clear();
            errorLims.clear();
        }

        String getAtomName() {
            return atomName;
        }

        double getProb(final double value, final double errorValue) {
            double deltaScaled;
            // fixme should we not use sigma once we have an assignment from peak?
            //   that is should sigma and errorValue below be swapped?
            if (useList) {
                deltaScaled = (value - getPPM()) / sigma;
            } else {
                deltaScaled = (value - getPPM()) / errorValue;
            }
            double prob = (1.0 / (Math.sqrt(2.0 * Math.PI * sigma))) * Math.exp(-1.0 * deltaScaled * deltaScaled / 2.0);
            return prob;
        }
    }

    class PeakValue extends Value {

        PeakValue(int index, double[] values, double[] tvalues) {
            this.index = index;
            this.values = values;
            this.tvalues = tvalues;
        }

        double getProbability(AtomValue b) {
            double cumProb = 1.0;
            for (int i = 0; i < values.length; i++) {
                PPMTol ppmTol = b.getPPMTol(i);
                double prob = ppmTol.getProb(values[i], tvalues[i]);
//System.out.printf("%3d %5.3f %5.3f %5.3f",i,values[i],ppmTol.getPPM(),prob);
                cumProb *= prob;
            }
//System.out.printf("%5.3f\n",cumProb);
            if (cumProb < 1.0e-3) {
                cumProb = 0.0;
            }
            return cumProb;
        }

        public String toString() {
            StringBuilder sBuild = new StringBuilder();
            for (double value : values) {
                sBuild.append(value);
                sBuild.append(' ');
            }
            return sBuild.toString();
        }
    }

    class AtomValue extends Value {

        AtomValue(int index, String[] names) {
            this.index = index;
            atoms = new int[names.length];
            int i = 0;
            for (String name : names) {
                Integer id = atomMap.get(name);
                if (id == null) {
                    System.err.println("No atom " + name + " in map");
                    System.exit(0);
                }
                atoms[i++] = id;
            }
        }

        double getValue(int index) {
//System.out.println("getval " + index + " " + atoms[index]);
            return atomPPMList.get(atoms[index]).getPPM();
        }

        PPMTol getPPMTol(int index) {
            return atomPPMList.get(atoms[index]);
        }

        double getDistance(PeakValue v) {
            return 0.0;
        }

        int size() {
            return atoms.length;
        }

        public String toString() {
            StringBuilder sBuild = new StringBuilder();
            for (int i = 0; i < atoms.length; i++) {
                PPMTol ppmTol = getPPMTol(i);
                sBuild.append(ppmTol.getAtomName());
                sBuild.append(' ');
            }
            for (int i = 0; i < atoms.length; i++) {
                PPMTol ppmTol = getPPMTol(i);
                sBuild.append(ppmTol.getPPM());
                sBuild.append(' ');
            }
            return sBuild.toString();
        }
    }

    class PeakListType {

        ArrayList<AtomValue> valuesAtom = new ArrayList<>();
        ArrayList<PeakValue> valuesPeak = new ArrayList<>();

        ArrayList<AtomValue> getAtoms() {
            return valuesAtom;
        }

        ArrayList<PeakValue> getPeaks() {
            return valuesPeak;
        }
    }
    double[] tols = {2.0, 2.0};
    HashMap<String, Integer> atomMap = new HashMap<>();

    ArrayList<PPMTol> atomPPMList = new ArrayList<>();
    HashMap<String, PeakListType> peakListTypes = new HashMap<>();
    PeakListType peakType;

    public void clearPPMTols() {
        for (PPMTol ppmTol : atomPPMList) {
            ppmTol.clearPPM();
        }
    }

    public double globalScore(boolean dump) {
        double scoreSum = 0.0;
        double normSum = 0.0;
        if (dump) {
            System.out.printf("%10s %3s %8s %8s %8s %8s %8s %8s\n", "Atom", "nVa", "ppm", "stdev", "delta", "QPred", "QMulti", "qTotal");
        }
        for (PPMTol ppmTol : atomPPMList) {
            long nValues = ppmTol.stats.getN();
            double qTotal = 0.0;
            double norm = weightPred + weightMulti * nValues;

            if (nValues == 0) {
                qTotal = 0.0;
                if (dump) {
                    System.out.printf("%10s %3d %8.3f\n", ppmTol.getAtomName(), nValues, ppmTol.getPPM());
                }
            } else {
                double QPred = ppmTol.getQPred(ppmTol.getPPM());
                double QMulti = ppmTol.getQMultiSum();
                qTotal = weightPred * QPred + weightMulti * QMulti;
                double delta = ppmTol.stats.getMax() - ppmTol.stats.getMin();
                double stdev = ppmTol.stats.getStandardDeviation();
                if (dump) {
                    System.out.printf("%10s %3d %8.3f %8.3f %8.3f %8.3f %8.3f %8.3f\n", ppmTol.getAtomName(), nValues, ppmTol.getPPM(), stdev, delta, QPred, (QMulti / nValues), qTotal / norm);
                }
            }
            scoreSum += qTotal;
            normSum += norm;
        }
        double score = scoreSum / normSum;
        if (dump) {
            System.out.println("score: " + score);
        }
        return score;
    }

    public void processPPMFile(String fileName) {
        File file = new File(fileName);
        try {
            Scanner in = new Scanner(file);
            while (in.hasNextLine()) {
                String line = in.nextLine();
                String[] fields = line.split(" ");
                String atom = fields[0];
                double value = Double.parseDouble(fields[1]);
                double sigma = Double.parseDouble(fields[2]);
                PPMTol ppmTol = new PPMTol(atom, value, sigma);
                int index = atomPPMList.size();
                atomPPMList.add(ppmTol);
                atomMap.put(atom, index);
            }
        } catch (IOException ioE) {
            System.out.println(ioE.getMessage());
            return;
        }
    }

    public double processTypes(String[] types) {
        return processTypes(types, null);
    }

    public double processTypes(String[] types, int[][] requireValues) {
        int i = 0;
        for (String type : types) {
            int[] require = null;
            if ((requireValues != null) && (requireValues[i] != null)) {
                require = requireValues[i];
                processType(type, require);
            } else {
                bestMatches[i] = processType(type, require);
            }
            i++;
        }
        return globalScore(false);
    }

    public void genStarts(String[] types) {
        clearPPMTols();
        bestMatches = new int[types.length][];
        double score = processTypes(types);
        int j = 0;
        int[][] requireValues = new int[types.length][];
        requireValues[0] = new int[2];
        for (int i = 0; i < bestMatches[0].length; i++) {
            if (bestMatches[0][i] != -1) {
                clearPPMTols();
                //requireValues[0][0]=i;
                //requireValues[0][1]=bestMatches[0][i];
                score = processTypes(types, requireValues);
                System.out.println("gscore " + score);
            }
        }
    }

    public void processFile(String fileName) {
        File file = new File(fileName);
        try {
            Scanner in = new Scanner(file);
            while (in.hasNextLine()) {
                String line = in.nextLine();
                processLine(line);
            }
        } catch (IOException ioE) {
            System.out.println(ioE.getMessage());
            return;
        }
    }

    public int nPeaks(String type) {
        PeakListType peakListType = peakListTypes.get(type);
        ArrayList<PeakValue> valuesPeak = peakListType.getPeaks();
        return valuesPeak.size();
    }

    public int[] processType(String type, int[] require) {
        PeakListType peakListType = peakListTypes.get(type);
        ArrayList<PeakValue> valuesPeak = peakListType.getPeaks();
        ArrayList<AtomValue> valuesAtom = peakListType.getAtoms();
        int nPeaks = valuesPeak.size();
        int nAtoms = valuesAtom.size();
        System.out.println(nPeaks + " " + nAtoms);
        int nTotal = nAtoms + nPeaks;
        BipartiteMatcher matcher = new BipartiteMatcher();
        matcher.reset(nTotal, true);
        // should we allow duplicate peaks for overlap
        // fixme should we add reciprocol match
        for (int i = 0; i < nPeaks; i++) {
            matcher.setWeight(i, nAtoms + i, -1.0);
            matcher.setWeight(nAtoms + i, i, -1.0);
        }
        for (int j = 0; j < nAtoms; j++) {
            matcher.setWeight(nPeaks + j, j, -1.0);
            matcher.setWeight(j, nPeaks + j, -1.0);
        }
        int[] bestMatch = new int[nAtoms];
        System.out.printf("%s\t%s\t%4s\n", "atom", "peak", "prob");
        for (int iAtom = 0; iAtom < nAtoms; iAtom++) {
            double bestProb = Double.NEGATIVE_INFINITY;
            bestMatch[iAtom] = -1;
            int nMatch = 0;
            for (int jPeak = 0; jPeak < nPeaks; jPeak++) {
                int pkIndex = valuesPeak.get(jPeak).index;
                int atomIndex = valuesAtom.get(iAtom).index;
                double probability = valuesPeak.get(jPeak).getProbability(valuesAtom.get(iAtom));
                if (probability > 0.0) {
                    if ((require == null) || (require[jPeak] == -1) || (require[jPeak] == iAtom)) {
                        if (probability > bestProb) {
                            bestProb = probability;
                            bestMatch[iAtom] = jPeak;
                        }
                        System.out.printf("%4d\t%4d\t%4.2f\n", atomIndex, pkIndex, probability);
                        matcher.setWeight(iAtom, jPeak, probability);
                        nMatch++;
                    }
                }
            }
            if (nMatch < 2) {
                bestMatch[iAtom] = -1;
            }
        }
        int[] matching = matcher.getMatching();

        System.out.println(
                "Maximum-weight matching:");
        System.out.printf("%s\t%4s\t%4s\t%4s\t%s\t%10s\t%s\n", "type", "iAtm", "iPk", "prob", "atoms", "peak", "pkppms");
        for (int iAtom = 0; iAtom < nTotal; iAtom++) {
            int jPeak = matching[iAtom];
            String name = "arti";
            String pkname = "pkarti";
            String atomString = "";
            String peakString = "";
            int pkIndex = -1;
            int atomIndex = -1;
            double probability = matcher.getWeight(iAtom, jPeak) - 2.0;
            if ((iAtom >= 0) && (iAtom < valuesAtom.size())) {
                AtomValue atomValue = valuesAtom.get(iAtom);
                atomIndex = atomValue.index;
                atomString = atomValue.toString();
                if ((jPeak >= 0) && (jPeak < valuesPeak.size())) {
                    PeakValue peakValue = valuesPeak.get(jPeak);
                    pkIndex = peakValue.index;
                    peakString = peakValue.toString();
                    if (probability > 0.0) {
                        for (int iDim = 0; iDim < atomValue.size(); iDim++) {
                            int kAtom = atomValue.atoms[iDim];
                            PPMTol ppmTol = atomPPMList.get(kAtom);
                            ppmTol.addPPM(peakValue.values[iDim], peakValue.tvalues[iDim]);
                        }
                    }
                }
            }
            if ((jPeak >= 0) && (jPeak < valuesPeak.size())) {
                pkname = "pk" + jPeak;
            }
            System.out.printf("%s\t%4d\t%4d\t%4.5f\t%s\t%10s\t%s\n", type, atomIndex, pkIndex, probability, atomString, pkname, peakString);
        }
        return bestMatch;
    }

    void processLine(String line) {
        String[] fields = line.split(" ");
        if (fields[0].equals("type")) {
            peakType = new PeakListType();
            peakListTypes.put(fields[1], peakType);
        } else {
            String set = fields[0];
            Value value;
            if (fields[0].equals("Peak")) {
                int nDim = (fields.length - 2) / 2;
                double[] dArray = new double[nDim];
                double[] tArray = new double[nDim];
                int index = Integer.parseInt(fields[1]);
                for (int i = 0; i < nDim; i++) {
                    dArray[i] = Double.parseDouble(fields[i * 2 + 2]);
                    tArray[i] = Double.parseDouble(fields[i * 2 + 3]);
                    if (tArray[i] < 0.05) {
                        tArray[i] = 0.05;
                    }
                }
                value = new PeakValue(index, dArray, tArray);
            } else {
                int nDim = fields.length - 2;
                int index = Integer.parseInt(fields[1]);
                String[] sArray = new String[nDim];
                for (int i = 0; i < nDim; i++) {
                    sArray[i] = fields[i + 2];
                }
                value = new AtomValue(index, sArray);
            }
            if (set.equals("Peak")) {
                peakType.valuesPeak.add((PeakValue) value);
            } else {
                peakType.valuesAtom.add((AtomValue) value);
            }
        }

    }
}
