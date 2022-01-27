package org.nmrfx.structure.seqassign;

import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author brucejohnson
 */
public class RunAbout {

    SpinSystems spinSystems = new SpinSystems(this);
    Map<String, PeakList> peakListMap = new LinkedHashMap<>();
    PeakList refList;
    List<PeakList> peakLists = new ArrayList<>();
    Map<String, List<String>> dimLabels;
    Map<String, String> peakListTypes = new HashMap<>();
    Map<String, DatasetBase> datasetMap = new HashMap<>();
    Map<String, List<String>> aTypeMap = new HashMap<>();
    Map<String, TypeInfo> typeInfoMap = new HashMap<>();
    boolean active = false;

    List<Map<String, Object>> typeList;

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

    public List<PeakList> getPeakLists() {
        return peakLists;
    }

    public List<String> getDimLabel(String dimType) {
        return dimLabels.get(dimType);
    }

    public int getTypeCount(String typeName) {
        return typeInfoMap.get(typeName).nTotal;
    }

    List<String> getPatterns(PeakList peakList) {
        double[] tols = {0.04, 0.5, 0.6}; // fixme
        List<String> patElems = new ArrayList<>();
        List<String> aTypes = new ArrayList<>();
        for (var sDim : peakList.getSpectralDims()) {
            String patElem = sDim.getPattern();
            patElems.add(patElem);
            int dotPos = patElem.indexOf(".");
            String aType = patElem.substring(dotPos + 1, dotPos + 2);
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

    void setAtomCount(String typeName, List<String> patElems, int[][] counts, List<String> stdNames) {
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
            System.out.println("elem " + elem + " " + parts.length);
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
                        int nameIdx = stdNames.indexOf(aName);
                        counts[iDir][nameIdx] += dimMult[i];
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
    }

    public void setPeakLists(List<PeakList> lists) {
        refList = lists.get(0);
        List<String> stdNames = Arrays.asList(SpinSystem.ATOM_TYPES);
        int[][] counts = new int[2][stdNames.size()];
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
            setAtomCount(typeName, patElems, counts, stdNames);
        }
        SpinSystem.nAtmPeaks = counts;
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
        for (int i = 0; i < dataset.getNDim(); i++) {
            int iDim = iDims[i];
            String dataDimName = dataset.getLabel(iDim);
            SpectralDim sDim = peakList.getSpectralDim(dataDimName);
            sDims.add(sDim);
        }
        return sDims;
    }

    public int[] getIDims(DatasetBase dataset, String typeName, List<String> dims) {
        int[] iDims = new int[dataset.getNDim()];
        System.out.println(typeName + " " + dims);
        int j = 0;
        for (String dim : dims) {
            String dimName;
            int sepPos = dim.indexOf("_");
            if (sepPos != -1) {
                dimName = dim.substring(0, sepPos);
            } else {
                dimName = dim;
            }
            int index = aTypeMap.get(typeName).indexOf(dimName);
            iDims[index] = j;
            j++;
        }
        return iDims;
    }

    public void assemble() {
        System.out.println("assemble " + peakListMap.keySet());
        getSpinSystems().assembleWithClustering(refList, peakLists);
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

    public void filterPeaks() {
        double tolScale = 3.0;
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
                    dims[j++] = sDim.getDataDim();
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
            System.out.println(peakList.getName() + " " + nFiltered);
        }
    }

    public boolean getHasAllAtoms(SpinSystem spinSystem) {
        int nTypes = SpinSystem.getNAtomTypes();
        boolean ok = true;
        for (int k = 0; k < 2; k++) {
            boolean isGly = false;
            boolean justCB = true;
            for (int i = 0; i < nTypes; i++) {
                int n = SpinSystem.getNPeaksForType(k, i);
                if (n != 0) {
                    String aName = SpinSystem.getAtomName(i);
                    if (k == 0) {
                        aName = aName.toLowerCase();
                    } else {
                        aName = aName.toUpperCase();
                    }
                    double value = spinSystem.getValue(k, i);
                    if (!Double.isNaN(value) && aName.equalsIgnoreCase("ca")) {
                        if ((value < 50.0)  && (value > 40.0)) {
                            isGly = true;
                        }
                    }
                    if (Double.isNaN(value)) {
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
}
