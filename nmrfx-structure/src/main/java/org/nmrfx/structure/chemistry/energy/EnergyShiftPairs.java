/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.energy;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.PPMv;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.structure.chemistry.predict.Predictor;
import org.nmrfx.structure.chemistry.predict.RNAAttributes;
import org.nmrfx.structure.fastlinear.FastVector3D;

import java.util.HashMap;
import java.util.List;

/**
 * @author brucejohnson
 */
public class EnergyShiftPairs extends EnergyPairs {

    HashMap<String, List> allowedSourcesMap = new HashMap<>();

    public EnergyShiftPairs(EnergyCoords eCoords) {
        super(eCoords);
    }

    @Override
    public void addPair(int i, int j, int iUnit, int jUnit) {
        if (getRNAClass(eCoords.atoms[i]) >= 0) {
            super.addPair(i, j, iUnit, jUnit);
        }
        if (getRNAClass(eCoords.atoms[j]) >= 0) {
            super.addPair(j, i, jUnit, iUnit);
        }
    }

    @Override
    void resize(int size) {
        if ((iAtoms == null) || (iAtoms.length < size)) {
            super.resize(size);
        }
    }

    final void setupShifts() {
        Atom[] atoms = eCoords.atoms;
        Predictor.checkRNADistData();
        for (int i = 0; i < atoms.length; i++) {
            Atom atom = eCoords.atoms[i];
            int atomClass = RNAAttributes.getAtomSourceIndex(atom);
            eCoords.shiftClass[i] = atomClass;
            eCoords.baseShifts[i] = atomClass >= 0 ? Predictor.getAlphaIntercept(atomClass) : 0.0;
            eCoords.refShifts[i] = 0.0;
            PPMv ppmV = atom.getPPM(0);
            if ((ppmV != null) && ppmV.isValid()) {
                eCoords.shifts[i] = ppmV.getValue();
            }
        }
    }

    public double calcDistShifts(boolean calcDeriv, double rLim, double intraScale, double weight) {
        //setupAtomPairs(rLim);
        eCoords.setCells(this, 1000, rLim, 0.0, true, 0.0, 0.0, false);
        double[] baseShifts = eCoords.baseShifts;
        double[] refShifts = eCoords.refShifts;
        int[] shiftClass = eCoords.shiftClass;
        double[] shifts = eCoords.shifts;
        Atom[] atoms = eCoords.atoms;
        FastVector3D[] vecCoords = eCoords.getVecCoords();

        double r2Lim = rLim * rLim;
        System.arraycopy(baseShifts, 0, refShifts, 0, baseShifts.length);

        for (int i = 0; i < nPairs; i++) {
            int iAtom = iAtoms[i];
            int jAtom = jAtoms[i];

            if ((baseShifts[iAtom] != 0.0) && shiftClass[jAtom] >= 0) {
                int alphaClass = getRNAClass(atoms[iAtom]);
                if (alphaClass >= 0) {
                    FastVector3D iV = vecCoords[iAtom];
                    FastVector3D jV = vecCoords[jAtom];
                    double r2 = iV.disSq(jV);
                    if (r2 <= r2Lim) {
                        double r = Math.sqrt(r2);
                        int alphaIndex = shiftClass[jAtom];
                        double alpha = Predictor.getAlpha(alphaClass, alphaIndex);
                        double shiftContrib = alpha / (r * r2);
                        if (atoms[iAtom].getEntity().getIDNum() == atoms[jAtom].getEntity().getIDNum()) {
                            shiftContrib *= intraScale;

                        }
                        refShifts[iAtom] += shiftContrib;

                        if (calcDeriv) {
                            //  what is needed is actually the derivative/r, therefore
                            // we divide by r
                            // fixme problems if r near 0.0 so we add small adjustment.  Is there a better way???
                            //derivs[i] = -2.0 * weights[i] * weight * dif / (r + RADJ);
                        }
                    }
                }
            }
        }
        double[] angleValues = new double[4];
        for (int i = 0; i < baseShifts.length; i++) {
            Atom atom = atoms[i];
            int alphaClass = getRNAClass(atom);
            if (alphaClass >= 0) {
                double chi = calcChi(atom);
                angleValues[0] = Math.cos(chi);
                angleValues[1] = Math.sin(chi);
                double nu2 = calcNu(atom);
                angleValues[2] = Math.cos(nu2);
                angleValues[3] = Math.sin(nu2);
                double angleDelta = 0.0;
                for (int j = 0; j < angleValues.length; j++) {
                    double alpha = Predictor.getAngleAlpha(alphaClass, j);
                    angleDelta += angleValues[j] * alpha;
                }

                refShifts[i] += angleDelta;
            }

        }

        double sum = 0.0;
        for (int i = 0; i < baseShifts.length; i++) {
            if (baseShifts[i] != 0.0) {
                double mae = Predictor.getMAE(atoms[i]);
                double shiftDelta = (shifts[i] - refShifts[i]) / mae;
                sum += weight * shiftDelta * shiftDelta;
                atoms[i].setRefPPM(refShifts[i]);
            }
        }
        return sum;
    }

    double calcChi(Atom atom) {
        Atom[] atoms = ((Residue) atom.getEntity()).getChiAtoms();
        return eCoords.calcDihedral(atoms[0].eAtom, atoms[1].eAtom, atoms[2].eAtom, atoms[3].eAtom);
    }

    double calcNu(Atom atom) {
        Atom[] atoms = ((Residue) atom.getEntity()).getNu2Atoms();
        return eCoords.calcDihedral(atoms[0].eAtom, atoms[1].eAtom, atoms[2].eAtom, atoms[3].eAtom);
    }

    int getRNAClass(Atom atom) {
        String aName = atom.getName();
        String nucName = atom.getEntity().getName();
        int rnaClass = Predictor.getAlphaIndex(nucName, aName);
        return rnaClass;
    }
}
