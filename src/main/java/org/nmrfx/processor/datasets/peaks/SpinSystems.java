package org.nmrfx.processor.datasets.peaks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 *
 * @author brucejohnson
 */
public class SpinSystems {

    List<SpinSystem> spinSystems = new ArrayList<>();

    public int getSize() {
        return spinSystems.size();
    }

    public SpinSystem get(int i) {
        return spinSystems.get(i);
    }

    public SpinSystem get(int i, int dir) {
        SpinSystem spinSystem = spinSystems.get(i);
        System.out.println("get " + i + " " + dir);
        System.out.println(spinSystem);
        if (dir == -1) {
            if (!spinSystem.spinMatchP.isEmpty()) {
                spinSystem = spinSystem.spinMatchP.get(0).spinSystemA;
            } else {
                spinSystem = null;
            }
        } else if (dir == 1) {
            if (!spinSystem.spinMatchS.isEmpty()) {
                spinSystem = spinSystem.spinMatchS.get(0).spinSystemB;
            } else {
                spinSystem = null;
            }
        }
        System.out.println(spinSystem);

        return spinSystem;

    }

    public static int[] matchDims(PeakList peakListA, PeakList peakListB) {
        int nDimA = peakListA.getNDim();
        int nDimB = peakListB.getNDim();
        int[] aMatch = new int[nDimA];
        for (int i = 0; i < nDimA; i++) {
            aMatch[i] = -1;
            SpectralDim sDimA = peakListA.getSpectralDim(i);
            for (int j = 0; j < nDimB; j++) {
                SpectralDim sDimB = peakListB.getSpectralDim(j);
                if (sDimA.getPattern().equals(sDimB.getPattern())) {
                    aMatch[i] = j;
                }
            }
        }
        return aMatch;
    }

    public static double comparePeaks(Peak peakA, Peak peakB, int[] aMatch) {
        boolean ok = true;
        double sum = 0.0;
        for (int i = 0; i < aMatch.length; i++) {
            if (aMatch[i] != -1) {
                double tolA = peakA.getPeakList().getSpectralDim(i).getIdTol();
                Float valueA = peakA.peakDims[i].getChemShift();
                Float valueB = peakB.peakDims[aMatch[i]].getChemShift();
                if ((valueA != null) && (valueB != null)) {
                    double delta = Math.abs(valueA - valueB);
                    if (delta > 2.0 * tolA) {
                        ok = false;
                        break;
                    } else {
                        delta /= tolA;
                        sum += delta * delta;
                    }
                } else {
                    ok = false;
                }
            }
        }
        double result = 0.0;
        if (ok) {
            double dis = Math.sqrt(sum);
            result = Math.exp(-dis);
        }
        return result;
    }

    double[][] calcNormalization(List<PeakList> peakLists) {
        PeakList refList = peakLists.get(0);
        double[][] sums = new double[refList.peaks().size()][peakLists.size() - 1];
        int i = 0;
        for (Peak pkA : refList.peaks()) {
            int j = 0;
            for (PeakList peakListB : peakLists) {
                int[] aMatch = matchDims(refList, peakListB);
                if (peakListB != refList) {
                    double sumF = peakListB.peaks().stream().filter(pkB -> pkB.getStatus() >= 0).
                            mapToDouble(pkB -> comparePeaks(pkA, pkB, aMatch)).sum();
                    sums[i][j] = sumF;
                    j++;
                }
            }
            i++;
        }
        return sums;
    }

    public void assembleWithClustering(List<PeakList> peakLists) {
        double[][] sums = calcNormalization(peakLists);
        PeakList refList = peakLists.get(0);
        PeakList.clusterOrigin = refList;
        boolean[] useDim = new boolean[refList.getNDim()];
        for (int i = 0; i < useDim.length; i++) {
            useDim[i] = true;
        }
        int nPeakTypes = 0;
        for (PeakList peakList : peakLists) {
            peakList.unLinkPeaks();
            if (peakList != refList) {
                int[] aMatch = matchDims(refList, peakList);
                for (int i = 0; i < aMatch.length; i++) {
                    if (aMatch[i] == -1) {
                        useDim[i] = false;
                    }
                }
            }
            int totalCount = 1;
            int[] counts = SpinSystem.getCounts(peakList);
            for (int count : counts) {
                totalCount *= count;
            }
            System.out.println("nt " + peakList.getName() + " " + totalCount);
            nPeakTypes += totalCount;
        }
        final int nExpected = nPeakTypes;
        Map<PeakList, Integer> peakMap = new HashMap<>();
        int j = 0;
        for (PeakList peakList : peakLists) {
            if (peakList != refList) {
                peakMap.put(peakList, j);
                j++;
            }
            peakList.clearSearchDims();
            int[] aMatch = matchDims(refList, peakList);
            for (int i = 0; i < aMatch.length; i++) {
                if (useDim[i] && (aMatch[i] != -1)) {
                    double tol = peakList.getSpectralDim(aMatch[i]).getIdTol();
                    peakList.addSearchDim(aMatch[i], tol);
                }
            }
        }

        PeakList.clusterPeaks(peakLists);
        int i = 0;
        for (Peak pkA : refList.peaks()) {
            SpinSystem spinSys = new SpinSystem(pkA);
            spinSystems.add(spinSys);
            for (Peak pkB : PeakList.getLinks(pkA, 0)) {// fixme calculate correct dim
                if (pkA != pkB) {
                    PeakList peakListB = pkB.getPeakList();
                    if (refList != peakListB) {
                        Integer jList = peakMap.get(peakListB);
                        if (jList == null) {
                            System.out.println("n peakListb " + peakListB);
                        } else {
                            int[] aMatch = matchDims(refList, peakListB);
                            double f = comparePeaks(pkA, pkB, aMatch);
                            if (f >= 0.0) {
                                double p = f / sums[i][jList];
                                spinSys.addPeak(pkB, p);
                            }
                        }
                    }
                }
            }
            i++;
            int nPeaks = spinSys.peakMatches.size();
            System.out.println("cluster " + pkA.getName() + " " + nExpected + " " + nPeaks);
        }
    }

    public void assemble(List<PeakList> peakLists) {
        spinSystems.clear();
        peakLists.forEach(peakListA -> {
            peakListA.unLinkPeaks();
        });
        peakLists.forEach(peakListA -> {
            // set status to 0 for all active (status >= 0) peaks
            peakListA.peaks().stream().filter(p -> p.getStatus() >= 0).forEach(p -> p.setStatus(0));
            int nDim = peakListA.getNDim();
            for (int i = 0; i < nDim; i++) {
                SpectralDim sDim = peakListA.getSpectralDim(i);
            }
        });

        int spinID = 0;
        peakLists.forEach(peakListA -> {
            peakListA.peaks().stream().filter(pkA -> pkA.getStatus() == 0).forEach(pkA -> {
                SpinSystem spinSys = new SpinSystem(pkA);
                spinSystems.add(spinSys);
                pkA.setStatus(1);
                peakLists.stream().filter(peakListB -> peakListB != peakListA).forEach(peakListB -> {
                    int[] aMatch = matchDims(peakListA, peakListB);
                    double sumF = peakListB.peaks().stream().filter(pkB -> pkB.getStatus() >= 0).
                            mapToDouble(pkB -> comparePeaks(pkA, pkB, aMatch)).sum();
                    peakListB.peaks().stream().filter(pkB -> pkB.getStatus() == 0).
                            forEach(pkB -> {
                                double f = comparePeaks(pkA, pkB, aMatch);
                                if (f > 0.0) {
                                    double p = f / sumF;
                                    if (p > 0.0) {
                                        spinSys.addPeak(pkB, p);
                                        pkB.setStatus(1);
                                    }
                                }
                            });
                });
            });
        });
    }

    public void compare() {
        int n = spinSystems.size();
        System.out.println("compare " + n);
        double[] sumsP = new double[n];
        double[] sumsS = new double[n];

        for (int i = 0; i < n; i++) {

            SpinSystem spinSysA = spinSystems.get(i);
            spinSysA.spinMatchP.clear();
            spinSysA.spinMatchS.clear();
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    SpinSystem spinSysB = spinSystems.get(j);
                    Optional<Double> result = spinSysA.compare(spinSysB, true);
                    if (result.isPresent()) {
                        sumsP[i] += result.get();
                    }
                    result = spinSysA.compare(spinSysB, false);
                    if (result.isPresent()) {
                        sumsS[i] += result.get();
                    }
                }
            }
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    SpinSystem spinSysB = spinSystems.get(j);
                    Optional<Double> result = spinSysA.compare(spinSysB, true);
                    if (result.isPresent()) {
                        double score = result.get() / sumsP[i];
                        spinSysA.spinMatchP.add(new SpinSystemMatch(spinSysB, spinSysA, score));
                    }
                    result = spinSysA.compare(spinSysB, false);
                    if (result.isPresent()) {
                        double score = result.get() / sumsS[i];
                        spinSysA.spinMatchS.add(new SpinSystemMatch(spinSysA, spinSysB, score));

                    }
                }
            }
            spinSysA.spinMatchP.sort((s1, s2) -> Double.compare(s2.score, s1.score));
            System.out.println(i + " " + spinSysA.spinMatchP);
            spinSysA.spinMatchS.sort((s1, s2) -> Double.compare(s2.score, s1.score));
            System.out.println(i + " " + spinSysA.spinMatchS);
        }
    }

    public void dump() {
        for (SpinSystem spinSys : spinSystems) {
            System.out.println(spinSys.toString());
        }
    }

    public void calcCombinations() {
        for (SpinSystem spinSys : spinSystems) {
            spinSys.calcCombinations();
        }
    }

}
