/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.rna;

import org.nmrfx.chemistry.Residue;
import org.nmrfx.chemistry.SecondaryStructure;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author bajlabuser
 */
public class InteractionType {

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
            } else if (loopSize > 4) {
                classification = 'L';
            }
        }
        return classification;
    }

    public static String determineType(Residue aResObj, Residue bResObj) {
        String interType = null;
        LinkedHashMap<String, Boolean> typeMap = new LinkedHashMap<String, Boolean>();
        int dis = distance(aResObj, bResObj);
        char aResLoopType = classifyLoop(aResObj.secStruct);
        char bResLoopType = classifyLoop(bResObj.secStruct);
        boolean sameRes = aResObj.equals(bResObj);
        boolean sameSS = (aResObj.secStruct == bResObj.secStruct);
        boolean basePair = aResObj.pairedTo == bResObj;
        boolean bothInLoop = aResObj.secStruct instanceof Loop && bResObj.secStruct instanceof Loop;
        boolean bothInHelix = aResObj.secStruct instanceof RNAHelix && bResObj.secStruct instanceof RNAHelix;
        boolean oneAwayBasePair = sameSS && bothInHelix && (aResObj.previous != null) && (bResObj == aResObj.previous.pairedTo);
        boolean loopAndHelix = (aResObj.secStruct instanceof RNAHelix && bResObj.secStruct instanceof Loop) || (aResObj.secStruct instanceof Loop && bResObj.secStruct instanceof RNAHelix);
        boolean bulgeAndHelix = (aResObj.secStruct instanceof Bulge && bResObj.secStruct instanceof RNAHelix);
        boolean helixAndBulge = (aResObj.secStruct instanceof RNAHelix && bResObj.secStruct instanceof Bulge);
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
        typeMap.put("BH", sameSS && bulgeAndHelix && dis == 1); //filter reverse cases
        typeMap.put("HB", sameSS && helixAndBulge && dis == 1);
        typeMap.put("BB", !sameRes && bothInSameBulge);
        typeMap.put("TH", loopAndHelix && aResLoopType == 'T' && dis == 1);
        typeMap.put("HT", loopAndHelix && bResLoopType == 'T' && dis == 1);
        typeMap.put("SH", loopAndHelix && aResLoopType == 'S' && dis == 1);
        typeMap.put("HS", loopAndHelix && bResLoopType == 'S' && dis == 1);
        typeMap.put("LH", loopAndHelix && aResLoopType == 'L' && dis == 1);
        typeMap.put("HL", loopAndHelix && bResLoopType == 'L' && dis == 1);
        typeMap.put("TA", bothInHelix && dis == 2);

        for (Map.Entry<String, Boolean> type : typeMap.entrySet()) {
            if (type.getValue()) {
                interType = type.getKey();
                break;
            }
        }
        return interType;
    }

}
