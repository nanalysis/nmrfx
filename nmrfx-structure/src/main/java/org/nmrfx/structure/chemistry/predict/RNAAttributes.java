package org.nmrfx.structure.chemistry.predict;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.Residue;

import java.util.*;

/**
 * @author Bruce Johnson
 */
public class RNAAttributes {

    static Map<String, String> attrMap = new HashMap<>();
    static Map<String, RNAStats> statMap = new HashMap<>();
    static List<String> rnaAtomSources = Arrays.asList("C1'", "C2'", "C3'", "C4'", "C5'", "P", "OP1", "OP2", "O2'", "O3'", "O4'", "O5'",
            "AN9", "AC8", "AN7", "AC5", "AC4", "AN3", "AC2", "AN1", "AC6", "AN6",
            "CN1", "CC2", "CO2", "CN3", "CC4", "CN4", "CC5", "CC6",
            "GN9", "GC8", "GN7", "GC5", "GC4", "GN3", "GC2", "GN2", "GN1", "GC6", "GO6",
            "UN1", "UC2", "UO2", "UN3", "UC4", "UO4", "UC5", "UC6");
    static List<String> types = new ArrayList<>();
    static Map<String, Integer> rnaAtomSourceMap = new HashMap<>();

    static {
        for (int i = 0; i < rnaAtomSources.size(); i++) {
            rnaAtomSourceMap.put(rnaAtomSources.get(i), i);
        }
    }

    public static void setTypes(List<String> newTypes) {
        types.clear();
        types.addAll(newTypes);
    }

    public static List<String> getTypes() {
        return types;
    }

    public static List<String> getAtomSources() {
        return rnaAtomSources;
    }

    public static int getAtomSourceIndex(Atom atom) {
        String resName = atom.getEntity().getName();
        String atomName = atom.getName();
        String key = resName + atomName;
        if (atomName.contains("'") || atomName.contains("P")) {
            key = atomName;
        }

        Integer value = rnaAtomSourceMap.get(key);
        return value == null ? -1 : value;
    }

    public static void put(String resName, String attributes) {
        attrMap.put(resName, attributes);
    }

    public static String get(String resName) {
        return attrMap.get(resName);
    }

    public static void putStats(Atom atom, RNAStats stats) {
        statMap.put(atom.getFullName(), stats);
    }

    public static void putStats(String name, RNAStats stats) {
        Atom atom = MoleculeBase.getAtomByName(name);
        statMap.put(atom.getFullName(), stats);
    }

    public static RNAStats getStats(Atom atom) {
        return statMap.get(atom.getFullName());
    }

    public static String get(Atom atom) {
        String attr = "";
        if (atom.getEntity() instanceof Residue) {
            Residue residue = (Residue) atom.getEntity();
            String resName = residue.getName();
            String resNum = residue.getNumber();
            String polymerName = residue.getPolymer().getName();
            String name = polymerName + ":" + resName + resNum;
            attr = attrMap.get(name);
            if (attr == null) {
                name = resName + resNum;
                attr = attrMap.get(name);
                if (attr == null) {
                    attr = attrMap.get(resNum);
                }
            }
        }
        return attr;
    }

    public static void clear() {
        attrMap.clear();
    }

    public static void dump() {
        for (String key : attrMap.keySet()) {
            System.out.println(key + " " + attrMap.get(key).toString());
        }
    }
}
