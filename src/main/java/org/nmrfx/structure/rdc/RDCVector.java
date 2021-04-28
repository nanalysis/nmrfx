/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.rdc;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.nmrfx.chemistry.Atom;

/**
 *
 * @author brucejohnson
 */
public class RDCVector {

    Atom atom1;
    Atom atom2;
    Vector3D vector;
    double rdc;

    public RDCVector(Atom atom1, Atom atom2, Vector3D vector) {
        this.atom1 = atom1;
        this.atom2 = atom2;
        this.vector = vector;
    }

    public double getRDC() {
        return rdc;
    }

    public void setRDC(double rdc) {
        this.rdc = rdc;
    }

    public Atom getAtom1() {
        return atom1;
    }

    public Atom getAtom12() {
        return atom2;
    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(atom1.getFullName()).append(" ").append(atom1.getEntity().getName()).append(" ");
        sBuilder.append(atom2.getFullName()).append(" ").append(atom2.getEntity().getName()).append(" ");
        sBuilder.append(String.format("%.2f", rdc));
        return sBuilder.toString();
    }

}
