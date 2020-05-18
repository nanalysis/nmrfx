/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets.peaks;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author brucejohnson
 */
public class JournalFormatPeaks {

    static Map<String, JournalFormat> journalFormats = new HashMap<>();


    static JournalFormat parseYamlMap(Map<String, Object> map) {
        JournalFormat jformat = new JournalFormat();
        jformat.name = (String) map.get("name");
        jformat.solventMode = (String) map.get("solvent");
        jformat.header = (String) map.get("header");
        jformat.header = (String) map.get("header");
        jformat.m = (String) map.get("m");
        jformat.s = (String) map.get("s");
        jformat.o = (String) map.get("o");
        jformat.sep = (String) map.get("sep");
        jformat.jPrec = (Integer) map.get("jprec");
        jformat.ppmPrec = (Integer) map.get("ppmprec");;
        jformat.broad = (Double) map.get("broad");
        return jformat;
    }

    public void loadFormats() {

    }

    public static void loadYaml(String fileName) throws FileNotFoundException, IOException {
        try (InputStream input = new FileInputStream(fileName)) {
            loadYaml(input);

        }
    }

    public static void loadYaml() {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        InputStream istream = cl.getResourceAsStream("jformat.yaml");
        loadYaml(istream);

    }

    public static void loadYaml(InputStream istream) {

        Yaml yaml = new Yaml();
        List<Map<String, Object>> yamlData = (List<Map<String, Object>>) yaml.load(istream);
        for (Map<String, Object> map : yamlData) {
            JournalFormat journalFormat = parseYamlMap(map);
            journalFormats.put(journalFormat.name, journalFormat);
        }
    }
    
    public static JournalFormat getFormat(String journal) {
        return journalFormats.get(journal);
    }
}
