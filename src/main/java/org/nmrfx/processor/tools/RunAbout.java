package org.nmrfx.processor.tools;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.SpinSystems;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author brucejohnson
 */
public class RunAbout {

    SpinSystems spinSystems = new SpinSystems();
    Map<String, Object> yamlData = null;
    Map<String, PeakList> peakListMap = new LinkedHashMap<>();
    List<PeakList> peakLists = new ArrayList<>();
    Map<String, Map<String, List<String>>> arrange;
    Map<String, List<String>> dimLabels;
    Map<String, Dataset> datasetMap = new HashMap<>();
    Map<String, List<String>> aTypeMap = new HashMap<>();
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

    void setPatterns(List<Map<String, Object>> typeList, String typeName, PeakList peakList) {
        double[] tols = {0.04, 0.5, 0.6}; // fixme
        for (Map<String, Object> type : typeList) {
            String thisName = (String) type.get("name");
            if (typeName.equals(thisName)) {
                List<String> patElems = (List<String>) type.get("patterns");
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

            }
        }
    }

    void setupPeakLists() {
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

        List<String> peakListTypes = (List<String>) yamlData.get("peakLists");
        typeList = (List<Map<String, Object>>) yamlData.get("types");
        for (String typeName : peakListTypes) {
            String datasetName = datasetNameMap.get(typeName);
            PeakList peakList = PeakList.getPeakListForDataset(datasetName);
            if (peakList != null) {
                setPatterns(typeList, typeName, peakList);
                peakListMap.put(typeName, peakList);
                peakLists.add(peakList);
            }
        }
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
        getSpinSystems().calcCombinations();
    }

    public void compare() {
        getSpinSystems().compare();
        getSpinSystems().dump();
    }
}
