/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.energy;

import java.util.HashMap;
import java.util.List;
import org.apache.commons.math3.util.FastMath;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.PPMv;
import org.nmrfx.structure.chemistry.Point3;
import org.nmrfx.structure.chemistry.Residue;
import org.nmrfx.structure.chemistry.predict.Predictor;
import org.nmrfx.structure.chemistry.predict.RNAAttributes;
import org.nmrfx.structure.fastlinear.FastVector3D;

/**
 *
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
    }

    void resize(int size) {
        if ((iAtoms == null) || (iAtoms.length < size)) {
            super.resize(size);
        }
    }

    public double calcDistShifts(boolean calcDeriv, double rLim, double intraScale, double weight) {
        setupAtomPairs(rLim);
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
//            if (atoms[iAtom].getShortName().equals("5.C1'")) {
//                System.out.println(i + " " + iAtom + " " + jAtom + " " + atoms[iAtom].getShortName()+ " " + atoms[jAtom].getShortName() + " " + baseShifts[iAtom]+ " " + shiftClass[jAtom]);
//            }
            if ((baseShifts[iAtom] != 0.0) && shiftClass[jAtom] >= 0) {
                int alphaClass = getRNAClass(atoms[iAtom]);
                if (alphaClass >= 0) {
                    FastVector3D iV = vecCoords[iAtom];
                    FastVector3D jV = vecCoords[jAtom];
                    double r2 = iV.disSq(jV);
                    if (r2 <= r2Lim) {
                        double r = FastMath.sqrt(r2);
                        int alphaIndex = shiftClass[jAtom];
                        double alpha = Predictor.getAlpha(alphaClass, alphaIndex);
                        double shiftContrib = alpha / (r * r2);
                        if (atoms[iAtom].getEntity().getIDNum() == atoms[jAtom].getEntity().getIDNum()) {
                            shiftContrib *= intraScale;

                        }
                        refShifts[iAtom] += shiftContrib;
//                        if (atoms[iAtom].getShortName().equals("5.C1'")) {
//                            System.out.println(atoms[jAtom].getShortName() + " " + alphaIndex + " " + alphaClass + " " + alpha + " " + baseShifts[iAtom] + " " + shiftContrib + " " + r + " " + refShifts[iAtom]);
//                        }
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
//                    if (atoms[i].getShortName().equals("5.C1'")) {
//                        System.out.println(atoms[i].getShortName() + " " + alphaClass + " " +alpha + " " + chi + " " + nu2 + " " +  angleDelta + " " + (refShifts[i]+angleDelta));
//                    }
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
                //   System.out.println(i + " " + atoms[i].getShortName() + " " + refShifts[i]);
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

    void setupShifts(int i) {
        Atom atom = eCoords.atoms[i];
        String aName = atom.getName();
        int atomClass = RNAAttributes.getAtomSourceIndex(atom);
        eCoords.shiftClass[i] = atomClass;
        Double baseValue = Predictor.getDistBaseShift(atom);
        eCoords.baseShifts[i] = baseValue == null ? 0.0 : baseValue;
        eCoords.refShifts[i] = 0.0;
        PPMv ppmV = atom.getPPM(0);
        if ((ppmV != null) && ppmV.isValid()) {
            eCoords.shifts[i] = ppmV.getValue();
        }
    }

    public void setupAtomPairs(double rLim) {
        nPairs = 0;
        Atom[] atoms = eCoords.atoms;
        for (int i = 0; i < atoms.length; i++) {
            setupShifts(i);
        }
        for (int i = 0; i < atoms.length; i++) {
            if (eCoords.baseShifts[i] != 0.0) {
                addAtomPairs(0, rLim, i);
            }
        }
    }

    public void addAtomPairs(final int iStruct, double distLim, int iAtom) {
        Atom[] atoms = eCoords.atoms;
        FastVector3D[] vecCoords = eCoords.getVecCoords();
        Atom targetAtom = atoms[iAtom];
        List origAtomSources = RNAAttributes.getAtomSources();

        Point3 targetPt = targetAtom.getPoint(iStruct);
        for (int jAtom = 0; jAtom < atoms.length; jAtom++) {
            Atom sourceAtom = atoms[jAtom];
            String resName = sourceAtom.getEntity().getName();
            String atomName = sourceAtom.getName();
            String key = resName + atomName;
            if (atomName.contains("'") || atomName.contains("P")) {
                key = atomName;
            }
            int sourceResID = sourceAtom.getEntity().getIDNum();
            int targetResID = targetAtom.getEntity().getIDNum();
            if ((targetAtom != sourceAtom) && (sourceAtom.getAtomicNumber() != 1)
                    && ((sourceResID != targetResID) || !Predictor.isRNAPairFixed(targetAtom, sourceAtom))) {
                Point3 sourcePt = sourceAtom.getPoint(iStruct);
                if ((targetPt != null) && (sourcePt != null)) {
                    FastVector3D iV = vecCoords[iAtom];
                    FastVector3D jV = vecCoords[jAtom];
                    double r2 = iV.disSq(jV);

                    double r = Math.sqrt(r2);
                    if (r < distLim && origAtomSources.contains(key)) {
                        if (r != 0.0) {
                            int iUnit;
                            int jUnit;
                            if (targetAtom.rotGroup != null) {
                                iUnit = targetAtom.rotGroup.rotUnit;
                            } else {
                                iUnit = -1;
                            }
                            if (sourceAtom.rotGroup != null) {
                                jUnit = sourceAtom.rotGroup.rotUnit;
                            } else {
                                jUnit = -1;
                            }
                            addPair(iAtom, jAtom, iUnit, jUnit);
                        }
                    }
                }

            }
        }
    }
}
