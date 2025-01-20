package org.nmrfx.structure.chemistry.energy;

import org.nmrfx.chemistry.Atom;

public class StructureBond {
    Atom atom1;
    Atom atom2;
    String mode;

    double lower = 1.08 - 0.001;
    double upper = 1.08 + 0.001;

    public StructureBond(Atom atom1, Atom atom2) {
        this.atom1 = atom1;
        this.atom2 = atom2;
    }
    public StructureBond(Atom atom1, Atom atom2, String mode) {
        this.atom1 = atom1;
        this.atom2 = atom2;
        this.mode = mode;
    }
}
