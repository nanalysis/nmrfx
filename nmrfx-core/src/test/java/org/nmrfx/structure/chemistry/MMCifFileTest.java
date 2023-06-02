/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry;

import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.io.MMcifReader;
import org.nmrfx.chemistry.io.MMcifWriter;
import org.nmrfx.peaks.InvalidPeakException;
import org.nmrfx.star.ParseException;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * @author Martha
 */
public class MMCifFileTest {
    // TODO NMR-5200: the following tests skip some of the assertion statements
    //  2png, 2juw, 2jr2, 2kpu, 5lbm, 6ack, 6aj4, 6j91

    List<List<Object>> orig = new ArrayList<>();
    List<List<Object>> written = new ArrayList<>();

    @Test
    public void testFile2KKO() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
        loadData("2kko");
        testAll();
    }

    @Test
    public void testFile2KO1() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
        loadData("2ko1");
        testAll();
    }

    @Test
    public void testFile2KZN() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
        loadData("2kzn");
        testAll();
    }

    @Test
    public void testFile2PNG() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
        loadData("2png");
        testAll();
    }

    @Test
    public void testFile5LBM() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
        loadData("5lbm");
        testAll();
    }

    @Test
    public void testFile1G18() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
        loadData("1g18");
        testAll();
    }

    @Test
    public void testFile1FOC() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
        loadData("1foc");
        testAll();
    }

    @Test
    public void testFile1G2M() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
        loadData("1g2m");
        testAll();
    }

    @Test
    public void testFile2JUW() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
        loadData("2juw");
        testAll();
    }

    @Test
    public void testFile2JR2() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
        loadData("2jr2");
        testAll();
    }

    @Test
    public void testFile1PQX() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
        loadData("1pqx");
        testAll();
    }

    //    @Test
//    public void testFile1PQX2() throws IOException {
//        loadData("1pqx_2");
//        testAll();
//    }
    @Test
    public void testFile2K2E() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
        loadData("2k2e");
        testAll();
    }

    @Test
    public void testFile2KPU() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
        loadData("2kpu");
        testAll();
    }

    @Test
    public void testFile2KW5() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
        loadData("2kw5");
        testAll();
    }

    @Test
    public void testFile2LOY() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
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
    public void testFile2K07() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
        loadData("2k07");
        testAll();
    }

    @Test
    public void testFile2KCU() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
        loadData("2kcu");
        testAll();
    }
//    @Test
//    public void testFile6NBN() throws IOException {
//        loadData("6nbn");
//        testAll();
//    }

    @Test
    public void testFile3PUK() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
        loadData("3puk");
        testAll();
    }

    @Test
    public void testFile3Q4F() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
        loadData("3q4f");
        testAll();
    }

    @Test
    public void testFile6ACK() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
        loadData("6ack");
        testAll();
    }

    @Test
    public void testFile6AJ4() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
        loadData("6aj4");
        testAll();
    }

    @Test
    public void testFile2RF4() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
        loadData("2rf4");
        testAll();
    }

    @Test
    public void testFile6J91() throws IOException, InvalidMoleculeException, ParseException, InvalidPeakException {
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
            String[] sLine = line.trim().split("\\s+");
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
                        if (seqMap.containsKey(key)) {
                            seqMap.put(key + "_dup", values);
                        } else {
                            seqMap.put(key, values);
                        }
                    } else if (line.contains("#")) {
                        break;
                    }
                }
            }
        }
        return seqMap;
    }

    private Map<String, List<Object>> buildAtomTypesMap(List<List<Object>> dataArray) {
        Map<String, List<Object>> typeMap = new HashMap<>();
        boolean inTypes = false;
        for (List<Object> line : dataArray) {
            if (line.size() > 0) {
                if (line.get(0).toString().equals("_atom_type.symbol")) {
                    inTypes = true;
                }
                if (inTypes) {
                    if (line.size() == 1 && line.get(0) instanceof String && !line.get(0).equals("#")) {
                        String key = (String) line.get(0);
                        List<Object> values = new ArrayList<>();
                        values.add(line.get(0));
                        if (typeMap.containsKey(key)) {
                            typeMap.put(key + "_dup", values);
                        } else {
                            typeMap.put(key, values);
                        }
                    } else if (line.contains("#")) {
                        break;
                    }
                }
            }
        }
        return typeMap;
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

    private Map<String, List<Object>> buildStructAsymMap(List<List<Object>> dataArray) {
        Map<String, List<Object>> asymMap = new HashMap<>();
        boolean inAsym = false;
        boolean sepBlock = false;
        String key = "";
        List<Object> values = new ArrayList<>();
        for (List<Object> line : dataArray) {
            if (line.size() > 0) {
                if (line.get(0).toString().contains("_struct_asym")) {
                    inAsym = true;
                    if (line.size() == 1) {
                        sepBlock = true;
                    }
                }
                if (inAsym) {
                    if (sepBlock) {
                        int iChain = 0;
                        int iEntity = 3;
                        values = new ArrayList<>();
                        if (line.size() > 1 && line.get(iEntity) instanceof Integer) {
                            key = line.get(iChain).toString() + "." + line.get(iEntity).toString();
                            values.add(line.get(iChain + 1));
                            values.add(line.get(iChain + 2));
                            values.add(line.get(iChain + 4));
                            asymMap.put(key, values);
                        } else if (line.contains("#")) {
                            break;
                        }
                    } else {
                        if (line.size() > 1) {
                            if (line.get(0).toString().contains(".id")) {
                                key = line.get(1).toString();
                            } else if (line.get(0).toString().contains("entity_id")) {
                                key += "." + line.get(1).toString();
                            } else {
                                values.add(line.get(1).toString());
                            }
                            if (key.contains(".")) {
                                asymMap.put(key, values);
                            }
                        } else if (line.contains("#")) {
                            break;
                        }
                    }
                }
            }
        }
        return asymMap;
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

    public void loadData(String cifFileName) throws IOException, ParseException, InvalidMoleculeException, InvalidPeakException {
        String fileName = String.join(File.separator, "src", "test", "data", "ciffiles", cifFileName + ".cif");
        String outPath = "tmp";
        File tmpDir = new File(outPath);
        if (!tmpDir.exists()) {
            Files.createDirectory(tmpDir.toPath());
        }
        String outFile = String.join(File.separator, outPath, cifFileName + "_mmCif_outTest.cif");
        if (orig.isEmpty()) {
            MMcifReader.read(fileName);
            MMcifWriter.writeAll(outFile, cifFileName.toUpperCase());
            orig = convertFileLines(fileName);
            written = convertFileLines(outFile);

        }
    }

    public boolean compareMaps(String mode, Map<String, List<Object>> origMap, Map<String, List<Object>> writtenMap) {
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

    }

    public void testAll() throws IOException {
        testSeqBlock();
//        testChemCompBlock();
        testStructAsymBlock();
        testStructConfBlock();
        testSheetBlock();
        testAtomTypesBlock();
        testAtomSitesBlock();
    }

    public void testSeqBlock() throws IOException {
        Map<String, List<Object>> origSeq = buildSequenceMap(orig);
        Map<String, List<Object>> writtenSeq = buildSequenceMap(written);
        if (!origSeq.isEmpty() && !writtenSeq.isEmpty()) {
            boolean ok = compareMaps("seq", origSeq, writtenSeq);
            Assert.assertTrue(ok);
        }
    }

    public void testAtomTypesBlock() throws IOException {
        Map<String, List<Object>> origTypes = buildAtomTypesMap(orig);
        Map<String, List<Object>> writtenTypes = buildAtomTypesMap(written);
        if (!origTypes.isEmpty() && !writtenTypes.isEmpty()) {
            boolean ok = compareMaps("atom types", origTypes, writtenTypes);
            Assert.assertTrue(ok);
        }
    }

    public void testAtomSitesBlock() throws IOException {
        Map<String, List<Object>> origSites = buildAtomSitesMap(orig);
        Map<String, List<Object>> writtenSites = buildAtomSitesMap(written);
        if (!origSites.isEmpty() && !writtenSites.isEmpty()) {
            boolean ok = compareMaps("atom sites", origSites, writtenSites);
            Assert.assertTrue(ok);
        }
    }

    public void testChemCompBlock() throws IOException {
        Map<String, List<Object>> origComp = buildChemCompMap(orig);
        Map<String, List<Object>> writtenComp = buildChemCompMap(written);
        if (!origComp.isEmpty() && !writtenComp.isEmpty()) {
            boolean ok = compareMaps("chem comp", origComp, writtenComp);
            Assert.assertTrue(ok);
        }
    }

    public void testStructAsymBlock() throws IOException {
        Map<String, List<Object>> origAsym = buildStructAsymMap(orig);
        Map<String, List<Object>> writtenAsym = buildStructAsymMap(written);
        if (!origAsym.isEmpty() && !writtenAsym.isEmpty()) {
            boolean ok = compareMaps("struct asym", origAsym, writtenAsym);
            Assert.assertTrue(ok);
        }
    }

    public void testStructConfBlock() throws IOException {
        Map<String, List<Object>> origConf = buildStructConfMap(orig);
        Map<String, List<Object>> writtenConf = buildStructConfMap(written);
        if (!origConf.isEmpty() && !writtenConf.isEmpty()) {
            boolean ok = compareMaps("struct conf", origConf, writtenConf);
            Assert.assertTrue(ok);
        }
    }

    public void testSheetBlock() throws IOException {
        Map<String, List<Object>> origSheet = buildSheetMap(orig);
        Map<String, List<Object>> writtenSheet = buildSheetMap(written);
        if (!origSheet.isEmpty() && !writtenSheet.isEmpty()) {
            boolean ok = compareMaps("sheet range", origSheet, writtenSheet);
            Assert.assertTrue(ok);
        }
    }

}
