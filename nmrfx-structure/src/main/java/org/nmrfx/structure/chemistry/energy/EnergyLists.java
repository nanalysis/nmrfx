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

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.constraints.*;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.energy.RNARotamer.RotamerScore;
import org.nmrfx.structure.chemistry.predict.Predictor;
import org.nmrfx.structure.fastlinear.FastVector;
import org.nmrfx.structure.fastlinear.FastVector3D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class EnergyLists {
    private static final Logger log = LoggerFactory.getLogger(EnergyLists.class);

    public List<AtomPair> atomList = new ArrayList<>();
    public List<AtomPair> atomList2 = new ArrayList<>();
    public List<CompoundPair> compoundPairList = new ArrayList<>();
    private List<Atom> refAtoms = new ArrayList<>();
    private List<BondPair> bondList = new ArrayList<>();
    private List<AngleConstraint> angleBoundList = new ArrayList<>();
    private int iStruct = 0;
    private List<Atom> angleAtoms = new ArrayList<Atom>();
    private CompoundSphere[] compoundArray = null;
    private Molecule molecule;
    private double distanceLimit = 8.0;
    private boolean includeH = false;
    private boolean useCourseGrain = false;
    private int deltaEnd = 0;
    private int deltaStart = 0;
    private int updateAt = 20;
    private int swapInterval = 0;
    private double hardSphere = 0;
    private double shrinkValue = 0.0;
    private double shrinkHValue = 0.0;
    private ForceWeight forceWeight = new ForceWeight();
    private RingCurrentShift ringShifts = new RingCurrentShift();
    RDCEnergy rdcEnergy = null;
    private Predictor predictor = null;
    AtomBranch[] branches = null;
    static final double toDeg = 180.0 / Math.PI;
    static final double toRad = Math.PI / 180;
    static boolean REPORTBAD = false;
    boolean stochasticMode = false;
    boolean[] stochasticResidues = null;
    boolean constraintsSetup = false;
    private Map<Integer, List<DistanceConstraint>> distancePairMap = new HashMap<>();

    public EnergyLists() {
    }

    public EnergyLists(Molecule molecule) {
        this.molecule = molecule;
    }

    public void setStochasticResidues(boolean[] residuesStates) {
        stochasticResidues = residuesStates;
    }

    void clearAngleBoundaries() {
        angleBoundList.clear();
    }

    public void setCourseGrain(final boolean value) {
        useCourseGrain = value;
        compoundArray = null;
    }

    public boolean getCourseGrain() {
        return useCourseGrain;
    }

    public void setIncludeH(final boolean value) {
        includeH = value;
        compoundArray = null;
    }

    public boolean getIncludeH() {
        return includeH;
    }

    public void setHardSphere(final double value) {
        hardSphere = value;
    }

    public double getHardSphere() {
        return hardSphere;
    }

    public void setDistanceLimit(final double value) {
        distanceLimit = value;
    }

    public double getDistanceLimit() {
        return distanceLimit;
    }

    public void setDeltaStart(final int value) {
        deltaStart = value;
    }

    public int getDeltaStart() {
        return deltaStart;
    }

    public void setDeltaEnd(final int value) {
        deltaEnd = value;
    }

    public int getDeltaEnd() {
        return deltaEnd;
    }

    public void setUpdateAt(final int value) {
        updateAt = value;
    }

    public int getUpdateAt() {
        return updateAt;
    }

    public void setShrinkValue(final double value) {
        shrinkValue = value;
    }

    public double getShrinkValue() {
        return shrinkValue;
    }

    public void setShrinkHValue(final double value) {
        shrinkHValue = value;
    }

    public double getShrinkHValue() {
        return shrinkHValue;
    }

    public void setSwap(final int value) {
        swapInterval = value;
    }

    public int getSwap() {
        return swapInterval;
    }

    void addAngleBoundary(AngleConstraint angleBoundary) {
        angleBoundList.add(angleBoundary);
    }

    public void addAtomRef(Atom atom) {
        refAtoms.add(atom);
    }

    public List<Atom> getRefAtoms() {
        return refAtoms;
    }

    class CompoundSphere {

        final Compound compound;
        final Atom atom;
        final double radius;
        final ArrayList<SpatialSet> sSets;

        CompoundSphere(final Compound compound, final Atom atom, final double radius, ArrayList<SpatialSet> sSets) {
            this.compound = compound;
            this.atom = atom;
            this.radius = radius;
            this.sSets = sSets;
        }

        public String toString() {
            return compound.getNumber() + "." + atom.getName() + " " + radius;
        }
    }

    class CompoundPair {

        final CompoundSphere cSphere1;
        final CompoundSphere cSphere2;
        final ArrayList<AtomPair> atomPairs = new ArrayList<>();

        CompoundPair(CompoundSphere cSphere1, CompoundSphere cSphere2) {
            this.cSphere1 = cSphere1;
            this.cSphere2 = cSphere2;
        }

    }

    public boolean isCourseGrain(String atomType) {
        if (atomType.endsWith("g")) {
            return true;
        } else {
            return false;
        }
    }

    public void setForceWeight(final ForceWeight forceWeight) {
        this.forceWeight = forceWeight;
        molecule.getEnergyCoords().setForceWeight(forceWeight);
    }

    public ForceWeight getForceWeight() {
        return forceWeight;
    }

    public void setStructure(final int iStruct) {
        this.iStruct = iStruct;
    }

    public Molecule getMolecule() {
        return molecule;
    }

    public int getStructure() {
        return iStruct;
    }

    public void setRingShifts(String filterString) {
        ringShifts = new RingCurrentShift();
        ringShifts.makeRingList(molecule);

        MolFilter molFilter = new MolFilter(filterString);
        List<SpatialSet> spatialSets = MoleculeBase.matchAtoms(molFilter, molecule);
        ringShifts.setBasePPMs(spatialSets);
    }

    public void setRingShifts() {
        setRingShifts("*.H8,H6,H5,H2,H1',H2',H3',H4',H5',H5''");
    }

    public void updateShifts() {
        if (predictor == null) {
            predictor = new Predictor();
        }
        for (Polymer polymer : molecule.getPolymers()) {
            predictor.predictRNAWithDistances(polymer, 0, 0, true);
        }
    }

    public void setRDCSet(String name) {
        RDCConstraintSet rdcConstraintSet = molecule.getMolecularConstraints().getRDCSet(name);
        if (rdcConstraintSet != null) {
            rdcEnergy = new RDCEnergy(rdcConstraintSet);
        }
    }

    public static double calcDistance(Point3 pt1, Point3 pt2) {
        return Vector3D.distance(pt1, pt2);
    }

    public static double calcAngle(final Point3 pt1, final Point3 pt2, final Point3 pt3) {
        Vector3D v12 = pt1.subtract(pt2);
        Vector3D v32 = pt3.subtract(pt2);
        return Vector3D.angle(v12, v32);
    }

    public static double volume(Vector3D a, Vector3D b, Vector3D c, Vector3D d) {
        Vector3D i = a.subtract(d);
        Vector3D j = b.subtract(d);
        Vector3D k = c.subtract(d);
        // triple product
        double volume = Vector3D.dotProduct(i, Vector3D.crossProduct(j, k));
        return volume;
    }

    public Atom findClosestAtom(AtomIterable atomContainer, Point3 pt1) {
        double x = 0;
        double y = 0;
        double z = 0;
        int nPoints = 0;
        Atom cAtom = null;
        double minDistance = Double.MAX_VALUE;
        for (Atom atom : atomContainer) {
            Point3 pt2 = atom.getPoint();
            double distance = calcDistance(pt1, pt2);
            if (pt2 != null) {
                if (distance < minDistance) {
                    minDistance = distance;
                    cAtom = atom;
                }
            }
        }
        return cAtom;
    }

    public double getRadius(AtomIterable atomContainer, Point3 pt1) {
        double x = 0;
        double y = 0;
        double z = 0;
        int nPoints = 0;
        double maxDistance = Double.NEGATIVE_INFINITY;
        for (Atom atom : atomContainer) {
            Point3 pt2 = atom.getPoint();
            if (pt2 != null) {
                double distance = calcDistance(pt1, pt2);
                if (distance > maxDistance) {
                    maxDistance = distance;
                }
            }
        }
        return maxDistance;
    }

    public Point3 getCenter(AtomIterable atomContainer) {
        double x = 0;
        double y = 0;
        double z = 0;
        int nPoints = 0;

        for (Atom atom : atomContainer) {
            Point3 point3 = atom.getPoint();
            if (point3 != null) {
                x += point3.getX();
                y += point3.getY();
                z += point3.getZ();
                nPoints++;
            }
        }
        if (nPoints > 0) {
            x /= nPoints;
            y /= nPoints;
            z /= nPoints;
        }
        return new Point3(x, y, z);
    }

    public void makeCompoundList(final String molName) {
        //using molName which represents the name of the molecule creates a list of all compounds
        //get(molName) returns molecule
        molecule = Molecule.get(molName);
        //calls makeCompoundList which a molecule as a parameter
        makeCompoundList(molecule);
    }

    public void addBondConstraint(final String atomName1, final String atomName2, final double r0) {
        Atom atom1 = MoleculeBase.getAtomByName(atomName1);
        Atom atom2 = MoleculeBase.getAtomByName(atomName2);
        bondList.add(new BondPair(atom1, atom2, r0));
    }

    public Map<Integer, List<DistanceConstraint>> getDistancePairMap() {
        return distancePairMap;
    }

    public void clearDistanceMap() {
        distancePairMap.clear();
    }

    //calculates distance between center of the residues. If center is far away, no need to check atoms of residue
    public void makeCompoundList(Molecule molecule) {
        try {
            AtomEnergyProp.readPropFile();
            AtomEnergyProp.makeIrpMap();
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
        this.molecule = molecule;
        // initialize energy types for atoms
        List<Atom> atoms = molecule.getAtomArray();
        for (Atom atom : atoms) {
            atom.setAtomEnergyProp(AtomEnergyProp.get(atom.getType()));
        }

        //initializes firstResidue to null
        //Residue - component of polymer
        Residue firstResidue = null;
        //initializes lastResidue to null
        Residue lastResidue = null;
        //initializes a compound object
        Compound compound;
        //initializes an enitity object
        Entity entity;

        //clears a list of compounds spheres
        ArrayList<CompoundSphere> compoundList = new ArrayList<CompoundSphere>();

        molecule.getAtomTypes();
        Iterator e = molecule.entities.values().iterator();

        while (e.hasNext()) {
            //entity - component of the molecule (could be individual residue or polymer)
            entity = (Entity) e.next();

            if (entity instanceof Polymer) {
                Polymer polymer = (Polymer) entity;
                //residue - each monomer of the polymer
                firstResidue = polymer.getFirstResidue();
                lastResidue = polymer.getLastResidue();
                //Residues are compounds
                compound = (Compound) firstResidue;
            } else {
                compound = (Compound) entity;
            }

            while (compound != null) {
                for (Atom atom : compound) {
                    if (atom.getAtomEnergyProp() == null) {
                        continue;
                    }
                    if ((atom.getAtomicNumber() == 1) && !includeH) {
                        continue;
                    }

                    if (useCourseGrain) {
                        if (!isCourseGrain(atom.getType())) {
                            continue;
                        }
                    } else if (isCourseGrain(atom.getType())) {
                        continue;
                    }
                }

                //loops over all compounds in the polymer
                if (entity instanceof Polymer) {
                    if (compound == lastResidue) {
                        break;
                    }

                    compound = ((Residue) compound).next;
                } else {
                    break;
                }
            }
        }
        //if array is not already made or wrong size create new array
        if ((compoundArray == null) || (compoundArray.length != compoundList.size())) {
            compoundArray = new CompoundSphere[compoundList.size()];
        }

        compoundArray = compoundList.toArray(compoundArray);

        //used to rotate angles to minimize energy
        //building list of angles that can be rotated
        angleAtoms = molecule.setupAngles();
        molecule.setupRotGroups();
        makeAtomList2();
    }

    public void updateFixed(Dihedral dihedrals) {
        if (dihedrals == null) {
            return;
        }
        dihedrals.saveDihedrals();
        EnergyCoords eCoords = molecule.getEnergyCoords();
        double[][][] dRange = eCoords.getFixedRange();
        int nUpdates = 10;
        for (int i = 0; i < nUpdates; i++) {
            dihedrals.randomizeAngles();
            molecule.genCoordsFastVec3D(null);
            eCoords.updateRanges(dRange);
        }
        eCoords.updateFixed(dRange);
        dihedrals.restoreDihedrals();
        molecule.genCoordsFastVec3D(null);
    }

    public void clear() {
        atomList.clear();
        bondList.clear();
    }

    public String dump(final double limitVal, final double shiftVal) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        dump(limitVal, shiftVal, writer);
        return stringWriter.toString();
    }

    public void dump(final double limitVal, final double shiftVal, String fileName) {
        PrintStream out = System.out;
        try {
            if (!fileName.equals("")) {
                out = new PrintStream(fileName);
            }
        } catch (IOException ioE) {
            log.warn(ioE.getMessage(), ioE);
        }

        OutputStream outStream = new BufferedOutputStream(out);
        PrintWriter writer = new PrintWriter(outStream);
        dump(limitVal, shiftVal, writer);
    }

    public void dump(final double limitVal, final double shiftVal, PrintWriter writer) {
        double dihEnergy = 0.0;
        int nDih = 0;
        double cffnbEnergy = 0.0;
        int nCFF = 0;
        double repelEnergy = 0.0;
        int nRepel = 0;
        double distanceEnergy = 0.0;
        double stackingEnergy = 0.0;
        int nDistance = 0;
        int nStack = 0;
        double maxDis = 0.0;
        double irpEnergy = 0.0;
        double rdcEnergyValue = 0.0;
        int nIrp = 0;
        double shiftTotEnergy = 0.0;
        int nShift = 0;
        double probDih = 0.0;
        int nRotamers = 0;
        try {
            if (forceWeight.getDihedral() > 0.0) {
                for (AngleConstraint angleBoundary : angleBoundList) {
                    AtomEnergy energy = calcDihedralEnergy(angleBoundary, forceWeight, false);
                    dihEnergy += energy.getEnergy();
                    nDih++;
                    if (energy.getEnergy() > limitVal) {
                        double dihedral = grabDihedral(angleBoundary);
                        writer.format("Dih: %40s %5.2f %5.2f %5.2f %5.2f\n", angleBoundary.getAtomNames(), toDeg * dihedral,
                                toDeg * angleBoundary.getLower(), toDeg * angleBoundary.getUpper(), energy.getEnergy());
                    }
                }
            }
            if (forceWeight.getShift() > 0.0) {
                for (Atom atom : refAtoms) {
                    double deltaShift = AtomMath.calcDeltaShift(atom);
                    if (deltaShift != -1.0) {
                        Double mae = Predictor.getMAE(atom);
                        if (mae != null) {
                            deltaShift /= mae;
                        }
                        double shiftEnergy = AtomMath.calcShiftEnergy(deltaShift, forceWeight);
                        shiftTotEnergy += shiftEnergy;
                        nShift++;
                        if (Math.abs(deltaShift) > shiftVal) {
                            writer.format("Shi: %10s %10s %5.2f %5.2f %5.2f %5.3f\n", atom.getFullName(), "",
                                    atom.getPPM(0).getValue(), atom.getRefPPM(0).getValue(), deltaShift, shiftEnergy);
                        }
                    }
                }
            }

            if (forceWeight.getCFFNB() > 0.0) {
                EnergyCoords eCoords = molecule.getEnergyCoords();
                cffnbEnergy = eCoords.calcRepel(false, forceWeight.getCFFNB(), forceWeight.getElectrostatic());
                nCFF = eCoords.getNContacts();
                for (int i = 0; i < nCFF; i++) {
                    ViolationStats stat = eCoords.getRepelError(i, limitVal, forceWeight.getCFFNB(), forceWeight.getElectrostatic());
                    if (stat != null) {
                        String errMsg = stat.toString();
                        writer.print(errMsg);
                    }
                }
            } else {
                EnergyCoords eCoords = molecule.getEnergyCoords();
                repelEnergy = eCoords.calcRepel(false, forceWeight.getRepel(), -1.0);
                nRepel = eCoords.getNContacts();
                for (int i = 0; i < nRepel; i++) {
                    ViolationStats stat = eCoords.getRepelError(i, limitVal, forceWeight.getRepel(), -1.0);
                    if (stat != null) {
                        String errMsg = stat.toString();
                        writer.print(errMsg);
                    }
                }
            }
            for (BondPair bondPair : bondList) {
                AtomEnergy energy = AtomMath.calcBond(bondPair.atom1.getPoint(), bondPair.atom2.getPoint(), bondPair,
                        forceWeight, false);
                final double p = Vector3D.distance(bondPair.atom1.getPoint(), bondPair.atom2.getPoint());
                if (energy.getEnergy() > limitVal) {
                    writer.format("Bon: %10s %10s %5.2f %7.3f %5.2f\n", bondPair.atom1.getFullName(),
                            bondPair.atom2.getFullName(), bondPair.r0, energy.getEnergy(), p);
                }
            }
            if (forceWeight.getNOE() > 0.0) {
                EnergyCoords eCoords = molecule.getEnergyCoords();
                distanceEnergy = eCoords.calcNOE(false, forceWeight.getNOE());
                nDistance = eCoords.getNNOE();
                System.out.println("NNOE " + nDistance + " nRepel " + eCoords.getNContacts());
                for (int i = 0; i < nDistance; i++) {
                    ViolationStats stat = eCoords.getNOEError(i, limitVal, forceWeight.getNOE());
                    if (stat != null) {
                        if (Math.abs(stat.getViol()) > Math.abs(maxDis)) {
                            maxDis = stat.getViol();
                        }
                        String errMsg = stat.toString();
                        writer.print(errMsg);
                    }
                }
            }
            if (forceWeight.getStacking() > 0.0) {
                EnergyCoords eCoords = molecule.getEnergyCoords();
                stackingEnergy = eCoords.calcStacking(false, forceWeight.getStacking());
                nStack = eCoords.getNStacking();
            }
            EnergyCoords eCoords = molecule.getEnergyCoords();
            List<Polymer> polymers = molecule.getPolymers();
            for (Polymer polymer : polymers) {
                if (polymer.isRNA()) {
                    for (int i = 1; i < polymer.size(); i++) {
                        Residue residue = polymer.getResidue(i);
                        if (!residue.isStandard()) {
                            i++;
                            continue;
                        }

                        nRotamers++;
                        RotamerScore[] rotamerScores = RNARotamer.getNBest(residue, 1, eCoords);
                        if ((rotamerScores == null) || (rotamerScores.length == 0)) {
                            continue;
                        }

                        double rotamerEnergy = RNARotamer.calcEnergy(rotamerScores);
                        if (forceWeight.getDihedralProb() > 0.0) {
                            rotamerEnergy *= forceWeight.getDihedralProb();
                            probDih += rotamerEnergy;
                        }
                        for (RotamerScore rotamerScore : rotamerScores) {
                            writer.format("Tor: %3d %3s %3s %6.2f %s\n", i,
                                    residue.getNumber(),
                                    residue.getName(),
                                    rotamerEnergy,
                                    rotamerScore.toString());
                        }
                    }
                }
            }

            if (forceWeight.getIrp() > 0.0) {
                for (Atom atom : angleAtoms) {
                    if ((atom.irpIndex > 1) && (atom.irpIndex < 9999) && atom.rotActive) {
                        double eVal = calcIRP(atom);
                        irpEnergy += eVal;
                        nIrp++;
                        if (eVal > limitVal) {
                            writer.format("Irp: %10s %5.2f %5.2f\n", atom.getFullName(), toDeg * atom.dihedralAngle,
                                    eVal);
                        }
                    }
                }

            }

            if ((forceWeight.getRDC() > 0.0) && (rdcEnergy != null)) {
                rdcEnergyValue = rdcEnergy.calcEnergy() * forceWeight.getRDC();
            }

            double energySum = dihEnergy + cffnbEnergy + repelEnergy + distanceEnergy + irpEnergy + shiftTotEnergy + probDih + rdcEnergyValue;
            writer.format(
                    "Irp %5d %8.3f Dih %5d %8.3f CFF %5d %8.3f Repel %5d %8.3f Distance %5d %8.3f %8.3f Shift %5d %8.3f ProbT %5d %8.3f Stack %5d %8.3f RDC %8.3f Total %8.3f\n",
                    nIrp, irpEnergy, nDih, dihEnergy, nCFF, cffnbEnergy, nRepel, repelEnergy, nDistance, distanceEnergy,
                    maxDis, nShift, shiftTotEnergy, nRotamers, probDih, nStack, stackingEnergy, rdcEnergyValue, energySum);
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }
        writer.close();
    }

    public static double grabDihedral(AngleConstraint boundary) {
        double dihedral;
        int atomListLength = boundary.getAtoms().length;
        switch (atomListLength) {
            case 1:
                dihedral = boundary.getAtom().dihedralAngle;
                return dihedral;
            case 4:
                Point3 pt0,
                        pt1,
                        pt2,
                        pt3;
                Atom[] atoms = boundary.getAtoms();
                pt0 = atoms[0].getPoint();
                pt1 = atoms[1].getPoint();
                pt2 = atoms[2].getPoint();
                pt3 = atoms[3].getPoint();
                dihedral = AtomMath.calcDihedral(pt0, pt1, pt2, pt3);
                return dihedral;
            default:
                throw new IllegalArgumentException("Invalid atom list size of " + atomListLength);
        }
    }

    public static AtomEnergy calcDihedralEnergy(AngleConstraint boundary, final ForceWeight forceWeight,
                                                final boolean calcDeriv) {
        double dihedral = grabDihedral(boundary);
        double upper = boundary.getUpper();
        double lower = boundary.getLower();
        return AtomMath.calcDihedralEnergy(dihedral, lower, upper, forceWeight, calcDeriv);
    }

    public double calcDihedralEnergyFast(double[] gradient) {
        EnergyCoords eCoords = molecule.getEnergyCoords();
        double energyTotal = 0.0;
        for (AngleConstraint angleBoundary : angleBoundList) {
            double dihedral;
            Atom[] atoms = angleBoundary.getAtoms();
            if (atoms.length == 1) {
                dihedral = atoms[0].dihedralAngle;
            } else {
                dihedral = eCoords.calcDihedral(atoms[0].eAtom, atoms[1].eAtom, atoms[2].eAtom, atoms[3].eAtom);
            }
            AtomEnergy energy = AtomMath.calcDihedralEnergy(dihedral, angleBoundary.getLower(), angleBoundary.getUpper(), forceWeight,
                    gradient != null);
            energyTotal += energy.getEnergy();
            if (gradient != null) {
                gradient[angleBoundary.getIndex()] -= energy.getDeriv();
            }
        }

        return energyTotal;

    }

    public double calcIRP(Atom atom) {
        EnergyCoords eCoords = molecule.getEnergyCoords();
        double weight = forceWeight.getIrp();
        double energyTotal = 0.0;
        Atom[] atoms = new Atom[4];
        atoms[3] = atom.daughterAtom;
        if (atoms[3] != null) {
            atoms[2] = atom;
            atoms[1] = atom.parent;
            if (atoms[1] != null) {
                atoms[0] = atoms[1].parent;
            }
        }
        if (atoms[0] != null) {
            int irpIndex = atom.irpIndex;
            if ((irpIndex > 0) && (irpIndex < 9999)) {
                double angle = eCoords.calcDihedral(atoms[0].eAtom, atoms[1].eAtom, atoms[2].eAtom, atoms[3].eAtom);
                angle = Util.reduceAngle(angle);
                double[][] irpValues = AtomEnergyProp.irpTable[irpIndex - 1];
                for (double[] irpVal : irpValues) {
                    double v = irpVal[0];
                    double n = irpVal[1];
                    double phi = irpVal[2];
                    double energy = weight * v * (1.0 + Math.cos(n * angle - phi));
                    energyTotal += energy;
                }
            }
        }
        return energyTotal;
    }

    public double calcIRPFast(double[] gradient) {
        EnergyCoords eCoords = molecule.getEnergyCoords();
        double energyTotal = 0.0;
        int i = 0;
        Atom[] atoms = new Atom[4];
        double weight = forceWeight.getIrp();
        for (Atom atom : angleAtoms) {
            atoms[3] = atom.daughterAtom;
            if (atoms[3] != null) {
                atoms[2] = atom;
                atoms[1] = atom.parent;
                if (atoms[1] != null) {
                    atoms[0] = atoms[1].parent;
                }
            }
            if (atoms[0] != null) {
                int irpIndex = atom.irpIndex;
                if ((irpIndex > 0) && (irpIndex < 9999)) {
                    double angle = eCoords.calcDihedral(atoms[0].eAtom, atoms[1].eAtom, atoms[2].eAtom, atoms[3].eAtom);
                    angle = Util.reduceAngle(angle);
                    double[][] irpValues = AtomEnergyProp.irpTable[irpIndex - 1];
                    for (double[] irpVal : irpValues) {
                        double v = irpVal[0];
                        double n = irpVal[1];
                        double phi = irpVal[2];
                        double energy = weight * v * (1.0 + Math.cos(n * angle - phi));
                        energyTotal += energy;
                        double deriv = 0.0;
                        if (gradient != null) {
                            deriv = -weight * v * n * Math.sin(n * angle - phi);
                            gradient[i] += deriv;
                        }
                    }
                }
            }
            i++;
        }
        return energyTotal;

    }

    public double calcRobsen(boolean calcDeriv) {
        double totalEnergy = 0;
        for (AtomPair atomPair : atomList) {
            Point3 pt1 = atomPair.spSet1.getPoint();
            Point3 pt2 = atomPair.spSet2.getPoint();
            AtomEnergy energy = AtomMath.calcCFF(pt1, pt2, atomPair, forceWeight, calcDeriv);
            if (calcDeriv) {
                addDeriv(atomPair, energy.getDeriv(), pt1, pt2);
            }
            totalEnergy += energy.getEnergy();
        }
        return totalEnergy;
    }

    public double calcRepelFast(boolean calcDeriv) {
        EnergyCoords eCoords = molecule.getEnergyCoords();
        double weight = forceWeight.getCFFNB() > 0.0 ? forceWeight.getCFFNB() : forceWeight.getRepel();
        double eWeight = forceWeight.getCFFNB() > 0.0 ? forceWeight.getElectrostatic() : -1.0;
        double energy = eCoords.calcRepel(calcDeriv, weight, eWeight);
        if (calcDeriv) {
            eCoords.addRepelDerivs(branches);
        }

        return energy;
    }

    public double calcRepel(boolean calcDeriv) {
        double totalEnergy = 0;
        double[] eD = new double[2];
        for (AtomPair atomPair : atomList) {
            //            if (stochasticMode && (randomData.nextUniform(0.0, 1.0) > 0.05)) {
            //            if (stochasticMode) {
            //                Atom atom1 = atomPair.spSet1.atom;
            //                Atom atom2 = atomPair.spSet2.atom;
            //                Compound compound1 = (Compound) atom1.entity;
            //                Compound compound2 = (Compound) atom2.entity;
            //                int iRes = Integer.parseInt(compound1.number);
            //                int jRes = Integer.parseInt(compound2.number);
            //                if ((stochasticResidues != null) && (!stochasticResidues[iRes] || !stochasticResidues[jRes])) {
            //                    continue;
            //                }
            //            }
            atomPair.getEnergy(calcDeriv, eD);
            totalEnergy += eD[0];
            if (calcDeriv) {
                addDeriv(atomPair, eD[1]);
            }
        }
        return totalEnergy;
    }

    public double calcShiftsFast(boolean calcDeriv) {
        EnergyCoords eCoords = molecule.getEnergyCoords();
        eCoords.setupShifts();
        double weight = forceWeight.getShift();
        double energy = eCoords.calcDistShifts(calcDeriv, Predictor.getRMax(), Predictor.getIntraScale(), weight);

        return energy;
    }

    public double calcShift(boolean calcDeriv) {
        double totalEnergy = 0;
        if (calcDeriv) {
            //fixme calcDerive should not be on
            return -1.0;
        }
        updateShifts();
        for (Atom atom : refAtoms) {
            double deltaShift = AtomMath.calcDeltaShift(atom);
            Double mae = Predictor.getMAE(atom);
            if (mae != null) {
                deltaShift /= mae;
            }
            totalEnergy += AtomMath.calcShiftEnergy(deltaShift, forceWeight);
        }
        return totalEnergy;
    }

    public double calcbondEnergy(boolean calcDeriv) {
        double totalEnergy = 0;
        for (BondPair bondPair : bondList) {
            AtomEnergy energy = AtomMath.calcBond(bondPair.atom1.getPoint(), bondPair.atom2.getPoint(), bondPair, forceWeight,
                    calcDeriv);
            totalEnergy += energy.getEnergy();
        }
        return totalEnergy;
    }

    public double calcProbDih(boolean calcDeriv, double[] derivs) {
        EnergyCoords eCoords = molecule.getEnergyCoords();
        double totalEnergy = 0;
        List<Polymer> polymers = molecule.getPolymers();
        for (Polymer polymer : polymers) {
            if (polymer.isRNA()) {
                for (int i = 1; i < polymer.size(); i++) {
                    Residue residue = polymer.getResidue(i);
                    if (!residue.isStandard()) {
                        i++;
                        continue;
                    }
                    RotamerScore rotamerScore = RNARotamer.getBest(polymer, i, eCoords);
                    if (rotamerScore == null) {
                        continue;
                    }
                    RotamerScore[] rotamerScores = {rotamerScore};
                    double rotamerEnergy = RNARotamer.calcEnergy(rotamerScores);
                    if (Double.isNaN(rotamerEnergy) || !Double.isFinite(rotamerEnergy)) {
                        System.out.println("rotamer nan " + rotamerScores.length);
                    }
                    if (calcDeriv) {
                        Map<Integer, Double> rotDerivs = RNARotamer.calcDerivs(rotamerScores, rotamerEnergy);
                        for (int atomIndex : rotDerivs.keySet()) {
                            if (atomIndex >= derivs.length) {
                                throw new RuntimeException("Atom Index " + atomIndex
                                        + " too large " + derivs.length + " for residue " + residue.toString()
                                        + " angle atoms size " + angleAtoms.size());
                            }
                            double deriv = forceWeight.getDihedralProb() * rotDerivs.get(atomIndex);
                            derivs[atomIndex] += (deriv);
                            if (Double.isNaN(derivs[atomIndex]) || !Double.isFinite(derivs[atomIndex])) {
                                System.out.println("deriv nan");
                            }
                        }
                    }
                    totalEnergy += (forceWeight.getDihedralProb() * rotamerEnergy);
                }

            }
        }
        return totalEnergy;
    }

    public double calcNOEFast(boolean calcDeriv) {
        EnergyCoords eCoords = molecule.getEnergyCoords();
        double weight = forceWeight.getNOE();
        double energy = eCoords.calcNOE(calcDeriv, weight);
        if (calcDeriv) {
            eCoords.addNOEDerivs(branches);
        }

        return energy;
    }

    public double calcStackingFast(boolean calcDeriv) {
        EnergyCoords eCoords = molecule.getEnergyCoords();
        double weight = forceWeight.getStacking();
        double energy = eCoords.calcStacking(calcDeriv, weight);
        if (calcDeriv) {
            eCoords.addStackingDerivs(branches);
        }

        return energy;
    }

    boolean checkInRange(EnergyCoords eCoords, Noe noe) {
        boolean allInRange = true;
        if (deltaEnd > 0) {
            for (AtomDistancePair atomDistancePair : noe.getAtomPairs()) {
                Atom atom1 = atomDistancePair.getAtoms1()[0];
                Atom atom2 = atomDistancePair.getAtoms2()[0];
                if (true || !eCoords.fixedCurrent() || !eCoords.getFixed(atom1.eAtom, atom2.eAtom)) {
                    Compound compound1 = (Compound) atom1.entity;
                    Compound compound2 = (Compound) atom2.entity;
                    int iRes = Integer.parseInt(compound1.number);
                    int jRes = Integer.parseInt(compound2.number);
                    if (Math.abs(iRes - jRes) >= deltaEnd) {
                        allInRange = false;
                        break;
                    }
                }
            }
        }
        return allInRange;
    }

    boolean rotGroupOK(Noe noe) {
        boolean rotUnitOK = false;
        for (AtomDistancePair atomDistancePair : noe.getAtomPairs()) {
            Atom atom1 = atomDistancePair.getAtoms1()[0];
            Atom atom2 = atomDistancePair.getAtoms2()[0];
            int iUnit = atom1.rotGroup == null ? -1 : atom1.rotGroup.rotUnit;
            int jUnit = atom2.rotGroup == null ? -1 : atom2.rotGroup.rotUnit;
            if (((iUnit != -1) || (jUnit != -1)) && (iUnit != jUnit)) {
                rotUnitOK = true;
            }
        }
        return rotUnitOK;
    }

    void addPair(EnergyCoords eCoords, Noe noe, int iGroup, double weight) {
        boolean ok = false;
        SpatialSetGroup spatialSetGroup1 = noe.getSpg1();
        SpatialSetGroup spatialSetGroup2 = noe.getSpg2();
        int nPairs = spatialSetGroup1.getSpSets().size() * spatialSetGroup2.getSpSets().size();
        for (SpatialSet spatialSet1 : spatialSetGroup1.getSpSets()) {
            for (SpatialSet spatialSet2 : spatialSetGroup2.getSpSets()) {
                Atom atom1 = spatialSet1.getAtom();
                Atom atom2 = spatialSet2.getAtom();
                if (deltaEnd == 0) {
                    ok = true;
                } else {
                    Compound compound1 = (Compound) atom1.entity;
                    Compound compound2 = (Compound) atom2.entity;
                    int iRes = Integer.parseInt(compound1.number);
                    int jRes = Integer.parseInt(compound2.number);
                    if (Math.abs(iRes - jRes) < deltaEnd) {
                        ok = true;
                    }
                }
                if (ok) {
                    int iAtom = atom1.eAtom;
                    int jAtom = atom2.eAtom;
                    int iUnit = atom1.rotGroup == null ? -1 : atom1.rotGroup.rotUnit;
                    int jUnit = atom2.rotGroup == null ? -1 : atom2.rotGroup.rotUnit;
                    if (((iUnit != -1) || (jUnit != -1)) && (iUnit != jUnit)) {
                        eCoords.addPair(iAtom, jAtom, iUnit, jUnit, noe.getLower(), noe.getUpper(), noe.isBond(),
                                iGroup, weight / nPairs);
                    }
                }
            }
        }
    }

    boolean checkStochastic(Noe noe) {
        Atom atom1 = noe.getSpg1().getAnAtom();
        Atom atom2 = noe.getSpg2().getAnAtom();
        Compound compound1 = (Compound) atom1.entity;
        Compound compound2 = (Compound) atom2.entity;
        int iRes = Integer.parseInt(compound1.number);
        int jRes = Integer.parseInt(compound2.number);
        return ((stochasticResidues != null) && (!stochasticResidues[iRes] || !stochasticResidues[jRes]));

    }
    public void updateNOEPairs() {
        EnergyCoords eCoords = molecule.getEnergyCoords();
        molecule.updateVecCoords();
        eCoords.eConstraintPairs.clear();
        if (!eCoords.fixedCurrent()) {
            if (molecule.getDihedrals() == null) {
                return;
            }
            updateFixed(molecule.getDihedrals());
        }
        AtomicInteger iAGroup = new AtomicInteger(0);
        for (NoeSet distanceSet : molecule.getMolecularConstraints().noeSets.values()) {
            var peakNoeMap = distanceSet.getPeakMapEntries();
            peakNoeMap.forEach(e -> {
                int iGroup = iAGroup.getAndIncrement();
                List<Noe> noes = e.getValue();
                int nNoes = noes.size();
                for (Noe noe : noes) {
                    double weight = noe.getWeight();
                    if (noe.isBond() || distanceSet.containsBonds()) {
                        weight *= forceWeight.getBondWt();
                    }
                    if (stochasticMode && checkStochastic(noe)) {
                        continue;
                    }
                    addPair(eCoords, noe, iGroup, weight / nNoes);
                }
            });
        }
        eCoords.updateGroups();
    }

    public void setupConstraints() {
        updateNOEPairs();
        constraintsSetup = true;
    }

    public EnergyDeriv energyAndDeriv() {
        EnergyDeriv eDeriv = energy(true);
        return eDeriv;
    }

    public double energy() {
        EnergyDeriv eDeriv = energy(false);
        return eDeriv.getEnergy();
    }

    public EnergyDeriv energy(boolean calcDeriv) {
        if (!constraintsSetup) {
            setupConstraints();
        }
        double energyTotal = 0.0;
        double[] gradient = null;
        if (calcDeriv) {
            if (branches == null) {
                setupDihedrals();
            }
            zeroBranches();
        }
        try {
            //two ways to calculate whether atoms are bumping into one another - 1) calc repel, 2)calc robsen
            if (forceWeight.getCFFNB() > 0.0) {
                energyTotal += calcRepelFast(calcDeriv);
            } else if (forceWeight.getRepel() > 0.0) {
                energyTotal += calcRepelFast(calcDeriv);
            }
            energyTotal += calcbondEnergy(calcDeriv);

            if (forceWeight.getNOE() > 0.0) {
                energyTotal += calcNOEFast(calcDeriv);
            }
            if (forceWeight.getStacking() > 0.0) {
                energyTotal += calcStackingFast(calcDeriv);
            }
            if (calcDeriv) {
                gradient = recurrentDerivative();
            }

            if (forceWeight.getShift() > 0.0) {
                energyTotal += calcShiftsFast(calcDeriv);
            }

            if (forceWeight.getDihedralProb() > 0.0) {
                energyTotal += calcProbDih(calcDeriv, gradient);
            }
            if (forceWeight.getDihedral() > 0.0) {
                energyTotal += calcDihedralEnergyFast(gradient);
            }
            if (forceWeight.getIrp() > 0.0) {
                if (true) {  // placeholder for new fast mode
                    if (forceWeight.getIrp() > 0.0) {
                        energyTotal += calcIRPFast(gradient);
                    }
                } else {

                    int i = 0;
                    for (Atom atom : angleAtoms) {
                        AtomEnergy energy = AtomMath.calcIrpEnergy(atom.daughterAtom, forceWeight, calcDeriv);
                        energyTotal += energy.getEnergy();
                        if (calcDeriv) {
                            gradient[i++] += energy.getDeriv();
                        }
                    }
                }
            }
            if ((forceWeight.getRDC() > 0.0) && (rdcEnergy != null)) {
                energyTotal += rdcEnergy.calcEnergy() * forceWeight.getRDC();
            }

            if (calcDeriv) {
                for (int i = 0; i < branches.length; i++) {
                    if (REPORTBAD && (Math.abs(gradient[i]) > 100000.0)) {
                        System.out.println("bad force " + i + " " + angleAtoms.get(i).getFullName() + " " + gradient[i]);
                    }
                    branches[i].force = gradient[i];
                }
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            System.exit(1);
        }

        return new EnergyDeriv(energyTotal, gradient);
    }

    public void setupDihedrals() {
        EnergyCoords eCoords = molecule.getEnergyCoords();
        FastVector3D[] vecCoords = eCoords.getVecCoords();
        try {
            List<Atom> atoms = molecule.getAtomArray();
            int nBranch = 0;
            for (Atom atom : atoms) {
                if (atom.rotUnit != -1) {
                    int unit = atom.rotUnit;
                    if (unit > nBranch) {
                        nBranch = unit;
                    }
                }
            }
            nBranch++;
            branches = new AtomBranch[nBranch];
            for (Atom atom : atoms) {
                if (atom.rotUnit != -1) {
                    int unit = atom.rotUnit;
                    FastVector3D iVecCoords = vecCoords[atom.eAtom];
                    FastVector3D pVecCoords = vecCoords[atom.parent.eAtom];
                    branches[unit] = new AtomBranch(iVecCoords, pVecCoords);
                    branches[unit].setAtom(atom);
                    int nAtomBranches = 0;
                    for (Atom branchAtom : atom.branchAtoms) {
                        if ((branchAtom != null) && branchAtom.rotActive) {
                            nAtomBranches++;
                        }
                    }
                    branches[unit].branches = new AtomBranch[nAtomBranches];
                }
            }

            for (Atom atom : atoms) {
                if (atom.rotUnit != -1) {
                    int unit = atom.rotUnit;
                    if (unit >= 0) {
                        int i = 0;
                        for (Atom branchAtom : atom.branchAtoms) {
                            if ((branchAtom != null) && branchAtom.rotActive) {
                                branches[unit].branches[i++] = branches[branchAtom.rotUnit];
                                branches[branchAtom.rotUnit].prev = branches[unit];
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            System.exit(1);
        }
    }

    public void addDeriv(AtomPair atmPair, double deriv, final Point3 pt1, final Point3 pt2) {
        addDeriv(atmPair.spSet1, atmPair.spSet2, deriv, pt1, pt2);
    }

    public void addDeriv(AtomDistancePair atmPair, double deriv) {
        Atom atom1 = atmPair.getAtoms1()[0];
        Atom atom2 = atmPair.getAtoms2()[0];
        Point3 pt1 = atmPair.getCenter1();
        Point3 pt2 = atmPair.getCenter2();
        addDeriv(atom1.getSpatialSet(), atom2.getSpatialSet(), deriv, pt1, pt2);
    }

    public void addDeriv(SpatialSet spSet1, SpatialSet spSet2, double deriv, final Point3 pt1, final Point3 pt2) {
        System.out.println("a " + deriv);
        FastVector pv1 = new FastVector(pt1.toArray());
        FastVector pv2 = new FastVector(pt2.toArray());

        FastVector v1 = new FastVector(3);
        pv1.crossProduct(pv2, v1);
        v1.multiply(deriv);

        FastVector v2 = new FastVector(3);
        pv1.subtract(pv2, v2);
        v2.multiply(deriv);

        if (spSet1.atom.rotGroup != null) {
            int unit1 = spSet1.atom.rotGroup.rotUnit;
            if (unit1 >= 0) {
                branches[unit1].addToF(v1);
                branches[unit1].addToG(v2);
            }
        }
        if (spSet2.atom.rotGroup != null) {
            int unit2 = spSet2.atom.rotGroup.rotUnit;
            if (unit2 >= 0) {
                branches[unit2].subtractToF(v1);
                branches[unit2].subtractToG(v2);
            }
        }
    }

    public void addDeriv(AtomPair atomPair, double deriv) {
        System.out.println("b " + deriv);
        Point3 pt1 = atomPair.spSet1.getPoint();
        Point3 pt2 = atomPair.spSet2.getPoint();
        FastVector pv1 = new FastVector(pt1.toArray());
        FastVector pv2 = new FastVector(pt2.toArray());

        FastVector v1 = new FastVector(3);
        pv1.crossProduct(pv2, v1);
        v1.multiply(deriv);

        FastVector v2 = new FastVector(3);
        pv1.subtract(pv2, v2);
        v2.multiply(deriv);

        if (atomPair.unit1 >= 0) {
            branches[atomPair.unit1].addToF(v1);
            branches[atomPair.unit1].addToG(v2);

        }
        if (atomPair.unit2 >= 0) {
            branches[atomPair.unit2].subtractToF(v1);
            branches[atomPair.unit2].subtractToG(v2);
        }
    }

    public void zeroBranches() {
        int i = 0;
        try {
            for (AtomBranch branch : branches) {
                if (branch != null) {
                    branch.initF();
                    branch.initG();
                } else {
                    System.out.println("branch null at " + i);
                }
                i++;
            }
        } catch (Exception ex) {
            log.error("Error at branch {} {}", i, ex.getMessage(), ex);
            System.exit(1);
        }
    }

    public void dumpBranches() {
        int n = branches.length;
        for (int i = 0; i < n; i++) {
            System.out.printf("branch %3d %10s ", i, branches[i].atom.getFullName());
            for (int k = 0; k < 3; k++) {
                System.out.printf(" f %7.1f g %7.1f", branches[i].farr[k], branches[i].garr[k]);
            }
            System.out.println("");
        }
        try {
            for (int i = n - 1; i >= 0; i--) {
                System.out.print("recur " + i + " " + branches[i].branches.length);
                for (int j = 0; j < branches[i].branches.length; j++) {
                    if (branches[i].branches[j] != null) {
                        System.out.printf(" recur %3d %10s %10s ", i, branches[i].atom.getFullName(), branches[i].branches[j].atom.getFullName());
                        for (int k = 0; k < 3; k++) {
                            System.out.printf(" %7.1f %7.1f ", branches[i].branches[j].farr[k], branches[i].branches[j].garr[k]);
                        }
                    }
                }
                System.out.println("");
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            System.exit(1);
        }
    }

    public double[] recurrentDerivative() {
        int n = branches.length;
        double[] df = new double[n];
        try {
            for (int i = n - 1; i >= 0; i--) {
                for (int j = 0; j < branches[i].branches.length; j++) {
                    if (branches[i].branches[j] != null) {
                        branches[i].addToF(branches[i].branches[j].farr);
                        branches[i].addToG(branches[i].branches[j].garr);
                    }
                }
            }
            int k = 0;
            FastVector3D cross = new FastVector3D();
            for (int i = 0; i < n; i++) {
                FastVector3D eaF3D = branches[i].getUnitVecF();
                double dot1 = eaF3D.dotProduct(branches[i].farr);
                eaF3D.crossProduct(branches[i].iVec, cross);
                double dot2 = cross.dotProduct(branches[i].garr);
                if (REPORTBAD && (Math.abs(dot1 + dot2) > 100000.0)) {
                    System.out.printf("%5d dot1 %9.5g dot2 %9.5g df %9.5g %s\n", i, dot1, dot2, (dot1 + dot2),
                            branches[i].atom.getFullName());
                }
                df[k++] = -1.0 * (dot1 + dot2);
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            System.exit(1);
        }
        return df;
    }

    public void resetConstraints() {
        constraintsSetup = false;
    }

    public void makeAtomListFast() {
        EnergyCoords eCoords = molecule.getEnergyCoords();
        if (!eCoords.fixedCurrent()) {
            if (molecule.getDihedrals() == null) {
                return;
            }
            updateFixed(molecule.getDihedrals());
        }
        if (!constraintsSetup) {
            updateNOEPairs();
        }
        eCoords.setCells(eCoords.eDistancePairs, deltaEnd, distanceLimit, hardSphere,
                includeH, shrinkValue, shrinkHValue, forceWeight.getCFFNB() > 0.0);
    }

    public void makeAtomList2() {
        if (compoundArray == null) {
            makeCompoundList(molecule);
        }
        for (int i = 0; i < compoundArray.length; i++) {
            CompoundSphere cSphere = compoundArray[i];
            double radius = getRadius(cSphere.compound, cSphere.atom.getPoint());
            CompoundSphere cSphereNew = new CompoundSphere(cSphere.compound, cSphere.atom, radius, cSphere.sSets);
            compoundArray[i] = cSphereNew;
        }
        atomList2.clear();
        compoundPairList.clear();
        try {
            for (int i = 0; i < compoundArray.length; i++) {
                CompoundSphere cSphere1 = compoundArray[i];
                Point3 pt1 = cSphere1.atom.getPoint();
                int jLast = compoundArray.length - 1;
                if (deltaEnd >= 0) {
                    jLast = i + deltaEnd;
                    if (jLast >= compoundArray.length) {
                        jLast = compoundArray.length - 1;
                    }
                }
                for (int j = i + deltaStart; j <= jLast; j++) {
                    if (i == j) {
                        CompoundPair cPair = new CompoundPair(cSphere1, cSphere1);
                        compoundPairList.add(cPair);
                        for (int iAtom = 0; iAtom < cSphere1.sSets.size(); iAtom++) {
                            SpatialSet spSet1 = cSphere1.sSets.get(iAtom);
                            Atom atom1 = spSet1.atom;

                            for (int jAtom = iAtom + 1; jAtom < cSphere1.sSets.size(); jAtom++) {
                                SpatialSet spSet2 = cSphere1.sSets.get(jAtom);
                                Atom atom2 = spSet2.atom;
                                Atom atom2RotParent = atom2.rotGroup != null ? atom2.rotGroup.parent : null;
                                if (AtomEnergyProp.interact(atom1, atom2) && (atom1 != atom2.rotGroup)
                                        && (atom1.rotGroup != atom2.rotGroup) && (atom1 != atom2RotParent)) {
                                    AtomPair atomPair = new AtomPair(atom1, atom2, hardSphere, includeH, shrinkValue, shrinkHValue,
                                            forceWeight.getRepel());
                                    cPair.atomPairs.add(atomPair);
                                }
                            }
                        }
                    } else {
                        CompoundSphere cSphere2 = compoundArray[j];
                        CompoundPair cPair = new CompoundPair(cSphere1, cSphere2);
                        compoundPairList.add(cPair);
                        for (SpatialSet spSet1 : cSphere1.sSets) {
                            Atom atom1 = spSet1.atom;
                            for (SpatialSet spSet2 : cSphere2.sSets) {
                                Atom atom2 = spSet2.atom;
                                boolean ok = AtomEnergyProp.interact(atom1, atom2);
                                if (ok) {
                                    if ((i + 1) == j) {
                                        ok = (atom1 != atom2.rotGroup) && (atom1.rotGroup != atom2.rotGroup)
                                                && (atom1 != atom2.rotGroup.parent);
                                    } else if ((i == 0) && ((j + 1) == compoundArray.length)) {
                                        ok = (atom2 != atom1.rotGroup) && (atom1.rotGroup != atom2.rotGroup)
                                                && ((atom1.rotGroup == null) || (atom2 != atom1.rotGroup.parent));
                                    }
                                }
                                if (ok) {
                                    AtomPair atomPair = new AtomPair(atom1, atom2, hardSphere, includeH, shrinkValue, shrinkHValue,
                                            forceWeight.getRepel());
                                    cPair.atomPairs.add(atomPair);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }
    }
}
