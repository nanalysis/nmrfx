package org.nmrfx.analyst.netmatch;

/**
 *
 * @author brucejohnson
 */
class AtomValue extends Value {

    final PeakMatcher peakMatcher;
    private final int[] resOffsets;
    private final boolean complete;
    private final boolean empty;
    private final int[] atoms;

    AtomValue(int index, String[] names, PeakMatcher peakMatcher) throws IllegalArgumentException {
        super(index, null, null);
        this.peakMatcher = peakMatcher;
        atoms = new int[names.length];
        resOffsets = new int[names.length];
        int i = 0;
        int maxRes = Integer.MIN_VALUE;
        boolean gotAll = true;
        boolean allEmpty = true;  // will be true for PRO
        for (String name : names) {
            int idNum = -1;
            int resNum = 0;
            if (!name.equals("NA")) {
                int dotIndex = name.indexOf('.');
                if (dotIndex != -1) {
                    resNum = Integer.parseInt(name.substring(0, dotIndex));
                }
                Integer id = PeakMatcher.atomIndexMap.get(name);
                if (id == null) {
                    throw new IllegalArgumentException("No atom " + name + " in map");
                }
                idNum = id;
                if (resNum > maxRes) {
                    maxRes = resNum;
                }
                allEmpty = false;
            } else {
                gotAll = false;
            }
            resOffsets[i] = resNum;
            atoms[i++] = idNum;
        }
        complete = gotAll;
        empty = allEmpty;
        for (i = 0; i < resOffsets.length; i++) {
            if (atoms[i] != -1) {
                resOffsets[i] -= maxRes;
            }
        }
    }

    int getAtom(int i) {
        return atoms[i];
    }

    int[] getOffsets() {
        return resOffsets.clone();
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
        } else if (atoms[index] == -1) {
            return null;
        } else {
            return PeakMatcher.atomShiftsList.get(atoms[index]);
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
