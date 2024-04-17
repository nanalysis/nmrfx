package org.nmrfx.structure.chemistry.miner;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.AtomContainer;
import org.nmrfx.chemistry.IAtom;
import org.nmrfx.chemistry.IBond;

import java.util.*;

public class BreadthFirstIterator implements Iterator {

    AtomContainer ac = null;
    boolean debug = false;
    ArrayList sphereOfAtoms = new ArrayList();
    ArrayList nextSphereOfAtoms = new ArrayList();
    Map atomMap = new HashMap();
    ArrayList bondedAtoms = new ArrayList();

    public BreadthFirstIterator(AtomContainer ac) {
        this.ac = ac;
        setupBondedAtoms();
    }

    public BreadthFirstIterator(AtomContainer ac, int iAtom) {
        this.ac = ac;
        setupBondedAtoms();
        initialize(iAtom);
    }

    void setupBondedAtoms() {
        int i = 0;
        for (IAtom atom : ac.atoms()) {
            atomMap.put(atom, Integer.valueOf(i++));
        }
        for (IAtom startAtom : ac.atoms()) {
            ArrayList aList = new ArrayList();
            bondedAtoms.add(aList);

            if (startAtom.getAtomicNumber() != 1) {
                List<IBond> bonds = ac.getConnectedBondsList(startAtom);

                for (IBond bond : bonds) {
                    IAtom atom = bond.getConnectedAtom(startAtom);

                    if (atom.getAtomicNumber() != 1) {
                        aList.add(Integer.valueOf(getAtomIndex(atom)));
                    }
                }
            }
        }
    }

    void initialize(int iAtom) {

        for (IAtom atom : ac.atoms()) {
            atom.setFlag(Atom.VISITED, false);
        }

        IAtom atom = ac.getAtom(iAtom);
        sphereOfAtoms.clear();
        sphereOfAtoms.add(Integer.valueOf(iAtom));
        atom.setFlag(Atom.VISITED, true);
    }

    int getAtomIndex(IAtom atom) {
        Integer value = (Integer) atomMap.get(atom);
        int index = -1;

        if (value != null) {
            index = value.intValue();
        }

        return index;
    }

    boolean bfIterate() {
        nextSphereOfAtoms.clear();

        boolean addedAtom = false;
        for (int i = 0, n = sphereOfAtoms.size(); i < n; i++) {
            int iAtom = ((Integer) sphereOfAtoms.get(i)).intValue();
            ArrayList aList = (ArrayList) bondedAtoms.get(iAtom);

            for (int j = 0, m = aList.size(); j < m; j++) {
                int jAtom = ((Integer) aList.get(j)).intValue();
                Atom atom = (Atom) ac.getAtom(jAtom);

                if (!atom.getFlag(Atom.VISITED)) {
                    nextSphereOfAtoms.add(Integer.valueOf(jAtom));
                    atom.setFlag(Atom.VISITED, true);
                    addedAtom = true;
                }
            }
        }
        sphereOfAtoms.clear();
        sphereOfAtoms.addAll(nextSphereOfAtoms);
        return addedAtom;
    }

    public void remove() {
    }

    public Object next() {
        if (debug) {
            System.out.println("#################################");
        }

        return sphereOfAtoms;
    }

    public boolean hasNext() {
        return bfIterate();
    }
}
