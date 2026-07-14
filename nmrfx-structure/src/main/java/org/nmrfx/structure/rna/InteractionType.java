/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.rna;

import org.nmrfx.chemistry.Residue;
import org.nmrfx.chemistry.SecondaryStructure;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

/**
 * @author bajlabuser
 */
public class InteractionType {
    static Map<String, Map<String, Double>> atomMaps = new HashMap<>();
    static final String RES_PAIR_TABLE_FILENAME = "/data/res_pair_table.txt";

    private static double maxDist = 5.5;
    private static double limitScale = 4.0;

    public static int distance(Residue aResObj, Residue bResObj) {
        int distance;
        distance = bResObj.iRes - aResObj.iRes;
        return distance;
    }

    public static char classifyLoop(SecondaryStructure struct) {
        int loopSize;
        char classification = 0;
        if (struct instanceof Loop) {
            loopSize = struct.size;
            if (loopSize < 4) {
                classification = 'S';
            } else if (loopSize == 4) {
                classification = 'T';
            } else {
                classification = 'L';
            }
        }
        return classification;
    }

    public static String determineType(Residue aResObj, Residue bResObj) {
        String interType = null;
        LinkedHashMap<String, Boolean> typeMap = new LinkedHashMap<>();
        int dis = distance(aResObj, bResObj);
        char aResLoopType = classifyLoop(aResObj.secStruct);
        char bResLoopType = classifyLoop(bResObj.secStruct);
        boolean sameRes = aResObj.equals(bResObj);
        boolean sameSS = (aResObj.secStruct == bResObj.secStruct);
        boolean samePolymer = aResObj.polymer == bResObj.polymer;
        boolean basePair = aResObj.pairedTo == bResObj;
        boolean bothInLoop = aResObj.secStruct instanceof Loop && bResObj.secStruct instanceof Loop;
        boolean bothInHelix = aResObj.secStruct instanceof RNAHelix && bResObj.secStruct instanceof RNAHelix;
        boolean oneAwayBasePair = sameSS && bothInHelix && (aResObj.previous != null) && (bResObj == aResObj.previous.pairedTo);
        boolean loopAndHelix = (aResObj.secStruct instanceof RNAHelix && bResObj.secStruct instanceof Loop) || (aResObj.secStruct instanceof Loop && bResObj.secStruct instanceof RNAHelix);
        boolean bulgeAndHelix = ((aResObj.secStruct instanceof Bulge) && (bResObj.secStruct instanceof RNAHelix));
        boolean helixAndBulge = ((aResObj.secStruct instanceof RNAHelix) && (bResObj.secStruct instanceof Bulge));
        boolean bothInSameBulge = (aResObj.secStruct instanceof Bulge && bResObj.secStruct instanceof Bulge && sameSS);
        boolean inTetraLoop = (bothInLoop && sameSS && aResLoopType == 'T');
        boolean T12 = (inTetraLoop && (aResObj.secStruct.getResidues().get(0).equals(aResObj) && bResObj.secStruct.getResidues().get(1).equals(bResObj)));
        boolean T13 = (inTetraLoop && (aResObj.secStruct.getResidues().get(0).equals(aResObj) && bResObj.secStruct.getResidues().get(2).equals(bResObj)));
        boolean T14 = (inTetraLoop && (aResObj.secStruct.getResidues().get(0).equals(aResObj) && bResObj.secStruct.getResidues().get(3).equals(bResObj)));
        boolean T23 = (inTetraLoop && (aResObj.secStruct.getResidues().get(1).equals(aResObj) && bResObj.secStruct.getResidues().get(2).equals(bResObj)));
        boolean T24 = (inTetraLoop && (aResObj.secStruct.getResidues().get(1).equals(aResObj) && bResObj.secStruct.getResidues().get(3).equals(bResObj)));
        boolean T34 = (inTetraLoop && (aResObj.secStruct.getResidues().get(2).equals(aResObj) && bResObj.secStruct.getResidues().get(3).equals(bResObj)));
        typeMap.put("ADJ", sameSS && bothInHelix && dis == 1);
        typeMap.put("BP", sameSS && basePair);
        typeMap.put("BPD", !sameSS && basePair);
        typeMap.put("OABP", oneAwayBasePair);
        typeMap.put("L1", bothInLoop && sameSS && aResLoopType == 'L' && dis == 1);
        typeMap.put("L2", bothInLoop && sameSS && aResLoopType == 'L' && dis == 2);
        typeMap.put("L3", bothInLoop && sameSS && aResLoopType == 'L' && dis == 3);
        typeMap.put("SRH", sameRes && bothInHelix);
        typeMap.put("SRL", sameRes && bothInLoop && aResLoopType == 'L');
        typeMap.put("SRT", sameRes && bothInLoop && aResLoopType == 'T');
        typeMap.put("SRB", sameRes && aResObj.secStruct instanceof Bulge);
        typeMap.put("S1", sameSS && aResLoopType == 'S' && dis == 1);
        typeMap.put("S2", sameSS && aResLoopType == 'S' && dis == 2);
        typeMap.put("T12", T12);
        typeMap.put("T13", T13);
        typeMap.put("T14", T14);
        typeMap.put("T23", T23);
        typeMap.put("T24", T24);
        typeMap.put("T34", T34);
        typeMap.put("BH", samePolymer && bulgeAndHelix && dis == 1); //filter reverse cases
        typeMap.put("HB", samePolymer && helixAndBulge && dis == 1);
        typeMap.put("BB", !sameRes && bothInSameBulge);
        typeMap.put("TH", loopAndHelix && aResLoopType == 'T' && dis == 1);
        typeMap.put("HT", loopAndHelix && bResLoopType == 'T' && dis == 1);
        typeMap.put("SH", loopAndHelix && aResLoopType == 'S' && dis == 1);
        typeMap.put("HS", loopAndHelix && bResLoopType == 'S' && dis == 1);
        typeMap.put("LH", loopAndHelix && aResLoopType == 'L' && dis == 1);
        typeMap.put("HL", loopAndHelix && bResLoopType == 'L' && dis == 1);
        typeMap.put("TA", bothInHelix && dis == 2);

        for (Map.Entry<String, Boolean> type : typeMap.entrySet()) {
            if (Boolean.TRUE.equals(type.getValue())) {
                interType = type.getKey();
                break;
            }
        }
        return interType;
    }

    public static void maxDist(double value) {
        atomMaps.clear();
        maxDist = value;
    }

    public static void limitScale(int value) {
        atomMaps.clear();
        limitScale = value;
    }

    record AtomResDistance(String type, String res1, String res2, String atom1, String atom2, double distance, int n) {
        String getKey() {
            String resA = res1;
            String resB = res2;
            if (atom1.endsWith("'")) {
                resA = "r";
            } else if (resA.equals("A") || resA.equals("G")) {
                resA = "P";
            } else {
                resA = "p";
            }
            if (atom2.endsWith("'")) {
                resB = "r";
            } else if (resB.equals("A") || resB.equals("G")) {
                resB = "P";
            } else {
                resB = "p";
            }
            return type + "." + resA + "." + atom1 + "." + resB + "." + atom2;
        }

        String getShortKey() {
            return type + "." + res1 + "." + res2;
        }

        String getAtomKey() {
            return atom1 + "." + atom2;
        }

    }

    public static Map<String, Map<String, Double>> getInteractionMap() {
        if (atomMaps.isEmpty()) {
            loadInteractionMap();
        }
        return atomMaps;
    }

    public static void loadInteractionMap() {

        Map<String, Double> sums = new HashMap<>();
        Map<String, Integer> nInter = new HashMap<>();
        Map<String, Integer> nTypes = new HashMap<>();
        Map<String, Integer> nExample = new HashMap<>();
        List<AtomResDistance> atomResDistances = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(InteractionType.class.getResourceAsStream(RES_PAIR_TABLE_FILENAME))))) {
            List<String> lines = reader.lines().toList();
            lines.forEach(line -> {
                line = line.trim();
                if (!line.isEmpty() && (line.charAt(0) != '#')) {
                    String[] fields = line.split("\t");
                    if ((fields.length == 9) && !fields[0].trim().equals("Type")) {
                        String interType = fields[0].trim();
                        String res1 = fields[1].trim();
                        String res2 = fields[2].trim();
                        String atom1 = fields[3].trim();
                        String atom2 = fields[4].trim();
                        double avgDis = Double.parseDouble(fields[7].trim());
                        int nInst = Integer.parseInt(fields[8].trim());
                        AtomResDistance atomResDistance = new AtomResDistance(interType, res1, res2, atom1, atom2, avgDis, nInst);
                        String key = atomResDistance.getKey();
                        sums.merge(key, avgDis * nInst, Double::sum);
                        nInter.merge(key, nInst, Integer::sum);
                        nTypes.merge(interType, nInst, Integer::sum);
                        nExample.merge(interType, 1, Integer::sum);
                        atomResDistances.add(atomResDistance);
                    }
                }
            });
        } catch (IOException ioException) {
        }
        for (var atomResDistance : atomResDistances) {
            double sum = sums.get(atomResDistance.getKey());
            int nInst = nInter.get(atomResDistance.getKey());
            double distance = sum / nInst;
            String type = atomResDistance.type;
            double limit = (double) nTypes.get(type) / nExample.get(type) / limitScale;
            if ((distance < maxDist) && (nInst  > limit)) {
                String key = atomResDistance.getShortKey();
                String key2 = atomResDistance.getAtomKey();
                var aMap = atomMaps.computeIfAbsent(key, k -> new HashMap<>());
                aMap.putIfAbsent(key2, distance);
            }
        }
    }
}
