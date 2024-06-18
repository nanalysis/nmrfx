package org.nmrfx.structure.chemistry.energy;

import org.nmrfx.chemistry.*;
import org.nmrfx.structure.chemistry.HydrogenBond;
import org.nmrfx.structure.chemistry.MissingCoordinatesException;
import org.nmrfx.structure.chemistry.Molecule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class PropertyGenerator {
    private static final Logger log = LoggerFactory.getLogger(PropertyGenerator.class);
    Molecule molecule;
    Map<String, HydrogenBond> hBondMap = null;
    Map<String, Double> eShiftMap = null;
    Map<String, Double> contactMap = null;
    Map<String, Double> valueMap = new HashMap<>();

    public double calcDistance(String aname1, String aname2, int structureNum)
            throws MissingCoordinatesException, InvalidMoleculeException {
        MolFilter mf1 = new MolFilter(aname1);
        MolFilter mf2 = new MolFilter(aname2);
        double distance = molecule.calcDistance(mf1, mf2, structureNum);
        return distance;
    }

    public double calcRingShift(String aname1, int structureNum) {
        MolFilter mf1 = new MolFilter(aname1);
        SpatialSet spSet = molecule.findSpatialSet(mf1);
        if (spSet == null) {
            System.out.println("no atom " + aname1);
            return 0.0;
        }
        RingCurrentShift ringShifts = new RingCurrentShift();
        ringShifts.makeRingList(molecule);
        double shift = ringShifts.calcRingContributions(spSet, structureNum, 1.0);
        return shift;
    }

    public double calcHBondAngle(Map<String, HydrogenBond> hBondMap, String hydrogenAtom, int structureNum) {
        Atom atom = molecule.findAtom(hydrogenAtom);
        double value = 0.0;
        if ((hBondMap != null) && (atom != null)) {
            HydrogenBond hBond = hBondMap.get(atom.getFullName());
            if (hBond != null) {
                double angle = hBond.getAngle(structureNum);
                if (angle > Math.PI / 2.0) {
                    value = -Math.cos(angle);
                }
            }
        }
        return value;
    }

    public double calcHBondShift(Map<String, HydrogenBond> hBondMap, String hydrogenAtom, double power, int structureNum) {
        Atom atom = molecule.findAtom(hydrogenAtom);
        double shift = 0.0;
        if ((hBondMap != null) && (atom != null)) {
            HydrogenBond hBond = hBondMap.get(atom.getFullName());
            if (hBond != null) {
                shift = hBond.getShift(structureNum, power);
            }
        }
        return 100.0 * shift;
    }

    public double calcHBondDistance(Map<String, HydrogenBond> hBondMap, String hydrogenAtom, int structureNum) {
        Atom atom = molecule.findAtom(hydrogenAtom);
        double dis = 0.0;
        if ((hBondMap != null) && (atom != null)) {
            HydrogenBond hBond = hBondMap.get(atom.getFullName());
            if (hBond != null) {
                dis = hBond.getHADistance(structureNum);
                dis = 3.5 - dis;
            }
        }
        if (dis < 0.0) {
            dis = 0.0;
        }
        return dis;
    }

    public double calcEInteractionShift(Map<String, Double> eShiftMap, String hydrogenAtom, int structureNum) {
        Atom atom = molecule.findAtom(hydrogenAtom);
        double shift = 0.0;
        if ((atom != null) && (eShiftMap != null)) {
            Double shiftDouble = eShiftMap.get(atom.getFullName());
            if (shiftDouble != null) {
                shift = shiftDouble;
            }
        }
        return shift;
    }

    public double getFRandom(String aname1, double cSum) {
        double cMin = 300.0;
        double cMax = 1300.0;
        double fExp = Math.exp(-2.0 * (cSum - cMin) / (cMax - cMin));
        double f = (1.0 - fExp) / (1.0 + fExp);
        return f;
    }

    public double getContactSum(String aname1) {
        Double contactSum = contactMap.get(aname1);
        if (contactSum == null) {
            contactSum = 0.0;
        }
        return contactSum;
    }

    public double getPPM(String aname) {
        MolFilter molFilter = new MolFilter(aname);
        SpatialSet spatialSet = molecule.findSpatialSet(molFilter);
        if (spatialSet == null) {
            return Double.NaN;
        } else {
            PPMv ppmV = spatialSet.getRefPPM();
            return ppmV != null && ppmV.isValid() ? ppmV.getValue() : Double.NaN;
        }
    }

    public double getPPM(Atom atom) {
        SpatialSet spatialSet = atom.getSpatialSet();
        if (spatialSet == null) {
            return Double.NaN;
        } else {
            PPMv ppmV = spatialSet.getRefPPM();
            return ppmV != null && ppmV.isValid() ? ppmV.getValue() : Double.NaN;
        }
    }

    public double getOccupancy(String name) {
        MolFilter molFilter = new MolFilter(name);
        SpatialSet spatialSet = molecule.findSpatialSet(molFilter);
        double occupancy = 0.0;
        if (spatialSet != null) {
            occupancy = spatialSet.getOccupancy();
        }
        return occupancy;
    }
    public boolean getAtomProperties(Atom atom, int structureNum) {
        String atomName = atom.getName();
        String atomSpec = atom.getFullName();
        String hAtomSpec = "";
        if (atomName.equals("N")) {
            hAtomSpec = atomSpec.substring(0, atomSpec.length() - 1) + "H";
        }
        try {
            double contactSum = getContactSum(atomSpec);
            valueMap.put("contacts", contactSum);
            valueMap.put("fRandom", getFRandom(atomName, contactSum));
            valueMap.put("ring", calcRingShift(atomSpec, structureNum));
            double eInteractionShift = 0.0;
            if (atomName.charAt(0) == 'H') {
                eInteractionShift = calcEInteractionShift(eShiftMap, atomSpec, structureNum);
            } else if (atomName.charAt(0) == 'N') {
                eInteractionShift = calcEInteractionShift(eShiftMap, hAtomSpec, structureNum);
            }
            valueMap.put("eshift", eInteractionShift);
            double hbondShift = 0.0;
            if (atomName.charAt(0) == 'H') {
                hbondShift = calcHBondShift(hBondMap, atomSpec, 3.0, structureNum);
            } else if (atomName.charAt(0) == 'N') {
                hbondShift = calcHBondShift(hBondMap, hAtomSpec, 3.0, structureNum);
            }
            valueMap.put("hshift", hbondShift);
            hbondShift = 0.0;
            if (atomName.charAt(0) == 'H') {
                hbondShift = calcHBondShift(hBondMap, atomSpec, 1.0, structureNum);
            } else if (atomName.charAt(0) == 'N') {
                hbondShift = calcHBondShift(hBondMap, hAtomSpec, 1.0, structureNum);
            }
            valueMap.put("hshift1", hbondShift);
            hbondShift = 0.0;
            if (atomName.charAt(0) == 'H') {
                hbondShift = calcHBondShift(hBondMap, atomSpec, 2.0, structureNum);
            } else if (atomName.charAt(0) == 'N') {
                hbondShift = calcHBondShift(hBondMap, hAtomSpec, 2.0, structureNum);
            }
            valueMap.put("hshift2", hbondShift);
            hbondShift = 0.0;
            if (atomName.charAt(0) == 'H') {
                hbondShift = calcHBondShift(hBondMap, atomSpec, 3.0, structureNum);
            } else if (atomName.charAt(0) == 'N') {
                hbondShift = calcHBondShift(hBondMap, hAtomSpec, 3.0, structureNum);
            }
            valueMap.put("hshift3", hbondShift);

            double hbondAngle = 0.0;
            if (atomName.charAt(0) == 'H') {
                hbondAngle = calcHBondAngle(hBondMap, atomSpec, structureNum);
            } else if (atomName.charAt(0) == 'N') {
                hbondAngle = calcHBondAngle(hBondMap, hAtomSpec, structureNum);
            }
            valueMap.put("hbondang", hbondAngle);
            double hbondDistance = 0.0;
            if (atomName.charAt(0) == 'H') {
                hbondDistance = calcHBondDistance(hBondMap, atomSpec, structureNum);
            } else if (atomName.charAt(0) == 'N') {
                hbondDistance = calcHBondDistance(hBondMap, hAtomSpec, structureNum);
            }
            valueMap.put("hbonddis", hbondDistance);
            valueMap.put("randoff", 1.0);
            double cs, acs;
            cs = getPPM(atomSpec);
            acs = getOccupancy(atomSpec);
            valueMap.put("cs", cs);
            valueMap.put("acscorr", acs);
            if (atom.getPolymerName().equals("polypeptide")) {
                double h3 = 0.0;
                String resName = atom.getResidueName();
                if (atomName.startsWith("H") && !atom.isMethyl() && atomName.endsWith("3")) {
                    h3 = 1.0;
                } else if (resName.equals("LEU") && atomName.startsWith("CD") && atom.isMethylCarbon() && (atomName.charAt(2) == '2')) {
                    h3 = 1.0;
                } else if (resName.equals("LEU") && atomName.startsWith("HD") && atom.isMethyl() && (atomName.charAt(2) == '2')) {
                    h3 = 1.0;
                } else if (resName.equals("VAL") && atomName.startsWith("CG") && atom.isMethylCarbon() && (atomName.charAt(2) == '2')) {
                    h3 = 1.0;
                } else if (resName.equals("VAL") && atomName.startsWith("HG") && atom.isMethyl() && (atomName.charAt(2) == '2')) {
                    h3 = 1.0;
                }
                valueMap.put("h3", h3);
                double methyl = 0.0;
                if (atom.isMethyl()) {
                    methyl = 1.0;
                }
                valueMap.put("methyl", methyl);
            }
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
            return false;
        }
        return true;
    }

}
