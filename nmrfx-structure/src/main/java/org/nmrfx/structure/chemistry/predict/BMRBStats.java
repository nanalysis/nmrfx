/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.predict;

import org.nmrfx.chemistry.PPMv;
import org.nmrfx.chemistry.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author brucejohnson
 */
public class BMRBStats {

    static Map<String, Map<String, PPMv>> resMap = new HashMap<>();

    public static Optional<PPMv> getValue(String compName, String atomName) {
        Optional<PPMv> result = Optional.empty();
        Map<String, PPMv> atomMap = resMap.get(compName);
        if (atomMap != null) {
            PPMv ppmV = atomMap.get(atomName);
            if (ppmV != null) {
                result = Optional.of(ppmV);
            }
        }
        return result;
    }

    public static boolean loadAllIfEmpty() {
        if (!resMap.isEmpty()) {
            return true;
        }
        String[] types = {"protein", "rna", "dna"};
        boolean result = true;
        for (String type : types) {
            if (!load(type)) {
                result = false;
            }
        }
        return result;
    }

    public static boolean load(String type) {
        String fileName = "data/" + type + "_shifts.tbl";
        return load(fileName, true);
    }

    public static boolean load(String fileName, boolean resourceMode) {
        // comp_id,atom_id,count,min,max,avg,std
        // ALA,H,62507,3.53,12.110,8.195,0.580
        boolean result = false;
        InputStream iStream = Util.getResourceStream(fileName, resourceMode);
        if (iStream != null) {
            try (BufferedReader bReader = new BufferedReader(new InputStreamReader(iStream))) {
                boolean firstLine = true;
                result = true;
                while (true) {
                    String line = bReader.readLine();
                    if (line == null) {
                        break;
                    }
                    if (!firstLine) {
                        String[] fields = line.split(",");
                        if (fields.length == 7) {
                            String compName = fields[0];
                            String atomName = fields[1];
                            double avgValue = Double.valueOf(fields[5]);
                            double sdevValue = Double.valueOf(fields[6]);
                            PPMv ppmV = new PPMv(avgValue);
                            ppmV.setError(sdevValue);
                            if (atomName.charAt(0) == 'M') {
                                atomName = "H" + atomName.substring(1);
                                for (int i = 1; i <= 3; i++) {
                                    setValue(compName, atomName + i, ppmV);
                                }
                            } else {
                                setValue(compName, atomName, ppmV);
                            }
                        }
                    } else {
                        firstLine = false;
                    }

                }

            } catch (IOException ex) {
                result = false;
            }
        }
        return result;
    }

    static void setValue(String compName, String atomName, PPMv ppmV) {
        Map<String, PPMv> atomMap = resMap.get(compName);
        if (atomMap == null) {
            atomMap = new HashMap<>();
            resMap.put(compName, atomMap);
        }
        atomMap.put(atomName, ppmV);
    }

}
