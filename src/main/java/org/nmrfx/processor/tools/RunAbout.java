package org.nmrfx.processor.tools;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.SpinSystems;
import org.yaml.snakeyaml.Yaml;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author brucejohnson
 */
public class RunAbout {

    SpinSystems spinSystems = new SpinSystems();
    Map<String, Object> yamlData = null;
    List<PeakList> peakLists = new ArrayList<>();

    public void loadYaml(String fileName) throws FileNotFoundException, IOException {
        try (InputStream input = new FileInputStream(fileName)) {
            Yaml yaml = new Yaml();
            yamlData = (Map<String, Object>) yaml.load(input);
        }
        setupPeakLists();
    }

    public SpinSystems getSpinSystems() {
        return spinSystems;
    }

    public Map<String, Object> getYamlData() {
        return yamlData;
    }

    void setPatterns(List<Map<String, Object>> typeList, String typeName, PeakList peakList) {
        double[] tols = {0.04, 0.5, 0.6}; // fixme
        for (Map<String, Object> type : typeList) {
            String thisName = (String) type.get("name");
            if (typeName.equals(thisName)) {
                List<String> patElems = (List<String>) type.get("patterns");
                System.out.println("patelems " + patElems);
                for (int i = 0; i < patElems.size(); i++) {
                    peakList.getSpectralDim(i).setPattern(patElems.get(i).trim());
                    peakList.getSpectralDim(i).setTol(tols[i]);
                }
            }
        }
    }

    void setupPeakLists() {
        Map<String, String> datasetMap = (Map<String, String>) yamlData.get("datasets");
        List<String> peakListTypes = (List<String>) yamlData.get("peakLists");
        List<Map<String, Object>> typeList = (List<Map<String, Object>>) yamlData.get("types");
        peakLists.clear();
        for (String typeName : peakListTypes) {
            String datasetName = datasetMap.get(typeName);
            PeakList peakList = PeakList.getPeakListForDataset(datasetName);
            System.out.println(typeName + " " + datasetName + " " + peakList);
            if (peakList != null) {
                setPatterns(typeList, typeName, peakList);
                peakLists.add(peakList);
            }
        }
    }

    public void assemble() {
        System.out.println("assemble " + peakLists);
        getSpinSystems().assembleWithClustering(peakLists);
        // getSpinSystems().compare();
        getSpinSystems().dump();
    }
}
