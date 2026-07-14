package org.nmrfx.analyst.netmatch;

import org.nmrfx.chemistry.Atom;

/**
 *
 * @author brucejohnson
 */
class AtomValue extends Value {
    final PeakMatcher peakMatcher;
    private final boolean complete;
    private final boolean empty;
    private final Atom[] atoms;

    AtomValue(int index, Atom[] atoms, PeakMatcher peakMatcher) throws IllegalArgumentException {
        super(index, null, null);
        this.peakMatcher = peakMatcher;
        this.atoms = new Atom[atoms.length];
        int i = 0;
        boolean gotAll = true;
        boolean allEmpty = true;  // will be true for PRO
        for (Atom atom : atoms) {
            if (atom != null) {
                allEmpty = false;
            } else {
                gotAll = false;
            }
            this.atoms[i++] = atom;
        }
        complete = gotAll;
        empty = allEmpty;
    }

    Atom getAtom(int i) {
        return atoms[i];
    }

    boolean getComplete() {
        return complete;
    }

    boolean getEmpty() {
        return empty;
    }

    AtomShifts getAtomShifts(int index) {
        if (index == -1) {
            return null;
        } else if (atoms[index] == null) {
            return null;
        } else {
            return PeakMatcher.atomIndexMap.get(atoms[index]);
        }
    }

    int size() {
        return atoms.length;
    }

    public String toString() {
        StringBuilder sBuild = new StringBuilder();
        for (int i = 0; i < atoms.length; i++) {
            AtomShifts atomShifts = getAtomShifts(i);
            if (atomShifts != null) {
                sBuild.append(atomShifts.getAtomName());
            }
            sBuild.append(' ');
        }
        for (int i = 0; i < atoms.length; i++) {
            AtomShifts atomShifts = getAtomShifts(i);
            if (atomShifts != null) {
                sBuild.append(atomShifts.getPPM());
            }
            sBuild.append(' ');
        }
        return sBuild.toString();
    }
}
