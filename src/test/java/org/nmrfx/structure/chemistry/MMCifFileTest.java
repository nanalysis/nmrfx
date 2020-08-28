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

    // old files:
    // ok 1pqx 2k2e 2kpu 2kw5 2loy 2luz
    // HH% twice 2jr2 2juw 2kko
    // HB% twice etc 2ko1
    // Asp HD2 2kzn   should have residue modifier +HD2
    // ILE CGx CGy  (these are not stereo equiv  2png
    // has ligand 6nbn
    
    // new files:
    // OK 1pqx 2jr2 2juw 2k2e 2kcu 2kpu 2kw5 2ko1

    // MISTAKES IN ORIGINAL FILES
    // SEQUENCE 6nbn has unreadable residue ACD
    // CHEM SHIFT 2k07 only one each of 90 HD% and HE% in written when 2 each in orig (HD/E% repeats mistake in original file, should be HD1/2, HE1/2, etc.)
    // DISTANCE 2loy 2kzn dict key mismatches, written HE2% should be HE% (HE% mistake in original file, should be HE2%. GLN wildcards are HB%, HG%, and HE2%)
    // DISTANCE 2k07 2kko 2luz (previously passed, now fails) dict key mismatches, e.g. written HDx/y% should be HD1/2% (based on response below, mistake in original file. HDx/y% is correct, not HD1/2%)

    // ISSUES TO BE ADDRESSED
    // DISTANCE 2png dict key mismatches, e.g. written HG% should be HGy and HGx (multiple lines w/ same restraint not getting split correctly) 
    List<List<Object>> orig = new ArrayList<>();
    List<List<Object>> written = new ArrayList<>();

//    @Test
//    public void testFile2KO1() throws IOException {
//        loadData("2ko1");
//        testAll();
//    }
//    @Test
//    public void testFile2PNG() throws IOException {
//        loadData("2png");
//        testAll();
//    }
//    @Test
//    public void testFile2KZN() throws IOException {
//        loadData("2kzn");
//        testAll();
//    }
//    @Test
//    public void testFile2KKO() throws IOException {
//        loadData("2kko");
//        testAll();
//    }
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

//    @Test
//    public void testFile2K2E() throws IOException {
//        loadData("2k2e");
//        testAll();
//    }

//    @Test
//    public void testFile2KPU() throws IOException {
//        loadData("2kpu");
//        testAll();
//    }

//    @Test
//    public void testFile2KW5() throws IOException {
//        loadData("2kw5");
//        testAll();
//    }

//    @Test
//    public void testFile2LOY() throws IOException {
//        loadData("2loy");
//        testAll();
//    }
//    @Test
//    public void testFile2LUZ() throws IOException {
//        loadData("2luz");
//        testAll();
//    }
//    
//    @Test
//    public void testFile2K07() throws IOException {
//        loadData("2k07");
//        testAll();
//    }

//    @Test
//    public void testFile2KCU() throws IOException {
//        loadData("2kcu");
//        testAll();
//    }
    
//    @Test
//    public void testFile6NBN() throws IOException {
//        loadData("6nbn");
//        testAll();
//    }

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

    private Map<String, List<Object>> buildDistanceMap(List<List<Object>> dataArray) {
        Map<String, List<Object>> distMap = new HashMap<>();
        Map<Integer, String> keys = new HashMap<>();
        boolean inDist = false;
        List<Object> values = new ArrayList<>();
        for (List<Object> line : dataArray) {
            if (line.size() > 0) {
                if (line.get(0).toString().contains("_pdbx_validate_close_contact")) {
                    inDist = true;
                }
                if (inDist) {
                    int iChain = 3;
                    int iSeq = 5;
                    int iAtomName = 2;
                    int nAtoms = 2;
                    if (line.size() > 1 && line.get(0) instanceof Integer) {
                        int pdbModelNum = (int) line.get(1);
                        String[] keyParts = new String[nAtoms];
                        for (int i = 0; i < keyParts.length; i++) {
                            keyParts[i] = line.get(iChain + 6 * i) + "." + line.get(iSeq + 6 * i).toString() + "." + line.get(iAtomName + 6 * i);
                        }
                        String key = String.join(";", keyParts);
                        if (keys.containsKey(pdbModelNum)) {
                            String oldKey = keys.get(pdbModelNum);
                            String newKey = oldKey + "_" + key;
                            keys.put(pdbModelNum, newKey);
                            distMap.remove(oldKey);
                        } else {
                            values = new ArrayList<>();
                            keys.put(pdbModelNum, key);
                        }
                        for (int i = iSeq + 6 * (nAtoms - 1) + 3; i < line.size(); i++) {
                            values.add(line.get(i));
                        }
                        key = keys.get(pdbModelNum);
                        distMap.put(key, values);
                    } else if (line.contains("#")) {
                        break;
                    }
                }
            }
        }
        return distMap;
    }

    private Map<String, List<Object>> buildTorsionMap(List<List<Object>> dataArray) {
        Map<String, List<Object>> dihedralMap = new HashMap<>();
        boolean inDihedral = false;
        for (List<Object> line : dataArray) {
            if (line.size() > 0) {
                if (line.get(0).toString().contains("_pdbx_validate_torsion")) {
                    inDihedral = true;
                }
                if (inDihedral) {
                    int iStruct = 1;
                    int iChain = 3;
                    int iSeq = 4;
                    int iResName = 2;
                    if (line.size() > 1 && line.get(0) instanceof Integer) {
                        String key = line.get(iStruct) + "." + line.get(iChain) + "." + line.get(iSeq).toString() + "." + line.get(iResName);
                        List<Object> values = new ArrayList<>();
                        for (int i = iSeq + 3; i < line.size(); i++) {
                            values.add(line.get(i));
                        }
                        dihedralMap.put(key, values);
                    } else if (line.contains("#")) {
                        break;
                    }
                }
            }
        }
        return dihedralMap;
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
                } else {
                    List<Object> origValues = entry.getValue();
                    List<Object> writtenValues = writtenMap.get(key);
                    List<List<Object>> allValues = new ArrayList<>();
                    allValues.add(origValues);
                    allValues.add(writtenValues);
                    if (mode.equals("torsion")) {
                        for (int v=0; v<origValues.size(); v++) {
                            if ((v == 1 || v == 3 || v == 4) && 
                                    !origValues.get(v).equals(writtenValues.get(v))) {
                                for (int l=0; l<allValues.size(); l++) {
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
        testDistanceBlock();
        testTorsionBlock();
    }

    public void testSeqBlock() throws IOException {
        try {
            Map<String, List<Object>> origSeq = buildSequenceMap(orig);
            Map<String, List<Object>> writtenSeq = buildSequenceMap(written);
            boolean ok = compareMaps("seq", origSeq, writtenSeq);
            Assert.assertTrue(ok);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void testAtomSitesBlock() throws IOException {
        try {
            Map<String, List<Object>> origSites = buildAtomSitesMap(orig);
            Map<String, List<Object>> writtenSites = buildAtomSitesMap(written);
            boolean ok = compareMaps("atom sites", origSites, writtenSites);
            Assert.assertTrue(ok);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public void testChemCompBlock() throws IOException {
        try {
            Map<String, List<Object>> origComp = buildChemCompMap(orig);
            Map<String, List<Object>> writtenComp = buildChemCompMap(written);
            boolean ok = compareMaps("chem comp", origComp, writtenComp);
            Assert.assertTrue(ok);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public void testStructConfBlock() throws IOException {
        try {
            Map<String, List<Object>> origConf = buildStructConfMap(orig);
            Map<String, List<Object>> writtenConf = buildStructConfMap(written);
            boolean ok = compareMaps("struct conf", origConf, writtenConf);
            Assert.assertTrue(ok);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public void testSheetBlock() throws IOException {
        try {
            Map<String, List<Object>> origSheet = buildSheetMap(orig);
            Map<String, List<Object>> writtenSheet = buildSheetMap(written);
            boolean ok = compareMaps("sheet range", origSheet, writtenSheet);
            Assert.assertTrue(ok);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void testDistanceBlock() throws IOException {
        try {
            Map<String, List<Object>> origDist = buildDistanceMap(orig);
            Map<String, List<Object>> writtenDist = buildDistanceMap(written);
            boolean ok = compareMaps("distance", origDist, writtenDist);
            Assert.assertTrue(ok);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void testTorsionBlock() throws IOException {
        try {
            Map<String, List<Object>> origDihedral = buildTorsionMap(orig);
            Map<String, List<Object>> writtenDihedral = buildTorsionMap(written);
            boolean ok = compareMaps("torsion", origDihedral, writtenDihedral);
            Assert.assertTrue(ok);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
