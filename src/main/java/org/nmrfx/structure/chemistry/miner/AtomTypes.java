package org.nmrfx.structure.chemistry.miner;

import org.nmrfx.chemistry.AtomContainer;
import org.nmrfx.chemistry.IAtom;

import java.util.List;

public class AtomTypes {

    AtomContainer ac = null;

    public AtomTypes(AtomContainer ac) {
        this.ac = ac;
        execute();
    }

    String get(int i) {
        return get(ac.getAtom(i));
    }

    String get(IAtom atom) {
        Object value = atom.getProperty("type");
        if (value == null) {
            return "";
        } else {
            return (String) value;
        }
    }

    private void execute() {
        int nAtoms = ac.getAtomCount();
        for (IAtom atom : ac.atoms()) {
            String propValue = atom.getSymbol() + getNonHydrogenCount(atom) + "." + atom.getHybridization();
            atom.setProperty("type", propValue);
        }
    }

    int getNonHydrogenCount(IAtom atom) {
        int value = 0;
        List<IAtom> atomsList = ac.getConnectedAtomsList(atom);
        for (IAtom cAtom : atomsList) {
            if (cAtom.getAtomicNumber() != 1) {
                value++;
            }
        }

        return value;
    }
}
