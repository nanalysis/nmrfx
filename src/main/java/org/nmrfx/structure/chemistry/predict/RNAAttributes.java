package org.nmrfx.structure.chemistry.predict;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.Residue;

/**
 *
 * @author Bruce Johnson
 */
public class RNAAttributes {

    static Map<String, String> attrMap = new HashMap<>();
    static Map<String, RNAStats> statMap = new HashMap<>();
    static List<String> types = new ArrayList<>();

    public static void setTypes(List<String> newTypes) {
        types.clear();
        types.addAll(newTypes);
    }

    public static List<String> getTypes() {
        return types;
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
        Atom atom = Molecule.getAtomByName(name);
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
