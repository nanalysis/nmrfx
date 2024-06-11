package org.nmrfx.structure.chemistry.predict;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.energy.RNAPropertyGenerator;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RNAPredictor {
    RNAPropertyGenerator propertyGenerator;
    Molecule molecule = null;
    static Map<String, List<String>> atomTypes = new HashMap<>();
    {
        atomTypes.put("A", Arrays.asList("H2", "H8", "H1'", "H2'", "H3'", "H4'", "H5'","H5''"));
        atomTypes.put("U", Arrays.asList("H8", "H1'", "H2'", "H3'", "H4'", "H5'","H5''"));
        atomTypes.put("G", Arrays.asList("H5", "H6", "H1'", "H2'", "H3'", "H4'", "H5'","H5''"));
        atomTypes.put("C", Arrays.asList("H5", "H6", "H1'", "H2'", "H3'", "H4'", "H5'","H5''"));
    }

    public void init(Molecule mol, int iStructure) throws InvalidMoleculeException, IOException {
        propertyGenerator = new RNAPropertyGenerator();
        propertyGenerator.init(mol, iStructure);
        this.molecule = mol;
    }

    public double[][] predict(Residue residue, int structureNum) {
        int nAtoms = (int) residue.getAtoms().stream().filter(a -> atomTypes.get(residue.getName()).contains(a.getName())).count();
        double[][] attrs = null;
        Map<String, Double> valueMap = propertyGenerator.getValues();
        Polymer polymer = residue.getPolymer();
        int i = 0;
        if (propertyGenerator.getResidueProperties(polymer, residue, structureNum)) {
            propertyGenerator.calcAngles();
            for (Atom atom : residue.getAtoms()) {
                if (atomTypes.get(residue.getName()).contains(atom.getName())) {
                    propertyGenerator.getAtomProperties(atom, structureNum);
                    if (attrs == null) {
                        attrs = new double[nAtoms][valueMap.values().size()];
                    }
                    int j = 0;
//                    for (Map.Entry<String, Double> entry : valueMap.entrySet()) {
//                        if (entry.getValue() == null) {
//                            System.out.println(residue.getName() + " " + residue.getNumber() + " " + atom.getName() + " " + entry.getKey() + " " + entry.getValue());
//                        }
//                    }
                    for (double value : valueMap.values()) attrs[i][j++] = value;
                    i++;
                }
            }
        }
        return attrs;
    }

    public void predict(int structureNum) throws InvalidMoleculeException, IOException {
        for (Polymer polymer : molecule.getPolymers()) {
            if (polymer.isRNA()) {
                predict(polymer, structureNum);
            }
        }
    }

    public void predict(Polymer polymer, int structureNum) throws IOException {
        for (Residue residue : polymer.getResidues()) {
            if (residue.isStandard()) {
                predict(residue, structureNum);
            }
        }
    }
}
