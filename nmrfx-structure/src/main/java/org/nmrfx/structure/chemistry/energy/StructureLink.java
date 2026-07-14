package org.nmrfx.structure.chemistry.energy;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Polymer;

public class StructureLink {
    Atom atom1;
    Atom atom2;
    int nLinks = 6;
    double length = 5.0;
    double valAngle = 110.0;
    double dihAngle = 135.0;

    boolean isRNALink = false;

    boolean isBond = false;

    boolean isCyclic = false;

    Polymer polymer = null;

    String bondOrder = "SINGLE";

    public StructureLink(Atom atom1, Atom atom2) {
        this.atom1 = atom1;
        this.atom2 = atom2;
    }
    public StructureLink(Atom atom1, Atom atom2, boolean rna) {
        this(atom1, atom2);
        isRNALink = rna;
    }
    public StructureLink(Atom atom1, Atom atom2, int nLinks, double length, double valAngle, double dihAngle) {
        this(atom1, atom2);
        this.nLinks = nLinks;
        this.length = length;
        this.valAngle =valAngle;
        this.dihAngle = dihAngle;
    }

    public void rna(boolean state) {
        isRNALink = state;
    }
    public void bond(boolean state) {
        isBond = state;
    }
    public void cyclic(boolean state) {
        isCyclic = state;
    }

    public void polymer(Polymer polymer) {
        this.polymer = polymer;
    }
    public void order(String order) {
        this.bondOrder = order;
    }

    public String toString() {
        return atom1 + " " + atom2 + " n " + nLinks + " len " + length + " val " + valAngle + " dih " + dihAngle +
                " rna " + isRNALink + " bond" + isBond + " cuc " + isCyclic + " order " + bondOrder + " " + polymer;
    }
}
