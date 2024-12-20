package org.nmrfx.structure.seqassign;

import org.nmrfx.chemistry.Residue;
import org.nmrfx.chemistry.io.NMRStarWriter;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.star.Loop;
import org.nmrfx.star.ParseException;
import org.nmrfx.star.Saveframe;
import org.nmrfx.star.SaveframeWriter;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author brucejohnson
 */
public class RunAbout implements SaveframeWriter {
    static final List<String> peakListTags = List.of("ID", "Spectral_peak_list_ID");
    static final private Map<Integer, RunAbout> runaboutMap = new HashMap<>();
    int id = 1;
    SpinSystems spinSystems = new SpinSystems(this);
    Map<String, PeakList> peakListMap = new LinkedHashMap<>();
    PeakList refList;
    List<PeakList> peakLists = new ArrayList<>();
    Map<String, List<String>> dimLabels;
    Map<String, String> peakListTypes = new HashMap<>();
    Map<String, DatasetBase> datasetMap = new HashMap<>();
    Map<String, List<String>> aTypeMap = new HashMap<>();
    Map<String, TypeInfo> typeInfoMap = new HashMap<>();

    EnumMap<SpinSystem.AtomEnum, Integer>[] countMap = new EnumMap[2];

    boolean active = false;
    Map<Residue, SpinSystem> residueSpinSystemsMap = new HashMap<>();

    List<Map<String, Object>> typeList;

    public RunAbout() {
        ProjectBase.getActive().addSaveframe(this);
        runaboutMap.put(id, this);
    }

    public void close() {
        spinSystems.clearAll();
        peakListMap.clear();
        peakLists.clear();
        peakListTypes.clear();
        datasetMap.clear();
        aTypeMap.clear();
        typeInfoMap.clear();
        residueSpinSystemsMap.clear();
        runaboutMap.clear();
    }
    public static RunAbout getRunAbout(int id) {
        return runaboutMap.get(id);
    }

    public boolean isActive() {
        return active;
    }

    public SpinSystems getSpinSystems() {
        return spinSystems;
    }

    public Optional<DatasetBase> getDataset(String key) {
        Optional<DatasetBase> result;
        result = datasetMap.containsKey(key)
                ? Optional.of(datasetMap.get(key)) : Optional.empty();
        return result;
    }

    public PeakList getPeakList(String key) {
        return peakListMap.get(key);
    }

    public PeakList getRefList() {
        return refList;
    }

    public List<PeakList> getPeakLists() {
        return peakLists;
    }

    public List<String> getDimLabel(String dimType) {
        return dimLabels.get(dimType);
    }

    public int getTypeCount(String typeName) {
        return typeInfoMap.get(typeName).nTotal;
    }

    public int getExpected(int k, SpinSystem.AtomEnum atomEnum) {
        return countMap[k].getOrDefault(atomEnum, 0);
    }

    List<String> getPatterns(PeakList peakList) {
        List<String> patElems = new ArrayList<>();
        List<String> aTypes = new ArrayList<>();
        for (var sDim : peakList.getSpectralDims()) {
            String patElem = sDim.getPattern();
            String aType = "";
            if (!patElem.isBlank()) {
                patElems.add(patElem);
                int dotPos = patElem.indexOf(".");
                if (dotPos != -1) {
                    aType = patElem.substring(dotPos + 1, dotPos + 2);
                }
            }
            aTypes.add(aType);
        }
        aTypeMap.put(peakList.getExperimentType(), aTypes);
        return patElems;
    }

    public static class TypeInfo {

        final boolean[][] intraResidue;
        final String[][] names;
        final int[][] signs;
        final int nTotal;

        TypeInfo(int nDim, int nTotal) {
            intraResidue = new boolean[nDim][];
            names = new String[nDim][];
            signs = new int[nDim][];
            this.nTotal = nTotal;
        }

        void setIntraResidue(int iDim, boolean[] values) {
            intraResidue[iDim] = values;
        }

        void setNames(int iDim, String[] values) {
            names[iDim] = values;
        }

        void setSigns(int iDim, int[] values) {
            signs[iDim] = values;
        }

        String[] getNames(int iDim) {
            return names[iDim];
        }

        boolean[] getIntraResidue(int iDim) {
            return intraResidue[iDim];
        }

    }

    public TypeInfo getTypeInfo(String typeName) {
        return typeInfoMap.get(typeName);
    }

    public static List<String> getPatterns(SpectralDim sDim) {
        String pattern = sDim.getPattern();
        String[] parts = pattern.split("\\.");
        List<String> choices = new ArrayList<>();
        if (parts.length == 2) {
            String[] types = parts[0].split(",");
            String[] aNames = parts[1].split(",");
            for (String type : types) {
                for (String aName : aNames) {
                    String pat = type + "." + aName;
                    choices.add(pat);
                }
            }
        }
        return choices;
    }

    void setAtomCount(String typeName, List<String> patElems) {
        int[] dimCount = new int[patElems.size()];
        int i = 0;
        for (String elem : patElems) {
            String[] parts = elem.split("\\.");
            if (parts.length == 2) {
                String[] types = parts[0].split(",");
                String[] aNames = parts[1].split(",");
                dimCount[i] = types.length * aNames.length;
            }
            i++;
        }
        int[] dimMult = new int[dimCount.length];
        for (i = 0; i < dimCount.length; i++) {
            dimMult[i] = 1;
            for (int j = 0; j < dimCount.length; j++) {
                if (i != j) {
                    dimMult[i] *= dimCount[j];
                }
            }
        }
        int total = 1;
        for (i = 0; i < dimCount.length; i++) {
            total *= dimCount[i];
        }
        i = 0;
        TypeInfo typeInfo = new TypeInfo(patElems.size(), total);
        for (String elem : patElems) {
            String[] parts = elem.split("\\.");
            if (parts.length == 2) {
                String[] types = parts[0].split(",");
                String[] aNames = parts[1].split(",");
                int nTypes = types.length * aNames.length;
                boolean[] intraResidue = new boolean[nTypes];
                String[] allNames = new String[nTypes];
                int[] signs = new int[nTypes];

                int j = 0;

                for (String type : types) {
                    int iDir = type.endsWith("-1") ? 0 : 1;
                    for (String aName : aNames) {
                        int sign = 0;
                        if (aName.endsWith("-")) {
                            aName = aName.substring(0, aName.length() - 1);
                            sign = -1;
                        } else if (aName.endsWith("+")) {
                            aName = aName.substring(0, aName.length() - 1);
                            sign = 1;
                        }
                        aName = aName.toLowerCase();
                        SpinSystem.AtomEnum atomEnum = SpinSystem.AtomEnum.valueOf(aName.toUpperCase());
                        Integer count = countMap[iDir].getOrDefault(atomEnum, 0);
                        count += dimMult[i];
                        countMap[iDir].put(atomEnum, count);
                        intraResidue[j] = iDir == 1;
                        allNames[j] = aName;
                        signs[j] = sign;
                        j++;
                    }
                }
                typeInfo.setIntraResidue(i, intraResidue);
                typeInfo.setSigns(i, signs);
                typeInfo.setNames(i, allNames);
                typeInfoMap.put(typeName, typeInfo);
            }
            i++;
        }
    }

    public void setRefList(PeakList peakList) {
        refList = peakList;
        PeakList.clusterOrigin = refList;
    }

    public void setPeakLists(List<PeakList> lists) {
        refList = lists.get(0);
        PeakList.clusterOrigin = refList;
        countMap[0] = new EnumMap<>(SpinSystem.AtomEnum.class);
        countMap[1] = new EnumMap<>(SpinSystem.AtomEnum.class);
        peakLists.clear();
        peakListMap.clear();
        datasetMap.clear();
        peakListTypes.clear();
        peakLists.addAll(lists);

        for (var peakList : peakLists) {
            String typeName = peakList.getExperimentType();
            peakListMap.put(typeName, peakList);
            peakListTypes.put(peakList.getName(), typeName);
            datasetMap.put(typeName, DatasetBase.getDataset(peakList.getDatasetName()));
            List<String> patElems = getPatterns(peakList);
            setAtomCount(typeName, patElems);
        }
        active = true;
    }


    public Optional<String> getTypeName(String row, String dDir) {
        Optional<String> typeName = Optional.empty();
        dDir = dDir.replace("h", "i");
        dDir = dDir.replace("j", "i");
        dDir = dDir.replace("k", "i");
        for (Map<String, Object> typeMap : typeList) {
            String typeRow = (String) typeMap.get("row");
            String typeDir = (String) typeMap.get("dir");
            if (row.equals(typeRow) && dDir.equals(typeDir)) {
                typeName = Optional.of((String) typeMap.get("name"));
                break;
            }
        }
        return typeName;
    }

    public Optional<SpectralDim> getCarbonDim(PeakList peakList) {
        for (var sDim : peakList.getSpectralDims()) {
            if (!sDim.getPattern().contains("H") && !sDim.getPattern().contains("N")
                    && sDim.getPattern().contains("C")) {
                return Optional.of(sDim);
            }
        }
        return Optional.empty();
    }

    public boolean checkRowType(String pattern, String testType) {
        pattern = pattern.toUpperCase();
        String[] types = {"CB", "CA", "C"};
        String patternType = "";
        for (var type : types) {
            if (pattern.contains(type)) {
                patternType = type;
                break;
            }
        }
        return patternType.equals(testType);
    }

    public Optional<PeakList> getPeakListForCell(String row, String dDir) {
        Optional<String> typeName = Optional.empty();
        dDir = dDir.replace("h", "i");
        dDir = dDir.replace("j", "i");
        dDir = dDir.replace("k", "i");
        final String iDir = dDir;
        Optional<PeakList> result = Optional.empty();
        for (PeakList peakList : peakLists) {
            Optional<SpectralDim> sDimOpt = getCarbonDim(peakList);
            if (sDimOpt.isPresent()) {
                var sDim = sDimOpt.get();
                String pattern = sDim.getPattern();
                if (pattern.contains(".")) {
                    String[] patternParts = pattern.split("\\.", 2);
                    String resPart = patternParts[0];
                    String atomPart = patternParts[1];
                    if (checkRowType(atomPart, row)) {
                        String[] resTypes = resPart.split(",");
                        if (((resTypes.length == 2) && (iDir.equals("i"))) ||
                                ((resTypes.length == 1) && resTypes[0].equals(iDir))) {
                            result = Optional.of(peakList);
                        }
                    }
                }
            }
        }
        return result;
    }

    public List<SpectralDim> getPeakListDims(PeakList peakList, DatasetBase dataset, int[] iDims) {
        List<SpectralDim> sDims = new ArrayList<>();
        for (int iDim : iDims) {
            String dataDimName = dataset.getLabel(iDim);
            SpectralDim sDim = peakList.getSpectralDim(dataDimName);
            sDims.add(sDim);
        }
        return sDims;
    }

    public int[] getIDims(DatasetBase dataset, PeakList peakList, String typeName, List<String> dims) {
        int[] iDims = new int[dims.size()];
        int j = 0;
        for (String dim : dims) {
            String dimName;
            int sepPos = dim.indexOf("_");
            if (sepPos != -1) {
                dimName = dim.substring(0, sepPos);
            } else {
                dimName = dim;
            }
            int peakDim = aTypeMap.get(typeName).indexOf(dimName);
            String peakListDimName = peakList.getSpectralDim(peakDim).getDimName();
            iDims[j] = dataset.getDim(peakListDimName);
            j++;
        }
        return iDims;
    }

    public void assemble() {
        getSpinSystems().assembleWithClustering(refList, peakLists);
    }

    public void addLists(List<PeakList> newPeakLists) {
        getSpinSystems().addLists(refList, newPeakLists);
    }

    public void autoSetTolerance(double scale) {
        autoSetTolerance(peakLists, scale);
    }

    public void autoSetTolerance(Collection<PeakList> peakLists, double scale) {
        for (var peakList : peakLists) {
            int nDim = peakList.getNDim();
            for (int i = 0; i < nDim; i++) {
                var stat = peakList.widthDStatsPPM(i);
                double median = stat.getPercentile(50.0);
                peakList.getSpectralDim(i).setIdTol(median * scale);
            }
        }
    }

    public void calcCombinations() {
        for (PeakList peakList : peakLists) {
            for (Peak peak : peakList.peaks()) {
                for (PeakDim peakDim : peak.getPeakDims()) {
                    peakDim.setUser("");
                }

            }
        }
        getSpinSystems().calcCombinations();
    }

    public void compare() {
        getSpinSystems().compare();
        getSpinSystems().checkConfirmed();
        getSpinSystems().updateFragments();
        getSpinSystems().dump();
    }

    public static String getHDimName(PeakList peakList) {
        String result = null;
        for (int i = 0; i < peakList.getNDim(); i++) {
            String dimName = peakList.getSpectralDim(i).getDimName();
            if (dimName.contains("H")) {
                result = dimName;
                break;
            }
        }
        return result;
    }

    public static String getNDimName(PeakList peakList) {
        String result = null;
        for (int i = 0; i < peakList.getNDim(); i++) {
            String dimName = peakList.getSpectralDim(i).getDimName();
            if (dimName.contains("N") && !dimName.contains("H")) {
                result = dimName;
                break;
            }
        }
        return result;
    }

    public Map<String, Integer> filterPeaks() {
        double tolScale = 3.0;
        Map<String, Integer> result = new HashMap<>();
        if (peakLists.isEmpty()) {
            return result;
        }
        PeakList refList = peakLists.get(0);
        refList.clearSearchDims();
        List<String> commonDimNames = new ArrayList<>();
        commonDimNames.add(getHDimName(refList));
        commonDimNames.add(getNDimName(refList));
        for (String dimName : commonDimNames) {
            SpectralDim sDim = refList.getSpectralDim(dimName);
            refList.addSearchDim(dimName, sDim.getIdTol() * tolScale);
        }

        for (PeakList peakList : peakLists) {
            AtomicInteger nFiltered = new AtomicInteger();
            if (refList != peakList) {
                int[] dims = new int[commonDimNames.size()];
                int j = 0;
                for (String dimName : commonDimNames) {
                    SpectralDim sDim = peakList.getSpectralDim(dimName);
                    dims[j++] = sDim.getIndex();
                }
                double[] ppms = new double[dims.length];
                peakList.peaks().forEach(peak -> {
                    int jDim = 0;
                    for (int dim : dims) {
                        ppms[jDim++] = peak.getPeakDim(dim).getChemShiftValue();
                    }
                    List<Peak> peaks = refList.findPeaks(ppms);
                    if (peaks.isEmpty()) {
                        peak.setStatus(-1);
                        nFiltered.incrementAndGet();
                    }
                });
                peakList.compress();
                peakList.reNumber();
            }
            result.put(peakList.getName(), nFiltered.intValue());
        }
        return result;
    }

    public boolean getHasAllAtoms(SpinSystem spinSystem) {
        boolean ok = true;
        for (int k = 0; k < 2; k++) {
            boolean isGly = false;
            boolean justCB = true;
            for (SpinSystem.AtomEnum atomEnum : SpinSystem.AtomEnum.values()) {
                int n = getExpected(k, atomEnum);
                if (n != 0) {
                    String aName = atomEnum.name();
                    if (k == 0) {
                        aName = aName.toLowerCase();
                    } else {
                        aName = aName.toUpperCase();
                    }
                    Optional<Double> valueOpt = spinSystem.getValue(k, atomEnum);
                    if (valueOpt.isPresent()) {
                        double value = valueOpt.get();
                        if (aName.equalsIgnoreCase("ca")) {
                            if ((value < 50.0) && (value > 40.0)) {
                                isGly = true;
                            }
                        }
                    } else {
                        if (!aName.equalsIgnoreCase("cb")) {
                            justCB = false;
                        }
                        ok = false;
                    }
                }
            }
            if (!ok && justCB && isGly) {
                ok = true;
            }
        }
        return ok;
    }

    public int getExtraOrMissing(SpinSystem spinSys) {
        int result = 0;
        for (var peakList : peakLists) {
            String typeName = peakList.getExperimentType();
            int nExpected = getTypeCount(typeName);
            int nPeaks = 0;
            for (var peakMatch : spinSys.peakMatches()) {
                if (peakMatch.getPeak().getPeakList() == peakList) {
                    nPeaks++;
                }
            }
            if (nPeaks > nExpected) {
                result |= 1;
            } else if (nPeaks < nExpected) {
                result |= 2;
            }
        }
        return result;
    }

    public void trim(SpinSystem spinSys) {
        for (var peakList : peakLists) {
            String typeName = peakList.getExperimentType();
            int nExpected = getTypeCount(typeName);
            int nPeaks = 0;
            for (var peakMatch : spinSys.peakMatches()) {
                if (peakMatch.getPeak().getPeakList() == peakList) {
                    nPeaks++;
                }
            }
            if (nPeaks > nExpected) {
                spinSys.peakMatches().stream().
                        map(SpinSystem.PeakMatch::getPeak).
                        sorted(Comparator.comparingDouble(a -> Math.abs(a.getIntensity()))).
                        limit(nPeaks - nExpected).
                        forEach(Peak::delete);
                spinSys.purgeDeleted();
                peakList.compress();
            }
        }
    }

    void readSTARSaveFrame(Saveframe saveframe) throws ParseException {
        String category = "_Runabout";
        String frameCode = saveframe.getValue(category, "Sf_framecode");
        String id = saveframe.getValue(category, "ID");
        readPeakLists(saveframe);
        spinSystems.readSTARSaveFrame(saveframe);
    }


    @Override
    public void write(Writer chan) throws ParseException, IOException {
        writeToSTAR(chan);
    }

    void writeToSTAR(Writer chan) throws IOException {
        String category = "_Runabout";
        String categoryName = "runabout";
        StringBuilder sBuilder = new StringBuilder();
        NMRStarWriter.initSaveFrameOutput(sBuilder, category, categoryName, String.valueOf(id));

        writePeakLists(sBuilder);

        spinSystems.writeSpinSystemFragments(sBuilder);

        spinSystems.writeSpinSystems(sBuilder);

        spinSystems.writeSpinSystemPeaks(sBuilder);

        chan.write(sBuilder.toString());
        chan.write("save_\n\n");
    }

    void writePeakLists(StringBuilder sBuilder) {

        NMRStarWriter.openLoop(sBuilder, "_Runabout_peak_lists", peakListTags);
        int i = 1;
        sBuilder.append(String.format("%3d %3d\n", i, refList.getId()));
        for (PeakList peakList : peakLists) {
            if (peakList != refList) {
                sBuilder.append(String.format("%3d %3d\n", i, peakList.getId()));
            }
            i++;
        }
        NMRStarWriter.endLoop(sBuilder);
    }

    void readPeakLists(Saveframe saveframe) throws ParseException {
        Loop peakListLoop = saveframe.getLoop("_Runabout_peak_lists");
        if (peakListLoop != null) {
            List<PeakList> loopLists = new ArrayList<>();
            List<Integer> idColumn = peakListLoop.getColumnAsIntegerList("ID", -1);
            List<Integer> peakListIDColumn = peakListLoop.getColumnAsIntegerList("Spectral_peak_list_ID", -1);
            for (int i = 0; i < idColumn.size(); i++) {
                Optional<PeakList> peakListOpt = PeakList.get(peakListIDColumn.get(i));
                if (peakListOpt.isPresent()) {
                    PeakList peakList = peakListOpt.get();
                    loopLists.add(peakList);
                }
            }
            setPeakLists(loopLists);
        }
    }

    public void mapSpinSystemToResidue() {
        var sortedSystems = getSpinSystems().getSortedSystems();
        residueSpinSystemsMap.clear();
        for (SpinSystem spinSys : sortedSystems) {
            Optional<SeqFragment> fragmentOpt = spinSys.getFragment();
            fragmentOpt.ifPresent(seqFragment -> {
                if (seqFragment.isFrozen()) {
                    var spinSystemMatches = seqFragment.getSpinSystemMatches();
                    var resSeqScore = seqFragment.getResSeqScore();
                    if (resSeqScore != null) {
                        Residue residue = resSeqScore.getFirstResidue();
                        for (int i = 0; i < resSeqScore.getNResidues(); i++) {
                            int j = i < 3 ? 0 : i - 2;
                            if (j < spinSystemMatches.size()) {
                                var spinSystemMatch = spinSystemMatches.get(j);
                                SpinSystem spinSystem;
                                if (i < 2) {
                                    spinSystem = spinSystemMatch.getSpinSystemA();
                                } else {
                                    spinSystem = spinSystemMatch.getSpinSystemB();
                                }
                                residueSpinSystemsMap.put(residue, spinSystem);
                            }
                            residue = residue.getNext();
                        }
                    }
                }
            });
        }
    }

    public SpinSystem getSpinSystemForResidue(Residue residue) {
        return residueSpinSystemsMap.get(residue);
    }

}
