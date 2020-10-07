/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.structure.chemistry.io.MMcifReader;
import org.nmrfx.structure.chemistry.io.MMcifWriter;

/**
 *
 * @author Martha
 */
public class MMCifFileTest {

    List<List<Object>> orig = new ArrayList<>();
    List<List<Object>> written = new ArrayList<>();

    @Test
    public void testFile2KKO() throws IOException {
        loadData("2kko");
        testAll();
    }
    @Test
    public void testFile2KO1() throws IOException {
        loadData("2ko1");
        testAll();
    }
    @Test
    public void testFile2KZN() throws IOException {
        loadData("2kzn");
        testAll();
    }
    @Test
    public void testFile2PNG() throws IOException {
        loadData("2png");
        testAll();
    }
    @Test
    public void testFile4HIW() throws IOException {
        loadData("4hiw");
        testAll();
    }
    @Test
    public void testFile2JUW() throws IOException {
        loadData("2juw");
        testAll();
    }

    @Test
    public void testFile2JR2() throws IOException {
        loadData("2jr2");
        testAll();
    }

    @Test
    public void testFile1PQX() throws IOException {
        loadData("1pqx");
        testAll();
    }

//    @Test
//    public void testFile1PQX2() throws IOException {
//        loadData("1pqx_2");
//        testAll();
//    }
    @Test
    public void testFile2K2E() throws IOException {
        loadData("2k2e");
        testAll();
    }
    @Test
    public void testFile2KPU() throws IOException {
        loadData("2kpu");
        testAll();
    }
    @Test
    public void testFile2KW5() throws IOException {
        loadData("2kw5");
        testAll();
    }
    @Test
    public void testFile2LOY() throws IOException {
        loadData("2loy");
        testAll();
    }
//    @Test
//    public void testFile2LUZ() throws IOException {
//        loadData("2luz");
//        testAll();
//    }
//    
    @Test
    public void testFile2K07() throws IOException {
        loadData("2k07");
        testAll();
    }
    @Test
    public void testFile2KCU() throws IOException {
        loadData("2kcu");
        testAll();
    }
//    @Test
//    public void testFile6NBN() throws IOException {
//        loadData("6nbn");
//        testAll();
//    }
    @Test
    public void testFile3PUK() throws IOException { 
        loadData("3puk");
        testAll();
    }
    @Test
    public void testFile3Q4F() throws IOException { 
        loadData("3q4f");
        testAll();
    }
    @Test
    public void testFile6ACK() throws IOException { 
        loadData("6ack");
        testAll();
    }
    @Test
    public void testFile6AJ4() throws IOException { 
        loadData("6aj4");
        testAll();
    }
    @Test
    public void testFile2RF4() throws IOException { 
        loadData("2rf4");
        testAll();
    }
    @Test
    public void testFile6J91() throws IOException { 
        loadData("6j91");
        testAll();
    }
    
    private List<List<Object>> convertFileLines(String filePath) throws FileNotFoundException, IOException {
        List<List<Object>> convertedLines = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            List<String> sLine = Arrays.asList(line.trim().split("\\s+"));
            List<Object> cLine = new ArrayList<>();
            for (String s : sLine) {
                try {
                    cLine.add(Integer.parseInt(s));
                } catch (NumberFormatException ex1) {
                    try {
                        double sD = Double.parseDouble(s);
                        cLine.add(sD);
                    } catch (NumberFormatException ex2) {
                        cLine.add(s);
                    }
                }
            }
            convertedLines.add(cLine);
        }
        return convertedLines;
    }

    private Map<String, List<Object>> buildSequenceMap(List<List<Object>> dataArray) {
        Map<String, List<Object>> seqMap = new HashMap<>();
        boolean inSeq = false;
        for (List<Object> line : dataArray) {
            if (line.size() > 0) {
                if (line.get(0).toString().contains("_entity_poly_seq")) {
                    inSeq = true;
                }
                if (inSeq) {
                    int iChain = 0;
                    int iSeq = 1;
                    if (line.size() > 1 && line.get(0) instanceof Integer) {
                        String key = line.get(iChain) + "." + line.get(iSeq).toString();
                        List<Object> values = new ArrayList<>();
                        for (int i = 2; i < line.size(); i++) {
                            values.add(line.get(i));
                        }
                        seqMap.put(key, values);
                    } else if (line.contains("#")) {
                        break;
                    }
                }
            }
        }
        return seqMap;
    }

    private Map<String, List<Object>> buildAtomSitesMap(List<List<Object>> dataArray) {
        Map<String, List<Object>> siteMap = new HashMap<>();
        boolean inSites = false;
        for (List<Object> line : dataArray) {
            if (line.size() > 0) {
                if (line.get(0).toString().contains("_atom_site.")) {
                    inSites = true;
                }
                if (inSites) {
                    int iChain = 6;
                    int iSeq = 8;
                    int iAtomName = 3;
                    int iStruct = 20;
                    if (line.size() > 1 && line.get(1) instanceof Integer) {
                        String key = line.get(iChain) + "." + line.get(iSeq).toString() + "." + line.get(iAtomName) + "." + line.get(iStruct);
                        List<Object> values = new ArrayList<>();
                        for (int i = 9; i < line.size() - 1; i++) {
                            values.add(line.get(i));
                        }
                        if (siteMap.containsKey(key)) {
                            siteMap.put(key + "_dup", values);
                        } else {
                            siteMap.put(key, values);
                        }
                    } else if (line.contains("#")) {
                        break;
                    }
                }
            }
        }
        return siteMap;
    }

    private Map<String, List<Object>> buildChemCompMap(List<List<Object>> dataArray) {
        Map<String, List<Object>> compMap = new HashMap<>();
        boolean inComp = false;
        for (List<Object> line : dataArray) {
            if (line.size() > 0) {
                if (line.get(0).toString().contains("_chem_comp.")) {
                    inComp = true;
                }
                if (inComp) {
                    int iRes = 0;
                    if (line.size() > 1 && line.get(line.size() - 1) instanceof Double) {
                        String key = line.get(iRes).toString();
                        List<Object> values = new ArrayList<>();
                        for (int i = 1; i < line.size(); i++) {
                            values.add(line.get(i));
                        }
                        int iWeight = values.size() - 1;
                        Double weight = (Double) values.get(iWeight);
                        values.set(iWeight, Math.round(weight));
                        if (compMap.containsKey(key)) {
                            compMap.put(key + "_dup", values);
                        } else {
                            compMap.put(key, values);
                        }
                    } else if (line.contains("#")) {
                        break;
                    }
                }
            }
        }
        return compMap;
    }

    private Map<String, List<Object>> buildStructConfMap(List<List<Object>> dataArray) {
        Map<String, List<Object>> confMap = new HashMap<>();
        boolean inConf = false;
        List<Object> values = new ArrayList<>();
        for (List<Object> line : dataArray) {
            if (line.size() > 0) {
                if (line.get(0).toString().contains("_struct_conf")) {
                    inConf = true;
                }
                if (inConf) {
                    int iChain = 4;
                    int iSeq = 5;
                    int iRes = 3;
                    int nAtoms = 2;
                    if (line.size() > 1 && line.get(2) instanceof Integer) {
                        String[] keyParts = new String[nAtoms];
                        for (int i = 0; i < keyParts.length; i++) {
                            keyParts[i] = line.get(iChain + 4 * i) + "." + line.get(iSeq + 4 * i).toString() + "." + line.get(iRes + 4 * i);
                        }
                        String key = String.join(";", keyParts);
                        values = new ArrayList<>();
                        values.add(line.get(line.size() - 1));
                        confMap.put(key, values);
                    } else if (line.contains("#")) {
                        break;
                    }
                }
            }
        }
        return confMap;
    }

    private Map<String, List<Object>> buildSheetMap(List<List<Object>> dataArray) {
        Map<String, List<Object>> sheetMap = new HashMap<>();
        boolean inSheet = false;
        List<Object> values = new ArrayList<>();
        for (List<Object> line : dataArray) {
            if (line.size() > 0) {
                if (line.get(0).toString().contains("_struct_sheet_range")) {
                    inSheet = true;
                }
                if (inSheet) {
                    int iChain = 3;
                    int iSeq = 4;
                    int iRes = 2;
                    if (line.size() > 1 && line.get(1) instanceof Integer) {
                        String key = line.get(iChain) + "." + line.get(iSeq).toString() + "." + line.get(iRes);
                        values = new ArrayList<>();
                        for (int i = 6; i < 9; i++) {
                            values.add(line.get(i));
                        }
                        sheetMap.put(key, values);
                    } else if (line.contains("#")) {
                        break;
                    }
                }
            }
        }
        return sheetMap;
    }

    public void loadData(String nefFileName) throws IOException {
        String fileName = String.join(File.separator, "src", "test", "data", "ciffiles", nefFileName + ".cif");
        String outPath = "tmp";
        File tmpDir = new File(outPath);
        if (!tmpDir.exists()) {
            Files.createDirectory(tmpDir.toPath());
        }
        String outFile = String.join(File.separator, outPath, nefFileName + "_mmCif_outTest.cif");
        try {
            if (orig.isEmpty()) {
                MMcifReader.read(fileName);
                MMcifWriter.writeAll(outFile);
                orig = convertFileLines(fileName);
                written = convertFileLines(outFile);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public boolean compareMaps(String mode, Map<String, List<Object>> origMap, Map<String, List<Object>> writtenMap) throws IOException {
        try {
            boolean ok = true;
            for (Entry<String, List<Object>> entry : origMap.entrySet()) {
                String key = entry.getKey();
                if (!writtenMap.containsKey(key)) {
                    System.out.println(mode + " key " + key + " not in written");
                    ok = false;
                    if (mode.equals("atom sites") && key.contains("..")) { //don't flag skipped waters
                        ok = true;
                    }
                } else {
                    List<Object> origValues = entry.getValue();
                    List<Object> writtenValues = writtenMap.get(key);
                    List<List<Object>> allValues = new ArrayList<>();
                    allValues.add(origValues);
                    allValues.add(writtenValues);
                    if (mode.equals("torsion")) {
                        for (int v = 0; v < origValues.size(); v++) {
                            if ((v == 1 || v == 3 || v == 4)
                                    && !origValues.get(v).equals(writtenValues.get(v))) {
                                for (int l = 0; l < allValues.size(); l++) {
                                    List<Object> valList = allValues.get(l);
                                    double val = (double) valList.get(v);
                                    if (val >= 180.0 && val < 360.0) {
                                        valList.set(v, val - 180.0);
                                    } else if (val >= -360.0 && val < -180.0) {
                                        valList.set(v, val + 360.0);
                                    } else if (val >= -180.0 && val < 0.0) {
                                        valList.set(v, val + 180.0);
                                    }
                                }
                            }
                        }
                    }
                    for (int i = 0; i < origValues.size(); i++) {
                        if (!origValues.get(i).equals(writtenValues.get(i))) {
                            System.out.println(mode + " " + key + " " + origValues.toString() + " " + writtenValues.toString());
                            ok = false;
                        }
                    }
                }
            }
            for (Entry<String, List<Object>> entry : writtenMap.entrySet()) {
                String key = entry.getKey();
                if (!origMap.containsKey(key)) {
                    System.out.println(mode + " key " + key + " not in orig");
                    ok = false;
                }
            }
            return ok;
        } catch (Exception ex) {
            return false;
        }

    }

    public void testAll() throws IOException {
        testSeqBlock();
        testChemCompBlock();
        testStructConfBlock();
        testSheetBlock();
        testAtomSitesBlock();
    }

    public void testSeqBlock() throws IOException {
        try {
            Map<String, List<Object>> origSeq = buildSequenceMap(orig);
            Map<String, List<Object>> writtenSeq = buildSequenceMap(written);
            if (!origSeq.isEmpty() && !writtenSeq.isEmpty()) {
                boolean ok = compareMaps("seq", origSeq, writtenSeq);
                Assert.assertTrue(ok);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void testAtomSitesBlock() throws IOException {
        try {
            Map<String, List<Object>> origSites = buildAtomSitesMap(orig);
            Map<String, List<Object>> writtenSites = buildAtomSitesMap(written);
            if (!origSites.isEmpty() && !writtenSites.isEmpty()) {
                boolean ok = compareMaps("atom sites", origSites, writtenSites);
                Assert.assertTrue(ok);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void testChemCompBlock() throws IOException {
        try {
            Map<String, List<Object>> origComp = buildChemCompMap(orig);
            Map<String, List<Object>> writtenComp = buildChemCompMap(written);
            if (!origComp.isEmpty() && !writtenComp.isEmpty()) {
                boolean ok = compareMaps("chem comp", origComp, writtenComp);
                Assert.assertTrue(ok);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void testStructConfBlock() throws IOException {
        try {
            Map<String, List<Object>> origConf = buildStructConfMap(orig);
            Map<String, List<Object>> writtenConf = buildStructConfMap(written);
            if (!origConf.isEmpty() && !writtenConf.isEmpty()) {
                boolean ok = compareMaps("struct conf", origConf, writtenConf);
                Assert.assertTrue(ok);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void testSheetBlock() throws IOException {
        try {
            Map<String, List<Object>> origSheet = buildSheetMap(orig);
            Map<String, List<Object>> writtenSheet = buildSheetMap(written);
            if (!origSheet.isEmpty() && !writtenSheet.isEmpty()) {
                boolean ok = compareMaps("sheet range", origSheet, writtenSheet);
                Assert.assertTrue(ok);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
