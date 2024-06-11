package org.nmrfx.structure.chemistry.energy;

import org.nmrfx.chemistry.*;
import org.nmrfx.structure.chemistry.HydrogenBond;
import org.nmrfx.structure.chemistry.Molecule;
import org.python.modules.math;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RNAPropertyGenerator extends PropertyGenerator {
    private static final Logger log = LoggerFactory.getLogger(RNAPropertyGenerator.class);
    private static boolean verbose = true;
    String[] residueNames = {"A","U","G","C"};
    public void init(Molecule molecule, int iStructure) throws InvalidMoleculeException, IOException {
        this.molecule = molecule;
        contactMap = molecule.calcContactSum(iStructure, true);
        hBondMap = new HashMap<>();
        eShiftMap = new HashMap<>();
        String[] hbondAtomNames = {"H", "HA"};
        for (String atomName : hbondAtomNames) {
            MolFilter hydrogenFilter = new MolFilter("*." + atomName);
            MolFilter acceptorFilter = new MolFilter("*.O*");
            if (atomName.startsWith("HA")) {
                hydrogenFilter = new MolFilter("*." + atomName + "*");
            }
            Map<String, HydrogenBond> hBondMapForAtom = molecule.hydrogenBondMap(hydrogenFilter, acceptorFilter, iStructure);
            hBondMap.putAll(hBondMapForAtom);
            MolFilter sourceFilter = new MolFilter("*.O*,N,H");  // fixme ?  why is H here (for NH?)
            Map<String, Double> eShiftMapForAtom = molecule.electroStaticShiftMap(hydrogenFilter, sourceFilter, iStructure);
            eShiftMap.putAll(eShiftMapForAtom);
        }
    }
    public boolean getResidueProperties(Polymer polymer, Residue residue,
                                        int structureNum) {
        valueMap.clear();
        for (String residueName : residueNames) {
            valueMap.put(residueName, 0.0);
        }
        valueMap.put(residue.getName(), 1.0);

        try {
            for(Residue.AngleAtoms angleName : Residue.AngleAtoms.values()) {
                valueMap.put(angleName.toString(), angleName.calcAngle(residue));
            }
            valueMap.put("chi", residue.calcChi());
        } catch (Exception e) {
            System.out.println(e);
            log.warn(e.getMessage(), e);
            return false;
        }
        return true;
    }

    public static boolean checkAngles(Double... values) {
        for (Double value : values) {
            if ((value == null) || Double.isNaN(value) || Double.isInfinite(value)) {
                return false;
            }
        }
        return true;
    }

    void calcAngle(String name, Double angle) {
        valueMap.put("cos"+name, checkAngles(angle) ? math.cos(angle) : 0.0);
        valueMap.put("sin"+name, checkAngles(angle) ? math.sin(angle) : 0.0);
        valueMap.put("cos2"+name,checkAngles(angle) ? math.cos(2*angle) : 0.0);
        valueMap.put("sin2"+name,checkAngles(angle) ? math.sin(2*angle) : 0.0);
        valueMap.put("cos3"+name,checkAngles(angle) ? math.cos(3*angle) : 0.0);
        valueMap.put("sin3"+name,checkAngles(angle) ? math.sin(3*angle) : 0.0);
    }

    public boolean calcAngles() {
        for (Residue.AngleAtoms angleName : Residue.AngleAtoms.values()) {
            String name = angleName.toString().toLowerCase();
            Double angle = valueMap.get(name);
            calcAngle(name, angle);
        }
        calcAngle("chi", valueMap.get("chi"));
        return true;
    }

    public Map<String, Double> getValues() {
        return valueMap;
    }

}