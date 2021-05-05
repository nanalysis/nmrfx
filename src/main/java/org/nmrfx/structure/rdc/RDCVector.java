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

    public static boolean CALC_MAX_RDC = true;

    Atom atom1;
    Atom atom2;
    Vector3D vector;
    Double rdcPred;
    Double rdcExp;
    Double error;
    double maxRDC;

    public RDCVector(Atom atom1, Atom atom2, Vector3D vector) {
        this.atom1 = atom1;
        this.atom2 = atom2;
        this.vector = vector;
        String elemName1 = atom1.getElementName();
        String elemName2 = atom2.getElementName();
        maxRDC = AlignmentMatrix.calcMaxRDC(vector, elemName1, elemName2, CALC_MAX_RDC, false);
    }

    public Double getRDC() {
        return rdcPred;
    }

    public Double getExpRDC() {
        return rdcExp;
    }

    public Double getNormRDC() {
        return rdcPred / maxRDC;
    }

    public Double getNormExpRDC() {
        return rdcExp / maxRDC;
    }

    public Double getError() {
        return error;
    }

    public void setRDC(double rdc) {
        this.rdcPred = rdc;
    }

    public void setExpRDC(double rdc) {
        this.rdcExp = rdc;
    }

    public void setError(double err) {
        this.error = err;
    }

    public Atom getAtom1() {
        return atom1;
    }

    public Atom getAtom2() {
        return atom2;
    }

    public double getMaxRDC() {
        return maxRDC;
    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(atom1.getFullName()).append(" ").append(atom1.getEntity().getName()).append(" ");
        sBuilder.append(atom2.getFullName()).append(" ").append(atom2.getEntity().getName()).append(" ");
        sBuilder.append(String.format("%.2f", rdcPred)).append(" ");
        sBuilder.append(String.format("%.2f", rdcExp)).append(" ");
        sBuilder.append(String.format("%.2f", error));
        return sBuilder.toString();
    }

}
