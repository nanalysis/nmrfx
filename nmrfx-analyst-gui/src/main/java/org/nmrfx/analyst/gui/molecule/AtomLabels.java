package org.nmrfx.analyst.gui.molecule;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.PPMv;
import org.nmrfx.structure.chemistry.Molecule;

import java.util.ArrayList;

public class AtomLabels {

    public enum LabelTypes {
        LABEL_NONE,
        LABEL_LABEL,
        LABEL_FC,
        LABEL_SYMBOL,
        LABEL_NUMBER,
        LABEL_SYMBOL_AND_NUMBER,
        LABEL_FFC,
        LABEL_SECONDARY_STRUCTURE,
        LABEL_RESIDUE,
        LABEL_CHARGE,
        LABEL_VALUE,
        LABEL_TITLE,
        LABEL_MOLECULE_NAME,
        LABEL_STRING,
        LABEL_BOND,
        LABEL_CUSTOM,
        LABEL_NAME,
        LABEL_HPPM,
        LABEL_PPM,
        LABEL_NONHC,
        LABEL_NONHCO
    }

    public static String getAtomLabel(Atom atom, LabelTypes labelMode) {
        Molecule molecule = (Molecule) atom.getEntity().molecule;
        return switch (labelMode) {
            case LABEL_NONE, LABEL_SECONDARY_STRUCTURE, LABEL_RESIDUE, LABEL_STRING, LABEL_BOND, LABEL_CUSTOM, LABEL_TITLE:
                yield "";
            case LABEL_LABEL:
                yield atom.getFullName();
            case LABEL_FC:
                if (atom.fcharge != 0.0) {
                    yield String.valueOf(atom.fcharge);
                } else {
                    yield "";
                }

            case LABEL_SYMBOL:
                yield Atom.getElementName(atom.aNum);

            case LABEL_NUMBER:
                atom.label = String.valueOf(atom.iAtom);

            case LABEL_SYMBOL_AND_NUMBER:
                yield Atom.getElementName(atom.aNum) + " " + atom.iAtom;

            case LABEL_FFC:
                yield String.valueOf(atom.forceFieldCode);


            case LABEL_CHARGE: {
                if (atom.charge != 0.0) {
                    yield String.valueOf(atom.charge);
                } else {
                    yield "";
                }
            }

            case LABEL_VALUE:
                yield String.valueOf(atom.value);

            case LABEL_MOLECULE_NAME:
                yield molecule.getName();


            case LABEL_NAME:
                String thisLabel;
                if ((atom.aNum == 6) || (atom.aNum == 1)){
                    thisLabel = atom.getName().substring(1);
                } else {
                    thisLabel = atom.getName();
                }

                yield thisLabel;
            case LABEL_NONHC:
                if ((atom.aNum == 6) || (atom.aNum == 1)) {
                    yield "";
                } else {
                    yield Atom.getElementName(atom.aNum);
                }


            case LABEL_NONHCO:
                if ((atom.aNum == 6) || (atom.aNum == 1) || (atom.aNum == 8)) {
                    yield "";
                } else {
                    yield Atom.getElementName(atom.aNum);
                }


            case LABEL_HPPM:
                ArrayList<Atom> hydrogens = molecule.getAttachedHydrogens(atom);
                int nH = hydrogens.size();
                PPMv ppmV0 = null;
                PPMv ppmV1 = null;
                Atom hAtom;
                if (nH > 0) {
                    hAtom = hydrogens.get(0);
                    ppmV0 = hAtom.getPPM(0);
                    if (nH == 2) {
                        hAtom = hydrogens.get(1);
                        ppmV1 = hAtom.getPPM(0);
                    }
                }

                if (ppmV0 == null) {
                    yield "";
                } else if (ppmV1 == null) {
                    yield String.valueOf(ppmV0.getValue());
                } else {
                    yield ppmV0.getValue() + "," + ppmV1.getValue();
                }

            case LABEL_PPM:
                PPMv ppmV = atom.getPPM(0);
                if ((ppmV == null) || ((atom.getElementNumber() == 1) && atom.isMethyl() && !atom.isFirstInMethyl())) {
                    yield "";
                } else {
                    yield String.format("%.2f",ppmV.getValue());
                }
        };
    }
}
