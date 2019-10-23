/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry;

import java.util.*;

/**
 *
 * @author bajlabuser
 */
public class InteractionType {

    public static int distance(Residue aResObj, Residue bResObj) {
        int distance;
        distance = Math.abs(aResObj.iRes - bResObj.iRes);
        return distance;
    }

    public static char classifyLoop(SecondaryStructure loop) {
        int loopSize;
        char classification = 0;
        loopSize = loop.size;
        if (loopSize < 4) {
            classification = 'S';
        } else if (loopSize == 4) {
            classification = 'T';
        } else if (loopSize > 4) {
            classification = 'L';
        }
        return classification;
    }

    public static void determineType(Residue aResObj, Residue bResObj) {
        HashMap<String, Boolean> typeMap = new HashMap<String, Boolean>();
        boolean sameRes = aResObj.equals(bResObj);
        boolean basePair = aResObj.pairedTo == bResObj;
        boolean bothInLoop = aResObj.secStruct instanceof Loop && bResObj.secStruct instanceof Loop;
        boolean bothInHelix = aResObj.secStruct instanceof Helix && bResObj.secStruct instanceof Helix;
        boolean loopAndHelix = (aResObj.secStruct instanceof Helix && bResObj.secStruct instanceof Loop) || (aResObj.secStruct instanceof Loop && bResObj.secStruct instanceof Helix);
        boolean inSameLoop = (bothInLoop && aResObj.secStruct.locali == bResObj.secStruct.locali);
        boolean bulgeAndHelix = ((aResObj.secStruct instanceof Bulge && bResObj.secStruct instanceof Helix) || (aResObj.secStruct instanceof Helix && bResObj.secStruct instanceof Bulge));
        boolean bulgeAndLoop = ((aResObj.secStruct instanceof Bulge && bResObj.secStruct instanceof Loop) || (aResObj.secStruct instanceof Loop && bResObj.secStruct instanceof Bulge));
        boolean tetraAndHelix = ((aResObj.secStruct instanceof Loop && bResObj.secStruct instanceof Helix) || (aResObj.secStruct instanceof Helix && bResObj.secStruct instanceof Loop));
        boolean bothInBulge = (aResObj.secStruct instanceof Bulge && bResObj.secStruct instanceof Bulge);
        typeMap.put("ADJ", !sameRes && bothInHelix && distance(aResObj, bResObj) == 1);
        typeMap.put("BP", basePair);
        typeMap.put("L1", bothInLoop && inSameLoop && classifyLoop(aResObj.secStruct) == 'L' && distance(aResObj, bResObj) == 1);
        typeMap.put("L2", bothInLoop && inSameLoop && classifyLoop(aResObj.secStruct) == 'L' && distance(aResObj, bResObj) == 2);
        typeMap.put("L3", bothInLoop && inSameLoop && classifyLoop(aResObj.secStruct) == 'L' && distance(aResObj, bResObj) == 3);
        typeMap.put("SRH", sameRes && bothInHelix);
        typeMap.put("SRL", sameRes && bothInLoop && classifyLoop(aResObj.secStruct) == 'L');
        typeMap.put("SRT", sameRes && bothInLoop && classifyLoop(aResObj.secStruct) == 'T');
        typeMap.put("SRB", sameRes && aResObj.secStruct instanceof Bulge);
        typeMap.put("S1", bothInLoop && classifyLoop(aResObj.secStruct) == 'S' && inSameLoop && distance(aResObj, bResObj) == 1);
        typeMap.put("S2", bothInLoop && classifyLoop(aResObj.secStruct) == 'S' && inSameLoop && distance(aResObj, bResObj) == 2);
        typeMap.put("SH", !sameRes && loopAndHelix && distance(aResObj, bResObj) == 1);
        typeMap.put("T1", bothInLoop && classifyLoop(aResObj.secStruct) == 'T' && inSameLoop && distance(aResObj, bResObj) == 1);
        typeMap.put("T2", bothInLoop && classifyLoop(aResObj.secStruct) == 'T' && inSameLoop && distance(aResObj, bResObj) == 2);
        typeMap.put("T3", bothInLoop && classifyLoop(aResObj.secStruct) == 'T' && inSameLoop && distance(aResObj, bResObj) == 3);
        typeMap.put("TB", bulgeAndLoop && (classifyLoop(aResObj.secStruct) == 'T' || classifyLoop(bResObj.secStruct) == 'T'));
        typeMap.put("HB", !sameRes && bulgeAndHelix);
        typeMap.put("BB", !sameRes && bothInBulge);
        typeMap.put("TH", !sameRes && tetraAndHelix && (classifyLoop(aResObj.secStruct) == 'T' || classifyLoop(bResObj.secStruct) == 'T'));

        for (Map.Entry<String, Boolean> type : typeMap.entrySet()) {
            if (type.getValue()) {
                System.out.println( type.getKey()+ "  "+ aResObj.iRes + "  "+ aResObj.secStruct+ " " +bResObj.iRes+ "  "+ bResObj.secStruct);
            }
        }
    }

}
