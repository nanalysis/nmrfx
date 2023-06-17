package org.nmrfx.structure.chemistry.energy;

import org.nmrfx.chemistry.*;
import org.nmrfx.structure.chemistry.HydrogenBond;
import org.nmrfx.structure.chemistry.MissingCoordinatesException;
import org.nmrfx.structure.chemistry.Molecule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

public class PropertyGenerator {

    private static final Logger log = LoggerFactory.getLogger(PropertyGenerator.class);
    private static HashMap<String, HashMap<String, Double>> properties = null;
    private static HashMap<String, double[]> residueFactors = null;
    private static HashMap<String, Double> offsetTable;
    private static boolean verbose;
    private static Locale stdLocale = new Locale("en", "US");

    static NumberFormat nFormat = NumberFormat.getInstance(stdLocale);
    static DecimalFormat formatter = (DecimalFormat) nFormat;
    Map<String, HydrogenBond> hBondMap = null;
    Map<String, Double> eShiftMap = null;
    Map<String, Double> contactMap = null;
    Map<String, Double> valueMap = new HashMap<>();
    private Molecule molecule;
    String[] residueNames = {"ALA", "ARG", "ASN", "ASP", "CYS", "GLU", "GLN",
            "GLY", "HIS", "ILE", "LEU", "LYS", "MET", "PHE", "PRO", "SER", "THR",
            "TRP", "TYR", "VAL"};

    static {
        formatter.setMinimumFractionDigits(4);
        formatter.setMaximumFractionDigits(4);
        formatter.setMinimumIntegerDigits(1);
        formatter.setGroupingUsed(false);
    }

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

    public HydrogenBond calcHBond(String hydrogenAtom, int structureNum) throws InvalidMoleculeException {
        int[] structures = {structureNum};
        MolFilter hydrogenFilter = new MolFilter(hydrogenAtom);
        MolFilter acceptorFilter = new MolFilter("*.O,O*");
        ArrayList<HydrogenBond> hBonds = molecule.hydrogenBonds(structures, hydrogenFilter, acceptorFilter);
        double shift = 0.0;
        HydrogenBond hBest = null;
        for (HydrogenBond hBond : hBonds) {
            double testShift = hBond.getShift(structureNum);
            if (testShift > shift) {
                hBest = hBond;
                shift = testShift;
            }
        }
        return hBest;
    }

    public double calcHBondAngle(String hydrogenAtom, int structureNum) throws InvalidMoleculeException {
        HydrogenBond hBond = calcHBond(hydrogenAtom, structureNum);
        double angle = Math.PI / 2.0;
        if (hBond != null) {
            angle = hBond.getAngle(structureNum);
        }
        return angle;
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

    public double calcEInteractionShift(Map<String, Double> eShiftMap, Atom atom, int structureNum) {
        double shift = 0.0;
        Double shiftDouble = eShiftMap.get(atom.getFullName());
        if (shiftDouble != null) {
            shift = shiftDouble;
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

    public double calculatePhi(String polyName, int res, int structureNum) {
        MolFilter mf1 = new MolFilter(polyName + ":" + Integer.toString(res - 1) + ".C");
        MolFilter mf2 = new MolFilter(polyName + ":" + Integer.toString(res) + ".N");
        MolFilter mf3 = new MolFilter(polyName + ":" + Integer.toString(res) + ".CA");
        MolFilter mf4 = new MolFilter(polyName + ":" + Integer.toString(res) + ".C");
        return molecule.calcDihedral(mf1, mf2, mf3, mf4, structureNum);
    }

    public double calculatePhi(String polyName, Residue residue, int structureNum) throws MissingCoordinatesException {
        Atom[] atoms = new Atom[4];
        atoms[0] = residue.previous.getAtom("C");
        atoms[1] = residue.getAtom("N");
        atoms[2] = residue.getAtom("CA");
        atoms[3] = residue.next.getAtom("C");
        for (int i = 0; i < atoms.length; i++) {
            if (atoms[i] == null) {
                throw new IllegalArgumentException("Calc phi for residue: " + residue.getNumber() + " atom not found " + i);
            }
        }

        return molecule.calcDihedral(atoms, structureNum);
    }

    public double calculateOmega(String polyName, Residue residue, int structureNum) throws MissingCoordinatesException {
        Atom[] atoms = new Atom[4];
        atoms[0] = residue.previous.getAtom("O");
        atoms[1] = residue.previous.getAtom("C");
        atoms[2] = residue.getAtom("N");
        atoms[3] = residue.getAtom("CA");
        return molecule.calcDihedral(atoms, structureNum);
    }

    public double calculateOmega(String polyName, int res, int structureNum) {
        MolFilter mf1 = new MolFilter(polyName + ":" + Integer.toString(res - 1) + ".O");
        MolFilter mf2 = new MolFilter(polyName + ":" + Integer.toString(res - 1) + ".C");
        MolFilter mf3 = new MolFilter(polyName + ":" + Integer.toString(res) + ".N");
        MolFilter mf4 = new MolFilter(polyName + ":" + Integer.toString(res) + ".CA");
        return molecule.calcDihedral(mf1, mf2, mf3, mf4, structureNum) + Math.PI;
    }

    public double calculatePsi(String polyName, Residue residue, int structureNum) throws MissingCoordinatesException {
        Atom[] atoms = new Atom[4];
        atoms[0] = residue.getAtom("N");
        atoms[1] = residue.getAtom("CA");
        atoms[2] = residue.getAtom("C");
        atoms[3] = residue.next.getAtom("N");
        return molecule.calcDihedral(atoms, structureNum);
    }

    public double calculatePsi(String polyName, int res, int structureNum) {
        MolFilter mf1 = new MolFilter(polyName + ":" + Integer.toString(res) + ".N");
        MolFilter mf2 = new MolFilter(polyName + ":" + Integer.toString(res) + ".CA");
        MolFilter mf3 = new MolFilter(polyName + ":" + Integer.toString(res) + ".C");
        MolFilter mf4 = new MolFilter(polyName + ":" + Integer.toString(res + 1) + ".n");
        return molecule.calcDihedral(mf1, mf2, mf3, mf4, structureNum);
    }

    public double[] calculatePsi(String polyName, int first, int last, int structureNum) {
        double[] ret = new double[last - first + 1];
        for (int i = 0; i < ret.length; ++i) {
            ret[i] = calculatePsi(polyName, first + i, structureNum);
        }
        return ret;
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

    public int getFirstRes() {
        Entity entity = molecule.getEntity(molecule.getName());
        Polymer polymer = (Polymer) entity;
        Residue residue = polymer.getFirstResidue();
        return Integer.parseInt(residue.getNumber());
    }

    public int getLastRes() {
        Entity entity = molecule.getEntity(molecule.getName());
        Polymer polymer = (Polymer) entity;
        Residue residue = polymer.getLastResidue();
        return Integer.parseInt(residue.getNumber());
    }

    public int getFirstRes(Entity entity) {
        Polymer polymer = (Polymer) entity;
        Residue residue = polymer.getFirstResidue();
        return Integer.parseInt(residue.getNumber());
    }

    public int getLastRes(Entity entity) {
        Polymer polymer = (Polymer) entity;
        Residue residue = polymer.getLastResidue();
        return Integer.parseInt(residue.getNumber());
    }

    public double calcDihedral(String s1, String s2, String s3, String s4) {
        MolFilter mf1 = new MolFilter(s1);
        MolFilter mf2 = new MolFilter(s2);
        MolFilter mf3 = new MolFilter(s3);
        MolFilter mf4 = new MolFilter(s4);
        return molecule.calcDihedral(mf1, mf2, mf3, mf4);
    }

    public boolean doesAtomExist(String polyName, int res, String atomName) {
        Atom atom = molecule.findAtom(Integer.toString(res) + ".N");
        if (atom == null) {
            System.out.println(res + ".N doesn't exist");
            return false;
        }
        String resName = atom.getEntity().name;
        if (resName.equals("GLY")) {
            if (atomName.equals("CB")) {
                return false;
            }
        }
        return true;
    }

    public double calculateChi(String polyName, int res) {
        String resName = molecule.findAtom(Integer.toString(res) + ".N").getEntity().name;
        if (resName.equals("ALA") || resName.equals("GLY")) {
            return Double.NaN;
        } else {
            String rs = Integer.toString(res);
            MolFilter mf1 = new MolFilter(rs + ".N");
            MolFilter mf2 = new MolFilter(rs + ".CA");
            MolFilter mf3 = new MolFilter(rs + ".CB");
            /*
             *The fourth atom depends on resName 
             *We use the following mapping. The first entry is the atom, and the rest are the amino acids that use that atom.
             *CG ASP GLU PHE HIS LYS LEU MET ASN GLN ARG TRP TYR  PRO
             CG1 ILE VAL
             OG1 THR
             OG SER
             SG CYS
             * Since CG has a huge list of atoms we will use that as the fallback: any residues not one  of the other three will use CG
             * 
             */
            String mf4a = null;
            if (resName.equals("THR")) {
                mf4a = ".OG1";
            } else if (resName.equals("SER")) {
                mf4a = ".OG";
            } else if (resName.equals("CYS")) {
                mf4a = ".SG";
            } else if (resName.equals("ILE") || resName.equals("VAL")) {
                mf4a = ".CG1";
            } else {
                mf4a = ".CG";
            }
            MolFilter mf4 = new MolFilter(rs + mf4a);
            return molecule.calcDihedral(mf1, mf2, mf3, mf4);
        }
    }

    public double calculateChi(String polyName, Residue residue, int structureNum) throws MissingCoordinatesException {
        String resName = residue.getName();
        if (resName.equals("ALA") || resName.equals("GLY")) {
            return Double.NaN;
        } else {
            Atom[] atoms = new Atom[4];
            atoms[0] = residue.getAtom("N");
            atoms[1] = residue.getAtom("CA");
            atoms[2] = residue.getAtom("CB");
            /*
             *The fourth atom depends on resName 
             *We use the following mapping. The first entry is the atom, and the rest are the amino acids that use that atom.
             *CG ASP GLU PHE HIS LYS LEU MET ASN GLN ARG TRP TYR  PRO
             CG1 ILE VAL
             OG1 THR
             OG SER
             SG CYS
             * Since CG has a huge list of atoms we will use that as the fallback: any residues not one  of the other three will use CG
             * 
             */
            if (resName.equals("THR")) {
                atoms[3] = residue.getAtom("OG1");
            } else if (resName.equals("SER")) {
                atoms[3] = residue.getAtom("OG");
            } else if (resName.equals("CYS")) {
                atoms[3] = residue.getAtom("SG");
            } else if (resName.equals("ILE") || resName.equals("VAL")) {
                atoms[3] = residue.getAtom("CG1");
            } else {
                atoms[3] = residue.getAtom("CG");
            }
            int iAtom = 0;
            for (Atom atom : atoms) {
                if (atom == null) {
                    System.out.println(resName + residue.getNumber() + " " + iAtom);
                    return Double.NaN;
                }
                iAtom++;
            }
            if (atoms[3] == null) {
            }
            return Molecule.calcDihedral(atoms, structureNum);
        }
    }

    public double calculateChi2(Residue residue, int structureNum) throws MissingCoordinatesException {
        String resName = residue.getName();
        Atom[] atoms = new Atom[4];
        if (resName.equals("PHE")
                || resName.equals("TRP")
                || resName.equals("TYR")) {
            atoms[0] = residue.getAtom("CA");
            atoms[1] = residue.getAtom("CB");
            atoms[2] = residue.getAtom("CG");
            atoms[3] = residue.getAtom("CD1");
        } else if (resName.equals("HIS")) {
            atoms[0] = residue.getAtom("CA");
            atoms[1] = residue.getAtom("CB");
            atoms[2] = residue.getAtom("CG");
            atoms[3] = residue.getAtom("CD2");
        } else if (resName.equals("ASN")
                || resName.equals("ASP")) {
            atoms[0] = residue.getAtom("CA");
            atoms[1] = residue.getAtom("CB");
            atoms[2] = residue.getAtom("CG");
            atoms[3] = residue.getAtom("OD1");
        } else if (resName.equals("GLN")
                || resName.equals("GLU")
                || resName.equals("LYS")
                || resName.equals("ARG")) {
            atoms[0] = residue.getAtom("CA");
            atoms[1] = residue.getAtom("CB");
            atoms[2] = residue.getAtom("CG");
            atoms[3] = residue.getAtom("CD");
        } else if (resName.equals("MET")) {
            atoms[0] = residue.getAtom("CA");
            atoms[1] = residue.getAtom("CB");
            atoms[2] = residue.getAtom("CG");
            atoms[3] = residue.getAtom("SD");
        } else if (resName.equals("ILE")) {
            atoms[0] = residue.getAtom("CA");
            atoms[1] = residue.getAtom("CB");
            atoms[2] = residue.getAtom("CG1");
            atoms[3] = residue.getAtom("CD1");

        } else if (resName.equals("LEU")) {
            atoms[0] = residue.getAtom("CA");
            atoms[1] = residue.getAtom("CB");
            atoms[2] = residue.getAtom("CG");
            atoms[3] = residue.getAtom("CD1");
        } else {
            return Double.NaN;
        }
        return Molecule.calcDihedral(atoms, structureNum);
    }

    public void init(Molecule molecule, int iStructure) throws InvalidMoleculeException, IOException {
        if (properties == null) {
            properties = loadPropertyFile();
        }
        HashMap<String, TreeMap<Integer, LinkedHashMap<String, String>>> data = new HashMap<String, TreeMap<Integer, LinkedHashMap<String, String>>>();
        this.molecule = molecule;
        contactMap = molecule.calcContactSum(iStructure, true);
        hBondMap = new HashMap<>();
        eShiftMap = new HashMap<>();
        String[] hbondAtomNames = {"H", "HA"};
        for (String atomName : hbondAtomNames) {
            MolFilter hydrogenFilter = new MolFilter("*." + atomName);
            MolFilter acceptorFilter = new MolFilter("*.O*");
            if (atomName.startsWith("HA")) {
                hydrogenFilter = new MolFilter("*." + atomName + "*");
            }
            Map<String, HydrogenBond> hBondMapForAtom = molecule.hydrogenBondMap(hydrogenFilter, acceptorFilter, iStructure);
            hBondMap.putAll(hBondMapForAtom);
            MolFilter sourceFilter = new MolFilter("*.O*,N,H");  // fixme ?  why is H here (for NH?)
            Map<String, Double> eShiftMapForAtom = molecule.electroStaticShiftMap(hydrogenFilter, sourceFilter, iStructure);
            eShiftMap.putAll(eShiftMapForAtom);
        }
    }

    public void clearMap() {
        valueMap.clear();
    }

    public Map<String, Double> getValues() {
        return valueMap;
    }

    public boolean getResidueProperties(Polymer polymer, Residue residue,
                                        int structureNum) {
        valueMap.clear();
        Residue prevResidue = residue.previous;
        Residue nextResidue = residue.next;
        int resNum = residue.getResNum();
        for (String residueName : residueNames) {
            valueMap.put(residueName, 0.0);
        }
        valueMap.put(residue.getName(), 1.0);
        valueMap.put("N1", 0.0);
        valueMap.put("N2", 0.0);
        valueMap.put("C1", 0.0);
        valueMap.put("C2", 0.0);

        try {
            String polyName = polymer.getName();
            valueMap.put("phiP", null);
            valueMap.put("chiP", null);
            valueMap.put("psiP", null);
            valueMap.put("omega", null);
            valueMap.put("phiC", null);
            valueMap.put("psiC", null);
            valueMap.put("phiS", null);
            valueMap.put("chiS", null);
            valueMap.put("psiS", null);
            String[] suffixes = {"_P", "_S"};
            for (String suffix : suffixes) {
                valueMap.put("HPHB" + suffix, 0.0);
                valueMap.put("BULK" + suffix, 0.0);
                valueMap.put("CHRG" + suffix, 0.0);
                valueMap.put("PRO" + suffix, 0.0);
                valueMap.put("ARO" + suffix, 0.0);
                valueMap.put("DIS" + suffix, 0.0);
            }

            if (prevResidue != null) {
                if (prevResidue.previous != null) {
                    valueMap.put("phiP", calculatePhi(polyName, prevResidue, structureNum));
                } else {
                    valueMap.put("N2", 1.0);
                }
                valueMap.put("chiP", calculateChi(polyName, prevResidue, structureNum));
                valueMap.put("psiP", calculatePsi(polyName, prevResidue, structureNum));
                valueMap.put("omega", calculateOmega(polyName, residue, structureNum));
                if (nextResidue != null) {
                    valueMap.put("phiC", calculatePhi(polyName, residue, structureNum));
                }
                if (!getResProps(prevResidue, "_P")) {
                    return false;
                }
            } else {
                valueMap.put("N1", 1.0);
            }
            valueMap.put("chiC", calculateChi(polyName, residue, structureNum));
            valueMap.put("chi2C", calculateChi2(residue, structureNum));
            if (!getResProps(residue, "")) {
                return false;
            }
            if (nextResidue != null) {
                valueMap.put("psiC", calculatePsi(polyName, residue, structureNum));
                valueMap.put("chiS", calculateChi(polyName, nextResidue, structureNum));
                if (nextResidue.next != null) {
                    valueMap.put("phiS", calculatePhi(polyName, nextResidue, structureNum));
                    valueMap.put("psiS", calculatePsi(polyName, nextResidue, structureNum));

                    if (!getResProps(nextResidue, "_S")) {
                        return false;
                    }
                } else {
                    valueMap.put("C2", 1.0);
                }
            } else {
                valueMap.put("C1", 1.0);
            }
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
            return false;
        }
        return true;
    }

    public boolean getResProps(Residue residue, String suffix) {
        try {
            valueMap.put("HPHB" + suffix, getProperty("HYDROPHOBICITY", residue));
            valueMap.put("BULK" + suffix, getProperty("BULK", residue));
            valueMap.put("CHRG" + suffix, getProperty("CHARGE", residue));
            valueMap.put("PRO" + suffix, getProperty("PROLINE", residue));
            valueMap.put("ARO" + suffix, getProperty("AROMATIC", residue));
            valueMap.put("DIS" + suffix, getProperty("DISULFIDE", residue));
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
            return false;
        }
        return true;

    }

    public boolean getAtomProperties(Polymer polymer, int res, String resName,
                                     String atomName, int structureNum) {
        atomName = atomName.toUpperCase();
        String atomSpec = polymer.getName() + ":" + Integer.toString(res) + "." + atomName;
        Atom atom = molecule.findAtom(atomSpec);
        return getAtomProperties(atom, structureNum);

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
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
            return false;
        }
        return true;
    }

    public void analyzeResidue(Polymer polymer, String polyName, int res, String csString, int structureNum) {

        Atom atom = molecule.findAtom(polyName + ":" + Integer.toString(res) + ".N");
        if (atom == null) {
            return;
        }
        Atom atomP = molecule.findAtom(polyName + ":" + Integer.toString(res - 1) + ".N");
        if (atomP == null) {
            return;
        }
        Atom atomS = molecule.findAtom(polyName + ":" + Integer.toString(res + 1) + ".N");
        if (atomS == null) {
            return;
        }
        String resName = atom.getEntity().name;
        String resNameP = atomP.getEntity().name;
        String resNameS = atomS.getEntity().name;
        String[] atomNames = new String[1];
        atomNames[0] = csString;
        if (resName.equals("GLY") && csString.equals(".CB")) {
            return;
        }
        if (resName.equals("GLY") && csString.equals(".HA")) {
            atomNames = new String[2];
            atomNames[0] = ".HA2";
            atomNames[1] = ".HA3";
        }
        if (resName.equals("PRO") && csString.equals(".H")) {
            return;
        }
        for (String atomName : atomNames) {
            String outResName = resName;
            if (verbose) {
                System.out.printf("%d:%s\n", res, resName);
            }
            LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
            double omega = calculateOmega(polyName, res, structureNum);
            Double contactSum = null;
            Double fRandom = null;
        }
    }

    public static void writeData(String name, HashMap<String, TreeMap<Integer, LinkedHashMap<String, String>>> data) {
        try (BufferedWriter b = new BufferedWriter(new FileWriter(name))) {
            b.write("@relation " + new Date().toString().replace(" ", "_"));
            b.newLine();
            /*
             * Because the arff format apparently can't specify the class attribute, it defaults to the last attribute
             * so chemical_shift must come last in the list.
             */

            /*
             * Warning: cludge ahead.
             * Because we have to define attributes now but don't see them until the third level of maps,
             * We have a flag to indicate whether or not we've defined the attributes. This should only happen once otherwise we're screwed.
             * This is ridiculous but better than the alternative which is converting each levl keyset to an array, getting 0, and getting that particular key.
             */
            boolean haveDefinedAttributes = false;
            for (String protein : data.keySet()) {
                for (Integer residue : data.get(protein).keySet()) {
                    if (!haveDefinedAttributes) {
                        for (String a : data.get(protein).get(residue).keySet()) {
                            if (a.equals("chemicalshift")) {
                                continue;
                            }
                            b.write("@attribute ");
                            a = a.replace(" ", "");
                            a = a.replace(",", "_");
                            b.write(a);
                            b.write(" real");
                            b.newLine();
                        }
                        b.write("@attribute chemicalshift real");
                        b.newLine();
                        b.write("@data");
                        b.newLine();
                        haveDefinedAttributes = true;
                    }
                    // the attributes are all in the same order because we're using tree maps
                    // so we can just dump the value sets

                    LinkedHashMap<String, String> map = data.get(protein).get(residue);
                    String cs = map.get("chemicalshift");
                    map.remove("chemicalshift");
                    String v = map.values().toString();
                    v = v.substring(1, v.length() - 1); // trim brackets
                    b.write(v);
                    b.write(",");
                    b.write(cs);
                    b.newLine();
                }
            }
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }
    }

    public static double getRandomShift(String aName, String resName) {
        Double value = offsetTable.get(resName + "_" + aName);
        if (value == null) {
            value = 0.0;
        }
        return value;
    }

    public static double getOffset(String aName, String resName, int offset) {
        String[] offsetChars = {"A", "B", "", "C", "D"};
        Double value = offsetTable.get(resName + "_" + aName + "_" + offsetChars[offset + 2]);
        if (value == null) {
            value = 0.0;
        }
        return value;
    }

    public double getCorrectedRandomShift(String polyName, int firstRes, int iRes, int lastRes, String aName) {
        String resName = molecule.findAtom(polyName + ":" + Integer.toString(iRes) + ".N").getEntity().name;
        double corr = getRandomShift(aName, resName);
        int[] offsets = {-2, -1, 1, 2};
        for (int offset : offsets) {
            int kRes = iRes + offset;
            if ((kRes >= firstRes) && (kRes <= lastRes)) {
                double offsetValue = 0.0;
                try {
                    resName = molecule.findAtom(polyName + ":" + Integer.toString(kRes) + ".N").getEntity().name;
                    offsetValue = getOffset(aName, resName, -offset);
                } catch (Exception e) {
                    log.warn(e.getMessage(), e);
                }
                corr += offsetValue;
            }
        }
        return corr;
    }

    public double[] calculatePhi(String polyName, int first, int last, int structureNum) {
        double[] ret = new double[last - first + 1];
        for (int i = 0; i < ret.length; ++i) {
            ret[i] = calculatePhi(polyName, first + i, structureNum);
        }
        return ret;
    }

    private static HashMap<String, HashMap<String, Double>> loadPropertyFile() throws FileNotFoundException, IOException {
        InputStream iStream = PropertyGenerator.class.getResourceAsStream("/data/predict/protein/resprops.txt");
        HashMap<String, HashMap<String, Double>> properties = new HashMap<String, HashMap<String, Double>>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(iStream))) {
            String[] keys = null;
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (keys == null) {
                    keys = line.split("\t");
                } else {
                    HashMap<String, Double> map = new HashMap<String, Double>();
                    String[] values = line.split("\t");
                    for (int i = 1; i < keys.length; ++i) {
                        map.put(keys[i].trim(), Double.parseDouble(values[i].trim()));
                    }
                    properties.put(values[0], map);
                }
            }
        }
        return properties;
    }

    private static HashMap<String, double[]> loadResidueFactors() throws FileNotFoundException, IOException {
        InputStream iStream = PropertyGenerator.class.getResourceAsStream("/data/predict/protein/resfactors.txt");
        HashMap<String, double[]> map = new HashMap<>();

        try (var reader = new BufferedReader(new InputStreamReader(iStream))) {
            while (true) {
                var line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (line.isBlank()) {
                    continue;
                }
                String[] strValues = line.split("\t");
                double[] values = new double[strValues.length - 1];
                for (int i = 0; i < values.length; i++) {
                    values[i] = Double.parseDouble(strValues[i + 1].trim());
                }
                map.put(strValues[0].trim(), values);
            }
        }
        return map;
    }

    public double getProperty(String property, Residue residue) throws IllegalStateException {
        if (properties == null) {
            throw new IllegalStateException("Attempting to use getProperty without a property table.");
        }
        if (residue == null) {
            return 0.0;
        }
        String resName = residue.getName();
        if (property.equals(resName.toUpperCase())) {
            return 1.0;
        } else if (property.equals("DISULFIDE")) {
            double value = 0.0;
            if (resName.equals("CYS")) {
                Atom sgAtom = residue.getAtom("SG");
                if (sgAtom != null) {
                    if (molecule.isDisulfide(sgAtom, 0)) {
                        value = 1.0;
                    }
                    double cs = getPPM(residue.getAtom("CB"));
                    if (!Double.isNaN(cs) && (cs > -90.0)) {
                        if (cs > 38.0) {
                            value = 1.0;
                        } else if (cs < 30.0) {
                            value = 0.0;
                        }
                    }
                } else {
                    System.out.println("no SG " + resName + " " + residue.getNumber());
                }
            }
            return value;
        } else {
            if (!properties.containsKey(resName)) {
                if (verbose) {
                    System.out.printf("property %s not found for residue %s\n", property, resName);
                }
                return 0.0;
            } else {
                if (properties.get(resName).containsKey(property)) {
                    return properties.get(resName).get(property);
                } else {
                    return 0.0;
                }
            }
        }
    }

    private static ArrayList<String> loadAttributes(String attributeFile) {
        if (verbose) {
            System.out.printf("Reading attributes from %s.\n", attributeFile);
        }
        try (BufferedReader b = new BufferedReader(new FileReader(attributeFile))) {
            ArrayList<String> attributes = new ArrayList<String>();

            String line = b.readLine();
            while (line != null) {
                String[] fields = line.split(" ");
                String attrLine = fields[1].replace("_", ",");
                attributes.add(attrLine);
                line = b.readLine();
            }
            return attributes;
        } catch (FileNotFoundException e) {
            System.out.println("Attribute file not found.");
            System.exit(-1);
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }
        return null;
    }

    public double[] getResidueFactors(String resName) throws IOException {
        if (residueFactors == null) {
            residueFactors = loadResidueFactors();
        }
        if (resName.equals("MSE")) {
            resName = "MET";
        }
        if (!residueFactors.containsKey(resName)) {
            return null;
        }
        return residueFactors.get(resName);
    }

    public double[] getResidueShiftProps(Residue residue, int nNeighbors, int nFactors,
                                         int iPPM, int iRef) throws IOException {
        String[] aNames = {"C", "CA", "CB", "N", "H", "HA", "HB"};

        Residue[] residues = new Residue[2 * nNeighbors + 1];
        residues[nNeighbors] = residue;
        for (int i = 1; i <= nNeighbors; i++) {
            int iP = nNeighbors - i;
            int iN = nNeighbors + i;
            residues[iP] = residues[iP + 1] == null ? null : residues[iP + 1].getPrevious();
            residues[iN] = residues[iN - 1] == null ? null : residues[iN - 1].getNext();
        }
        int nAtoms = aNames.length;
        int nRes = 2 * nNeighbors + 1;
        int nPerRes = nFactors + nAtoms * 2; // 2 for missing
        double[] result = new double[nPerRes * nRes];
        for (int iRes = -nNeighbors; iRes <= nNeighbors; iRes++) {
            Residue iResidue = residues[iRes + nNeighbors];
            double[] resFactors;
            if (iResidue == null) {
                resFactors = new double[nFactors];
            } else {
                resFactors = getResidueFactors(iResidue.getName());
            }
            if (resFactors == null) {
                throw new IllegalArgumentException("Unknown residue type " + iResidue.getName());
            }
            int resPos = iRes + nNeighbors;
            int iAtom = 0;
            int resOffset = resPos * nPerRes;
            int shiftOffset = resOffset + nFactors;
            int missingOffset = shiftOffset + nAtoms;
            int nMissing = 0;
            for (var aName : aNames) {
                boolean missing = true;
                var delta = 0.0;
                if (iResidue != null) {
                    if (iResidue.getName().equals("GLY") && aName.equals("HA")) {
                        aName = "HA2";
                        if (iResidue.getAtom(aName) == null) {
                            aName = "HA3";
                        }
                    } else if (aName.equals("HB")) {
                        if (iResidue.getAtom(aName) == null) {
                            aName = "HB2";
                            if (iResidue.getAtom(aName) == null) {
                                aName = "HB3";
                            }
                        }
                    }
                    Atom atom = iResidue.getAtom(aName);
                    if (atom != null) {
                        var ppmV = atom.getPPM(iPPM);
                        var refV = atom.getRefPPM(iRef);
                        if ((ppmV != null) && ppmV.isValid() && (refV != null) && refV.isValid()) {
                            double scale = atom.getName().charAt(0) == 'H' ? 1.0 : 10.0;
                            delta = (ppmV.getValue() - refV.getValue()) / scale;
                            if (delta > 1.0) {
                                delta = 1.0;
                            } else if (delta < -1.0) {
                                delta = -1.0;
                            }
                            missing = false;
                        }
                    }
                }
                result[shiftOffset + iAtom] = delta;
                result[missingOffset + iAtom] = missing ? 0 : 1;
                iAtom++;
                if (missing) {
                    nMissing++;
                }
            }
            System.arraycopy(resFactors, 0, result, resOffset, nFactors);
        }
        return result;
    }
}
