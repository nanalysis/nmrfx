package org.nmrfx.processor.datasets.peaks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.nmrfx.processor.datasets.peaks.SpinSystemMatch;

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
            spinSystem = spinSystem.spinMatchP.get(0).spinSystemA;
        } else if (dir == 1) {
            spinSystem = spinSystem.spinMatchS.get(0).spinSystemB;
        }
        System.out.println(spinSystem);
        
        return spinSystem;

    }

    int[] matchDims(PeakList peakListA, PeakList peakListB) {
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

    double comparePeaks(Peak peakA, Peak peakB, int[] aMatch) {
        boolean ok = true;
        double sum = 0.0;
        for (int i = 0; i < aMatch.length; i++) {
            if (aMatch[i] != -1) {
                double tolA = peakA.getPeakList().getSpectralDim(i).getTol();
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

    public void assemble(List<PeakList> peakLists) {
        spinSystems.clear();
        peakLists.forEach(peakListA -> {
            // set status to 0 for all active (status >= 0) peaks
            peakListA.peaks().stream().filter(p -> p.getStatus() >= 0).forEach(p -> p.setStatus(0));
            int nDim = peakListA.getNDim();
            System.out.println(peakListA.getName());
            for (int i=0;i<nDim;i++) {
                SpectralDim sDim = peakListA.getSpectralDim(i);
                System.out.println(sDim.getPattern() + " " + sDim.getTol());
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
                    for (int ii=0;ii<aMatch.length;ii++) {
                        System.out.print(" " + aMatch[ii]);
                    }
                    System.out.println("");
                    double sumF = peakListB.peaks().stream().filter(pkB -> pkB.getStatus() >= 0).
                            mapToDouble(pkB -> comparePeaks(pkA, pkB, aMatch)).sum();
                    System.out.println(peakListB.getName() + " " + pkA.getName() + " " + sumF + " " + pkA.getPeakDim(0).getChemShift());
                    peakListB.peaks().stream().filter(pkB -> pkB.getStatus() == 0).
                            forEach(pkB -> {
                                double f = comparePeaks(pkA, pkB, aMatch);
                                System.out.println(pkA.getName() + " " + pkB.getName() + " " + f);
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
            spinSys.calcCombinations();
        }
    }

}
