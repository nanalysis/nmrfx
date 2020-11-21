package org.nmrfx.structure.seqassign;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakListBase;
import org.nmrfx.peaks.SpectralDim;

/**
 *
 * @author brucejohnson
 */
public class SpinSystems {

    RunAbout runAbout;
    List<SpinSystem> systems = new ArrayList<>();

    public SpinSystems(RunAbout runAbout) {
        this.runAbout = runAbout;
    }

    public List<SpinSystem> getSystems() {
        return systems;
    }

    public int getSize() {
        return systems.size();
    }

    public SpinSystem get(int i) {
        return systems.get(i);
    }

    public SpinSystem get(int i, int dir, int pIndex, int sIndex) {
        SpinSystem spinSystem = systems.get(i);
        System.out.println(spinSystem);
        if (dir == -1) {
            if (!spinSystem.spinMatchP.isEmpty()) {
                if (pIndex >= spinSystem.spinMatchP.size()) {
                    pIndex = spinSystem.spinMatchP.size() - 1;
                }
                spinSystem = spinSystem.spinMatchP.get(pIndex).spinSystemA;
            } else {
                spinSystem = null;
            }
        } else if (dir == 1) {
            if (!spinSystem.spinMatchS.isEmpty()) {
                if (sIndex >= spinSystem.spinMatchS.size()) {
                    sIndex = spinSystem.spinMatchS.size() - 1;
                }
                spinSystem = spinSystem.spinMatchS.get(sIndex).spinSystemB;
            } else {
                spinSystem = null;
            }
        }

        return spinSystem;

    }

    public static int[] matchDims(PeakListBase peakListA, PeakListBase peakListB) {
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

    double[][] calcNormalization(List<PeakListBase> peakLists) {
        PeakListBase refList = peakLists.get(0);
        double[][] sums = new double[refList.peaks().size()][peakLists.size() - 1];
        int i = 0;
        for (Peak pkA : refList.peaks()) {
            int j = 0;
            for (PeakListBase peakListB : peakLists) {
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

    public void assembleWithClustering(List<PeakListBase> peakLists) {
        double[][] sums = calcNormalization(peakLists);
        PeakListBase refList = peakLists.get(0);
        PeakListBase.clusterOrigin = refList;
        boolean[] useDim = new boolean[refList.getNDim()];
        for (int i = 0; i < useDim.length; i++) {
            useDim[i] = true;
        }
        int nPeakTypes = 0;
        for (PeakListBase peakList : peakLists) {
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
        Map<PeakListBase, Integer> peakMap = new HashMap<>();
        int j = 0;
        for (PeakListBase peakList : peakLists) {
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

        PeakListBase.clusterPeaks(peakLists);
        int i = 0;
        for (Peak pkA : refList.peaks()) {
            SpinSystem spinSys = new SpinSystem(pkA, this);
            systems.add(spinSys);
            for (Peak pkB : PeakListBase.getLinks(pkA, 0)) {// fixme calculate correct dim
                if (pkA != pkB) {
                    PeakListBase peakListB = pkB.getPeakList();
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

    public void assemble(List<PeakListBase> peakLists) {
        systems.clear();
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
                SpinSystem spinSys = new SpinSystem(pkA, this);
                systems.add(spinSys);
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
        for (SpinSystem spinSysA : systems) {
            spinSysA.compare();
        }
    }

    public void dump() {
        for (SpinSystem spinSys : systems) {
            System.out.println(spinSys.toString());
        }
    }

    public void calcCombinations() {
        for (SpinSystem spinSys : systems) {
            spinSys.calcCombinations(false);
        }
    }

    public void buildSpinSystems(List<PeakListBase> peakLists) {
        PeakListBase refList = peakLists.get(0);
        for (Peak pkA : refList.peaks()) {
            SpinSystem spinSys = new SpinSystem(pkA, this);
            systems.add(spinSys);
            spinSys.getLinkedPeaks();
            spinSys.updateSpinSystem();
        }
        compare();
    }

    public List<SpinSystem> getSortedSystems() {
        Set<SeqFragment> fragments = new HashSet<>();
        List<SpinSystem> unconnectedSystems = new ArrayList<>();
        for (SpinSystem spinSys : systems) {
            if (spinSys.fragment.isPresent()) {
                fragments.add(spinSys.fragment.get());
            } else {
                unconnectedSystems.add(spinSys);
            }
        }

        List<SpinSystem> uniqueSystems = fragments.stream().sorted((e1, e2)
                -> Integer.compare(e2.spinSystemMatches.size(),
                        e1.spinSystemMatches.size())).
                map(frag -> frag.spinSystemMatches.get(0).spinSystemA).
                collect(Collectors.toList());

        uniqueSystems.addAll(unconnectedSystems);
        return uniqueSystems;
    }

}
