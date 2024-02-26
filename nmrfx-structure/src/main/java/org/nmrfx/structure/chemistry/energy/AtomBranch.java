/*
 * NMRFx Structure : A Program for Calculating Structures
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.nmrfx.structure.chemistry.energy;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.structure.fastlinear.FastDiagonalMatrix;
import org.nmrfx.structure.fastlinear.FastMatrix;
import org.nmrfx.structure.fastlinear.FastVector;
import org.nmrfx.structure.fastlinear.FastVector3D;

import java.util.ArrayList;

/**
 * @author brucejohnson
 */
public class AtomBranch {

    // used in recurrent derivative calculation
    double[] farr = new double[3];
    double[] garr = new double[3];

    // Array of AtomBranches that descend from this one
    AtomBranch[] branches = new AtomBranch[0];
    AtomBranch prev = null;

    // atom at end of rotatable bond
    Atom atom;
    int iAtom;
    int pAtom;
    FastVector3D iVec;
    FastVector3D pVec;

    // inertia, velocity and acceleration values in a reference frame fixed on rotated bond
    FastDiagonalMatrix inertialTensorF = new FastDiagonalMatrix(3);
    double[] vel = new double[2];
    double[] inertia = new double[2];
    double[][] accel = new double[2][3];
    double mass;

    // gradient of energy with respect to rotation of this group
    double force = 0.0;

    // list of all atoms in this AtomBranch
    ArrayList<Atom> atoms = new ArrayList<>();

    FastVector3D angVelF = new FastVector3D(); // angular velocity vector omega
    FastVector3D linVelF = new FastVector3D();// linear velocity vector v

    FastVector aVecF = new FastVector(6);
    FastVector eVecF = new FastVector(6);
    FastVector zVecF = new FastVector(6);

    FastMatrix pMatF = new FastMatrix(6, 6);
    FastMatrix phiMatF = new FastMatrix(6, 6);

    double dkF;
    double epskF;
    FastVector gkVecF = new FastVector(6);
    FastVector alphaVecF = new FastVector(6);

    // angular rotation velocity/acceleration  // theta
    double rotVel;
    double linVelMag;
    RealVector rotAccel = new ArrayRealVector(3);
    RealVector linAccel = new ArrayRealVector(3); // linear acceleration vector
    double linAccelMag;

    // index of this branch in the AtomBranch list (maintained in RotationalDynamics)
    int index = 0;
    int lastGroup = 0;

    public AtomBranch(FastVector3D iVec, FastVector3D pVec) {
        this.iVec = iVec;
        this.pVec = pVec;

    }

    public void setAtom(Atom atom) {
        this.atom = atom;
        iAtom = atom.iAtom;
        pAtom = atom.parent.iAtom;
    }

    public void inertialTensorSetup() {
        mass = 10.0 * Math.sqrt(atoms.size()) * 1.66;  // fixme should this be size -1?
        for (int i = 0; i < 3; i++) {
            // THIS IS NOT DEFINITE -- SEE EQUATION 12 from Guntert et al JMB 1997 273, 283-298
            inertialTensorF.setEntry(i, 0.4 * 25.0 * mass);
        }
    }

    public void dkSetup() {
        dkF = pMatF.vMv(eVecF);
    }

    void check(String name, double a, double b) {
        if (Math.abs(a - b) > 1.0e-8) {
            System.out.println(name + " " + a + " " + b);
        }
    }

    public void epskSetup() {

        FastVector temp6 = new FastVector(6);
        pMatF.operate(aVecF, temp6);
        zVecF.add(temp6, temp6);
        double dot = eVecF.dotProduct(temp6);
        epskF = -dot - force * 0.001;
    }

    public void gkVecSetup() {
        pMatF.operate(eVecF, gkVecF);
        gkVecF.divide(dkF);
    }

    public void alphaVecSetup() {
        alphaVecF.zero();
    }

    public void aVecSetup() {
        aVecF.zero();

        if (null != prev) {
            FastVector temp = new FastVector(3);
            linVelF.subtract(prev.linVelF, temp);
            prev.angVelF.crossProduct(temp, temp);
            aVecF.copyFrom(temp, 3);
        }

        FastVector ea = getUnitVecF();
        angVelF.crossProduct(ea, ea);
        ea.multiply(rotVel);
        aVecF.copyFrom(ea, 0);
    }

    public void eVecSetup() {
        FastVector ea = getUnitVecF();
        eVecF.zero();
        eVecF.copyFrom(ea, 0);
    }

    public void zVecSetup() {

        zVecF.zero();
        FastVector temp3 = new FastVector(3);
        inertialTensorF.operate(angVelF, temp3);
        angVelF.crossProduct(temp3, temp3);
        zVecF.copyFrom(temp3, 0);

    }

    public void pMatSetup() {

        pMatF.set(0.0);
        pMatF.setEntry(0, 0, inertialTensorF.getEntry(0));
        pMatF.setEntry(1, 1, inertialTensorF.getEntry(1));
        pMatF.setEntry(2, 2, inertialTensorF.getEntry(2));
        pMatF.setEntry(3, 3, mass);
        pMatF.setEntry(4, 4, mass);
        pMatF.setEntry(5, 5, mass);
    }

    public void phiMatSetup() {
        FastVector dVec = getDistVecF();
        phiMatF.set(0.0);
        for (int i = 0; i < 6; i++) {
            phiMatF.setEntry(i, i, 1);
        }

        phiMatF.setEntry(2, 4, dVec.getEntry(0));
        phiMatF.setEntry(0, 5, dVec.getEntry(1));
        phiMatF.setEntry(1, 3, dVec.getEntry(2));
        phiMatF.setEntry(1, 5, -dVec.getEntry(0));
        phiMatF.setEntry(2, 3, -dVec.getEntry(1));
        phiMatF.setEntry(0, 4, -dVec.getEntry(2));

    }

    /**
     * @return vector to the origin atom of this group
     */
    public FastVector3D getVectorF() {
        return iVec.copy();
    }

    /**
     * @return vector between the parent of the origin atom and the origin atom
     */
    public FastVector3D getUnitVecF() {
        FastVector3D uVec = new FastVector3D();
        iVec.subtract(pVec, uVec);
        uVec.normalize();
        return uVec;

    }

    /**
     * @return vector between the parent of the origin atom and the origin atom
     */
    public FastVector3D getDistVecF() {
        FastVector3D endPoint = iVec.copy();
        FastVector3D ea;
        if (prev == null) {
            ea = endPoint;
        } else {
            FastVector3D beginPoint = prev.iVec;
            endPoint.subtract(beginPoint, endPoint);
            ea = endPoint;
        }
        return ea;
    }

    void addToF(FastVector vec) {
        farr[0] += vec.getEntry(0);
        farr[1] += vec.getEntry(1);
        farr[2] += vec.getEntry(2);
    }

    void addToF(double[] vec) {
        farr[0] += vec[0];
        farr[1] += vec[1];
        farr[2] += vec[2];
    }

    void addToG(FastVector vec) {
        garr[0] += vec.getEntry(0);
        garr[1] += vec.getEntry(1);
        garr[2] += vec.getEntry(2);
    }

    void addToG(double[] vec) {
        garr[0] += vec[0];
        garr[1] += vec[1];
        garr[2] += vec[2];
    }

    void subtractToG(FastVector vec) {
        garr[0] -= vec.getEntry(0);
        garr[1] -= vec.getEntry(1);
        garr[2] -= vec.getEntry(2);
    }

    void subtractToG(double[] vec) {
        garr[0] -= vec[0];
        garr[1] -= vec[1];
        garr[2] -= vec[2];
    }

    void subtractToF(FastVector vec) {
        farr[0] -= vec.getEntry(0);
        farr[1] -= vec.getEntry(1);
        farr[2] -= vec.getEntry(2);
    }

    void subtractToF(double[] vec) {
        farr[0] -= vec[0];
        farr[1] -= vec[1];
        farr[2] -= vec[2];
    }

    void initF() {
        farr[0] = 0;
        farr[1] = 0;
        farr[2] = 0;
    }

    void initG() {
        garr[0] = 0;
        garr[1] = 0;
        garr[2] = 0;
    }
}
