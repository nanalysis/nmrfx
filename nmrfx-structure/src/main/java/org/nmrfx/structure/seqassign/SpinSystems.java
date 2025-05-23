package org.nmrfx.structure.seqassign;

import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.chemistry.io.NMRStarWriter;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;
import org.nmrfx.star.Loop;
import org.nmrfx.star.ParseException;
import org.nmrfx.star.Saveframe;
import org.nmrfx.structure.chemistry.Molecule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author brucejohnson
 */
public class SpinSystems {
    private static final Logger log = LoggerFactory.getLogger(SpinSystems.class);

    public enum ClusterModes {
        ALL,
        CORRECT,
        LONELY,
        MISSING,
        MISSING_PPM,
        EXTRA
    }

    RunAbout runAbout;
    private List<SpinSystem> systems = new ArrayList<>();
    Map<PeakList, double[]> sums;

    public SpinSystems(RunAbout runAbout) {
        this.runAbout = runAbout;
    }

    public int getSize() {
        return systems.size();
    }

    public void add(SpinSystem spinSystem) {
        systems.add(spinSystem);
    }

    public void remove(SpinSystem spinSystem) {
        systems.remove(spinSystem);
    }

    public Optional<SpinSystem> find(int idNum) {
        return systems.stream().filter(s -> s.getId() == idNum).findFirst();
    }

    public SpinSystem get(int i) {
        return systems.get(i);
    }

    public SpinSystem firstSpinSystem() {
        return systems.get(0);
    }

    public SpinSystem lastSpinSystem() {
        int lastIndex = systems.size() - 1;
        return systems.get(lastIndex);
    }

    public SpinSystem nextSpinSystem(SpinSystem currentSpinSystem) {
        int index = systems.indexOf(currentSpinSystem);
        if (index >= 0) {
            index++;
            if (index >= systems.size()) {
                index = systems.size() - 1;
            }
        } else {
            index = 0;
        }
        return systems.get(index);
    }

    public SpinSystem previousSpinSystem(SpinSystem currentSpinSystem) {
        int index = systems.indexOf(currentSpinSystem);
        if (index >= 0) {
            index--;
            if (index < 0) {
                index = 0;
            }
        } else {
            index = 0;
        }
        return systems.get(index);
    }

    public SpinSystem get(SpinSystem spinSystem, int dir, int pIndex, int sIndex) {
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

    Map<PeakList, double[]> calcNormalization(PeakList refList, List<PeakList> peakLists) {
        Map<PeakList, double[]> sumMap = new HashMap<>();
        for (Peak pkA : refList.peaks()) {
            for (PeakList peakListB : peakLists) {
                int[] aMatch = matchDims(refList, peakListB);
                if (peakListB != refList) {
                    double sumF = peakListB.peaks().stream().filter(pkB -> pkB.getStatus() >= 0).
                            mapToDouble(pkB -> comparePeaks(pkA, pkB, aMatch)).sum();
                    double[] sumArray = sumMap.computeIfAbsent(peakListB, k -> new double[refList.size()]);
                    sumArray[pkA.getIndex()] = sumF;
                }
            }
        }
        return sumMap;
    }

    public void addPeak(SpinSystem spinSys, Peak pkB) {
        if ((sums == null) || (sums.get(pkB.getPeakList()).length != spinSys.rootPeak.getPeakList().size())) {
            sums = calcNormalization(runAbout.getRefList(), runAbout.getPeakLists());
        }
        Peak rootPeak = spinSys.rootPeak;
        if (rootPeak != pkB) {
            PeakList peakListB = pkB.getPeakList();
            double[] sumArray = sums.get(peakListB);
            if (rootPeak.getPeakList() != peakListB) {
                int[] aMatch = matchDims(rootPeak.getPeakList(), peakListB);
                double f = comparePeaks(rootPeak, pkB, aMatch);
                if (f >= 0.0) {
                    double p = f / sumArray[rootPeak.getIndex()];
                    spinSys.addPeak(pkB, p);
                }

                for (int iDim = 0; iDim < aMatch.length; iDim++) {
                    if (aMatch[iDim] >= 0) {
                        PeakList.linkPeakDims(spinSys.getRootPeak().getPeakDim(iDim), pkB.getPeakDim(aMatch[iDim]));
                    }
                }
            }
        }
    }

    public static boolean[] getUseDims(PeakList refList, List<PeakList> peakLists) {
        boolean[] useDim = new boolean[refList.getNDim()];
        Arrays.fill(useDim, true);
        for (PeakList peakList : peakLists) {
            if (peakList != refList) {
                int[] aMatch = matchDims(refList, peakList);
                for (int i = 0; i < aMatch.length; i++) {
                    if (aMatch[i] == -1) {
                        useDim[i] = false;
                    }
                }
            }
        }
        return useDim;
    }

    public void addLists(PeakList refList, List<PeakList> newPeakLists) {
        for (PeakList peakList : newPeakLists) {
            peakList.unLinkPeaks();
        }

        List<PeakList> allLists = new ArrayList<>();
        allLists.addAll(runAbout.getPeakLists());
        allLists.addAll(newPeakLists);
        runAbout.setPeakLists(allLists);
        sums = calcNormalization(refList, allLists);
        var searchDims = refList.getSearchDims();
        double[] ppm = new double[searchDims.size()];
        int[] refDims = new int[searchDims.size()];
        int[] iDims = new int[searchDims.size()];
        for (var peakList : newPeakLists) {
            double[] sumArray = sums.get(peakList);
            int[] aMatch = matchDims(refList, peakList);

            int i = 0;
            for (var searchDim : refList.getSearchDims()) {
                var sDim = refList.getSpectralDim(searchDim.getDim());
                refDims[i] = sDim.getIndex();
                iDims[i] = peakList.getSpectralDim(sDim.getDimName()).getIndex();
                i++;
            }
            for (Peak peak : peakList.peaks()) {
                for (int j = 0; j < refDims.length; j++) {
                    ppm[j] = peak.getPeakDim(iDims[j]).getChemShiftValue();
                }
                var foundPeaks = refList.findPeaks(ppm);
                if (!foundPeaks.isEmpty()) {
                    Peak refPeak = foundPeaks.get(0);
                    findSpinSystem(refPeak).ifPresent(spinSystem -> {
                        for (int j = 0; j < refDims.length; j++) {
                            PeakList.linkPeaks(refPeak, refDims[j], peak, iDims[j]);
                        }
                        for (int iDim : iDims) {
                            PeakList.linkPeaks(refPeak, 0, peak, iDim);
                        }

                        double f = comparePeaks(refPeak, peak, aMatch);
                        if (f >= 0.0) {
                            double p = f / sumArray[refPeak.getIndex()];
                            spinSystem.addPeak(peak, p);
                        }
                    });
                }
            }
        }
    }

    public void assembleWithClustering(PeakList refList, List<PeakList> peakLists) throws IllegalStateException {
        sums = calcNormalization(refList, peakLists);
        PeakList.clusterOrigin = refList;
        for (PeakList peakList : peakLists) {
            peakList.unLinkPeaks();
            if ((peakList != refList) && !sums.containsKey(peakList)) {
                throw new IllegalStateException("Peaklist " + peakList.getName() + " not setup");
            }
        }
        boolean[] useDim = getUseDims(refList, peakLists);
        for (PeakList peakList : peakLists) {
            peakList.clearSearchDims();
            int[] aMatch = matchDims(refList, peakList);
            for (int i = 0; i < aMatch.length; i++) {
                if (useDim[i] && (aMatch[i] != -1)) {
                    double tol = peakList.getSpectralDim(aMatch[i]).getIdTol();
                    peakList.addSearchDim(aMatch[i], tol);
                }
            }
        }

        systems.clear();
        PeakList.clusterPeaks(peakLists, refList);
        var searchDim = refList.getSearchDims().get(0);
        for (Peak pkA : refList.peaks()) {
            SpinSystem spinSys = new SpinSystem(pkA, this);
            systems.add(spinSys);
            var linkedPeaks = PeakList.getLinks(pkA, searchDim.getDim());
            for (Peak pkB : linkedPeaks) {
                if (pkA != pkB) {
                    PeakList peakListB = pkB.getPeakList();
                    double[] sumArray = sums.get(peakListB);
                    if (refList != peakListB) {
                        int[] aMatch = matchDims(refList, peakListB);
                        double f = comparePeaks(pkA, pkB, aMatch);
                        if (f >= 0.0) {
                            double p = f / sumArray[pkA.getIndex()];
                            spinSys.addPeak(pkB, p);
                        }
                    }
                }
            }
        }
    }

    public void assemble(List<PeakList> peakLists) {
        systems.clear();
        peakLists.forEach(PeakList::unLinkPeaks);
        peakLists.forEach(peakListA ->
                // set status to 0 for all active (status >= 0) peaks
                peakListA.peaks().stream().filter(p -> p.getStatus() >= 0).forEach(p -> p.setStatus(0))
        );

        peakLists.forEach(peakListA -> peakListA.peaks().stream().filter(pkA -> pkA.getStatus() == 0).forEach(pkA -> {
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
        }));
    }

    public List<SpinSystemMatch> compare(SpinSystem spinSystemA, boolean prevMode) {
        List<SpinSystemMatch> matches = new ArrayList<>();
        for (SpinSystem spinSysB : systems) {
            if (spinSystemA != spinSysB) {
                Optional<SpinSystemMatch> result = spinSystemA.compare(spinSysB, prevMode);
                result.ifPresent(matches::add);
            }
        }
        return matches;
    }

    public void compare() {
        for (SpinSystem spinSysA : systems) {
            spinSysA.compare();
        }
    }

    public void checkConfirmed() {
        for (SpinSystem spinSys : systems) {
            spinSys.confirmP().ifPresent(spinSystemMatch -> {
                spinSys.setConfirmP(null);
                for (SpinSystemMatch spinSystemMatch1 : spinSys.spinMatchP) {
                    if (spinSystemMatch1.spinSystemB == spinSys) {
                        spinSys.setConfirmP(spinSystemMatch1);
                        break;
                    }
                }
            });
            spinSys.confirmS().ifPresent(spinSystemMatch -> {
                spinSys.setConfirmS(null);
                for (SpinSystemMatch spinSystemMatch1 : spinSys.spinMatchS) {
                    if (spinSystemMatch1.spinSystemA == spinSys) {
                        spinSys.setConfirmS(spinSystemMatch1);
                        break;
                    }
                }
            });
        }
    }

    public void updateFragments() {
        var fragments = getSortedFragments();
        for (SeqFragment seqFragment : fragments) {
            seqFragment.updateSpinSystemMatches();
        }
    }

    public void calcCombinations() {
        for (SpinSystem spinSys : systems) {
            spinSys.calcCombinations(false);
        }
    }

    public void buildSpinSystems(List<PeakList> peakLists) {
        PeakList refList = peakLists.get(0);
        for (Peak pkA : refList.peaks()) {
            SpinSystem spinSys = new SpinSystem(pkA, this);
            systems.add(spinSys);
            spinSys.getLinkedPeaks();
            spinSys.updateSpinSystem();
        }
        compare();
    }

    public List<SpinSystem> getSystems() {
        return systems;
    }

    public List<SpinSystem> getSystemsByType(ClusterModes clusterMode) {
        return systems.stream().filter(s -> {
                    int extraOrMissing = runAbout.getExtraOrMissing(s);
                    return switch (clusterMode) {
                        case ClusterModes.CORRECT -> extraOrMissing == 0;
                        case ClusterModes.EXTRA -> (extraOrMissing & 1) != 0;
                        case ClusterModes.MISSING -> (extraOrMissing & 2) != 0;
                        case ClusterModes.LONELY -> s.peakMatches.size() < 3;
                        case ClusterModes.MISSING_PPM -> !runAbout.getHasAllAtoms(s);
                        default -> true;
                    };
                }
        ).toList();
    }

    public List<SeqFragment> getSortedFragments() {
        Set<SeqFragment> fragments = new HashSet<>();
        for (SpinSystem spinSys : systems) {
            spinSys.fragment().ifPresent(frag -> {
                if (frag.spinSystemMatches.isEmpty()) {
                    spinSys.setFragment(null);
                } else {
                    fragments.add(frag);
                }
            });
        }
        return fragments.stream().sorted((e1, e2)
                        -> Integer.compare(e2.spinSystemMatches.size(),
                        e1.spinSystemMatches.size())).toList();
    }

    public List<SpinSystem> getUnconnectedSpinSystems() {
        List<SpinSystem> unconnectedSystems = new ArrayList<>();
        for (SpinSystem spinSys : systems) {
            if (spinSys.fragment().isEmpty()) {
                unconnectedSystems.add(spinSys);
            }
        }
        return unconnectedSystems;
    }

    public List<SpinSystem> getConnectedSpinSystems() {
        List<SeqFragment> sortedFragments = getSortedFragments();
        List<SpinSystem> spinSystems = new ArrayList<>();
        for (SeqFragment fragment : sortedFragments) {
            int iPos = 0;
            List<SpinSystemMatch> spinMatches = fragment.getSpinSystemMatches();
            SpinSystem spinSystem = spinMatches.get(0).spinSystemA;
            spinSystem.fragmentPosition = iPos++;
            spinSystems.add(spinSystem);
            for (SpinSystemMatch spinMatch : spinMatches) {
                spinSystem = spinMatch.getSpinSystemB();
                spinSystem.fragmentPosition = iPos++;
                spinSystems.add(spinSystem);
            }
        }
        return spinSystems;
    }

    public List<SpinSystem> getSortedSystems() {
        List<SeqFragment> sortedFragments = getSortedFragments();
        List<SpinSystem> unconnectedSystems = getUnconnectedSpinSystems();

        List<SpinSystem> uniqueSystems = sortedFragments.stream().
                map(frag -> frag.spinSystemMatches.get(0).spinSystemA).
                collect(Collectors.toList());
        uniqueSystems.addAll(unconnectedSystems);
        return uniqueSystems;
    }

    public Optional<SpinSystem> findSpinSystem(Peak peak) {
        for (var spinSys : systems) {
            for (var peakMatch : spinSys.peakMatches) {
                if (peak == peakMatch.peak) {
                    return Optional.of(spinSys);
                }
            }
        }
        return Optional.empty();
    }

    void readSTARPeakInfo(Map<Integer, SpinSystem> systemMap, Loop peakLoop) throws ParseException {
        List<Integer> peakIDs = peakLoop.getColumnAsIntegerList("ID", -1);
        List<Integer> peakSpinSystemID = peakLoop.getColumnAsIntegerList("Spin_system_ID", -1);
        List<Integer> peakListIDColumn = peakLoop.getColumnAsIntegerList("Spectral_peak_list_ID", -1);
        List<Integer> peakIDColumn = peakLoop.getColumnAsIntegerList("Peak_ID", -1);
        List<Double> matchScoreColumn = peakLoop.getColumnAsDoubleList("Match_score", 0.0);
        for (int i = 0; i < peakIDs.size(); i++) {
            Optional<PeakList> peakListOpt = PeakList.get(peakListIDColumn.get(i));
            if (peakListOpt.isPresent()) {
                PeakList peakList = peakListOpt.get();
                Peak peak = peakList.getPeakByID(peakIDColumn.get(i));
                SpinSystem spinSystem = systemMap.get(peakSpinSystemID.get(i));
                if ((spinSystem != null) && (peak != spinSystem.rootPeak)) {
                    double score = matchScoreColumn.get(i);
                    spinSystem.addPeak(peak, score);
                }
            }
        }
    }

    void readSTARMatchInfo(Map<Integer, SpinSystem> systemMap, List<Integer> spinSystemIDs, Map<Integer,
            SeqFragment> fragmentMap, List<Integer> fragmentIDColumn, Map<SpinSystem, SpinSystem> previousSystems,
                           Map<SpinSystem, SpinSystem> nextSystems) throws ParseException {
        for (int i = 0; i < spinSystemIDs.size(); i++) {
            SpinSystem system = systemMap.get(spinSystemIDs.get(i));
            SpinSystem nextSystem = nextSystems.get(system);
            SeqFragment fragment = fragmentMap.get(fragmentIDColumn.get(i));
            if (fragment != null) {
                system.setFragment(fragment);
            }
            if (nextSystem != null) {
                for (SpinSystemMatch match : system.getMatchToNext()) {
                    if ((match.getSpinSystemA() == system) && (match.getSpinSystemB() == nextSystem)) {
                        if (fragment == null) {
                            throw new ParseException("Could not parse STAR saveframe. Fragment was null.");
                        }
                        fragment.getSpinSystemMatches().add(match);
                        system.setConfirmS(match);
                        nextSystem.setConfirmP(match);
                    }
                }
            }
        }

    }

    Map<Integer, SeqFragment> readSTARFragmentLoop(Loop fragmentLoop) throws ParseException {
        Map<Integer, SeqFragment> fragmentMap = new HashMap<>();
        if (fragmentLoop != null) {
            List<Integer> idColumn = fragmentLoop.getColumnAsIntegerList("ID", -1);
            List<Integer> polymerIDColumn = fragmentLoop.getColumnAsIntegerList("Polymer_ID", -1);
            List<Integer> residueIDColumn = fragmentLoop.getColumnAsIntegerList("First_residue_ID", -1);
            List<Integer> nResiduesColumn = fragmentLoop.getColumnAsIntegerList("Residue_count", -1);
            List<Double> scoreColumn = fragmentLoop.getColumnAsDoubleList("Score", 0.0);
            MoleculeBase molecule = MoleculeFactory.getActive();
            for (int i = 0; i < idColumn.size(); i++) {
                SeqFragment fragment = new SeqFragment();
                fragment.id = idColumn.get(i);
                int polymerID = polymerIDColumn.get(i);
                if (polymerID != -1) {
                    int residueID = residueIDColumn.get(i);
                    int nResidues = nResiduesColumn.get(i);
                    double score = scoreColumn.get(i);
                    Polymer polymer = molecule.getPolymers().get(polymerID - 1);
                    Residue residue = polymer.getResidue(String.valueOf(residueID));
                    ResidueSeqScore residueSeqScore = new ResidueSeqScore(residue, nResidues, score);
                    fragment.setResSeqScore(residueSeqScore);
                    fragment.setFrozen(true);
                }
                fragmentMap.put(fragment.id, fragment);
            }

        }
        return fragmentMap;

    }

    record SpinSystemInfo(Map<Integer, SpinSystem> systemMap, List<Integer> spinSystemIDs,
                          List<Integer> fragmentIDColumn,
                          Map<SpinSystem, SpinSystem> previousSystems, Map<SpinSystem, SpinSystem> nextSystems) {

    }

    SpinSystemInfo readSTARSystemLoop(Loop systemLoop) throws ParseException {
        List<Integer> spinSystemIDs = systemLoop.getColumnAsIntegerList("ID", -1);
        List<Integer> peakListIDColumn = systemLoop.getColumnAsIntegerList("Spectral_peak_list_ID", -1);
        List<Integer> peakIDColumn = systemLoop.getColumnAsIntegerList("Peak_ID", -1);
        List<Integer> previousIDColumn = systemLoop.getColumnAsIntegerList("Confirmed_previous_ID", -1);
        List<Integer> nextIDColumn = systemLoop.getColumnAsIntegerList("Confirmed_next_ID", -1);
        List<Integer> fragmentIDColumn = systemLoop.getColumnAsIntegerList("Fragment_ID", -1);
        Map<Integer, SpinSystem> systemMap = new HashMap<>();
        Map<SpinSystem, SpinSystem> nextSystems = new HashMap<>();
        Map<SpinSystem, SpinSystem> previousSystems = new HashMap<>();
        for (int i = 0; i < spinSystemIDs.size(); i++) {
            Optional<PeakList> peakListOpt = PeakList.get(peakListIDColumn.get(i));
            if (peakListOpt.isPresent()) {
                PeakList peakList = peakListOpt.get();
                Peak peak = peakList.getPeakByID(peakIDColumn.get(i));
                int id = spinSystemIDs.get(i);
                SpinSystem spinSystem = new SpinSystem(peak, this);
                systemMap.put(id, spinSystem);
            }
        }
        for (int i = 0; i < spinSystemIDs.size(); i++) {
            Optional<PeakList> peakListOpt = PeakList.get(peakListIDColumn.get(i));
            if (peakListOpt.isPresent()) {
                SpinSystem spinSystem = systemMap.get(spinSystemIDs.get(i));
                Integer prev = previousIDColumn.get(i);
                SpinSystem prevSystem = prev != -1 ? systemMap.get(prev) : null;
                Integer next = nextIDColumn.get(i);
                SpinSystem nextSystem = next != -1 ? systemMap.get(next) : null;
                nextSystems.put(spinSystem, nextSystem);
                previousSystems.put(spinSystem, prevSystem);
            }
        }
        return new SpinSystemInfo(systemMap, spinSystemIDs, fragmentIDColumn, previousSystems, nextSystems);
    }

    void readSTARSaveFrame(Saveframe saveframe) throws ParseException {
        Loop fragmentLoop = saveframe.getLoop("_Fragments");
        Loop systemLoop = saveframe.getLoop("_Spin_system");
        Loop peakLoop = saveframe.getLoop("_Spin_system_peaks");

        if ((systemLoop != null) && (peakLoop != null)) {
            SpinSystemInfo spinSystemInfo = readSTARSystemLoop(systemLoop);
            Map<Integer, SeqFragment> fragmentMap = readSTARFragmentLoop(fragmentLoop);
            readSTARPeakInfo(spinSystemInfo.systemMap, peakLoop);

            systems = spinSystemInfo.systemMap.values().stream().
                    sorted(Comparator.comparingInt(SpinSystem::getId)).collect(Collectors.toList());
            for (SpinSystem spinSystem : systems) {
                spinSystem.updateSpinSystem();
            }
            compare();
            readSTARMatchInfo(spinSystemInfo.systemMap, spinSystemInfo.spinSystemIDs, fragmentMap, spinSystemInfo.fragmentIDColumn, spinSystemInfo.previousSystems, spinSystemInfo.nextSystems);
        }
    }

    public void trimAll() {
        for (SpinSystem spinSystem : systems) {
            runAbout.trim(spinSystem);
        }
    }

    public void extendAll(double minScore) {
        for (SpinSystem spinSystem : systems) {
            if (spinSystem.confirmP().isEmpty() || spinSystem.confirmS().isEmpty()) {
                SpinSystem.extend(spinSystem, minScore);
            }
        }
    }

    public void thawAll() {
        Molecule.getActive().getAtoms().forEach(atom -> atom.setPPMValidity(0, false));
        for (PeakList peakList : runAbout.peakLists) {
            peakList.clearAtomLabels();
        }
        getSortedFragments().forEach(SeqFragment::thawFragment);
    }

    public void clearAll() {
        for (SpinSystem spinSystem : systems) {
            spinSystem.setConfirmS(null);
            spinSystem.setConfirmP(null);
            spinSystem.setFragment(null);
        }
    }

    void writeSpinSystems(StringBuilder sBuilder) {
        NMRStarWriter.openLoop(sBuilder, "_Spin_system", SpinSystem.systemLoopTags);
        List<SpinSystem> allSystems = new ArrayList<>();
        allSystems.addAll(getConnectedSpinSystems());
        allSystems.addAll(getUnconnectedSpinSystems());
        for (SpinSystem spinSystem : allSystems) {
            sBuilder.append(spinSystem.getSystemSTARString()).append("\n");
        }
        NMRStarWriter.endLoop(sBuilder);
    }

    void writeSpinSystemPeaks(StringBuilder sBuilder) {
        NMRStarWriter.openLoop(sBuilder, "_Spin_system_peaks", SpinSystem.peakLoopTags);
        int i = 1;
        for (SpinSystem spinSystem : systems) {
            i = spinSystem.getPeakSTARString(sBuilder, i);
        }
        NMRStarWriter.endLoop(sBuilder);
    }

    void writeSpinSystemFragments(StringBuilder sBuilder) {
        NMRStarWriter.openLoop(sBuilder, "_Fragments", SpinSystem.fragmentLoopTags);
        int i = 0;
        for (SeqFragment fragment : getSortedFragments()) {
            fragment.setId(i);
            sBuilder.append(fragment.getFragmentSTARString());
            i++;
        }
        NMRStarWriter.endLoop(sBuilder);
    }
}
