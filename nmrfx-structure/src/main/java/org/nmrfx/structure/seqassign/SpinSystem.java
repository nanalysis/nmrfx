package org.nmrfx.structure.seqassign;

import org.apache.commons.math3.util.MultidimensionalCounter;
import org.apache.commons.math3.util.MultidimensionalCounter.Iterator;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.AtomResonance;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.peaks.*;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.seqassign.RunAbout.TypeInfo;
import smile.clustering.KMeans;

import java.util.*;
import java.util.Map.Entry;

import static org.nmrfx.structure.seqassign.SpinSystems.comparePeaks;
import static org.nmrfx.structure.seqassign.SpinSystems.matchDims;

/**
 * @author brucejohnson
 */
public class SpinSystem {
    static final List<String> systemLoopTags = List.of("ID", "Spectral_peak_list_ID", "Peak_ID",
            "Confirmed_previous_ID", "Confirmed_next_ID", "Fragment_ID", "Fragment_index");
    static final List<String> peakLoopTags = List.of("ID", "Spin_system_ID", "Spectral_peak_list_ID", "Peak_ID", "Match_score");
    static final List<String> fragmentLoopTags = List.of("ID", "Polymer_ID", "First_residue_ID", "Residue_count", "Score");
    static final Map<Integer, SpinSystem> peakToSpinSystemMap = new HashMap<>();

    SpinSystems spinSystems;
    final Peak rootPeak;
    List<PeakMatch> peakMatches = new ArrayList<>();
    List<SpinSystemMatch> spinMatchP = new ArrayList<>();
    List<SpinSystemMatch> spinMatchS = new ArrayList<>();
    private SpinSystemMatch confirmP = null;
    private SpinSystemMatch confirmS = null;
    private SeqFragment fragment = null;
    int fragmentPosition = -1;

    public enum AtomEnum {
        H("h", 0, 7, 0.04, false),
        N("n", 0, 7, 0.5, false),
        C("c", 2, 1, 0.6, true),
        HA("ha", 0, 0, 0.04, false),
        CA("ca", 2, 1, 0.6, true),
        CB("cb", 2, 1, 0.6, true);

        final String name;
        final int nPrevious;

        double tol;

        final int nThis;

        boolean resMatch;

        AtomEnum(String name, int nPrevious, int nThis, double tol, boolean resMatch) {
            this.name = name;
            this.nPrevious = nPrevious;
            this.nThis = nThis;
            this.tol = tol;
            this.resMatch = resMatch;
        }

        int n(int k) {
            return k == 0 ? nPrevious : nThis;
        }

        double tol() {
            return tol;
        }

        void tol(double value) {
            this.tol = value;
        }
    }

    EnumMap<AtomEnum, ShiftValue>[] shiftValues = new EnumMap[2];
    record ShiftValue(int n, double value, double range) {
    }

    static class ResAtomPattern {
        final Peak peak;
        final int[] resType;
        final String[] atomTypes;
        AtomEnum[] atomTypeIndex;
        final boolean requireSign;
        final boolean positive;
        final boolean ambiguousRes;

        ResAtomPattern(Peak peak, String[] resType, String[] atomTypes, boolean requireSign, boolean positive, boolean ambiguousRes) {
            this.peak = peak;
            this.resType = new int[resType.length];
            for (int i = 0; i < resType.length; i++) {
                this.resType[i] = resType[i].equals("i-1") ? -1 : 0;
            }
            this.atomTypes = atomTypes.clone();
            this.atomTypeIndex = new AtomEnum[atomTypes.length];
            this.requireSign = requireSign;
            this.positive = positive;
            this.ambiguousRes = ambiguousRes;
            for (int i = 0; i < atomTypes.length; i++) {
                atomTypeIndex[i] = AtomEnum.valueOf(atomTypes[i].toUpperCase());
            }
        }

        public String toString() {
            StringBuilder sBuilder = new StringBuilder();
            sBuilder.append(peak.getName()).append(" ");
            for (int res : resType) {
                sBuilder.append(res).append(" ");
            }
            for (String atomType : atomTypes) {
                sBuilder.append(atomType).append(" ");
            }
            for (AtomEnum atomTypeI : atomTypeIndex) {
                sBuilder.append(atomTypeI).append(" ");
            }
            sBuilder.append(positive).append(" ").append(requireSign);
            return sBuilder.toString();
        }
    }

    public static class PeakMatch {

        final Peak peak;
        final double prob;
        final AtomEnum[] atomEnums;
        final boolean[] intraResidue;

        PeakMatch(Peak peak, double prob) {
            this.peak = peak;
            this.prob = prob;
            atomEnums = new AtomEnum[peak.getNDim()];
            intraResidue = new boolean[peak.getNDim()];
        }

        void setIntraResidue(int dim, boolean state) {
            intraResidue[dim] = state;
        }

        void setIndex(int dim, AtomEnum atomEnum) {
            atomEnums[dim] = atomEnum;
        }

        public boolean getIntraResidue(int dim) {
            return intraResidue[dim];
        }

        public boolean getPositive() {
            return peak.getIntensity() > 0.0;
        }

        public AtomEnum getIndex(int dim) {
            return atomEnums[dim];
        }

        public Peak getPeak() {
            return peak;
        }

        public boolean isType(PeakList peakList, String aName, boolean intraMode) {
            boolean ok;
            if (peak.getPeakList() != peakList) {
                ok = false;
            } else {
                boolean dimOK = false;
                for (int i = 0; i < atomEnums.length; i++) {
                    if (atomEnums[i] != null) {
                        String curName = atomEnums[i].name;
                        if (aName.equalsIgnoreCase(curName) && (intraMode == intraResidue[i])) {
                            dimOK = true;
                            break;
                        }
                    }
                }
                ok = dimOK;
            }
            return ok;

        }

        public String toString() {
            StringBuilder sBuilder = new StringBuilder();
            sBuilder.append(peak.getName()).append(" ").append(prob);
            for (int i = 0; i < atomEnums.length; i++) {
                sBuilder.append(" ").append(atomEnums[i]).append(" ").append(intraResidue[i]);
            }
            return sBuilder.toString();
        }

    }

    public SpinSystem(Peak peak, SpinSystems spinSystems) {
        this.spinSystems = spinSystems;
        this.rootPeak = peak;
        addPeak(peak, 1.0);
        peakToSpinSystemMap.put(peak.getIdNum(), this);
        shiftValues[0] = new EnumMap<>(AtomEnum.class);
        shiftValues[1] = new EnumMap<>(AtomEnum.class);
    }

    public static Optional<SpinSystem> spinSystemFromPeak(Peak peak) {
        return Optional.ofNullable(peakToSpinSystemMap.get(peak.getIdNum()));
    }

    public Peak getRootPeak() {
        return rootPeak;
    }

    public int getId() {
        return rootPeak.getIdNum();
    }


    public List<PeakMatch> peakMatches() {
        return peakMatches;
    }

    public int getNPeaksWithList(PeakList peakList) {
        int n = 0;
        for (PeakMatch match : peakMatches) {
            if (peakList == match.getPeak().getPeakList()) {
                n++;
            }
        }
        return n;
    }

    public static class AtomPresent {

        final String name;
        final boolean intraResidue;
        final boolean present;

        public String getName() {
            return name;
        }

        public boolean isIntraResidue() {
            return intraResidue;
        }

        public boolean isPresent() {
            return present;
        }

        public AtomPresent(String name, boolean intraResidue, boolean present) {
            this.name = name.toUpperCase();
            this.intraResidue = intraResidue;
            this.present = present;
        }

    }

    public List<AtomPresent> getTypesPresent(TypeInfo typeInfo, PeakList peakList, int iDim) {
        purgeDeleted();
        String[] names = typeInfo.getNames(iDim);
        List<AtomPresent> result = new ArrayList<>();
        boolean[] intraResidue = typeInfo.getIntraResidue(iDim);
        for (int i = 0; i < names.length; i++) {
            boolean ok = false;
            for (PeakMatch peakMatch : peakMatches) {
                if (peakMatch.isType(peakList, names[i], intraResidue[i])) {
                    ok = true;
                    break;
                }
            }
            AtomPresent atomPresent = new AtomPresent(names[i], intraResidue[i], ok);
            result.add(atomPresent);
        }
        return result;
    }

    public final void addPeak(Peak peak, double prob) {
        PeakMatch peakMatch = new PeakMatch(peak, prob);
        peakMatches.add(peakMatch);
    }

    public void removePeak(Peak peak) {
        for (var peakMatch : peakMatches) {
            if (peak == peakMatch.peak) {
                peakMatches.remove(peakMatch);
                break;
            }
        }
    }

    public static int getNPeaksForType(int k, AtomEnum atomEnum) {
        return k == 0 ? atomEnum.nPrevious : atomEnum.nThis;
    }

    public EnumMap<AtomEnum, ShiftValue> getShiftValues(int k) {
        return shiftValues[k];
    }

    public Optional<Double> getValue(int dir, AtomEnum atomEnum) {
        Double value = null;
        ShiftValue shiftValue =shiftValues[dir].getOrDefault(atomEnum, null);
        if (shiftValue != null) {
            value =  shiftValue.value;
        }
        return Optional.ofNullable(value);
    }

    public Optional<Double> getRange(int dir, AtomEnum atomEnum) {
        Double range = null;
        ShiftValue shiftValue =shiftValues[dir].getOrDefault(atomEnum, null);
        if (shiftValue != null) {
            range =  shiftValue.range;
        }
        return Optional.ofNullable(range);
    }

    public Optional<Integer> getNValues(int dir, AtomEnum atomEnum) {
        Integer nValues = null;
        ShiftValue shiftValue =shiftValues[dir].getOrDefault(atomEnum, null);
        if (shiftValue != null) {
            nValues =  shiftValue.n();
        }
        return Optional.ofNullable(nValues);
    }

    public Optional<SpinSystemMatch> confirmP() {
        return Optional.ofNullable(confirmP);
    }

    public void setConfirmP(SpinSystemMatch confirmP) {
        this.confirmP = confirmP;
    }

    public Optional<SpinSystemMatch> confirmS() {
        return Optional.ofNullable(confirmS);
    }

    public void setConfirmS(SpinSystemMatch confirmS) {
        this.confirmS = confirmS;
    }

    public Optional<SeqFragment> fragment() {
        return Optional.ofNullable(fragment);
    }

    public void setFragment(SeqFragment fragment) {
        this.fragment = fragment;
    }

    public boolean confirmed(SpinSystemMatch spinSys, boolean prev) {
        boolean result = false;
        if (prev) {
            if (confirmP().isPresent()) {
                SpinSystemMatch prevMatch = confirmP().get();
                result = prevMatch.spinSystemB == this && prevMatch.spinSystemA == spinSys.spinSystemA;
            }
        } else {
            if (confirmS().isPresent()) {
                SpinSystemMatch nextMatch = confirmS().get();
                result = nextMatch.spinSystemA == this && nextMatch.spinSystemB == spinSys.spinSystemB;
            }
        }
        return result;
    }

    public boolean confirmed(boolean prev) {
        return prev ? confirmP().isPresent() : confirmS().isPresent();
    }

    public void confirm(SpinSystemMatch spinSysMatch, boolean prev) {
        if (confirmed(prev)) {
            return;
        }
        if (prev) {
            SpinSystem target = spinSysMatch.spinSystemA;
            setConfirmP(spinSysMatch);
            target.setConfirmS(spinSysMatch);
        } else {
            SpinSystem target = spinSysMatch.spinSystemB;
            setConfirmS(spinSysMatch);
            target.setConfirmP(spinSysMatch);
        }
        SeqFragment fragment = SeqFragment.join(spinSysMatch, false);
        if (fragment != null) {
            fragment.dump();
        }
    }

    public void unconfirm(SpinSystemMatch spinSysMatch, boolean prev) {
        if (!confirmed(spinSysMatch, prev)) {
            return;
        }
        if (prev) {
            SpinSystem target = spinSysMatch.spinSystemA;
            setConfirmP(null);
            target.setConfirmS(null);
        } else {
            SpinSystem target = spinSysMatch.spinSystemB;
            setConfirmS(null);
            target.setConfirmP(null);
        }
        List<SeqFragment> fragments = SeqFragment.remove(spinSysMatch, false);
        for (SeqFragment fragment : fragments) {

            if (fragment != null) {
                fragment.dump();
            }
        }
    }

    public Optional<SeqFragment> getFragment() {
        return fragment();
    }

    public static int[] getCounts(PeakList peakList) {
        int nDim = peakList.getNDim();
        int[] counts = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            SpectralDim sDim = peakList.getSpectralDim(i);
            String fullPattern = sDim.getPattern();
            String[] resAtoms = fullPattern.split("\\.");
            String[] resPats = resAtoms[0].split(",");
            String[] atomPats = resAtoms[1].split(",");
            counts[i] = resPats.length * atomPats.length;
        }
        return counts;
    }

    List<ResAtomPattern> getPatterns(Peak peak) {
        int nDim = peak.getNDim();
        String[][] allResPats = new String[nDim][];
        String[][] allAtomPats = new String[nDim][];
        int[] counts = new int[nDim];
        int nResPats = 1;
        for (int i = 0; i < nDim; i++) {
            SpectralDim sDim = peak.getPeakList().getSpectralDim(i);
            String fullPattern = sDim.getPattern();
            String[] resAtoms = fullPattern.split("\\.");
            String[] resPats = resAtoms[0].split(",");
            if (resPats.length > nResPats) {
                nResPats = resPats.length;
            }
            String[] atomPats = resAtoms[1].split(",");
            counts[i] = resPats.length * atomPats.length;
            allResPats[i] = new String[counts[i]];
            allAtomPats[i] = new String[counts[i]];
            int j = 0;
            for (String resPat : resPats) {
                for (String atomPat : atomPats) {
                    allResPats[i][j] = resPat;
                    allAtomPats[i][j] = atomPat;
                    j++;
                }
            }
        }
        MultidimensionalCounter counter = new MultidimensionalCounter(counts);
        Iterator iter = counter.iterator();
        List<ResAtomPattern> result = new ArrayList<>();
        while (iter.hasNext()) {
            iter.next();
            int[] indices = iter.getCounts();
            String[] resPats = new String[nDim];
            String[] atomPats = new String[nDim];
            boolean requireSign = false;
            boolean positive = false;
            for (int i = 0; i < nDim; i++) {
                resPats[i] = allResPats[i][indices[i]];
                atomPats[i] = allAtomPats[i][indices[i]];
                if (atomPats[i].endsWith("-")) {
                    atomPats[i] = atomPats[i].substring(0, atomPats[i].length() - 1);
                    requireSign = true;
                    positive = false;
                } else if (atomPats[i].endsWith("+")) {
                    atomPats[i] = atomPats[i].substring(0, atomPats[i].length() - 1);
                    requireSign = true;
                    positive = true;
                }
            }
            ResAtomPattern resAtomPattern = new ResAtomPattern(peak, resPats, atomPats, requireSign, positive, nResPats > 1);
            result.add(resAtomPattern);
        }
        return result;
    }

    double getProb(String aName, double ppm) {
        if (aName.equalsIgnoreCase("ca")) {
            if (ppm < 38.0) {
                return 0.0;
            }
        }
        return 1.0;
    }

    boolean checkPat(ResAtomPattern pattern, double intensity) {
        boolean ok = !pattern.requireSign || ((!pattern.positive || (!(intensity < 0.0))) && (pattern.positive || (!(intensity > 0.0))));

        boolean isGly = false;
        if (ok) {
            for (int i = 0; i < pattern.atomTypes.length; i++) {
                String aName = pattern.atomTypes[i];
                double shift = pattern.peak.getPeakDim(i).getAdjustedChemShiftValue();
                if (aName.equalsIgnoreCase("ca")) {
                    if (shift < 50.0) {
                        isGly = true;
                    }
                }
                double ppmProb = getProb(aName, shift);
                if (ppmProb < 1.0e-6) {
                    ok = false;
                    break;
                }
            }
        }
        if (ok) {
            if (pattern.ambiguousRes) {
                boolean isInter = false;
                for (int iRes : pattern.resType) {
                    if (iRes == -1) {
                        isInter = true;
                        break;
                    }
                }
                // 
                double limit = isGly ? 1.2 : 0.95;

                if (isInter && (Math.abs(intensity) > limit)) {
                    ok = false;
                }
            }
        }
        return ok;
    }

    double[] shiftRange(List<Double> shifts) {
        double sum = 0.0;
        double min = Double.MAX_VALUE;
        double max = Double.NEGATIVE_INFINITY;
        for (double shift : shifts) {
            sum += shift;
            min = Math.min(min, shift);
            max = Math.max(max, shift);
        }
        double range = max - min;
        double mean = sum / shifts.size();
        return new double[]{mean, range};
    }

    void dumpShifts(EnumMap<AtomEnum, List<Double>>[] shiftList) {
        double score = analyzeShifts(shiftList);
        if (score > 0.0) {
            double pMissing = Math.exp(-1.0);
            int nAtoms = 0;
            double pCum = 1.0;
            boolean[] isGly = new boolean[2];
            for (int k = 0; k < 2; k++) {
                List<Double> caShifts = shiftList[k].getOrDefault(AtomEnum.CA, Collections.EMPTY_LIST);
                if (!caShifts.isEmpty()) {
                    double caShift = shiftRange(caShifts)[0];
                    if (caShift < 50.0) {
                        isGly[k] = true;
                    }
                }
            }
            for (int k = 0; k < 2; k++) {
                for (AtomEnum atomEnum : AtomEnum.values()) {
                    List<Double> shifts = shiftList[k].getOrDefault(atomEnum, Collections.EMPTY_LIST);
                    int nExpected = spinSystems.runAbout.getExpected(k, atomEnum);
                    if (isGly[k] && (atomEnum == AtomEnum.CB)) {
                        nExpected = 0;
                    }
                    if (!shifts.isEmpty()) {
                        int nShifts = shifts.size();
                        if (nShifts < nExpected) {
                            pCum *= Math.pow(pMissing, nExpected - nShifts);
                        }
                        double shift = shiftRange(shifts)[0];
                        System.out.printf("%3s %5.1f %2d %2d ", atomEnum.name, shift, nShifts, nExpected);
                    } else {
                        System.out.printf("%3s %5.1f %2d %2d ", atomEnum.name, 0.0, 0, nExpected);
                    }
                }
                if (k == 0) {
                    System.out.print("     ");
                } else {
                    System.out.println(" " + score + " " + pCum + " " + isGly[0] + " " + isGly[1]);
                }
            }
        }
    }

    double analyzeShifts(EnumMap<AtomEnum, List<Double>>[] shiftList) {
        double pMissing = Math.exp(-1.0);
        int nAtoms = 0;
        double pCum = 1.0;
        boolean[] isGly = new boolean[2];
        for (int k = 0; k < 2; k++) {
            List<Double> shifts = shiftList[k].getOrDefault(AtomEnum.CA, Collections.EMPTY_LIST);

            if (!shifts.isEmpty()) {
                double caShift = shiftRange(shifts)[0];
                if (caShift < 50.0) {
                    isGly[k] = true;
                }
            }
        }
        for (int k = 0; k < 2; k++) {
            for (AtomEnum atomEnum : AtomEnum.values()) {
                List<Double> shifts = shiftList[k].getOrDefault(atomEnum, Collections.EMPTY_LIST);
                int nShifts = shifts.size();

                int nExpected = spinSystems.runAbout.getExpected(k, atomEnum);

                if (isGly[k] && (atomEnum == AtomEnum.CB)) {
                    nExpected = 0;
                }
                if ((nExpected == 0) && (nShifts > 0)) {
                    pCum = 0.0;
                    break;
                } else {
                    if (nExpected > 0) {
                        if (nShifts > 0) {
                            double[] range = shiftRange(shifts);
                            double mean = range[0];
                            for (double shift : shifts) {
                                double delta = Math.abs(mean - shift);
                                pCum *= Math.exp(-delta / atomEnum.tol);
                            }
                        }
                        if (nShifts < nExpected) {
                            pCum *= Math.pow(pMissing, nExpected - nShifts);
                        }
                    }
                }
            }
        }
        return pCum;
    }

    void saveShifts(EnumMap<AtomEnum, List<Double>>[] atomShifts) {
        for (int k = 0; k < 2; k++) {
            for (var entry : atomShifts[k].entrySet()) {
                var shifts = entry.getValue();
                int nShifts = shifts.size();
                if (nShifts > 0) {
                    double[] range = shiftRange(shifts);
                    ShiftValue shiftValue = new ShiftValue(nShifts, range[0], range[1]);
                    shiftValues[k].put(entry.getKey(), shiftValue);
                }

            }
        }
    }

    void writeShifts(EnumMap<AtomEnum, List<Double>>[] shiftList) {
        int nAtoms = 0;

        for (int k = 0; k < 2; k++) {
            for (AtomEnum atomEnum : AtomEnum.values()) {
                List<Double> shifts = shiftList[k].getOrDefault(atomEnum, Collections.emptyList());
                int nShifts = shifts.size();
                if (nShifts > 0) {
                    double[] range = shiftRange(shifts);
                    System.out.printf(" %6.2f:%1d", range[0], shifts.size());
                    nAtoms += shifts.size();
                } else {
                    System.out.print("   NA    ");
                }
            }
        }
        double pCum = analyzeShifts(shiftList);
        System.out.printf("  nA %2d %6.4f\n", nAtoms, pCum);
    }

    boolean addShift(int nPeaks, List<ResAtomPattern>[] resAtomPatterns, EnumMap<AtomEnum, List<Double>>[] shiftList, int[] pt) {
        int j = 0;
        for (int i = 0; i < nPeaks; i++) {
            int k = 0;
            if (resAtomPatterns[i].size() > 1) {
                k = pt[j++];
            }
            if (!resAtomPatterns[i].isEmpty()) {
                ResAtomPattern resAtomPattern = resAtomPatterns[i].get(k);
                if (resAtomPattern != null) {
                    int nDim = resAtomPattern.atomTypeIndex.length;
                    for (int iDim = 0; iDim < nDim; iDim++) {
                        AtomEnum atomEnum = resAtomPattern.atomTypeIndex[iDim];
                        int iRes = resAtomPattern.resType[iDim];
                        List<Double> shifts = shiftList[iRes + 1].computeIfAbsent(atomEnum, key -> new ArrayList<>());
                        double newValue = resAtomPattern.peak.getPeakDim(iDim).getChemShiftValue();
                        if (!shifts.isEmpty()) {
                            double current = shifts.get(0);
                            if (Math.abs(current - newValue) > 1.5 * atomEnum.tol) {
                                return false;
                            }
                        }
                        shifts.add(newValue);
                    }
                }
            }
        }
        return true;

    }

    double[] getNormalizedIntensities() {
        purgeDeleted();
        double[] intensities = new double[peakMatches.size()];
        Map<String, Double> intensityMap = new HashMap<>();
        Map<String, List<PeakMatch>> listOfMatches = new HashMap<>();

        for (PeakMatch peakMatch : peakMatches) {
            Peak peak = peakMatch.peak;
            peak.setFlag(1, false);
            String peakListName = peak.getPeakList().getName();
            double maxIntensity = 0.0;
            if (intensityMap.containsKey(peakListName)) {
                maxIntensity = intensityMap.get(peakListName);
            }
            maxIntensity = Math.max(maxIntensity, Math.abs(peak.getIntensity()));
            intensityMap.put(peakListName, maxIntensity);
            List<PeakMatch> listMatches = listOfMatches.computeIfAbsent(peakListName, k -> new ArrayList<>());
            listMatches.add(peakMatch);
        }
        for (Entry<String, List<PeakMatch>> entry : listOfMatches.entrySet()) {
            String peakListName = entry.getKey();
            String typeName = spinSystems.runAbout.peakListTypes.get(peakListName);
            TypeInfo typeInfo = spinSystems.runAbout.typeInfoMap.get(typeName);
            int nExpected = typeInfo.nTotal;
            List<PeakMatch> matches = entry.getValue();
            matches.sort(Comparator.comparingDouble(a -> Math.abs(a.getPeak().getIntensity())));
            int nExtra = matches.size() - nExpected;
            for (int i = 0; i < nExtra; i++) {
                matches.get(i).getPeak().setFlag(1, true);
            }

        }
        int iPeak = 0;
        for (PeakMatch peakMatch : peakMatches) {
            String peakListName = peakMatch.peak.getPeakList().getName();
            double maxIntensity = intensityMap.get(peakListName);
            intensities[iPeak++] = peakMatch.peak.getIntensity() / maxIntensity;
        }
        return intensities;
    }

    boolean getShifts(int nPeaks, List<ResAtomPattern>[] resAtomPatterns,
                      EnumMap<AtomEnum, List<Double>>[] shiftList, int[] pt
    ) {
        shiftList[0] = new EnumMap<>(AtomEnum.class);
        shiftList[1] = new EnumMap<>(AtomEnum.class);

        return addShift(nPeaks, resAtomPatterns, shiftList, pt);
    }

    public void calcCombinations(boolean display) {
        purgeDeleted();
        double[] intensities = getNormalizedIntensities();
        int nPeaks = peakMatches.size();
        List<ResAtomPattern>[] resAtomPatterns = new List[nPeaks];
        int nCountable = 0;
        int iPeak = 0;
        int[] counts = new int[nPeaks];
        for (PeakMatch peakMatch : peakMatches) {
            List<ResAtomPattern> okPats = new ArrayList<>();
            Peak peak = peakMatch.peak;
            if (!peak.getFlag(1)) {
                List<ResAtomPattern> patterns = getPatterns(peak);
                double intensity = intensities[iPeak];
                for (ResAtomPattern resAtomPattern : patterns) {
                    boolean added;
                    if (checkPat(resAtomPattern, intensity)) {
                        okPats.add(resAtomPattern);
                        added = true;
                    } else {
                        added = false;

                    }
                    if (display) {
                        System.out.println(resAtomPattern + " " + intensity + " " + added);

                    }
                }
            }
            // allow all peaks but root peak to be unused (artifact)
            if (iPeak != 0) {
                okPats.add(null);
            }
            resAtomPatterns[iPeak] = okPats;
            if (okPats.size() > 1) {
                nCountable++;
            }
            counts[iPeak] = okPats.size();
            iPeak++;
        }

        if (nCountable == 0) {
            EnumMap<AtomEnum, List<Double>>[] shiftList = new EnumMap[2];
            shiftList[0] = new EnumMap<>(AtomEnum.class);
            shiftList[1] = new EnumMap<>(AtomEnum.class);
            addShift(nPeaks, resAtomPatterns, shiftList, null);

        } else {
            int[] indices = new int[nCountable];
            int j = 0;
            for (int i = 0; i < nPeaks; i++) {
                if (resAtomPatterns[i].size() > 1) {
                    indices[j++] = resAtomPatterns[i].size();
                }
            }
            MultidimensionalCounter counter = new MultidimensionalCounter(indices);
            Iterator iter = counter.iterator();
            double best = 0.0;
            int bestIndex = -1;
            EnumMap<AtomEnum, List<Double>>[] shiftList = new EnumMap[2];
            shiftList[0] = new EnumMap<>(AtomEnum.class);
            shiftList[1] = new EnumMap<>(AtomEnum.class);
            while (iter.hasNext()) {
                iter.next();
                int[] pt = iter.getCounts();
                boolean validShifts = getShifts(nPeaks, resAtomPatterns, shiftList, pt);
                if (display) {
                    dumpShifts(shiftList);
                }
                if (validShifts) {
                    double prob = analyzeShifts(shiftList);
                    if (prob > best) {
                        best = prob;
                        bestIndex = iter.getCount();
                    }
                }
            }
            if (display && (bestIndex >= 0)) {
                int[] pt = counter.getCounts(bestIndex);
                boolean validShifts = getShifts(nPeaks, resAtomPatterns, shiftList, pt);
                System.out.println("best is " + bestIndex);
                dumpShifts(shiftList);
            }
            if (!display && (bestIndex >= 0)) {
                int[] pt = counter.getCounts(bestIndex);
                setUserFields(resAtomPatterns, pt);
                updateSpinSystem();
            }
        }
    }

    void getLinkedPeaks() {
        PeakList refList = rootPeak.getPeakList();
        for (Peak pkB : PeakList.getLinks(rootPeak, 0)) {// fixme calculate correct dim
            if (rootPeak != pkB) {
                PeakList peakListB = pkB.getPeakList();
                if (refList != peakListB) {
                    int[] aMatch = matchDims(refList, peakListB);
                    double f = comparePeaks(rootPeak, pkB, aMatch);
                    if (f >= 0.0) {
                        addPeak(pkB, f);
                    }
                }
            }
        }
        int nPeaks = peakMatches.size();
        System.out.println("cluster " + rootPeak.getName() + " " + nPeaks);

    }

    public void updateSpinSystem() {
        purgeDeleted();
        EnumMap<AtomEnum, List<Double>>[] atomShifts = new EnumMap[2];
        atomShifts[0] = new EnumMap<>(AtomEnum.class);
        atomShifts[1] = new EnumMap<>(AtomEnum.class);
        for (PeakMatch peakMatch : peakMatches) {
            Peak peak = peakMatch.peak;
            int iDim = 0;
            for (PeakDim peakDim : peak.getPeakDims()) {
                String userField = peakDim.getUser();
                if (userField.contains(".")) {
                    int dotIndex = userField.indexOf('.');
                    String resType = userField.substring(0, dotIndex);
                    String atomType = userField.substring(dotIndex + 1).toUpperCase();
                    int k = resType.endsWith("-1") ? 0 : 1;
                    AtomEnum atomEnum = AtomEnum.valueOf(atomType);
                    if (atomEnum != null) {
                        var shiftList = atomShifts[k].computeIfAbsent(atomEnum, key -> new ArrayList<>());
                        shiftList.add(peakDim.getChemShift().doubleValue());
                        peakMatch.setIndex(iDim, atomEnum);
                        peakMatch.setIntraResidue(iDim, k == 1);
                    }
                }
                iDim++;

            }
        }
        saveShifts(atomShifts);
    }

    public boolean userFieldsSet() {
        boolean userFieldSet = false;
        for (PeakMatch match : peakMatches) {
            Peak peak = match.peak;
            for (PeakDim peakDim : peak.getPeakDims()) {
                if (!peakDim.getUser().isBlank()) {
                    userFieldSet = true;
                    break;
                }
            }
            if (userFieldSet) {
                break;
            }
        }
        return userFieldSet;
    }

    void setUserFields(List<ResAtomPattern>[] resAtomPatterns, int[] pt) {
        StringBuilder sBuilder = new StringBuilder();
        String linkDim = RunAbout.getNDimName(rootPeak.getPeakList()); // fixme
        List<Peak> linkedPeaks = PeakList.getLinks(rootPeak,
                rootPeak.getPeakList().getSpectralDim(linkDim).getIndex());
        for (Peak peak : linkedPeaks) {
            for (PeakDim peakDim : peak.getPeakDims()) {
                peakDim.setUser("");
            }
        }

        int j = 0;
        for (List<ResAtomPattern> atomPattern : resAtomPatterns) {
            int k = 0;
            if (atomPattern.size() > 1) {
                k = pt[j++];
            }
            if (!atomPattern.isEmpty()) {
                ResAtomPattern resAtomPattern = atomPattern.get(k);
                if (resAtomPattern != null) {
                    int nDim = resAtomPattern.atomTypeIndex.length;
                    for (int iDim = 0; iDim < nDim; iDim++) {
                        AtomEnum atomEnum = resAtomPattern.atomTypeIndex[iDim];
                        int iRes = resAtomPattern.resType[iDim];
                        sBuilder.setLength(0);
                        sBuilder.append("i");
                        if (iRes < 0) {
                            sBuilder.append("-1");
                        }
                        sBuilder.append(".").append(atomEnum.name);
                        resAtomPattern.peak.getPeakDim(iDim).setUser(sBuilder.toString());
                    }
                }
            }
        }
    }

    public Optional<SpinSystemMatch> compare(SpinSystem spinSysB, boolean prev) {
        int idxB = prev ? 1 : 0;
        int idxA = prev ? 0 : 1;
        double sum = 0.0;
        boolean ok = false;
        int nMatch = 0;
        EnumSet<AtomEnum> matchedSet = EnumSet.noneOf(AtomEnum.class);

        for (var entryA : shiftValues[idxA].entrySet()) {
            var shiftValueB = spinSysB.shiftValues[idxB].get(entryA.getKey());
            double vA = entryA.getValue().value;
            double vB = shiftValueB != null ? shiftValueB.value : Double.NaN;
            double tolA = entryA.getKey().tol;
            if (Double.isFinite(vA) && Double.isFinite(vB)) {
                double delta = Math.abs(vA - vB);
                ok = true;
                if (delta > 2.0 * tolA) {
                    ok = false;
                    break;
                } else {
                    matchedSet.add(entryA.getKey());
                    delta /= tolA;
                    sum += delta * delta;
                    nMatch++;
                }
            }
        }


        Optional<SpinSystemMatch> result = Optional.empty();
        if (ok) {
            double dis = Math.sqrt(sum);
            double score = Math.exp(-dis);
            SpinSystemMatch spinMatch;
            if (prev) {
                spinMatch = new SpinSystemMatch(spinSysB, this, score, nMatch, matchedSet);
            } else {
                spinMatch = new SpinSystemMatch(this, spinSysB, score, nMatch, matchedSet);
            }
            result = Optional.of(spinMatch);
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(rootPeak.getName());
        for (PeakMatch peakMatch : peakMatches) {
            sBuilder.append(" ");
            sBuilder.append(peakMatch.peak.getName()).append(":");
            sBuilder.append(String.format("%.2f", peakMatch.prob));
        }
        return sBuilder.toString();
    }

    public SpinSystem split() {
        purgeDeleted();
        boolean[] useDims = SpinSystems.getUseDims(spinSystems.runAbout.refList, spinSystems.runAbout.getPeakLists());
        int nUseDims = 0;
        for (boolean useDim : useDims) {
            if (useDim) {
                nUseDims++;
            }
        }
        PeakList refList = spinSystems.runAbout.refList;
        double[][] values = new double[peakMatches.size()][nUseDims];
        Peak refPeak = peakMatches.get(0).getPeak();
        int i = 0;
        for (PeakMatch peakMatch : peakMatches) {
            Peak peak = peakMatch.peak;
            PeakList.unLinkPeak(peak);
            int j = 0;
            for (int iDim = 0; iDim < useDims.length; iDim++) {
                if (useDims[iDim]) {
                    double refShift = refPeak.getPeakDim(refList.getSpectralDim(iDim).getDimName()).getChemShiftValue();
                    double shift = peak.getPeakDim(refList.getSpectralDim(iDim).getDimName()).getChemShiftValue();
                    double tol = refList.getSpectralDim(iDim).getIdTol();
                    values[i][j++] = (shift - refShift) / tol;
                }
            }
            i++;
        }

        KMeans kMeans = KMeans.lloyd(values, 2);
        double[][] centroids = kMeans.centroids();
        int[] labels = kMeans.getClusterLabel();
        double dis0 = smile.math.Math.norm(centroids[0]);
        double dis1 = smile.math.Math.norm(centroids[1]);
        int origCluster;
        int newCluster;
        if (dis0 < dis1) {
            origCluster = 0;
            newCluster = 1;
        } else {
            origCluster = 1;
            newCluster = 0;
        }
        Peak newRoot = rootPeak.getPeakList().getNewPeak();
        rootPeak.copyTo(newRoot);
        int j = 0;
        for (int iDim = 0; iDim < useDims.length; iDim++) {
            if (useDims[iDim]) {
                double tol = refList.getSpectralDim(iDim).getIdTol();
                double refShift = refPeak.getPeakDim(refList.getSpectralDim(iDim).getDimName()).getChemShiftValue();
                double newShift = centroids[newCluster][j] * tol + refShift;
                newRoot.getPeakDim(iDim).setChemShiftValue((float) newShift);
                j++;
            }
        }

        SpinSystem newSys = new SpinSystem(newRoot, spinSystems);
        spinSystems.add(newSys);

        List<PeakMatch> oldPeaks = new ArrayList<>(peakMatches);
        peakMatches.clear();
        addPeak(rootPeak, 1.0);
        i = 0;
        for (PeakMatch peakMatch : oldPeaks) {
            var matchDims = SpinSystems.matchDims(refList, peakMatch.peak.getPeakList());
            if (peakMatch.peak != rootPeak) {
                if (labels[i] == origCluster) {
                    spinSystems.addPeak(this, peakMatch.peak);
                } else {
                    spinSystems.addPeak(newSys, peakMatch.peak);
                }
            }
            i++;
        }

        return newSys;

    }

    public List<SpinSystemMatch> getMatchToPrevious() {
        return spinMatchP;
    }

    public int getConfirmedPrevious() {
        if (spinMatchP.isEmpty() || confirmP().isEmpty()) {
            return 0;
        } else {
            return spinMatchP.indexOf(confirmP().get());
        }
    }

    public int getConfirmedNext() {
        if (spinMatchS.isEmpty() || confirmS().isEmpty()) {
            return 0;
        } else {
            return spinMatchS.indexOf(confirmS().get());
        }
    }

    public List<SpinSystemMatch> getMatchToNext() {
        return spinMatchS;
    }

    public void dumpPeakMatches() {
        for (PeakMatch peakMatch : peakMatches) {
            System.out.println(peakMatch.toString());
        }
    }

    public void compare() {
        spinMatchP.clear();
        spinMatchS.clear();
        double sumsP = 0.0;
        double sumsS = 0.0;
        spinMatchP.addAll(spinSystems.compare(this, true));
        spinMatchS.addAll(spinSystems.compare(this, false));
        for (var match : spinMatchP) {
            sumsP += match.score;
        }
        for (var match : spinMatchS) {
            sumsS += match.score;
        }


        for (SpinSystemMatch spinMatch : spinMatchP) {
            spinMatch.norm(sumsP);
        }
        for (SpinSystemMatch spinMatch : spinMatchS) {
            spinMatch.norm(sumsS);
        }

        spinMatchP.sort((s1, s2) -> Double.compare(s2.score, s1.score));
        spinMatchS.sort((s1, s2) -> Double.compare(s2.score, s1.score));
    }

    public void purgeDeleted() {
        for (int i = peakMatches.size() - 1; i >= 0; i--) {
            PeakMatch peakMatch = peakMatches.get(i);
            if (!peakMatch.peak.isValid() || peakMatch.peak.isDeleted()) {
                PeakList.unLinkPeak(peakMatch.peak);
                peakMatches.remove(i);
            }
        }
    }

    private void removeMatches(SpinSystem spinSys) {
        for (int i = spinMatchP.size() - 1; i >= 0; i--) {
            var match = spinMatchP.get(i);
            if (match.getSpinSystemA() == spinSys) {
                spinMatchP.remove(i);
            }
        }
        for (int i = spinMatchS.size() - 1; i >= 0; i--) {
            var match = spinMatchS.get(i);
            if (match.getSpinSystemB() == spinSys) {
                spinMatchS.remove(i);
            }
        }
    }

    public void score() {
        List<List<AtomShiftValue>> shiftValues = SeqFragment.getShiftsForSystem(this);
        Molecule molecule = Molecule.getActive();
        List<ResidueSeqScore> residueSeqScores = SeqFragment.scoreShifts(molecule, shiftValues, null);
        for (ResidueSeqScore residueSeqScore : residueSeqScores) {
            System.out.println(residueSeqScore.getFirstResidue() + " " + residueSeqScore.getNResidues() + " " + residueSeqScore.getScore());
        }
    }

    public void delete() {
        for (var spinMatch : spinMatchP) {
            var spinA = spinMatch.getSpinSystemA();
            spinA.removeMatches(this);
        }
        for (var spinMatch : spinMatchS) {
            var spinB = spinMatch.getSpinSystemB();
            spinB.removeMatches(this);
        }
        if (confirmP().isPresent()) {
            var match = confirmP().get();
            match.getSpinSystemA().unconfirm(match, false);
        }
        if (confirmS().isPresent()) {
            var match = confirmS().get();
            match.getSpinSystemB().unconfirm(match, false);
        }
        for (var peakMatch : peakMatches) {
            peakMatch.getPeak().delete();
        }
        purgeDeleted();
        spinSystems.remove(this);
        spinSystems.runAbout.refList.compress();
    }

    private boolean isRecipricol(SpinSystemMatch match, boolean prev) {
        var thisSys = prev ? match.getSpinSystemB() : match.getSpinSystemA();
        var otherSys = prev ? match.getSpinSystemA() : match.getSpinSystemB();
        boolean hasAMatch = prev ? !otherSys.getMatchToNext().isEmpty() : !otherSys.getMatchToPrevious().isEmpty();
        var recipSys = prev ? otherSys.getMatchToNext().get(0).getSpinSystemB()
                : otherSys.getMatchToPrevious().get(0).getSpinSystemA();
        return thisSys == recipSys;
    }

    public static void extend(SpinSystem startSys, double minScore) {
        SpinSystem spinSys = startSys;
        while (spinSys.confirmP().isPresent()) {
            SpinSystemMatch match = spinSys.confirmP().get();
            spinSys = match.getSpinSystemA();
        }
        extendPrevious(spinSys, minScore);
        spinSys = startSys;
        while (spinSys.confirmS().isPresent()) {
            SpinSystemMatch match = spinSys.confirmS().get();
            spinSys = match.getSpinSystemB();
        }
        extendNext(spinSys, minScore);
    }

    private static void extendPrevious(SpinSystem startSys, double minScore) {
        SpinSystem spinSys = startSys;
        while (spinSys.confirmP().isEmpty()) {
            if (spinSys.getMatchToPrevious().isEmpty()) {
                break;
            }
            var match = spinSys.getMatchToPrevious().get(0);
            boolean isRecipricol = spinSys.isRecipricol(match, true);
            boolean viable = SeqFragment.testFrag(match);
            int n = match.getN();
            double score = match.getScore();
            boolean available = !match.spinSystemA.confirmed(false);
            if (isRecipricol && available && viable && (n == 3) && (score > minScore)) {
                spinSys.confirm(match, true);
                spinSys = match.getSpinSystemA();
            } else {
                break;
            }
        }
    }

    private static void extendNext(SpinSystem startSys, double minScore) {
        SpinSystem spinSys = startSys;
        while (spinSys.confirmS().isEmpty()) {
            if (spinSys.getMatchToNext().isEmpty()) {
                break;
            }
            var match = spinSys.getMatchToNext().get(0);
            boolean isRecipricol = spinSys.isRecipricol(match, false);
            boolean viable = SeqFragment.testFrag(match);
            int n = match.getN();
            double score = match.getScore();
            boolean available = !match.spinSystemB.confirmed(true);
            if (isRecipricol && available && viable && (n == 3) && (score > minScore)) {
                spinSys.confirm(match, false);
                spinSys = match.getSpinSystemB();
            } else {
                break;
            }
        }
    }

    void assignPeaksInSystem(Residue residue) {
        for (var peakMatch : peakMatches()) {
            for (var peakDim : peakMatch.getPeak().getPeakDims()) {
                Optional<Atom> atomOpt = AtomResPattern.setLabelFromUserField(peakDim, residue);
                atomOpt.ifPresent(atom -> {
                    AtomResonance atomResonance = (AtomResonance) peakDim.getResonance();
                    atomResonance.setAtom(atom);
                });
            }
        }
    }

    void clearPeaksInSystem() {
        for (var peakMatch : peakMatches()) {
            for (var peakDim : peakMatch.getPeak().getPeakDims()) {
                peakDim.setLabel("");
                AtomResonance atomResonance = (AtomResonance) peakDim.getResonance();
                atomResonance.setAtom(null);

            }
        }
    }

    String getSystemSTARString() {
        String confirmedPrev = confirmP().isPresent() ? String.valueOf(confirmP().get().getSpinSystemA().getId()) : ".";
        String confirmedNext = confirmS().isPresent() ? String.valueOf(confirmS().get().getSpinSystemB().getId()) : ".";
        String fragmentID = fragment().isPresent() ? String.valueOf(fragment().get().id) : ".";
        String fragmentPos = fragment().isPresent() ? String.valueOf(fragmentPosition) : ".";
        return String.format("%3d %2d %3d %4s %4s %4s %3s", getId(), rootPeak.getPeakList().getId(),
                rootPeak.getIdNum(), confirmedPrev, confirmedNext, fragmentID, fragmentPos);
    }

    int getPeakSTARString(StringBuilder sBuilder, int i) {
        for (PeakMatch match : peakMatches) {
            sBuilder.append(String.format("%4d %4d %2d %3d %10.7f\n", i++, getId(), match.peak.getPeakList().getId(), match.peak.getIdNum(), match.prob));
        }
        return i;
    }
}