package org.nmrfx.structure.seqassign;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.SpectralDim;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author brucejohnson
 */
public class RunAbout {

    SpinSystems spinSystems = new SpinSystems(this);
    Map<String, Object> yamlData = null;
    Map<String, PeakList> peakListMap = new LinkedHashMap<>();
    List<PeakList> peakLists = new ArrayList<>();
    Map<String, Map<String, List<String>>> arrange;
    Map<String, List<String>> dimLabels;
    Map<String, String> peakListTypes = new HashMap<>();
    Map<String, Dataset> datasetMap = new HashMap<>();
    Map<String, List<String>> aTypeMap = new HashMap<>();
    Map<String, TypeInfo> typeInfoMap = new HashMap<>();
    boolean active = false;

    List<Map<String, Object>> typeList;

    public void loadYaml(String fileName) throws FileNotFoundException, IOException {
        try (InputStream input = new FileInputStream(fileName)) {
            Yaml yaml = new Yaml();
            yamlData = (Map<String, Object>) yaml.load(input);
        }
        setupPeakLists();
        active = true;
    }

    public boolean isActive() {
        return active;
    }

    public SpinSystems getSpinSystems() {
        return spinSystems;
    }

    public Map<String, Map<String, List<String>>> getArrangements() {
        return arrange;
    }

    public Optional<Dataset> getDataset(String key) {
        Optional<Dataset> result;
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

    public Map<String, Object> getYamlData() {
        return yamlData;
    }

    public List<String> getDimLabel(String dimType) {
        return dimLabels.get(dimType);
    }

    public int getTypeCount(String typeName) {
        return typeInfoMap.get(typeName).nTotal;
    }

    List<String> setPatterns(List<Map<String, Object>> typeList, String typeName, PeakList peakList) {
        double[] tols = {0.04, 0.5, 0.6}; // fixme
        List<String> patElems = new ArrayList<>();
        for (Map<String, Object> type : typeList) {
            String thisName = (String) type.get("name");
            if (typeName.equals(thisName)) {
                patElems = (List<String>) type.get("patterns");
                List<String> aTypes = new ArrayList<>();
                for (int i = 0; i < patElems.size(); i++) {
                    peakList.getSpectralDim(i).setPattern(patElems.get(i).trim());
                    peakList.getSpectralDim(i).setIdTol(tols[i]);
                    String patElem = patElems.get(i);
                    int dotPos = patElem.indexOf(".");
                    String aType = patElem.substring(dotPos + 1, dotPos + 2);
                    aTypes.add(aType);
                }
                aTypeMap.put(typeName, aTypes);
                break;
            }
        }
        return patElems;
    }

    public class TypeInfo {

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

    int setAtomCount(String typeName, List<String> patElems, int[][] counts, List<String> stdNames) {
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
        return total;
    }

    void setupPeakLists() {
        List<String> stdNames = Arrays.asList(SpinSystem.ATOM_TYPES);
        int[][] counts = new int[2][stdNames.size()];
        arrange = (Map<String, Map<String, List<String>>>) yamlData.get("arrangements");
        dimLabels = (Map<String, List<String>>) yamlData.get("dims");

        datasetMap.clear();
        peakListMap.clear();
        peakLists.clear();
        Map<String, String> datasetNameMap = (Map<String, String>) yamlData.get("datasets");
        for (Map.Entry<String, String> entry : datasetNameMap.entrySet()) {
            Dataset dataset = Dataset.getDataset(entry.getValue());
            datasetMap.put(entry.getKey(), dataset);
        }

        List<String> peakListNames = (List<String>) yamlData.get("peakLists");
        typeList = (List<Map<String, Object>>) yamlData.get("types");
        for (String typeName : peakListNames) {
            String datasetName = datasetNameMap.get(typeName);
            PeakList peakList = PeakList.getPeakListForDataset(datasetName);
            if (peakList != null) {
                peakListTypes.put(peakList.getName(), typeName);
                List<String> patElems = setPatterns(typeList, typeName, peakList);
                peakListMap.put(typeName, peakList);
                peakLists.add(peakList);
                if (!patElems.isEmpty()) {
                    int nPeaks = setAtomCount(typeName, patElems, counts, stdNames);
                }
            }
        }
        SpinSystem.nAtmPeaks = counts;
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

    public List<String> getPatterns(String row, String dDir) {
        Optional<String> typeName = Optional.empty();
        dDir = dDir.replace("h", "i");
        dDir = dDir.replace("j", "i");
        dDir = dDir.replace("k", "i");
        List<String> patElems = new ArrayList<>();
        for (Map<String, Object> typeMap : typeList) {
            String typeRow = (String) typeMap.get("row");
            String typeDir = (String) typeMap.get("dir");
            if (row.equals(typeRow) && dDir.equals(typeDir)) {
                patElems = (List<String>) typeMap.get("patterns");
                break;
            }
        }
        return patElems;
    }

    public int[] getIDims(Dataset dataset, String typeName, List<String> dims) {
        int[] iDims = new int[dims.size()];
        System.out.println(typeName + " " + dims.toString());
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
        System.out.println("assemble " + peakListMap.keySet().toString());
        getSpinSystems().assembleWithClustering(peakLists);
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
                peakList.peaks().stream().forEach(peak -> {
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
            System.out.println(peakList.getName() + " " + nFiltered.toString());
        }
    }

}
