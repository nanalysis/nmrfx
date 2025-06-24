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
package org.nmrfx.chemistry;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.chemistry.constraints.AngleConstraint;
import org.nmrfx.chemistry.constraints.DistanceConstraint;
import org.nmrfx.chemistry.io.AtomParser;
import org.nmrfx.chemistry.relax.*;
import org.nmrfx.utils.TableItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.vecmath.Point2d;
import java.util.*;

@PluginAPI({"residuegen", "ring"})
public class Atom implements IAtom, Comparable<Atom>, TableItem {

    private static final Logger log = LoggerFactory.getLogger(Atom.class);
    private static final String POINT_NULL_MSG_PREFIX = "Point null: ";
    private String type = "";
    private AtomEnergyProp atomEnergyProp = null;
    AtomProperty atomProperty = null;

    public AtomEnergyProp getAtomEnergyProp() {
        return atomEnergyProp;
    }

    public void setAtomEnergyProp(AtomEnergyProp atomEnergyProp) {
        this.atomEnergyProp = atomEnergyProp;
    }

    public enum ATOMFLAGS {
        VISITED(0),
        AROMATIC(1),
        RESONANT(2),
        AMIDE(3),
        RING(4),
        RNABASE(5);
        final int index;

        ATOMFLAGS(int index) {
            this.index = index;

        }

        public int getIndex() {
            return index;
        }

        public void setValue(Atom atom, boolean value) {
            atom.setFlag(index, value);
        }
    }

    public static final int SELECT = 0;
    public static final int DISPLAY = 1;
    public static final int SUPER = 2;
    public static final int LABEL = 2;
    public static final int VISITED = ATOMFLAGS.VISITED.index;
    public static final int AROMATIC = ATOMFLAGS.AROMATIC.index;
    public static final int RESONANT = ATOMFLAGS.RESONANT.index;
    public static final int AMIDE = ATOMFLAGS.AMIDE.index;
    public static final int RING = ATOMFLAGS.RING.index;
    public static final int RNABASE = ATOMFLAGS.RNABASE.index;
    static final public double NULL_PPM = -9990.0;
    protected static int lastAtom = 0;
    public int iAtom = 1;
    public int eAtom = -1;
    public int aAtom = 1;
    protected int origIndex = 0;
    public Atom parent = null;
    public String name;
    public String label = "";
    public Entity entity = null;
    public List<Bond> bonds;
    public double mass = 0.0;
    private int stereo = 0;
    public byte nPiBonds = 0;
    public byte nonHydrogens = 0;
    public byte hydrogens = 0;
    public float charge = 0.0f;
    public float fcharge = 0.0f;
    public float bondLength = 1.0f;
    public float bndSin = 1.0f;
    public float bndCos = 1.0f;
    public float bndSinNR = 1.0f;
    public float bndCosNR = 1.0f;
    public float valanceAngle = (float) (109.4 * Math.PI / 180.0);
    public float dihedralAngle = (float) (120.0 * Math.PI / 180.0);
    public String stereoStr = null;
    public float radius = 0.9f;
    public int aNum = 0;
    public String forceFieldCode = null;
    public float value = 0.0f;
    private boolean active = true;
    public ArrayList<AtomEquivalency> equivAtoms = null;

    private Point3 flatPoint;
    public SpatialSet spatialSet;
    AtomResonance resonance = null;
    public int irpIndex = 0;
    public int rotUnit = -1;
    public Atom rotGroup = null;
    public boolean rotActive = true;
    public int canonValue = 0;
    public Atom[] branchAtoms = new Atom[0];
    final boolean[] flags = new boolean[ATOMFLAGS.values().length];
    Optional<Map<String, Object>> properties = Optional.empty();
    public Atom daughterAtom = null;
    private Map<RelaxationSet, RelaxationData> relaxData = new HashMap<>();
    private Map<OrderParSet, OrderPar> orderPars = new HashMap<>();
    private Map<String, SpectralDensity> spectralDensities = new HashMap<>();

    public Atom(String name) {
        this.name = name;
    }

    public Atom(AtomParser atomParse) {
        this(atomParse.atomName);
        spatialSet = new SpatialSet(this);
        initialize(atomParse);
    }

    private Atom(String name, AtomEnergyProp atomEnergyProp) {
        this(name);
        spatialSet = new SpatialSet(this);
        bonds = new ArrayList<>(2);
        iAtom = lastAtom++;
        origIndex = iAtom;
        this.atomEnergyProp = atomEnergyProp;
        if (atomEnergyProp == null) {
            aNum = 0;
            type = "XX";
        } else {
            aNum = atomEnergyProp.getAtomNumber();
            type = atomEnergyProp.getName();
        }
        atomProperty = AtomProperty.get(aNum);
        radius = atomProperty.getRadius();

        setColorByType();

    }

    public static Atom genAtomWithType(String name, String aType) {
        AtomEnergyProp atomEnergyProp = AtomEnergyProp.get(aType);
        return new Atom(name, atomEnergyProp);
    }

    public static Atom genAtomWithElement(String name, String aType) {
        int aNum = AtomProperty.getElementNumber(aType);
        AtomEnergyProp eProp = AtomEnergyProp.getDefault(aNum);
        return new Atom(name, eProp);
    }

    public static Atom genAtomWithElement(String name, int aNum) {
        AtomEnergyProp eProp = AtomEnergyProp.getDefault(aNum);
        return new Atom(name, eProp);
    }

    @Override
    public void setType(String name) {
        type = name;
    }

    @Override
    public String getType() {
        return type;
    }

    protected final void initialize(AtomParser atomParse) {
        name = atomParse.atomName;
        if (!atomParse.elemName.isEmpty()) {
            aNum = getElementNumber(atomParse.elemName);
        } else {
            int len = name.length();

            while ((len > 0)
                    && ((aNum = getElementNumber(name.substring(0, len))) == 0)) {
                len--;
            }
        }

        atomProperty = AtomProperty.get(aNum);
        radius = atomProperty.getRadius();

        setColorByType();
        charge = (float) atomParse.charge;
        bonds = new ArrayList<>(2);
        iAtom = lastAtom;
        origIndex = iAtom;
        lastAtom++;
        setAtomTypeFromNumber();

    }

    public void setEnergyProp() {
        this.atomEnergyProp = AtomEnergyProp.getDefault(aNum);
        if (atomEnergyProp == null) {
            aNum = 0;
            type = "XX";
        } else {
            aNum = atomEnergyProp.getAtomNumber();
            type = atomEnergyProp.getName();
        }

    }

    void setAtomTypeFromNumber() {
        /*
          setAtomTypeFromNumber sets a starting atomType for an initialized
          atom. The type set may not be the most appropriate to use later on in
          calculating repulsion energies but allows for all atoms to contribute
          to repulsive interactions.
         */
        switch (aNum) {
            case 1:
                setType("H");
                break;
            case 6:
                setType("C3");
                break;
            case 7:
                setType("N'");
                break;
            case 16:
                setType("S");
                break;
            case 15:
                setType("P");
                break;
            case 8:
                setType("O");
                break;
            default:
                setType("C");
        }

    }

    public void setAtomicNumber(int num) {
        aNum = num;
        atomProperty = AtomProperty.get(num);
        setColorByType();
    }

    public void setAtomicNumber(String name) {
        atomProperty = AtomProperty.get(name);
        aNum = atomProperty.getElementNumber();
        setColorByType();
    }

    public final void setColorByType() {
        setRed(atomProperty.getRed());
        setGreen(atomProperty.getGreen());
        setBlue(atomProperty.getBlue());
    }

    public void setRed(float red) {
        spatialSet.red = red;
    }

    public void setGreen(float green) {
        spatialSet.green = green;
    }

    public void setBlue(float blue) {
        spatialSet.blue = blue;
    }

    @Override
    public Atom add(String name, String elementName, Order order) {
        Atom newAtom = Atom.genAtomWithElement(name, elementName);
        newAtom.parent = this;
        if (entity != null) {
            Compound compound = (Compound) entity;
            compound.addAtom(this, newAtom);
            addBond(this, newAtom, order, 0, false);
        }
        return newAtom;
    }

    @Override
    public int compareTo(Atom atom) {
        return Atom.compare(this, atom);
    }

    public static int compare(Atom atom1, Atom atom2) {
        if (atom1 == atom2) {
            return 0;
        }
        int result = 0;

        MoleculeBase mol1 = atom1.getEntity().molecule;
        MoleculeBase mol2 = atom2.getEntity().molecule;
        if ((mol1 != null) && (mol2 != null) && (mol1 != mol2)) {
            result = mol1.getName().compareTo(mol2.getName());
        }
        if (result == 0) {
            int entityID1 = atom1.getTopEntity().entityID;
            int entityID2 = atom2.getTopEntity().entityID;
            result = Integer.compare(entityID1, entityID2);
            if (result == 0) {
                entityID1 = atom1.getResidueNumber();
                entityID2 = atom2.getResidueNumber();
                result = Integer.compare(entityID1, entityID2);
                if (result == 0) {
                    result = Integer.compare(atom1.getIndex(), atom2.getIndex());
                }
            }
        }
        return result;
    }

    public void changed() {
        if (entity != null) {
            entity.changed(this);
        }
    }

    public void remove() {
        remove(false);
    }

    public void remove(final boolean record) {
        removeBonds();
        if (entity != null) {
            Compound compound = (Compound) entity;
            compound.removeAtom(this);
        }
        if (record) {
            if (entity instanceof Residue residue) {
                Polymer polymer = residue.polymer;

                AtomSpecifier atomSp = new AtomSpecifier(residue.getNumber(), residue.getName(), getName());
                polymer.deletedAtoms.add(atomSp);
            }
        }
    }

    public void removeBonds() {
        List<Atom> connected = getConnected();
        for (Atom item : connected) {
            item.removeBondTo(this);
        }
    }

    public void removeBondTo(Atom atom) {
        List<Bond> newBonds = new ArrayList<>(2);
        for (Bond bond : bonds) {
            Atom atomB = bond.begin;
            Atom atomE = bond.end;
            if ((atomB != atom) && (atomE != atom)) {
                newBonds.add(bond);
            } else {
                atom.entity.removeBond(bond);
            }
        }
        bonds = newBonds;
    }

    public void addSpectralDensity(String name, SpectralDensity data) {
        spectralDensities.put(name, data);
    }

    public Map<String, SpectralDensity> getSpectralDensity() {
        return spectralDensities;
    }

    public SpectralDensity getSpectralDensity(String ID) {
        return spectralDensities.get(ID);
    }

    public void addOrderPar(OrderParSet orderParSet, OrderPar data) {
        orderPars.put(orderParSet, data);
    }

    public Map<OrderParSet, OrderPar> getOrderPars() {
        return orderPars;
    }

    public void addRelaxationData(RelaxationSet relaxationSet, RelaxationData data) {
        relaxData.put(relaxationSet, data);
    }

    public Map<RelaxationSet, RelaxationData> getRelaxationData() {
        return relaxData;
    }

    public RelaxationData getRelaxationData(RelaxationSet relaxationSet) {
        return relaxData.get(relaxationSet);
    }

    public List<Atom> getConnected() {
        List<Atom> connected = new ArrayList<>(4);
        for (Bond bond : bonds) {
            Atom atomB = bond.begin;
            Atom atomE = bond.end;

            if (atomB == this) {
                connected.add(atomE);
            } else if (atomE == this) {
                connected.add(atomB);
            }
        }

        return connected;
    }

    public Atom[] getPlaneAtoms() {
        Atom[] planeAtoms = new Atom[2];
        List<Atom> connected = getConnected();
        int j = 0;
        for (Atom atom : connected) {
            if (atom.getFlag(RING)) {
                planeAtoms[j] = atom;
                j++;
                if (j == 2) {
                    break;
                }
            }
        }
        if (j == 2) {
            return planeAtoms;
        } else {
            return null;

        }
    }

    @Override
    public void setFlag(int flag, boolean state) throws IllegalArgumentException {
        if (flag > flags.length) {
            throw new IllegalArgumentException("Invalid flag");
        }
        flags[flag] = state;
    }

    @Override
    public boolean getFlag(int flag) throws IllegalArgumentException {
        if (flag > flags.length) {
            throw new IllegalArgumentException("Invalid flag");
        }
        return flags[flag];
    }

    public void setActive(boolean state) {
        active = state;
    }

    public boolean isActive() {
        return active;
    }

    public Optional<Bond> getBond(Atom atom) {
        Optional<Bond> result = Optional.empty();
        for (Bond bond : bonds) {
            Atom atomB = bond.begin;
            Atom atomE = bond.end;

            if (atomB == this) {
                if (atomE == atom) {
                    result = Optional.of(bond);
                    break;
                }
            } else if (atomE == this) {
                if (atomB == atom) {
                    result = Optional.of(bond);
                    break;
                }
            }
        }
        return result;
    }

    public boolean isBonded(Atom atom) {
        boolean bonded = false;
        for (Bond bond : bonds) {
            Atom atomB = bond.begin;
            Atom atomE = bond.end;

            if (atomB == this) {
                if (atomE == atom) {
                    bonded = true;
                    break;
                }
            } else if (atomE == this) {
                if (atomB == atom) {
                    bonded = true;
                    break;
                }
            }
        }
        return bonded;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public int getAtomicNumber() {
        return aNum;
    }

    @Override
    public String toString() {
        return getFullName();
    }

    public String getName() {
        return name;
    }

    public int getIndex() {
        return iAtom;
    }

    public int getTreeIndex() {
        return iAtom;
    }

    public void setTreeIndex(int index) {
        iAtom = index;
    }

    public String getShortName() {
        if (entity instanceof Residue) {
            return ((Residue) entity).number + "." + name;
        } else {
            return name;
        }
    }

    public Entity getEntity() {
        return entity;
    }

    public Entity getTopEntity() {
        if (entity instanceof Residue) {
            return ((Residue) entity).polymer;
        } else {
            return entity;
        }
    }

    public String getFullName() {
        if (entity instanceof Residue) {
            Polymer polymer = ((Residue) entity).polymer;
            String id = String.valueOf(polymer.getCoordSet().getID());
            return id + ":" + ((Residue) entity).number + "." + name;
        } else if (entity instanceof Compound compound) {
            String id = String.valueOf(compound.getIDNum());
            String seqCode = compound.getNumber();
            return id + ":" + seqCode + "." + name;
        } else {
            return entity.name + ":." + name;
        }
    }

    public int getResidueNumber() {
        Integer result = 0;
        if ((entity != null) && (entity instanceof Compound)) {
            result = ((Compound) entity).getResNum();
            if (result == null) {
                result = 0;
            }
        }
        return result;
    }

    public String getResidueName() {
        String result = "";
        if ((entity != null) && (entity instanceof Compound)) {
            result = entity.getName();
        }
        return result;
    }

    public String getPolymerName() {
        final String result;
        if (entity instanceof Residue) {
            result = ((Residue) entity).polymer.getName();
        } else {
            result = entity.getName();
        }
        return result;
    }

    public void addBond(Bond bond) {
        addBond(bond, -1);
    }

    public void addBond(Bond bond, int position) {
        boolean alreadyBonded = false;
        if (bond.begin == this) {
            alreadyBonded = isBonded(bond.end);
        } else if (bond.end == this) {
            alreadyBonded = isBonded(bond.begin);
        }
        if (!alreadyBonded) {
            if (position == -1) {
                bonds.add(bond);
            } else {
                bonds.add(position, bond);
            }
        }
    }

    public void addCoords(double x, double y, double z,
                          double occupancy, double bFactor) throws InvalidMoleculeException {
        spatialSet.addCoords(x, y, z, occupancy, bFactor);
    }

    public SpatialSet getSpatialSet() {
        return spatialSet;
    }

    public void setResonance(AtomResonance resonance) {
        AtomResonance current = this.resonance;
        if ((resonance == null) && (current != null)) {
            current.setAtom(null);
        }
        this.resonance = resonance;
        if (resonance != null) {
            resonance.setAtom(this);
        }
    }

    public AtomResonance getResonance() {
        return resonance;
    }

    public void setSelected(int value) {
        spatialSet.selected = value;
    }

    public int getSelected() {
        return spatialSet.getSelected();
    }

    public void setDisplayStatus(int value) {
        spatialSet.displayStatus = value;
    }

    public void setLabelStatus(int value) {
        spatialSet.labelStatus = value;
    }

    public void setColor(float red, float green, float blue) {
        spatialSet.red = red;
        spatialSet.green = green;
        spatialSet.blue = blue;
    }

    public float getRed() {
        return spatialSet.red;
    }

    public float getGreen() {
        return spatialSet.green;
    }

    public float getBlue() {
        return spatialSet.blue;
    }

    public Point3 getPoint() {
        return spatialSet.getPoint();
    }

    public Point3 getPoint(int i) {
        return spatialSet.getPoint(i);
    }

    public List<String> dumpCoords() {
        List<String> list = new ArrayList<>();
        list.add(spatialSet.getFullName());

        for (int i = 0; i < spatialSet.getPointCount(); i++) {
            if (spatialSet.getPoint(i) != null) {
                list.add(String.valueOf(i));

                Point3 p3 = spatialSet.getPoint(i);
                list.add(String.valueOf(p3.getX()));
                list.add(String.valueOf(p3.getY()));
                list.add(String.valueOf(p3.getZ()));
            }
        }
        return list;
    }

    public Point3 getFlatPoint(int i) {
        return flatPoint == null  || i != 0 ? getPoint() : flatPoint;
    }

    public void setFlatPoint(Point3 pt) {
        flatPoint = new Point3(pt);
    }
    public void setPoint(int i, Point3 pt) {
        spatialSet.setPoint(i, pt);
    }

    public void setPoint(Point3 pt) {
        spatialSet.setPoint(pt);
    }

    public void setPointValidity(int i, boolean validity) {
        spatialSet.setPointValidity(i, validity);
    }

    public void setPointValidity(boolean validity) {
        spatialSet.setPointValidity(validity);
    }

    public boolean getPointValidity(int i) {
        boolean valid = false;
        if (spatialSet != null) {
            valid = spatialSet.getPointValidity(i);
        }
        return valid;
    }

    public boolean getPointValidity() {
        boolean valid = false;
        if (spatialSet != null) {
            valid = spatialSet.getPointValidity();
        }
        return valid;
    }

    public Double getPPM() {
        PPMv ppmV = getPPM(0);
        if ((ppmV != null) && ppmV.isValid()) {
            return ppmV.getValue();
        } else {
            return null;
        }
    }

    public PPMv getPPM(int i) {
        PPMv ppmV = null;
        if (spatialSet != null) {
            ppmV = spatialSet.getPPM(i);
        }

        return ppmV;
    }

    public Double getRefPPM() {
        PPMv ppmV = getRefPPM(0);
        if ((ppmV != null) && ppmV.isValid()) {
            return ppmV.getValue();
        } else {
            return null;
        }
    }

    public PPMv getRefPPM(int i) {
        PPMv ppmV = null;
        if (spatialSet != null) {
            ppmV = spatialSet.getRefPPM(i);
        }
        return ppmV;
    }

    public PPMv getPPMByMode(int i, boolean mode) {
        return mode ? getRefPPM(i) : getPPM(i);
    }

    public Double getSDevRefPPM() {
        PPMv ppmV = getRefPPM(0);
        if ((ppmV != null) && ppmV.isValid()) {
            return ppmV.getError();
        } else {
            return null;
        }
    }

    public Double getDeltaPPM(int iSet1, int iSet2, boolean ref1, boolean ref2) {
        PPMv ppm1 = getPPMByMode(iSet1, ref1);
        PPMv ppm2 = getPPMByMode(iSet2, ref2);
        Double delta;
        if ((ppm1 != null) && ppm1.isValid() && (ppm2 != null) && ppm2.isValid()) {
            delta = (ppm1.getValue() - ppm2.getValue()) / ppm2.getError();
            delta = Math.round(delta * 100.0) / 100.0;
        } else {
            delta = null;
        }
        return delta;
    }

    public void setPPM(double value) {
        spatialSet.setPPM(0, value, false);
    }

    public void setPPM(int i, double value) {
        spatialSet.setPPM(i, value, false);
    }

    public void setPPMError(int i, double value) {
        spatialSet.setPPM(i, value, true);
    }

    public void setRefPPM(double value) {
        spatialSet.setRefPPM(0, value);
    }

    public void setRefPPM(int i, double value) {
        spatialSet.setRefPPM(i, value);
    }

    public void setRefError(double value) {
        spatialSet.setRefError(0, value);
    }

    public void setRefError(int i, double value) {
        spatialSet.setRefError(i, value);
    }

    public void setPPMValidity(int i, boolean validity) {
        spatialSet.setPPMValidity(i, validity);
    }

    public float getBFactor() {
        return spatialSet.getBFactor();
    }

    public void setBFactor(float bfactor) {
        spatialSet.setBFactor(bfactor);
    }

    public float getOccupancy() {
        return spatialSet.getOccupancy();
    }

    public void setOccupancy(float occupancy) {
        spatialSet.setOccupancy(occupancy);
    }

    public float getOrder() {
        return spatialSet.getOrder();
    }

    public void setOrder(float order) {
        spatialSet.setOrder(order);
    }

    public void setProperty(int propIndex) {
        spatialSet.setProperty(propIndex);
    }

    public void unsetProperty(int propIndex) {
        spatialSet.unsetProperty(propIndex);
    }

    public boolean getProperty(int propIndex) {
        return spatialSet.getProperty(propIndex);
    }

    public void setDihedral(double value) {
        dihedralAngle = (float) Math.toRadians(value);
    }

    public double getDihedral() {
        return Math.toDegrees(dihedralAngle);
    }

    public static double calcDistance(Point3 pt1, Point3 pt2) {
        double x;
        double y;
        double z;
        x = pt1.getX() - pt2.getX();
        y = pt1.getY() - pt2.getY();
        z = pt1.getZ() - pt2.getZ();

        return (Math.sqrt((x * x) + (y * y) + (z * z)));
    }

    public double calcDistance(Atom atom2) {
        Point3 pt1 = spatialSet.getPoint();
        Point3 pt2 = atom2.spatialSet.getPoint();
        double x;
        double y;
        double z;
        x = pt1.getX() - pt2.getX();
        y = pt1.getY() - pt2.getY();
        z = pt1.getZ() - pt2.getZ();

        return (Math.sqrt((x * x) + (y * y) + (z * z)));
    }

    public static double calcAngle(Point3 pt1, Point3 pt2, Point3 pt3) {
        double x;
        double y;
        double z;
        x = pt1.getX() - pt2.getX();
        y = pt1.getY() - pt2.getY();
        z = pt1.getZ() - pt2.getZ();
        Vector3D v12 = new Vector3D(x, y, z);
        x = pt3.getX() - pt2.getX();
        y = pt3.getY() - pt2.getY();
        z = pt3.getZ() - pt2.getZ();
        Vector3D v32 = new Vector3D(x, y, z);
        return Vector3D.angle(v12, v32);
    }

    public static double volume(Vector3D a, Vector3D b, Vector3D c, Vector3D d) {
        Vector3D i = a.subtract(d);
        Vector3D j = b.subtract(d);
        Vector3D k = c.subtract(d);
        // triple product
        return Vector3D.dotProduct(i, Vector3D.crossProduct(j, k));
    }

    public static double calcDihedral(Atom[] atoms) {
        return calcDihedral(atoms, 0);
    }

    public static double calcDihedral(Atom[] atoms, int structureNum) {
        SpatialSet[] spSets = new SpatialSet[4];
        Point3[] pts = new Point3[4];
        int i = 0;
        for (Atom atom : atoms) {
            spSets[i] = atom.spatialSet;
            pts[i] = spSets[i].getPoint(structureNum);
            if (pts[i] == null) {
                throw new IllegalArgumentException("No coordinates for atom " + atom.getFullName());
            }
            i++;
        }
        return calcDihedral(pts[0], pts[1], pts[2], pts[3]);
    }

    public static double calcDihedral(Point3 pt1, Point3 pt2, Point3 pt3, Point3 pt4) {
        Vector3D a = new Vector3D(pt1.getX(), pt1.getY(), pt1.getZ());
        Vector3D b = new Vector3D(pt2.getX(), pt2.getY(), pt2.getZ());
        Vector3D c = new Vector3D(pt3.getX(), pt3.getY(), pt3.getZ());
        Vector3D d = new Vector3D(pt4.getX(), pt4.getY(), pt4.getZ());

        double d12 = Vector3D.distance(a, b);
        double sd13 = Vector3D.distanceSq(a, c);
        double sd14 = Vector3D.distanceSq(a, d);
        double sd23 = Vector3D.distanceSq(b, c);
        double sd24 = Vector3D.distanceSq(b, d);
        double d34 = Vector3D.distance(c, d);
        double ang123 = Vector3D.angle(a.subtract(b), c.subtract(b));
        double ang234 = Vector3D.angle(b.subtract(c), d.subtract(c));
        double cosine = (sd13 - sd14 + sd24 - sd23 + 2.0 * d12 * d34 * Math.cos(ang123) * Math.cos(ang234))
                / (2.0 * d12 * d34 * Math.sin(ang123) * Math.sin(ang234));

        double volume = volume(a, b, c, d);

        double sgn = (volume < 0.0) ? 1.0 : -1.0;
        final double angle;
        if (cosine > 1.0) {
            angle = 0.0;
        } else if (cosine < -1.0) {
            angle = Math.PI;
        } else {
            angle = sgn * Math.acos(cosine);
        }
        return (angle);

    }

    public static double calcWeightedDistance(SpatialSetGroup spg1, SpatialSetGroup spg2, int iStruct, double expNum, final boolean throwNullPoint, final boolean sumAvg) {
        double x;
        double y;
        double z;
        double sum = 0.0;
        int n = 0;
        double distance;
        Set<SpatialSet> spSet1 = spg1.getSpSets();
        Set<SpatialSet> spSet2 = spg2.getSpSets();
        if ((spSet1.size() == 1) && (spSet2.size() == 1)) {
            Point3 pt1 = spg1.getSpatialSet().getPoint(iStruct);
            if (pt1 == null) {
                if (throwNullPoint) {
                    throw new IllegalArgumentException(POINT_NULL_MSG_PREFIX + spg1.getSpatialSet().getFullName());
                } else {
                    log.warn("{}{}", POINT_NULL_MSG_PREFIX, spg1.getSpatialSet().getFullName());
                    return 0.0;
                }
            }
            Point3 pt2 = spg2.getSpatialSet().getPoint(iStruct);
            if (pt2 == null) {
                if (throwNullPoint) {
                    throw new IllegalArgumentException(POINT_NULL_MSG_PREFIX + spg2.getSpatialSet().getFullName());
                } else {
                    log.warn("{}{}", POINT_NULL_MSG_PREFIX, spg2.getSpatialSet().getFullName());
                    return 0.0;
                }
            }
            distance = calcDistance(pt1, pt2);

        } else {
            double minDis = Double.MAX_VALUE;
            for (SpatialSet sp1 : spSet1) {
                Point3 pt1 = sp1.getPoint(iStruct);
                if (pt1 == null) {
                    if (throwNullPoint) {
                        throw new IllegalArgumentException(POINT_NULL_MSG_PREFIX + sp1.atom.getFullName());
                    } else {
                        log.warn("{}{}", POINT_NULL_MSG_PREFIX, sp1.atom.getFullName());
                        return 0.0;
                    }
                }
                for (SpatialSet sp2 : spSet2) {
                    Point3 pt2 = sp2.getPoint(iStruct);
                    if (pt2 == null) {
                        if (throwNullPoint) {
                            throw new IllegalArgumentException(POINT_NULL_MSG_PREFIX + sp2.atom.getFullName());
                        } else {
                            log.warn("{}{}", POINT_NULL_MSG_PREFIX, sp2.atom.getFullName());
                            return 0.0;
                        }
                    }
                    x = pt1.getX() - pt2.getX();
                    y = pt1.getY() - pt2.getY();
                    z = pt1.getZ() - pt2.getZ();
                    double dis = (Math.sqrt((x * x) + (y * y) + (z * z)));
                    if (dis < minDis) {
                        minDis = dis;
                    }
                    sum += Math.pow(dis, -expNum);
                    n++;
                }
            }
            int nMonomers = 1;
            if (!sumAvg) {
                nMonomers = n;
            }
            distance = Math.pow((sum / nMonomers), -1.0 / expNum);
        }
        return distance;
    }

    public static void getDistances(SpatialSetGroup spg1, SpatialSetGroup spg2, int iStruct, List<Double> dArray) {
        double x;
        double y;
        double z;
        int n = 0;
        Set<SpatialSet> spSet1 = spg1.getSpSets();
        Set<SpatialSet> spSet2 = spg2.getSpSets();
        if ((spSet1.size() == 1) && (spSet2.size() == 1)) {
            Point3 pt1 = spg1.getSpatialSet().getPoint(iStruct);
            if (pt1 == null) {
                log.warn("{}{}", POINT_NULL_MSG_PREFIX, spg1.getSpatialSet().getFullName());
                return;
            }
            Point3 pt2 = spg2.getSpatialSet().getPoint(iStruct);
            if (pt2 == null) {
                log.warn("{}{}", POINT_NULL_MSG_PREFIX, spg2.getSpatialSet().getFullName());
                return;
            }
            double distance = calcDistance(pt1, pt2);
            dArray.add(distance);

        } else {
            for (SpatialSet sp1 : spSet1) {
                Point3 pt1 = sp1.getPoint(iStruct);
                if (pt1 == null) {
                    log.warn("{}{}", POINT_NULL_MSG_PREFIX, sp1.atom.getFullName());
                    return;
                }
                for (SpatialSet sp2 : spSet2) {
                    Point3 pt2 = sp2.getPoint(iStruct);
                    if (pt2 == null) {
                        log.warn("{}{}", POINT_NULL_MSG_PREFIX, sp2.atom.getFullName());
                        return;
                    }
                    x = pt1.getX() - pt2.getX();
                    y = pt1.getY() - pt2.getY();
                    z = pt1.getZ() - pt2.getZ();
                    double dis = (Math.sqrt((x * x) + (y * y) + (z * z)));
                    dArray.add(dis);
                }
            }
        }

    }

    public static Point3 avgAtom(List<SpatialSet> selected) {
        return avgAtom(selected, 0);
    }

    public static Point3 avgAtom(List<SpatialSet> selected, int structureNum) {
        int i;
        Vector3D pt;
        Vector3D pt1 = new Vector3D(0.0, 0.0, 0.0);
        int nPoints = 0;

        for (i = 0; i < selected.size(); i++) {
            SpatialSet spatialSet = selected.get(i);
            pt = spatialSet.getPoint(structureNum);

            if (pt != null) {
                nPoints++;
                pt1 = pt1.add(pt);
            }
        }

        if (nPoints == 0) {
            return (null);
        }
        pt1 = pt1.scalarMultiply(1.0 / nPoints);
        return new Point3(pt1);
    }

    public Point3 avgAcrossStructures(List<Integer> structureNums) {
        Vector3D pt;
        Vector3D pt1 = new Vector3D(0.0, 0.0, 0.0);
        int nPoints = 0;
        for (int structureNum : structureNums) {
            pt = this.getPoint(structureNum);

            if (pt != null) {
                nPoints++;
                pt1 = pt1.add(pt);
            }
        }
        if (nPoints == 0) {
            return null;
        }
        pt1 = pt1.scalarMultiply(1.0 / nPoints);
        return new Point3(pt1);
    }

    public double rmsAtom() {
        return rmsAtom(spatialSet);
    }

    public double rmsAtom(SpatialSet spatialSet) {
        int i;
        Point3 pt;
        Vector3D pt1 = new Point3(0.0, 0.0, 0.0);
        int nPoints = 0;
        if (entity == null) {
            log.warn("null entity for {}", getFullName());
            return 0.0;
        } else {
            MoleculeBase molecule = entity.molecule;
            if (molecule == null) {
                log.warn("null molecule for {}", getFullName());
                return 0.0;
            }

        }
        int[] structures = entity.molecule.getActiveStructures();
        for (int iStruct : structures) {
            pt = spatialSet.getPoint(iStruct);

            if (pt != null) {
                nPoints++;
                pt1 = pt1.add(pt);
            }
        }

        if (nPoints == 0) {
            return (0.0);
        }
        pt1 = pt1.scalarMultiply(1.0 / nPoints);

        double sumSq = 0.0;
        double delX;
        double delY;
        double delZ;

        for (int iStruct : structures) {
            pt = spatialSet.getPoint(iStruct);
            if (pt != null) {
                sumSq += Vector3D.distanceSq(pt, pt1);
            }
        }

        return (Math.sqrt(sumSq / nPoints));
    }

    public String xyzToString(int iStruct, int iAtom) {
        return xyzToString(spatialSet, iStruct, iAtom);
    }

    public String xyzToString(SpatialSet spatialSet,
                              int iStruct, int iAtom) {
        Point3 pt;
        pt = spatialSet.getPoint(iStruct);

        if (pt == null) {
            return null;
        }

        String result = "";
        if (entity instanceof Residue) {
            // what if iStruct < 0
            String strStruct;
            if (iStruct < 0) {
                strStruct = "\"" + ((Residue) entity).number + "\"";
            } else {
                strStruct = String.valueOf(iStruct);
            }
            result = String.format("%5d %3s %s %5s %6s %-5s %1s %8.3f %8.3f %8.3f",
                    iAtom, strStruct, ((Residue) entity).polymer.name, ((Residue) entity).number, entity.name,
                    name, name.charAt(0), pt.getX(), pt.getY(), pt.getZ());
        }

        return result;
    }

    public String ppmToString(int iStruct, int iAtom) {
        return ppmToString(spatialSet, iStruct, iAtom);
    }

    public String ppmToString(SpatialSet spatialSet,
                              int iStruct, int iAtom) {
        PPMv ppmv = spatialSet.getPPM(iStruct);

        if (ppmv == null) {
            return null;
        }
        StringBuilder sBuilder = new StringBuilder();
        char sepChar = '\t';
        if (entity instanceof Residue) {
            //"%5d %5s %6s %-5s %c %8.3f  .  %2d\n"
            //j, atoms[i].rnum, atoms[i].rname, atoms[i].aname, atoms[i].aname[0], NvGetAtomPPM (istPPM, i), AtmAmbig (i));
            // _Atom_shift_assign_ID
            sBuilder.append(iAtom);
            sBuilder.append(sepChar);

            // _Residue_author_seq_code
            sBuilder.append(((Residue) entity).number);
            sBuilder.append(sepChar);

            // _Residue_seq_code
            sBuilder.append(entity.entityID);
            sBuilder.append(sepChar);

            // _Residue_label
            sBuilder.append(entity.name);
            sBuilder.append(sepChar);

            // _Atom_name
            sBuilder.append(name);
            sBuilder.append(sepChar);

            // _Atom_type
            sBuilder.append(name.charAt(0));
            sBuilder.append(sepChar);

            // _Chem_shift_value
            sBuilder.append(ppmv.getValue());
            sBuilder.append(sepChar);

            // _Chem_shift_value_error
            sBuilder.append(ppmv.getError());
            sBuilder.append(sepChar);

            // _Chem_shift_value_ambiguity_code
            sBuilder.append(ppmv.getAmbigCode());
            sBuilder.append(sepChar);
        }

        return (sBuilder.toString());
    }

    /**
     * Change atom names to NEF format.
     *
     * @param atom Atom to format.
     * @return writename. String. The formatted atom name.
     */
    public static String formatNEFAtomName(Atom atom) {
        String writeName = atom.name;
        if (!atom.isMethyl()) {
            if (atom.getStereo() == 0) { //x or y changes
                Atom[] partners = atom.getPartners(1, 1);
                if (partners.length == 1) {
                    if (atom.getIndex() < partners[0].getIndex()) {
                        writeName = atom.name.substring(0, atom.name.length() - 1) + "x";
                    } else {
                        writeName = atom.name.substring(0, atom.name.length() - 1) + "y";
                    }
                }
            }
        } else {
            Atom parent = atom.getParent();
            Optional<Atom> methylCarbonPartner = parent.getMethylCarbonPartner();
            if (methylCarbonPartner.isPresent()) {
                if (atom.getStereo() == 0) {
                    if (atom.getParent().getIndex() < methylCarbonPartner.get().getIndex()) {
                        writeName = atom.name.substring(0, atom.name.length() - 2) + "x%";
                    } else {
                        writeName = atom.name.substring(0, atom.name.length() - 2) + "y%";
                    }
                } else {
                    writeName = atom.name.substring(0, atom.name.length() - 1) + "%";
                }
            } else {
                writeName = atom.name.substring(0, atom.name.length() - 1) + "%";
            }
        }
        return writeName;
    }

    /**
     * Converts chemical shift information to a String in NEF format.
     *
     * @param iStruct   int. Index of molecular structure.
     * @param iAtom     int. Index of atom.
     * @param collapse  boolean. Whether to collapse methyl/methylene atoms into
     *                  a single entry with a % in the atom name.
     * @param sameShift boolean indicating whether this has same shift as
     *                  partner
     * @return ppmToNEFString(spSet, iStruct, iAtom, collapse).
     */
    public String ppmToNEFString(int iStruct, int iAtom, int collapse,
                                 int sameShift) {
        return ppmToNEFString(spatialSet, iStruct, iAtom, collapse, sameShift);
    }

    /**
     * Converts chemical shift information to a String in NEF format.
     *
     * @param spatialSet SpatialSet of the molecule.
     * @param iStruct    int. Index of molecular structure.
     * @param iAtom      int. Index of atom.
     * @param collapse   boolean. Whether to collapse methyl/methylene atoms into
     * @param sameShift  boolean indicating whether this has same shift as
     *                   partner a single entry with a % in the atom name.
     * @return String in NEF format.
     */
    public String ppmToNEFString(SpatialSet spatialSet,
                                 int iStruct, int iAtom, int collapse, int sameShift) {
        //chemical shift
        PPMv ppmv = spatialSet.getPPM(iStruct);
        if (ppmv == null) {
            return null;
        }

        //atom name
        String writeName;
        if (isMethyl()) {
            int nameLen = name.length();
            switch (collapse) {
                case 1:
                    String xy = name.charAt(nameLen - 2) == '2' ? "y" : "x";
                    writeName = Util.getXYName(this) + "%";
                    break;
                case 2:
                    writeName = name.substring(0, nameLen - 2) + "%";
                    break;
                default:
                    writeName = name.substring(0, nameLen - 1) + "%";
                    break;
            }
        } else if (sameShift > 0) {
            int nameLen = name.length();
            writeName = name.substring(0, nameLen - sameShift) + "%";
        } else {
            if (collapse > 0) {
                writeName = Util.getXYName(this);
            } else {
                writeName = getName();
            }
        }

        char chainID = 'A';
        int seqCode = 1;
        String resName = entity.name;
        if (resName.length() > 3) {
            resName = resName.substring(0, 3);
        }

        if (entity instanceof Residue) {
            String polymerName = ((Residue) entity).polymer.getName();
            //  chain code
            chainID = polymerName.charAt(0);

            // sequence code
            seqCode = Integer.parseInt(((Residue) entity).getNumber());

        } else if (entity instanceof Compound) {
            //sequence code
            seqCode = Integer.parseInt(((Compound) entity).getNumber());

            //chain ID
            String chainCode = entity.getPropertyObject("chain").toString();
            chainID = chainCode.charAt(0);

        }
        // value
        double shift = ppmv.getValue();

        // value uncertainty
        double shiftErr = ppmv.getError();

        return String.format("         %-9s %-9d %-9s %-9s %-9.3f %-4.3f", chainID, seqCode, resName, writeName, shift, shiftErr);
    }

    /**
     * Converts distance information to a String in NEF format.
     *
     * @param index            int. Index of the line in the file.
     * @param aCollapse        boolean[]. Whether to collapse methyl/methylene atoms in
     *                         the distance pair into a single entry with a % in the atom name.
     * @param restraintID      int. Restraint ID number.
     * @param restraintComboID String. Restraint combination ID. Default is ".".
     * @param distPair         DistancePair. The DistancePair object for the
     *                         restraintID.
     * @param atom1            Atom. First atom in the AtomDistancePair object.
     * @param atom2            Atom. Second atom in the AtomDistancePair object.
     * @return String in NEF format.
     */
    public static String toNEFDistanceString(int index, int[] aCollapse, int restraintID, String restraintComboID, DistanceConstraint distPair, Atom atom1, Atom atom2) {
        Atom[] atoms = {atom1, atom2};

        StringBuilder sBuilder = new StringBuilder();
        //index
        sBuilder.append("         ");
        sBuilder.append(String.format("%-8d", index));

        //restraint ID
        sBuilder.append(String.format("%-8d", restraintID));

        //restraint combo ID
        sBuilder.append(String.format("%-8s", restraintComboID));

        String polymerName = "A";
        int seqCode = 1;
        String resName = "";
        for (int a = 0; a < atoms.length; a++) {
            Atom atom = atoms[a];
            // chain code 
            if (atom.entity instanceof Residue) {
                polymerName = ((Residue) atom.entity).polymer.getName();
            } else if (atom.entity instanceof Compound) {
                polymerName = atom.entity.getPropertyObject("chain").toString();
            }
            char chainID = polymerName.charAt(0);
            sBuilder.append(String.format("%-8s", chainID));

            // sequence code 
            if (atom.entity instanceof Residue) {
                seqCode = Integer.parseInt(((Residue) atom.entity).getNumber());
            } else if (atom.entity instanceof Compound) {
                seqCode = Integer.parseInt(((Compound) atom.entity).getNumber());
            }
            sBuilder.append(String.format("%-8d", seqCode));

            // residue name
            if (atom.entity instanceof Residue) {
                resName = atom.entity.name;
            } else if (atom.entity instanceof Compound) {
                resName = atom.entity.name;
            }
            if (resName.length() > 3) {
                resName = resName.substring(0, 3);
            }
            sBuilder.append(String.format("%-8s", resName));

            // atom name 
            int collapse = aCollapse[a];
            String writeName;
            if (atom.isMethyl()) {
                if (collapse == 2) {
                    writeName = atom.name.substring(0, atom.name.length() - collapse) + "%";
                } else {
                    writeName = formatNEFAtomName(atom);

                }
            } else {
                if (collapse > 0) {
                    writeName = atom.name.substring(0, atom.name.length() - collapse) + "%";
                } else {
                    writeName = formatNEFAtomName(atom);
                }
            }
            sBuilder.append(String.format("%-8s", writeName));
        }

        // weight
        double weight = distPair.getWeight();
        sBuilder.append(String.format("%-8.1f", weight));

        // target value
        double target = distPair.getTarget();
        sBuilder.append(String.format("%-8.3f", target));

        // target value uncertainty
        double targetErr = distPair.getTargetError();
        if (targetErr < 1.0e-6) {
            sBuilder.append(String.format("%-8s", "."));
        } else {
            sBuilder.append(String.format("%-8.3f", targetErr));
        }

        // lower limit
        double lower = distPair.getLower();
        sBuilder.append(String.format("%-8.3f", lower));

        // upper limit
        double upper = distPair.getUpper();
        sBuilder.append(String.format("%-8.3f", upper));

        return sBuilder.toString();
    }

    /**
     * Converts dihedral angle information into a String in NEF format.
     *
     * @param bound            AngleBoundary. The dihedral angle object.
     * @param atoms            Atom[]. List of atoms that form the dihedral angle.
     * @param iBound           int. Index of the dihedral angle.
     * @param restraintID      int. The restraint ID.
     * @param restraintComboID String. The restraint combination ID. Default is
     *                         ".".
     * @return String in NEF format.
     */
    public static String toNEFDihedralString(AngleConstraint bound, Atom[] atoms, int iBound, int restraintID, String restraintComboID) {

        StringBuilder sBuilder = new StringBuilder();

        //index
        sBuilder.append(String.format("%6d", iBound));

        //restraint ID
        sBuilder.append(String.format("%6d", restraintID));

        //restraint combo ID
        sBuilder.append(String.format("%6s", restraintComboID));

        String polymerName = "A";
        int seqCode = 1;
        String resName = "";
        for (Atom atom : atoms) {
            // chain code 
            if (atom.entity instanceof Residue) {
                polymerName = ((Residue) atom.entity).polymer.getName();
            } else if (atom.entity instanceof Compound) {
                polymerName = atom.entity.getPropertyObject("chain").toString();
            }
            char chainID = polymerName.charAt(0);
            sBuilder.append(String.format("%6s", chainID));

            // sequence code 
            if (atom.entity instanceof Residue) {
                seqCode = ((Residue) atom.entity).getResNum();
            } else if (atom.entity instanceof Compound) {
                seqCode = Integer.parseInt(((Compound) atom.entity).getNumber());
            }
            sBuilder.append(String.format("%6d", seqCode));

            // residue name 
            if (atom.entity instanceof Residue) {
                resName = atom.entity.name;
            } else if (atom.entity instanceof Compound) {
                resName = atom.entity.name;
            }
            if (resName.length() > 3) {
                resName = resName.substring(0, 3);
            }
            sBuilder.append(String.format("%6s", resName));

            // atom name 
            String aName = atom.name;
            sBuilder.append(String.format("%6s", aName));
        }

        // weight
        double weight = bound.getWeight();
        sBuilder.append(String.format("%6.2f", weight));

        // target value
        double target = bound.getTargetValue();
        sBuilder.append(String.format("%9.3f", target));

        // target value uncertainty
        double targetErr = bound.getTargetError();
        sBuilder.append(String.format("%9.3f", targetErr));

        // lower limit
        double lower = Math.toDegrees(bound.getLower());
        sBuilder.append(String.format("%9.3f", lower));

        // upper limit
        double upper = Math.toDegrees(bound.getUpper());
        sBuilder.append(String.format("%9.3f", upper));

        // name
        String name = bound.getName();
        if (name.isEmpty()) {
            name = ".";
        }
        sBuilder.append(String.format("%6s", name));

        return sBuilder.toString();
    }

    public String xyzToXMLString(int iStruct, int iAtom) {
        return xyzToXMLString(spatialSet, iStruct, iAtom);
    }

    public String xyzToXMLString(SpatialSet spatialSet,
                                 int iStruct, int iAtom) {
        StringBuilder result = new StringBuilder();
        Point3 pt;
        pt = spatialSet.getPoint(iStruct);

        if (pt == null) {
            return null;
        }

        result.append("<coord ");

        if (entity instanceof Residue) {
            //  "%5d %3d %s %5s %6s %-5s %c %8.3f %8.3f %8.3f\n"
            // _Atom_ID
            result.append(" _Atom_ID=\"").append(iAtom).append("\"");

            if (iStruct < 0) {
                result.append(" _Conformer_number=\"").append(((Residue) entity).number).append("\"");
            }

            result.append(" _Mol_system_component_name=\"").append(((Residue) entity).polymer.name).append("\"");
            result.append(" _Residue_seq_code=\"").append(((Residue) entity).number).append("\"");
            result.append(" _Residue_label=\"").append(entity.name).append("\"");
            result.append(" _Atom_name=\"").append(name).append("\"");
            result.append(" _Atom_type=\"").append(name.charAt(0)).append("\"");
            result.append(" >\n");
            result.append("    <_Atom_coord_x>").append(pt.getX()).append("</_Atom_coord_x>\n");
            result.append("    <_Atom_coord_y>").append(pt.getY()).append("</_Atom_coord_y>\n");
            result.append("    <_Atom_coord_z>").append(pt.getZ()).append("</_Atom_coord_z>\n");
            result.append("</coord>");
        }

        return (result.toString());
    }

    public String ppmToXMLString(int iStruct, int iAtom) {
        return ppmToXMLString(spatialSet, iStruct, iAtom);
    }

    public String ppmToXMLString(SpatialSet spatialSet,
                                 int iPPM, int iAtom) {
        StringBuilder result = new StringBuilder();

        PPMv ppmv = spatialSet.getPPM(iPPM);

        if (ppmv == null) {
            return null;
        }

        result.append("<Chem_shift ");

        if (entity instanceof Residue) {
            result.append(" _Atom_shift_assign_ID=\"").append(iAtom).append("\"");
            result.append(" _Residue_seq_code=\"").append(((Residue) entity).number).append("\"");
            result.append(" _Residue_label=\"").append(entity.name).append("\"");
            result.append(" _Atom_name=\"").append(name).append("\"");
            result.append(" _Atom_type=\"").append(name.charAt(0)).append("\"");
            result.append(" >\n");
            result.append("    <_Chem_shift_value>").append(ppmv.getValue()).append("</_Chem_shift_value>\n");
            result.append("    <_Chem_shift_value_error>").append(ppmv.getError()).append("</_Chem_shift_value_error>\n");
            result.append("    <_Chem_shift_ambiguity_code>").append(ppmv.getAmbigCode()).append("</_Chem_shift_ambiguity_code>\n");
        }

        result.append("</Chem_shift>");

        return (result.toString());
    }

    public Atom getParent() {
        if (parent != null) {
            return parent;
        } else if ((bonds == null) || bonds.isEmpty()) {
            return null;
        } else {
            for (Object bondObject : bonds) {
                Bond bond = (Bond) bondObject;
                //if ((bond.begin != null) && (bond.begin != this) && (bond.begin.aNum != 1) && (this.aNum == 1)) {
                if ((bond.begin != null) && (bond.begin != this) && (bond.begin.aNum != 1)) {
                    if (bond.begin.iAtom < iAtom) {
                        return bond.begin;
                    }
                    //} else if ((bond.end != null) && (bond.end != this) && (bond.end.aNum != 1) && (this.aNum == 1)) {
                } else if ((bond.end != null) && (bond.end != this) && (bond.end.aNum != 1)) {
                    if (bond.end.iAtom < iAtom) {
                        return bond.end;
                    }
                }
            }
            return null;
        }
    }

    public List<Atom> getChildren() {
        List<Atom> children = new ArrayList<>(4);

        for (Bond bond : bonds) {
            Atom atomB = bond.begin;
            Atom atomE = bond.end;

            if (atomB == this) {
                if (atomE.iAtom > iAtom) {
                    children.add(atomE);
                }
            } else if (atomE == this) {
                if (atomB.iAtom > iAtom) {
                    children.add(atomB);
                }
            }
        }

        return children;
    }

    public int getTotalBondOrder() {
        int totalOrder = 0;
        for (Bond bond : bonds) {
            if (bond.order.getOrderNum() < 5) {
                totalOrder += bond.order.getOrderNum();
            } else if ((bond.order.getOrderNum() == 8)) {
                totalOrder += 2;
            } else if ((bond.order.getOrderNum() == 7)) {
                totalOrder += 1;
            }
        }
        return totalOrder;

    }

    public static int calcBond(Atom atom1, Atom atom2, Order order) {
        return calcBond(atom1, atom2, order, 0);
    }

    public static int calcBond(Atom atom1, Atom atom2, Order order, int stereo) {
        int iRes;
        int jRes;
        Point3 pt1;
        Point3 pt2;
        Bond bond;
        double dis;

        if (atom1.entity != atom2.entity) {
            if (!(atom1.entity instanceof Residue)) {
                return 2;
            } else if (!(atom2.entity instanceof Residue)) {
                return 2;
            } else {
                iRes = Integer.parseInt(((Residue) atom1.entity).number);
                jRes = Integer.parseInt(((Residue) atom2.entity).number);

                if (Math.abs(iRes - jRes) > 1) {
                    return 2;
                }
            }
        }

        if (atom1.name.startsWith("H") && atom2.name.startsWith("H")) {
            return 1;
        }

        double dLim = 1.8;

        if (atom1.name.startsWith("H") && atom2.name.startsWith("H")) {
            return 1;
        }

        if (atom1.name.startsWith("S") || atom2.name.startsWith("S")) {
            dLim = 2.0;
        }

        if (atom1.name.startsWith("H") || atom2.name.startsWith("H")) {
            dLim = 1.2;
        }
        int anumA = atom1.getAtomicNumber();
        int anumB = atom2.getAtomicNumber();
        int totalOrder1 = atom1.getTotalBondOrder();
        int totalOrder2 = atom2.getTotalBondOrder();
        int nPossibleA = 2;
        switch (anumA) {
            case 6:
                nPossibleA = 4;
                break;
            case 7:
                nPossibleA = 3;
                break;
            case 8:
                nPossibleA = 2;
                break;
            default:
                break;
        }
        int nPossibleB = 2;
        if (anumB == 6) {
            nPossibleB = 4;
        } else if (anumB == 7) {
            nPossibleB = 3;
        } else if (anumA == 8) {
            nPossibleB = 2;
        }
        boolean doubleOK = true;
        if ((nPossibleA - totalOrder1) < 2) {
            doubleOK = false;
        }
        if ((nPossibleB - totalOrder2) < 2) {
            doubleOK = false;
        }

        if (anumA > anumB) {
            int hold = anumA;
            anumA = anumB;
            anumB = hold;
        }

        double dBondLim = 0.0;
        if ((anumA == 6) && (anumB == 6)) { // C C
            dBondLim = 1.4;
        } else if ((anumA == 6) && (anumB == 8)) { // C O
            dBondLim = 1.3;
        }

        pt1 = atom1.getPoint();
        pt2 = atom2.getPoint();

        if ((pt1 != null) && (pt2 != null)) {
            dis = Atom.calcDistance(pt1, pt2);
            if (dis < dLim) {
                bond = new Bond(atom1, atom2);
                if ((dis < dBondLim) && doubleOK) {
                    bond.order = Order.DOUBLE;
                } else {
                    bond.order = Order.SINGLE;
                }
                bond.stereo = stereo;

                atom1.addBond(bond);
                atom2.addBond(bond);
                atom1.entity.addBond(bond);

                return 0;
            }
        }

        return 1;
    }

    public static int addBond(Atom atom1, Atom atom2, Order order, final boolean record) {
        return (addBond(atom1, atom2, order, 0, record));
    }

    public static int addBond(Atom atom1, Atom atom2, Order order, int stereo, final boolean record) {

        if (atom1 != null && atom2 != null) {

            Bond bond;

            bond = new Bond(atom1, atom2);
            bond.order = order;
            bond.stereo = stereo;
            if (atom1.aNum == 1) {
                atom1.parent = atom2;
            } else if (atom2.aNum == 1) {
                atom2.parent = atom1;
            }

            atom1.addBond(bond);
            atom2.addBond(bond);

            atom1.entity.addBond(bond);
            if (record) {
                Entity entity1 = atom1.getEntity();
                Entity entity2 = atom2.getEntity();
                if (entity1 instanceof Residue) {
                    Residue residue1 = (Residue) entity1;
                    Polymer polymer = residue1.polymer;
                    Compound compound2 = (Compound) entity2;

                    AtomSpecifier atomSp1 = new AtomSpecifier(residue1.getNumber(), residue1.getName(), atom1.getName());
                    AtomSpecifier atomSp2 = new AtomSpecifier(compound2.getNumber(), compound2.getName(), atom2.getName());
                    BondSpecifier bondSp = new BondSpecifier(atomSp1, atomSp2, order);
                    polymer.addedBonds.add(bondSp);
                }
            }
        }

        return 0;
    }

    public static String getElementName(int eNum) {
        return AtomProperty.getElementName(eNum);
    }

    public String getElementName() {
        return AtomProperty.getElementName(aNum);
    }

    public static byte getElementNumber(String elemName) {
        return AtomProperty.getElementNumber(elemName);
    }

    public static void resetLastAtom() {
        lastAtom = 0;
    }

    public int getStereo() {
        return stereo;
    }

    public void setStereo(int stereo) {
        this.stereo = stereo;
    }

    public boolean getRotActive() {
        return rotActive;
    }

    public void setRotActive(boolean rotActive) {
        this.rotActive = rotActive;
    }

    public void setFormalCharge(float charge) {
        this.fcharge = charge;
    }

    public float getFormalCharge() {
        return fcharge;
    }

    public void setCharge(float charge) {
        this.charge = charge;
    }

    public float getCharge() {
        return charge;
    }

    public List<Object> getEquivalency() {
        if (!entity.hasEquivalentAtoms()) {
            MoleculeBase.findEquivalentAtoms(entity);
        }
        List<Object> list = new ArrayList<>();
        if ((equivAtoms != null) && !equivAtoms.isEmpty()) {
            for (AtomEquivalency aEquiv : equivAtoms) {
                List<String> list2 = new ArrayList<>();
                list.add("c");
                list.add(aEquiv.getIndex());
                list.add(aEquiv.getShell());

                for (int j = 0; j < aEquiv.getAtoms().size(); j++) {
                    Atom atom = aEquiv.getAtoms().get(j);

                    if (!atom.getName().equals(getName())) {
                        list2.add(atom.getName());
                    }
                }

                list.add(list2);
            }

        }
        return list;
    }

    public boolean isBackbone() {
        if (getTopEntity() instanceof Polymer polymer) {
            boolean isProtein = polymer.isPeptide();
            boolean isRNA = polymer.isRNA();
            if (isRNA) {
                return name.equals("O3'") || name.equals("P")
                        || name.equals("O5'") || name.equals("C5'")
                        || name.equals("C4'") || name.equals("C3'");
            } else if (isProtein) {
                return name.equals("N") || name.equals("CA") || name.equals("C");
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean isLinker() {
        if (getTopEntity() instanceof Polymer polymer) {
            boolean isProtein = polymer.isPeptide();
            boolean isRNA = polymer.isRNA();
            String fullName = name;
            Character nameBase = fullName.charAt(0);
            return nameBase.equals('X');
        } else {
            return false;
        }
    }

    public void setLinkerRotationActive(boolean state) {
        if (this.isLinker()) {
            this.rotActive = state;
        }
    }

    public boolean isFirstInMethyl() {
        boolean result = false;
        if (isMethyl()) {
            List<Atom> atoms = parent.getConnected();
            Atom firstAtom = null;
            for (Atom atom : atoms) {
                if (atom.getAtomicNumber() == 1) {
                    if (firstAtom == null) {
                        firstAtom = atom;
                    } else {
                        if (atom.name.compareToIgnoreCase(firstAtom.name) < 0) {
                            firstAtom = atom;
                        }
                    }
                }
            }
            result = this == firstAtom;
        }
        return result;
    }

    public boolean isMethyl() {
        if ((aNum != 1) || (parent == null)) {
            return false;
        } else {
            List<Atom> atoms = parent.getConnected();
            int nH = 0;
            for (Atom atom : atoms) {
                if (atom.getAtomicNumber() == 1) {
                    nH++;
                }
            }
            return nH == 3;
        }
    }

    public boolean isMethylCarbon() {
        boolean result = false;
        if (aNum == 6) {
            List<Atom> atoms = getConnected();
            int nH = 0;
            for (Atom atom : atoms) {
                if (atom.getAtomicNumber() == 1) {
                    nH++;
                }
            }
            result = nH == 3;
        }
        return result;
    }

    public Optional<Atom> getMethylCarbonPartner() {
        Optional<Atom> result = Optional.empty();
        if ((parent != null) && (aNum == 6)) {
            List<Atom> partners = parent.getConnected();
            int nMethyl = 0;
            Atom mPartner = null;
            for (Atom partner : partners) {
                if (partner.getAtomicNumber() == 6) {
                    if (partner.isMethylCarbon()) {
                        nMethyl++;
                        if (partner != this) {
                            mPartner = partner;
                        }
                    }
                }
            }
            if (nMethyl == 2) {
                result = Optional.of(mPartner);
            }
        }
        return result;
    }

    public Point3 getMethylCenter(int structNum) {
        Atom atomsParent = getParent();
        List<Atom> children = atomsParent.getChildren();
        Vector3D pt1 = children.get(0).getPoint(structNum);
        pt1 = pt1.add(children.get(1).getPoint(structNum));
        pt1 = pt1.add(children.get(2).getPoint(structNum));
        pt1 = pt1.scalarMultiply(1.0 / 3.0);

        return new Point3(pt1);
    }

    public Atom[] getPartners(int targetANum, int shells) {
        Atom[] result = new Atom[0];
        if (!entity.hasEquivalentAtoms()) {
            MoleculeBase.findEquivalentAtoms(entity);
        }

        if ((aNum == targetANum) && (equivAtoms != null) && (!equivAtoms.isEmpty())) {
            int nAtoms = 0;
            for (int i = 0; (i < equivAtoms.size()) && (i < shells); i++) {
                AtomEquivalency aEquiv = equivAtoms.get(i);
                nAtoms += aEquiv.getAtoms().size();
            }
            result = new Atom[nAtoms - 1];
            int j = 0;
            for (int i = 0; (i < equivAtoms.size()) && (i < shells); i++) {
                AtomEquivalency aEquiv = equivAtoms.get(i);
                for (Atom equivAtom : aEquiv.getAtoms()) {
                    if (!equivAtom.getName().equals(getName())) {
                        result[j++] = equivAtom;
                    }
                }
            }
        }
        return result;
    }

    public List<List<Atom>> getPartners(int targetANum) {
        List<List<Atom>> result = new ArrayList<>();
        if (!entity.hasEquivalentAtoms()) {
            MoleculeBase.findEquivalentAtoms(entity);
        }
        int shells = 2;
        if (((targetANum == -1) || (aNum == targetANum)) && (equivAtoms != null) && (!equivAtoms.isEmpty())) {
            for (int i = 0; (i < equivAtoms.size()) && (i < shells); i++) {
                AtomEquivalency aEquiv = equivAtoms.get(i);
                if (!aEquiv.getAtoms().isEmpty()) {
                    List<Atom> shellAtoms = new ArrayList<>();
                    result.add(shellAtoms);
                    for (Atom equivAtom : aEquiv.getAtoms()) {
                        if (!equivAtom.getName().equals(getName())) {
                            shellAtoms.add(equivAtom);
                        }
                    }
                }
            }
        }
        return result;
    }

    public boolean isMethylene() {
        if ((parent != null) && (aNum == 1)) {
            List<Atom> partners = parent.getConnected();
            int nH = 0;
            for (Atom partner : partners) {
                if (partner.getAtomicNumber() == 1) {
                    nH++;
                }
            }
            return nH == 2;
        } else {
            return false;
        }
    }

    public Optional<Atom> getMethylenePartner() {
        Optional<Atom> result = Optional.empty();
        if ((parent != null) && (aNum == 1)) {
            List<Atom> partners = parent.getConnected();
            int nH = 0;
            Atom mPartner = null;
            for (Atom partner : partners) {
                if (partner.getAtomicNumber() == 1) {
                    nH++;
                    if (partner != this) {
                        mPartner = partner;
                    }
                }
            }
            if (nH == 2) {
                result = Optional.of(mPartner);
            }
        }
        return result;
    }

    public boolean isAromaticFlippable() {
        boolean result = false;
        if (entity.getName().equalsIgnoreCase("tyr") || (entity.getName().equalsIgnoreCase("phe"))) {
            if ((name.length() == 3)) {
                char aChar = name.charAt(1);
                if ((aChar == 'D') || (aChar == 'E')) {
                    result = true;
                }
            }
        }

        return result;
    }

    public boolean isAAAromatic() {
        boolean result = false;
        if (entity.getName().equalsIgnoreCase("tyr") || entity.getName().equalsIgnoreCase("phe")
                || entity.getName().equalsIgnoreCase("trp") || entity.getName().equalsIgnoreCase("his")) {
            if ((name.length() == 2) && (name.equals("CG") || name.charAt(1) == 'Z')) {
                result = true;
            } else if ((name.length() == 3) && (name.charAt(1) != 'B')) {
                result = true;
            }
        }

        return result;
    }

    public Optional<Atom> getAromaticPartner() {
        Optional<Atom> result = Optional.empty();
        if ((parent != null)) {
            if (entity.getName().equalsIgnoreCase("tyr") || (entity.getName().equalsIgnoreCase("phe"))) {
                if (name.length() == 3) {
                    char aChar = name.charAt(1);
                    if ((aChar == 'D') || (aChar == 'E')) {
                        StringBuilder partnerBuilder = new StringBuilder();
                        partnerBuilder.append(name.charAt(0));
                        partnerBuilder.append(name.charAt(1));
                        if (name.charAt(2) == '1') {
                            partnerBuilder.append('2');
                        } else {
                            partnerBuilder.append('1');
                        }
                        Residue residue = (Residue) entity;
                        Atom atom = residue.getAtom(partnerBuilder.toString());
                        if (atom != null) {
                            result = Optional.of(atom);
                        }
                    }
                }
            }
        }
        return result;
    }

    //###################################################################
//#       Chemical Shift Ambiguity Index Value Definitions          #
//#                                                                 #
//#   Index Value            Definition                             #
//#                                                                 #
//#      1             Unique (geminal atoms and geminal methyl     #
//#                         groups with identical chemical shifts   #
//#                         are assumed to be assigned to           #
//#                         stereospecific atoms)                   #
//#      2             Ambiguity of geminal atoms or geminal methyl #
//#                         proton groups                           #
//#      3             Aromatic atoms on opposite sides of          #
//#                         symmetrical rings (e.g. Tyr HE1 and HE2 #
//#                         protons)                                #
//#      4             Intraresidue ambiguities (e.g. Lys HG and    #
//#                         HD protons or Trp HZ2 and HZ3 protons)  #
//#      5             Interresidue ambiguities (Lys 12 vs. Lys 27) #
//#      9             Ambiguous, specific ambiguity not defined    #
//#                                                                 #
//###################################################################
    public int getBMRBAmbiguity() {
        if (!entity.hasEquivalentAtoms()) {
            MoleculeBase.findEquivalentAtoms(entity);
        }

        int aType;

        if ((equivAtoms == null) || (equivAtoms.isEmpty())) {
            aType = 1;
        } else if (equivAtoms.size() == 1) {
            AtomEquivalency aEquiv = equivAtoms.getFirst();

            if (aEquiv.getAtoms().size() == 3) {
                aType = 1;
            } else if (aEquiv.getShell() == 2) {
                aType = 2;
            } else {
                aType = 3;
            }
        } else {
            AtomEquivalency aEquiv = equivAtoms.getFirst();

            if (aEquiv.getAtoms().size() == 3) {
                aType = 2;
            } else {
                aType = 9;
            }
        }

        return aType;
    }

    public String getEquivIndices() {
        if (!entity.hasEquivalentAtoms()) {
            MoleculeBase.findEquivalentAtoms(entity);
        }

        if ((equivAtoms == null) || (equivAtoms.isEmpty())) {
            return "";
        } else {
            char[] resultCh = new char[(equivAtoms.size() * 2) - 1];
            int j = 0;

            for (int i = 0; i < equivAtoms.size(); i++) {
                AtomEquivalency aEquiv = equivAtoms.get(i);

                if (i > 0) {
                    resultCh[j] = ' ';
                    j++;
                }

                resultCh[j++] = (char) (('a' + aEquiv.getIndex()) - 1);
            }

            return new String(resultCh);
        }
    }

    public String getPseudoName(int level) {
        String pseudoName = name;
        if (isMethyl()) {
            if (entity instanceof Residue) {
                Residue residue = (Residue) entity;
                if (residue.getName().equals("LYS")) {
                    pseudoName = 'Q' + name.substring(1, name.length() - 1);
                } else if (residue.getName().equals("ILE") || residue.getName().equals("THR")) {
                    pseudoName = 'M' + name.substring(1, name.length() - 2);
                } else if ((level == 1) || (name.length() < 4)) {
                    pseudoName = 'M' + name.substring(1, name.length() - 1);
                } else {
                    pseudoName = 'Q' + name.substring(1, name.length() - 2);
                }
            }
        } else {
            if (!entity.hasEquivalentAtoms()) {
                MoleculeBase.findEquivalentAtoms(entity);
            }
            if (equivAtoms != null) {
                AtomEquivalency aEquiv = equivAtoms.getFirst();
                if (aEquiv.getAtoms().size() == 2) {
                    pseudoName = 'Q' + name.substring(1, name.length() - 1);
                }
            }

        }
        return pseudoName;

    }

    // fixme this could return other atoms that end in c
    public boolean isCoarse() {
        int nameLen = name.length();
        return name.charAt(nameLen - 1) == 'c';
    }

    // fixme this could return other atoms that end in p
    public boolean isPlanarity() {
        int nameLen = name.length();
        return name.charAt(nameLen - 1) == 'p';
    }

    public boolean isConnector() {
        return type.equals("XX");
    }
    @Override
    public Point2d getPoint2d() {
        return new Point2d(getPoint().getX(), getPoint().getY());
    }

    @Override
    public void setPoint2d(Point2d pt) {
        Point3 point = new Point3(pt.x, pt.y, 0.0);
        setPoint(point);
    }

    @Override
    public void setID(int i) {
        iAtom = i;
    }

    @Override
    public int getID() {
        return iAtom;
    }

    @Override
    public void setProperty(String name, Object value) {
        if (properties.isEmpty()) {
            properties = Optional.of(new HashMap<>());
        }
        properties.get().put(name, value);
    }

    @Override
    public Object getProperty(String name) {
        Object propValue = null;
        if (properties.isPresent()) {
            propValue = properties.get().get(name);
        }
        return propValue;
    }

    @Override
    public String getSymbol() {
        return getElementName();
    }

    @Override
    public String getHybridization() {
        return (String) getProperty("hyb");
    }

    @Override
    public List<IBond> getBonds() {
        return new ArrayList<>(bonds);
    }

    public static int compareByIndex(Atom a1, Atom a2) {
        return Integer.compare(a1.iAtom, a2.iAtom);
    }

    @Override
    public Double getDouble(String elemName) {
        if (elemName.startsWith("Seq")) {
            return (double) getResidueNumber();
        }
        boolean ref = elemName.startsWith("REF");
        int i = Integer.parseInt(elemName.substring(elemName.length() - 1));
        PPMv ppmv = getPPMByMode(i, ref);
        if (ppmv != null) {
            return ppmv.getValue();
        }
        return null;
    }
}
