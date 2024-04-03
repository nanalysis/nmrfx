package org.nmrfx.structure.chemistry.miner;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.AtomContainer;
import org.nmrfx.chemistry.IAtom;
import org.nmrfx.chemistry.IBond;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AtomTriplets {

    AtomContainer ac = null;
    Map tripletMap = new HashMap();

    public AtomTriplets(AtomContainer ac) {
        this.ac = ac;
        execute();
    }

    private void execute() {
        int nAtoms = ac.getAtomCount();
        AtomTypes aTypes = new AtomTypes(ac);
        for (IAtom startAtom : ac.atoms()) {

            if (startAtom.getAtomicNumber() == 1) {
                continue;
            }

            String iDesc = aTypes.get(startAtom);
            List<IBond> bonds = ac.getBonds(startAtom);
            IAtom[] atoms = new Atom[bonds.size()];
            int nAtoms2 = 0;

            for (IBond bond : bonds) {
                IAtom atom = bond.getConnectedAtom(startAtom);

                if (atom.getAtomicNumber() != 1) {
                    atoms[nAtoms2++] = atom;
                }
            }

            for (int j = 0; j < (nAtoms2 - 1); j++) {
                String jDesc = aTypes.get(atoms[j]);

                for (int k = (j + 1); k < nAtoms2; k++) {
                    String kDesc = aTypes.get(atoms[k]);

                    if (jDesc.compareTo(kDesc) < 0) {
                        add(jDesc + "-" + iDesc + "-" + kDesc);
                    } else {
                        add(kDesc + "-" + iDesc + "-" + jDesc);
                    }
                }
            }
        }

        System.out.println(tripletMap.size());

        Iterator iter = tripletMap.keySet().iterator();

        while (iter.hasNext()) {
            String key = (String) iter.next();
            Integer value = (Integer) tripletMap.get(key);
            System.out.println(key + " " + value);
        }
    }

    void add(String aTriplet) {
        Integer value = (Integer) tripletMap.get(aTriplet);
        int iValue = 0;

        if (value != null) {
            iValue = value.intValue();
        }

        iValue++;
        tripletMap.put(aTriplet,  Integer.valueOf(iValue));
    }
}
