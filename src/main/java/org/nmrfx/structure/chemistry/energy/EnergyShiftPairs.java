/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.energy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.math3.util.FastMath;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.PPMv;
import org.nmrfx.structure.chemistry.Point3;
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

    public void addPair(int i, int j, int iUnit, int jUnit) {
        resize(nPairs + 1);
        int iPair = nPairs;
        iAtoms[iPair] = i;
        jAtoms[iPair] = j;
        iUnits[iPair] = iUnit;
        jUnits[iPair] = jUnit;

        weights[iPair] = 1.0;
        derivs[iPair] = 0.0;
        nPairs = iPair + 1;

    }

    void resize(int size) {
        if ((iAtoms == null) || (iAtoms.length < size)) {
            super.resize(size);
        }
    }

    public double calcDistShifts(boolean calcDeriv, double rLim, double weight) {
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
            if ((baseShifts[iAtom] != 0.0) && shiftClass[jAtom] >= 0) {
                int alphaClass = getRNAClass(atoms[iAtom].getName());
                if (alphaClass >= 0) {
                    FastVector3D iV = vecCoords[iAtom];
                    FastVector3D jV = vecCoords[jAtom];
                    double r2 = iV.disSq(jV);
                    if (r2 <= r2Lim) {
                        double r = FastMath.sqrt(r2);
                        int alphaIndex = shiftClass[jAtom];
                        double alpha = Predictor.getAlpha(alphaClass, alphaIndex);
                        double shiftContrib = alpha / (r * r2);
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

    int getRNAClass(String aName) {
        int rnaClass = -1;
        if (aName.charAt(aName.length() - 1) == '\'') {
            if (aName.charAt(0) == 'H') {
                rnaClass = 3;
            } else if (aName.charAt(0) == 'C') {
                rnaClass = 1;

            }
        } else {
            if (aName.charAt(0) == 'H') {
                rnaClass = 2;
            } else if (aName.charAt(0) == 'C') {
                rnaClass = 0;

            }
        }
        return rnaClass;
    }

    void setupShifts(int i) {
        Atom atom = eCoords.atoms[i];
        String aName = atom.getName();
        int atomClass = RNAAttributes.getAtomSourceIndex(aName);
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
        Atom targetAtom = atoms[iAtom];

        List origAtomSources = RNAAttributes.getAtomSources();
        int numAtomSources = origAtomSources.size();

        List atomSources = new ArrayList(origAtomSources);
        String targetResName = targetAtom.getEntity().getName();
        String targetAtomName = targetAtom.getName();
        String targetKey = targetResName + targetAtomName;
        if (targetAtomName.contains("'")) {
            targetKey = targetAtomName;
        }
        if (!allowedSourcesMap.containsKey(targetKey)) {
            allowedSourcesMap.put(targetKey, Molecule.getAllowedSources(iStruct, origAtomSources, targetAtom));
//                System.out.println(allowedSourcesMap.keySet());
        }
        if (allowedSourcesMap.containsKey(targetKey)) {
            atomSources = allowedSourcesMap.get(targetKey);//getAllowedSources(iStruct, origAtomSources, targetAtom);
//                System.out.println(targetKey + ": " + atomSources);
        }

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
                    && ((sourceResID != targetResID) || atomSources.contains(key))) {
                Point3 sourcePt = sourceAtom.getPoint(iStruct);
                if ((targetPt != null) && (sourcePt != null)) {
                    double r = Atom.calcDistance(targetPt, sourcePt);
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
