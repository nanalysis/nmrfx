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
package org.nmrfx.structure.chemistry;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.io.Sequence;
import org.nmrfx.chemistry.protein.ProteinHelix;
import org.nmrfx.chemistry.protein.Sheet;
import org.nmrfx.chemistry.search.MNode;
import org.nmrfx.chemistry.search.MTree;
import org.nmrfx.structure.chemistry.energy.AngleTreeGenerator;
import org.nmrfx.structure.chemistry.energy.Dihedral;
import org.nmrfx.structure.chemistry.energy.EnergyCoords;
import org.nmrfx.structure.chemistry.energy.EnergyLists;
import org.nmrfx.structure.chemistry.miner.NodeValidator;
import org.nmrfx.structure.chemistry.miner.PathIterator;
import org.nmrfx.structure.chemistry.predict.Predictor;
import org.nmrfx.structure.chemistry.predict.RNAAttributes;
import org.nmrfx.structure.fastlinear.FastVector3D;
import org.nmrfx.structure.project.StructureProject;
import org.nmrfx.structure.rdc.AlignmentCalc;
import org.nmrfx.structure.rdc.AlignmentMatrix;
import org.nmrfx.structure.rna.BasePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

@PluginAPI("residuegen")
public class Molecule extends MoleculeBase {
    private static final Logger log = LoggerFactory.getLogger(Molecule.class);

    public final Map<String, List<SpatialSet>> sites = new HashMap<>();
    public static int selCycleCount = 0;
    public static final LinkedHashMap labelTypes = new LinkedHashMap();
    public static final LinkedHashSet displayTypes = new LinkedHashSet();
    public static final LinkedHashSet colorTypes = new LinkedHashSet();
    public static final LinkedHashSet shapeTypes = new LinkedHashSet();

    public Map<Atom, Map<Atom, Double>> ringClosures;
    List<List<Atom>> atomTree = null;
    HashMap<String, List> allowedSourcesMap = new HashMap<>();

    static {
        labelTypes.put(Integer.valueOf(LABEL_NONE), "none");
        labelTypes.put(Integer.valueOf(LABEL_LABEL), "label");
        labelTypes.put(Integer.valueOf(LABEL_FC), "fc");
        labelTypes.put(Integer.valueOf(LABEL_SYMBOL), "symbol");
        labelTypes.put(Integer.valueOf(LABEL_NUMBER), "number");
        labelTypes.put(Integer.valueOf(LABEL_SYMBOL_AND_NUMBER), "both");
        labelTypes.put(Integer.valueOf(LABEL_FFC), "ffc");
        labelTypes.put(Integer.valueOf(LABEL_SECONDARY_STRUCTURE), "ss");
        labelTypes.put(Integer.valueOf(LABEL_RESIDUE), "residue");
        labelTypes.put(Integer.valueOf(LABEL_CHARGE), "charge");
        labelTypes.put(Integer.valueOf(LABEL_VALUE), "value");
        labelTypes.put(Integer.valueOf(LABEL_TITLE), "title");
        labelTypes.put(Integer.valueOf(LABEL_MOLECULE_NAME), "mname");
        labelTypes.put(Integer.valueOf(LABEL_STRING), "string");
        labelTypes.put(Integer.valueOf(LABEL_BOND), "bond");
        labelTypes.put(Integer.valueOf(LABEL_CUSTOM), "custom");
        labelTypes.put(Integer.valueOf(LABEL_NAME), "name");
        labelTypes.put(Integer.valueOf(LABEL_HPPM), "hppm");
        labelTypes.put(Integer.valueOf(LABEL_PPM), "ppm");
        labelTypes.put(Integer.valueOf(LABEL_NONHC), "nonhc");

        labelTypes.put("none", Integer.valueOf(LABEL_NONE));
        labelTypes.put("fc", Integer.valueOf(LABEL_FC));
        labelTypes.put("label", Integer.valueOf(LABEL_LABEL));
        labelTypes.put("symbol", Integer.valueOf(LABEL_SYMBOL));
        labelTypes.put("number", Integer.valueOf(LABEL_NUMBER));
        labelTypes.put("both", Integer.valueOf(LABEL_SYMBOL_AND_NUMBER));
        labelTypes.put("ffc", Integer.valueOf(LABEL_FFC));

        labelTypes.put("residue", Integer.valueOf(LABEL_RESIDUE));
        labelTypes.put("charge", Integer.valueOf(LABEL_CHARGE));
        labelTypes.put("value", Integer.valueOf(LABEL_VALUE));
        labelTypes.put("title", Integer.valueOf(LABEL_TITLE));
        labelTypes.put("mname", Integer.valueOf(LABEL_MOLECULE_NAME));
        labelTypes.put("string", Integer.valueOf(LABEL_STRING));
        labelTypes.put("bond", Integer.valueOf(LABEL_BOND));
        labelTypes.put("custom", Integer.valueOf(LABEL_CUSTOM));
        labelTypes.put("name", Integer.valueOf(LABEL_NAME));
        labelTypes.put("hppm", Integer.valueOf(LABEL_HPPM));
        labelTypes.put("ppm", Integer.valueOf(LABEL_PPM));
        labelTypes.put("nonhc", Integer.valueOf(LABEL_NONHC));

        displayTypes.add("none");
        displayTypes.add("wire");
        displayTypes.add("hwire");
        displayTypes.add("bwire");

        displayTypes.add("ball");
        displayTypes.add("pball");
        displayTypes.add("cpk");

        colorTypes.add("atom");

        shapeTypes.add("circle");
        shapeTypes.add("square");
        shapeTypes.add("triangle");
    }

    public boolean labelsCurrent = false;
    public String originalName = null;
    public String source = null;
    public String comment = null;
    public String display = "wire";
    public String posShapeType = "sphere";
    public String negShapeType = "sphere";
    public String colorType = "atom";
    public float[][] model = new float[4][4];
    public float[][] view = new float[4][4];
    public float[] values = new float[10];
    public float[] color = new float[3];
    double[] center = new double[3];
    public float[] titlePosition = new float[3];
    public String energyType = null;
    public boolean deleted = false;
    float bondSpace = 12.0f;
    // FIXME should be crystal object
    public String crystal = null;
    private ArrayList<Atom> angleAtoms = null;
    private ArrayList<Atom> pseudoAngleAtoms = null;
    List<Atom> treeAtoms;

    int genVecs[][] = null;
    EnergyCoords eCoords = new EnergyCoords();
    Dihedral dihedrals = null;
    AlignmentMatrix alignmentMat = null;
    EnergyLists energyList;
    ProteinHelix helix;
    Sheet sheets;

    // fixme    public EnergyLists energyList = null;
    public Molecule(String name) {
        super(name);
        Atom.resetLastAtom();
        try {
            Class c = Class.forName("javafx.collections.FXCollections");
            Class[] argTypes = new Class[0];
            Method m = c.getDeclaredMethod("observableArrayList", argTypes);
            Object[] args = new Object[0];
            atoms = (List<Atom>) m.invoke(args);
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException |
                 IllegalArgumentException | InvocationTargetException ex) {
            atoms = new ArrayList<>();
        }
    }

    final void storeMolecule() {
        MoleculeFactory.putMolecule(this);
    }

    public void setTreeList(List<Atom> treeAtoms) {
        this.treeAtoms = treeAtoms;
    }

    public static boolean isAnyChanged() {
        boolean anyChanged = false;
        Collection<MoleculeBase> molecules = MoleculeFactory.getMolecules();
        for (MoleculeBase checkMol : molecules) {
            if (checkMol.isChanged()) {
                anyChanged = true;
                break;

            }
        }
        return anyChanged;
    }

    public static void clearAllChanged() {
        Collection<MoleculeBase> molecules = MoleculeFactory.getMolecules();
        for (MoleculeBase checkMol : molecules) {
            checkMol.clearChanged();
        }
    }

    public void setActive() {
        MoleculeFactory.setActive(this);
    }

    public static Molecule getActive() {
        return (Molecule) MoleculeFactory.getActive();
    }

    public void setTitle(final String value) {
        title = value.trim();
    }

    public String getTitle() {
        String value = name;
        if ((title != null) && !title.equals("")) {
            value = title;
        }
        return value;
    }

    public static void addMoleculeModel() {
    }

    /**
     * Removes the current active molecule and clears associated objects. If other molecules are available, one of
     * those molecules will be set to the new active molecule, but which molecule will be set is undefined since
     * molecules may be stored in a collection without ordering.
     */
    public void remove() {

        StructureProject.getActive().removeMolecule(name);
        globalSelected.clear();
        bselected.clear();
        structures.clear();
        resetActiveStructures();
        conditions.clear();

        Collection<MoleculeBase> mols = MoleculeFactory.getMolecules();

        MoleculeFactory.setActive(null);
        Iterator<MoleculeBase> it = mols.iterator();
        if (it.hasNext()) {
            MoleculeFactory.setActive(it.next());
        }
    }

    public Set getStructures() {
        return structures;
    }

    public String getDefaultEntity() {
        Object[] keys = entities.keySet().toArray();
        return keys[0].toString();
    }

    public void sortByIndex() {
        for (Polymer polymer : getPolymers()) {
            for (Residue residue : polymer.getResidues()) {
                residue.sortByIndex();
            }
        }
        for (Entity entity : getLigands()) {
            entity.sortByIndex();
        }
        invalidateAtomArray();
        updateAtomArray();
    }

    public static Molecule get(String name) {
        if (name == null) {
            return null;
        } else {
            return (Molecule) MoleculeFactory.getMolecule(name);
        }
    }

    public void setDihedrals(Dihedral dihedrals) {
        this.dihedrals = dihedrals;
    }

    public Dihedral getDihedrals() {
        return dihedrals;
    }

    public void setEnergyLists(EnergyLists eLists) {
        this.energyList = eLists;
    }

    public EnergyLists getEnergyLists() {
        return energyList;
    }

    public void setSheets(Sheet sheets) {
        this.sheets = sheets;
    }

    public Sheet getSheets() {
        return sheets;
    }

    public String getDotBracket() {
        String dotBracket = getProperty("vienna");
        dotBracket = dotBracket == null ? "" : dotBracket;
        return dotBracket;
    }

    public void setRDCResults(AlignmentMatrix results) {
        alignmentMat = results;
    }

    public static Point3 avgCoords(MolFilter molFilter1) throws IllegalArgumentException, InvalidMoleculeException {
        List<SpatialSet> selected1 = matchAtoms(molFilter1);
        Point3 pt1 = Atom.avgAtom(selected1, molFilter1.getStructureNum());
        if (pt1 == null) {
            throw new IllegalArgumentException("No coordinates for atom " + molFilter1.getString());
        }
        return pt1;
    }

    public static double calcDistance(String aname0, String aname1) {
        int structureNum = 0;
        Atom[] atoms = new Atom[2];
        atoms[0] = getAtomByName(aname0);
        atoms[1] = getAtomByName(aname1);
        SpatialSet[] spSets = new SpatialSet[2];
        Point3[] pts = new Point3[2];
        int i = 0;
        for (Atom atom : atoms) {
            if (atom == null) {
                log.warn("{} {}", aname0, aname1);
                throw new IllegalArgumentException("No atom for " + i);
            }
            spSets[i] = atom.spatialSet;
            pts[i] = spSets[i].getPoint(structureNum);
            if (pts[i] == null) {
                throw new IllegalArgumentException("No coordinates for atom " + atom.getFullName());
            }
            i++;
        }
        return Atom.calcDistance(pts[0], pts[1]);
    }

    public static double calcDistance(MolFilter molFilter1, MolFilter molFilter2)
            throws MissingCoordinatesException, InvalidMoleculeException {
        return calcDistance(molFilter1, molFilter2, molFilter1.getStructureNum());
    }

    public static double calcDistance(MolFilter molFilter1, MolFilter molFilter2,
                                      int structureNum)
            throws MissingCoordinatesException, InvalidMoleculeException {
        List<SpatialSet> selected1 = matchAtoms(molFilter1);
        List<SpatialSet> selected2 = matchAtoms(molFilter2);
        Point3 pt1 = Atom.avgAtom(selected1, structureNum);
        Point3 pt2 = Atom.avgAtom(selected2, structureNum);
        if (pt1 == null) {
            throw new MissingCoordinatesException("No coordinates for atom " + molFilter1.getString());
        }
        if (pt2 == null) {
            throw new MissingCoordinatesException("No coordinates for atom " + molFilter2.getString());
        }
        return (Atom.calcDistance(pt1, pt2));
    }

    public static double calcAngle(MolFilter molFilter1, MolFilter molFilter2, MolFilter molFilter3)
            throws MissingCoordinatesException {
        SpatialSet spSet1 = getSpatialSet(molFilter1);
        SpatialSet spSet2 = getSpatialSet(molFilter2);
        SpatialSet spSet3 = getSpatialSet(molFilter3);
        if (spSet1 == null) {
            throw new MissingCoordinatesException("No coordinates for atom " + molFilter1.getString());
        }
        if (spSet2 == null) {
            throw new MissingCoordinatesException("No coordinates for atom " + molFilter2.getString());
        }
        if (spSet3 == null) {
            throw new MissingCoordinatesException("No coordinates for atom " + molFilter3.getString());
        }

        Point3 pt1 = spSet1.getPoint(molFilter1.getStructureNum());
        Point3 pt2 = spSet2.getPoint(molFilter2.getStructureNum());
        Point3 pt3 = spSet3.getPoint(molFilter3.getStructureNum());
        if (pt1 == null) {
            throw new MissingCoordinatesException("No coordinates for atom " + molFilter1.getString());
        }
        if (pt2 == null) {
            throw new MissingCoordinatesException("No coordinates for atom " + molFilter2.getString());
        }
        if (pt3 == null) {
            throw new MissingCoordinatesException("No coordinates for atom " + molFilter3.getString());
        }
        return (Atom.calcAngle(pt1, pt2, pt3));
    }

    public double calcAngle(final String aname0, final String aname1, final String aname2) {
        int structureNum = 0;
        Atom[] atoms = new Atom[3];
        atoms[0] = getAtom(aname0);
        atoms[1] = getAtom(aname1);
        atoms[2] = getAtom(aname2);
        SpatialSet[] spSets = new SpatialSet[3];
        Point3[] pts = new Point3[3];
        int i = 0;
        for (Atom atom : atoms) {
            if (atom == null) {
                log.warn("{} {} {}", aname0, aname1, aname2);
                throw new IllegalArgumentException("No atom for " + i);
            }
            spSets[i] = atom.spatialSet;
            pts[i] = spSets[i].getPoint(structureNum);
            if (pts[i] == null) {
                throw new IllegalArgumentException("No coordinates for atom " + atom.getFullName());
            }
            i++;
        }
        return (Atom.calcAngle(pts[0], pts[1], pts[2]));

    }

    public double calcDihedral(MolFilter molFilter1, MolFilter molFilter2, MolFilter molFilter3,
                               MolFilter molFilter4) throws IllegalArgumentException {
        return calcDihedral(molFilter1, molFilter2, molFilter3, molFilter4, 0);

    }

    public double calcDihedral(MolFilter molFilter1, MolFilter molFilter2, MolFilter molFilter3,
                               MolFilter molFilter4, int structureNum) throws IllegalArgumentException {
        MolFilter[] molFilters = new MolFilter[4];
        molFilters[0] = molFilter1;
        molFilters[1] = molFilter2;
        molFilters[2] = molFilter3;
        molFilters[3] = molFilter4;
        SpatialSet[] spSets = new SpatialSet[4];
        Point3[] pts = new Point3[4];
        int i = 0;
        for (MolFilter molFilter : molFilters) {
            spSets[i] = findSpatialSet(molFilter);
            if (spSets[i] == null) {
                throw new IllegalArgumentException("No atom for " + molFilter.getString());
            }
            pts[i] = spSets[i].getPoint(structureNum);
            if (pts[i] == null) {
                throw new IllegalArgumentException("No coordinates for atom " + molFilter.getString());
            }
            i++;
        }
        return (Atom.calcDihedral(pts[0], pts[1], pts[2], pts[3]));
    }

    public static double calcDihedral(final Atom[] atoms) throws MissingCoordinatesException {
        int structureNum = 0;
        return calcDihedral(atoms, structureNum);
    }

    public static double calcDihedral(final Atom[] atoms, int structureNum) throws MissingCoordinatesException {
        SpatialSet[] spSets = new SpatialSet[4];
        Point3[] pts = new Point3[4];
        int i = 0;
        for (Atom atom : atoms) {
            spSets[i] = atom.spatialSet;
            pts[i] = spSets[i].getPoint(structureNum);
            if (pts[i] == null) {
                throw new MissingCoordinatesException("No coordinates for atom "
                        + atom.getFullName() + " in structure " + structureNum);
            }
            i++;
        }
        return (Atom.calcDihedral(pts[0], pts[1], pts[2], pts[3]));
    }

    public double calcDihedral(final String aname0, final String aname1, final String aname2, final String aname3) {
        int structureNum = 0;
        Atom[] atoms = new Atom[4];
        atoms[0] = getAtom(aname0);
        atoms[1] = getAtom(aname1);
        atoms[2] = getAtom(aname2);
        atoms[3] = getAtom(aname3);
        SpatialSet[] spSets = new SpatialSet[4];
        Point3[] pts = new Point3[4];
        int i = 0;
        for (Atom atom : atoms) {
            if (atom == null) {
                log.warn("{} {} {} {}", aname0, aname1, aname2, aname3);
                throw new IllegalArgumentException("No atom for " + i);
            }
            spSets[i] = atom.spatialSet;
            pts[i] = spSets[i].getPoint(structureNum);
            if (pts[i] == null) {
                throw new IllegalArgumentException("No coordinates for atom " + atom.getFullName());
            }
            i++;
        }
        return (Atom.calcDihedral(pts[0], pts[1], pts[2], pts[3]));
    }

    public void activateAtoms() {
        updateAtomArray();
        for (Atom atom : atoms) {
            atom.setActive(true);
        }
    }

    public void inactivateAtoms() {
        updateAtomArray();
        for (Atom atom : atoms) {
            atom.setActive(false);
        }
    }

    public void setAtomTree(List<List<Atom>> aTree) {
        atomTree = aTree;
    }

    // used from python
    public void genMeasuredTree(Atom startAtom) {
        updateAtomArray();
        if (startAtom == null) {
            startAtom = atoms.get(0);
        }
        AngleTreeGenerator aTreeGen = new AngleTreeGenerator();
        atomTree = aTreeGen.genTree(this, startAtom, null);
        // fixme  need to not measure already measured geometry till we can only measure once
        aTreeGen.measureAtomTree(this, atomTree, true, false);
        setRingClosures(aTreeGen.getRingClosures());
        setupGenCoords();
    }

    // used from python
    public void clearBondRingClosures() {
        for (Bond bond : getBondList()) {
            bond.setRingClosure(false);
        }
    }

    public void setupEnergy(EnergyLists energyLists) {
        try {
            AtomEnergyProp.readPropFile();
            AtomEnergyProp.makeIrpMap();
        } catch (FileNotFoundException ex) {
            log.warn(ex.getMessage(), ex);
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
        invalidateAtomArray();
        updateAtomArray();
        setupGenCoords();
        energyLists.makeAtomListFast();
        eCoords.setForceWeight(energyLists.getForceWeight());
        updateVecCoords();
    }

    public void invalidateAtomTree() {
        atomTree = null;
    }

    public void setupGenCoords() throws RuntimeException {
        setupGenCoords(false);
    }

    public void setupGenCoords(boolean fillCoords) throws IllegalArgumentException,
            RuntimeException {
        updateAtomArray();
        if (atomTree == null) {
            AngleTreeGenerator aTreeGen = new AngleTreeGenerator();
            atomTree = aTreeGen.genTree(this, null, null);
        }
        genVecs = CoordinateGenerator.setupCoords(atomTree);
        CoordinateGenerator.prepareAtoms(atoms, fillCoords);
    }

    public List<List<Atom>> getAtomTree() {
        if (atomTree == null) {
            AngleTreeGenerator aTreeGen = new AngleTreeGenerator();
            atomTree = aTreeGen.genTree(this, null, null);
        }
        return atomTree;
    }
    public void dumpCoordsGen() {
        if (genVecs == null) {
            return;
        }
        List<Atom> atomList;
        if (treeAtoms == null) {
            atomList = atoms;
        } else {
            atomList = treeAtoms;
        }
        CoordinateGenerator.dumpCoordsGen(genVecs, atomList);
    }

    public void resetGenCoords() {
        genVecs = null;
    }

    public void fillEntityCoords() {
        for (Entity entity : getEntities()) {
            AngleTreeGenerator.fillCoordinates(entity, null);
        }
    }

    public int genCoords() throws RuntimeException {
        return genCoordsFast(null, false, 0);
    }

    public int genCoords(int iStructure, boolean fillCoords) throws RuntimeException {
        return genCoordsFast(null, fillCoords, iStructure);
    }

    @Override
    public int genCoords(boolean fillCoords) throws RuntimeException {
        return genCoordsFast(null, fillCoords, 0);
    }

    public int genCoords(boolean fillCoords, final double[] dihedralAngles) throws RuntimeException {
        return genCoordsFast(dihedralAngles, fillCoords, 0);
    }

    public int genCoordsFast(final double[] dihedralAngles, boolean fillCoords, int iStructure) throws RuntimeException {
        if (!atoms.isEmpty()) {
            if (fillCoords) {
                boolean anyInvalid = false;
                for (Atom atom : atoms) {
                    if (!atom.getPointValidity(iStructure)) {
                        anyInvalid = true;
                        break;
                    }
                }
                if (!anyInvalid) {
                    structures.add(iStructure);
                    resetActiveStructures();
                    updateVecCoords(iStructure);
                    return 0;
                }
            }

            if (genVecs == null) {
                setupGenCoords(fillCoords);
            }
            List<Atom> atomList;
            if (treeAtoms == null) {
                atomList = atoms;
            } else {
                atomList = treeAtoms;
            }
            if (!fillCoords) {
                for (Atom atom : atoms) {
                    atom.setPointValidity(iStructure, false);
                }
            }
            int nAngles = CoordinateGenerator.genCoords(genVecs, atomList, iStructure, dihedralAngles);
            structures.add(iStructure);
            resetActiveStructures();
            updateVecCoords(iStructure);
            return nAngles;
        } else {
            return 0;
        }
    }

    public int genCoordsFastVec3D(final double[] dihedralAngles) throws RuntimeException {
        int nAngles = 0;
        if (genVecs == null) {
            setupGenCoords();
        }
        List<Atom> atomList;
        if (treeAtoms == null) {
            atomList = atoms;
        } else {
            atomList = treeAtoms;
        }
        FastVector3D[] vecCoords = eCoords.getVecCoords();
        FastVector3D[] origins = new FastVector3D[3];
        origins[0] = new FastVector3D(-1.0, -1.0, 0.0);
        origins[1] = new FastVector3D(-1.0, 0.0, 0.0);
        origins[2] = new FastVector3D(0.0, 0.0, 0.0);

        for (int i = 0; i < genVecs.length; i++) {
            if (genVecs[i].length > 3) {
                FastVector3D v1;
                FastVector3D v2;
                FastVector3D v3;
                if (genVecs[i][0] < 0) {
                    v1 = origins[genVecs[i][0] + 2];
                } else {
                    v1 = vecCoords[genVecs[i][0]];
                }
                if (genVecs[i][1] < 0) {
                    v2 = origins[genVecs[i][1] + 2];
                } else {
                    v2 = vecCoords[genVecs[i][1]];
                }
                v3 = vecCoords[genVecs[i][2]];

                Coordinates3DF coords = new Coordinates3DF(v1, v2, v3);
                coords.setupNeRF();
                double dihedralAngle = 0;
                for (int j = 3; j < genVecs[i].length; j++) {
                    FastVector3D v4 = vecCoords[genVecs[i][j]];
                    Atom a4 = atomList.get(genVecs[i][j]);
                    if (dihedralAngles == null) {
                        dihedralAngle += a4.dihedralAngle;
                    } else {
                        dihedralAngle += dihedralAngles[nAngles];
                    }
                    nAngles++;
                    boolean ok = coords.calculateNeRF(dihedralAngle, a4.bndCos, a4.bndSin, v4);
                    if (!ok) {
                        log.info("{} {} {}", a4.getParent(), a4.getFullName(), a4.valanceAngle);
                    }
                }
            }

        }

        structures.add(0);
        resetActiveStructures();
        return nAngles;
    }

    public EnergyCoords getEnergyCoords() {
        return eCoords;
    }

    public void updateVecCoords() {
        updateVecCoords(0);
    }

    public void updateVecCoords(int iStruct) {
        int i = 0;
        Entity lastEntity = null;
        int resNum = -1;
        getAtomTypes();
        List<Atom> atomList;
        if (treeAtoms == null) {
            atomList = atoms;
        } else {
            atomList = treeAtoms;
        }
        FastVector3D[] vecCoords = eCoords.getVecCoords(atomList.size());

        // fixme this is a hack because the treeAtoms are not in monotoniclly increasing order of residue number
        Map<Entity, Integer> resMap = new HashMap<>();
        for (Atom atom : atomList) {
            if (resMap.containsKey(atom.entity)) {
                resNum = resMap.get(atom.entity);
            } else {
                resNum = resMap.size();
                resMap.put(atom.entity, resNum);
            }
            Point3 pt = atom.getPoint(iStruct);
            if (pt == null) {
                log.warn("updateVecCoords null pt {} {}", atom.getFullName(), (i - 1));
            } else {
                eCoords.setCoords(i, pt.getX(), pt.getY(), pt.getZ(), resNum, atom);
            }
            i++;
        }
    }

    public void updateFromVecCoords() {
        FastVector3D[] vecCoords = eCoords.getVecCoords();
        List<Atom> atomList;
        if (treeAtoms == null) {
            atomList = atoms;
        } else {
            atomList = treeAtoms;
        }
        for (Atom atom : atomList) {
            Point3 pt = atom.getPoint();
            if (pt == null) {
                log.warn("updateFromVecCoords null pt {} {}", atom.getFullName(), atom.eAtom);
            } else {
                FastVector3D fVec = vecCoords[atom.eAtom];
                if (fVec == null) {
                    // fixme System.out.println("null vec " + atom.getFullName() + " " + (i - 1));
                } else {
                    Point3 newPt = new Point3(fVec.getEntry(0), fVec.getEntry(1), fVec.getEntry(2));
                    atom.setPoint(newPt);
                }

            }
        }
    }

    public ArrayList<Atom> getAttachedHydrogens(Atom atom) {
        ArrayList<Atom> hydrogens = new ArrayList<Atom>();
        for (int iBond = 0; iBond < atom.bonds.size(); iBond++) {
            Bond bond = atom.bonds.get(iBond);
            Atom checkAtom;
            if (bond.begin == atom) {
                checkAtom = bond.end;
            } else {
                checkAtom = bond.begin;
            }
            log.warn("{} {} {}", atom.getName(), checkAtom.getName(), checkAtom.getAtomicNumber());
            if (checkAtom.getAtomicNumber() == 1) {
                hydrogens.add(checkAtom);
            }
        }
        return hydrogens;
    }

    public int selectResidues() {
        List<SpatialSet> selected = new ArrayList<>(256);
        TreeSet completedResidues = new TreeSet();

        for (int i = 0; i < globalSelected.size(); i++) {
            SpatialSet spatialSet = globalSelected.get(i);

            if (spatialSet.selected != 1) {
                continue;
            }

            Compound compound = (Compound) spatialSet.atom.entity;

            if (completedResidues.contains(compound.number + spatialSet.getName())) {
                continue;
            } else {
                completedResidues.add(compound.number + spatialSet.getName());
            }

            for (Atom atom : compound.atoms) {
                SpatialSet spatialSet2 = atom.getSpatialSet();

                if (spatialSet2 != null) {
                    selected.add(spatialSet2);
                }
            }
        }

        int nSelected = setSelected(selected, false, false);
        return nSelected;
    }

    public int selectAtoms(String selectionString) throws InvalidMoleculeException {
        return selectAtoms(selectionString, false, false);
    }

    public int selectAtoms(String selectionString, boolean append, boolean inverse) throws InvalidMoleculeException {
        MolFilter molFilter = new MolFilter(selectionString);
        List<SpatialSet> selected = matchAtoms(molFilter);
        int nSelected = setSelected(selected, append, inverse);
        return nSelected;
    }

    public int selectAtoms(MolFilter molFilter, boolean append, boolean inverse) throws InvalidMoleculeException {
        List<SpatialSet> selected = matchAtoms(molFilter);
        int nSelected = setSelected(selected, append, inverse);
        return nSelected;
    }

    public void clearSelected() {
        clearSelectedAtoms();
        clearSelectedBonds();
    }

    public void clearSelectedAtoms() {
        for (SpatialSet spatialSet : globalSelected) {
            if (spatialSet != null) {
                spatialSet.setSelected(0);
            }
        }
        globalSelected.clear();
    }

    public void clearSelectedBonds() {
        for (var bond : bselected) {
            bond.unsetProperty(Atom.SELECT);
        }
        bselected.clear();
    }

    public int setSelected(List<SpatialSet> selected, boolean append, boolean inverse) {
        int i;
        int j;
        SpatialSet spatialSet = null;

        if (!append) {
            for (Atom atom : atoms) {
                atom.getSpatialSet().setSelected(0);

            }
        }

        if (selected == null) {
            globalSelected.clear();

            return 0;
        }

        if (inverse) {
            for (i = 0; i < atoms.size(); i++) {
                Atom atom = atoms.get(i);
                atom.spatialSet.setSelected(1);
            }

            for (i = 0; i < selected.size(); i++) {
                spatialSet = (SpatialSet) selected.get(i);
                spatialSet.setSelected(0);
            }

            globalSelected.clear();

            for (i = 0; i < atoms.size(); i++) {
                Atom atom = atoms.get(i);
                spatialSet = atom.spatialSet;
                if (spatialSet.getSelected() > 0) {
                    globalSelected.add(spatialSet);
                }
            }

            return (globalSelected.size());
        } else {
            if (!append) {
                globalSelected.clear();
            }

            for (i = 0; i < selected.size(); i++) {
                spatialSet = (SpatialSet) selected.get(i);

                if (spatialSet != null) {
                    spatialSet.setSelected(spatialSet.getSelected() + 1);
                    globalSelected.add(spatialSet);
                }
            }

            return globalSelected.size();
        }
    }

    public int selectBonds(String aName1, String aName2, boolean append) {
        if (!append) {
            for (var bond : bselected) {
                bond.unsetProperty(Atom.SELECT);
            }
            bselected.clear();
        }
        Atom atomB = getAtomByName(aName1);
        Atom atomE = getAtomByName(aName2);
        if ((atomB != null) && (atomE != null)) {
            for (var bond : atomB.getBonds()) {
                if ((bond.getAtom(0) == atomB) && (bond.getAtom(1) == atomE)) {
                    bselected.add((Bond) bond);
                } else if ((bond.getAtom(1) == atomB) && (bond.getAtom(0) == atomE)) {
                    bselected.add((Bond) bond);
                }
            }
        }

        return bselected.size();
    }

    public int selectBonds(String mode) {
        List<Bond> selected = matchBonds();
        int i;
        Bond bond;

        for (i = 0; i < bselected.size(); i++) {
            bond = bselected.get(i);
            bond.unsetProperty(Atom.SELECT);
        }

        bselected.clear();

        for (i = 0; i < selected.size(); i++) {
            bond = selected.get(i);
            bond.setProperty(Atom.SELECT);
            bselected.add(bond);
        }

        return selected.size();
    }

    public ArrayList<String> listAtoms() {
        int i;
        SpatialSet spatialSet;
        ArrayList<String> list = new ArrayList<>();

        for (i = 0; i < globalSelected.size(); i++) {
            spatialSet = globalSelected.get(i);
            list.add(spatialSet.getFullName());
        }
        return list;
    }

    public void unSelectLastAtom() {
        SpatialSet spatialSet;

        if (globalSelected.size() == 0) {
            return;
        }

        spatialSet = globalSelected.get(globalSelected.size() - 1);

        if ((spatialSet != null) && (spatialSet.getSelected() > 0)) {
            spatialSet.setSelected(spatialSet.getSelected() - 1);
        }

        globalSelected.remove(globalSelected.size() - 1);
    }

    public void makeSite(String siteName) throws IllegalArgumentException {
        if ((siteName == null) || (siteName.trim().equals(""))) {
            throw new IllegalArgumentException("makeSite: null or blank siteName");
        }

        List<SpatialSet> siteList = new ArrayList<>(globalSelected);
        sites.put(siteName, siteList);
    }

    public int selectSite(String siteName) throws IllegalArgumentException {
        if ((siteName == null) || (siteName.trim().equals(""))) {
            throw new IllegalArgumentException("selectSite: null or blank siteName");
        }

        List<SpatialSet> siteList = sites.get(siteName);

        if (siteList == null) {
            throw new IllegalArgumentException("selectSite: siteList \"" + siteName + "\" doesnt't exist");
        }

        int nSelected = setSelected(siteList, false, false);
        return nSelected;
    }

    public void withinSite(String siteName, float tolerance) throws IllegalArgumentException {
        if ((siteName == null) || (siteName.trim().equals(""))) {
            throw new IllegalArgumentException("selectSite: null or blank siteName");
        }

        List<SpatialSet> siteList = sites.get(siteName);

        if (siteList == null) {
            throw new IllegalArgumentException("withinSite: siteList \"" + siteName + "\" doesnt't exist");
        }

        List<SpatialSet> hitList = new ArrayList<>(128);

        for (int i = 0; i < globalSelected.size(); i++) {
            SpatialSet s1 = globalSelected.get(i);

            if (s1.getSelected() != 1) {
                continue;
            }

            Point3 pt1 = s1.getPoint();

            if (pt1 == null) {
                continue;
            }

            // fixme  should get array of coords once
            for (int j = 0; j < siteList.size(); j++) {
                SpatialSet s2 = siteList.get(j);
                Point3 pt2 = s2.getPoint();

                if (pt2 == null) {
                    continue;
                }

                double distance = Atom.calcDistance(pt1, pt2);

                if (distance < tolerance) {
                    hitList.add(s1);

                    break;
                }
            }
        }

        setSelected(hitList, false, false);
    }

    public List<Bond> selectedBonds() {
        return bselected;
    }

    public void setAtomProperty(int property, boolean state) {
        SpatialSet spatialSet;

        for (int i = 0; i < globalSelected.size(); i++) {
            spatialSet = globalSelected.get(i);

            if (spatialSet.getSelected() == 1) {
                if (state) {
                    spatialSet.setProperty(property);
                } else {
                    spatialSet.unsetProperty(property);
                }
            }
        }
    }

    public void setBondProperty(int property, boolean state) {
        Bond bond;

        for (int i = 0; i < bselected.size(); i++) {
            bond = bselected.get(i);

            if (state) {
                bond.setProperty(Bond.DISPLAY);
            } else {
                bond.unsetProperty(Bond.DISPLAY);
            }
        }
    }

    public void colorAtoms(float red, float green, float blue) {
        Atom atom;

        for (int i = 0; i < globalSelected.size(); i++) {
            atom = globalSelected.get(i).atom;
            atom.setColor(red, green, blue);
        }
    }

    public void colorAtomsByType() {
        Atom atom;

        for (int i = 0; i < globalSelected.size(); i++) {
            atom = globalSelected.get(i).atom;
            atom.setColorByType();
        }
    }

    public void colorBonds(float red, float green, float blue) {
        Bond bond;

        for (int i = 0; i < bselected.size(); i++) {
            bond = bselected.get(i);
            bond.red = red;
            bond.green = green;
            bond.blue = blue;
        }
    }

    public void radiusAtoms(float radius) {
        Atom atom;

        for (int i = 0; i < globalSelected.size(); i++) {
            atom = globalSelected.get(i).atom;
            atom.radius = radius;
        }
    }

    public void radiusBonds(float radius) {
        Bond bond;

        for (int i = 0; i < bselected.size(); i++) {
            bond = bselected.get(i);
            bond.radius = radius;
        }
    }

    public int createLineArray(int iStructure, float[] coords, int i, float[] colors) {
        int j;
        Atom atomB;
        Atom atomE;
        Point3 ptB;
        Point3 ptE;
        updateBondArray();
        for (Bond bond : bonds) {
            if (bond.getProperty(Bond.DISPLAY)) {
                atomB = bond.begin;
                atomE = bond.end;

                if ((atomB != null) && (atomE != null)) {

                    SpatialSet spatialSet = atomB.spatialSet;
                    ptB = atomB.getPoint(iStructure);
                    ptE = atomE.getPoint(iStructure);

                    if ((ptB != null) && (ptE != null)) {
                        j = i;

                        double dx = ptE.getX() - ptB.getX();
                        double dy = ptE.getY() - ptB.getY();
                        double dz = ptE.getZ() - ptB.getZ();
                        double x3 = -dy / bondSpace;
                        double y3 = dx / bondSpace;
                        double z3 = dz / bondSpace;

                        if (((bond.stereo == Bond.STEREO_BOND_UP) && (atomE.nonHydrogens < atomB.nonHydrogens))
                                || ((bond.stereo == Bond.STEREO_BOND_DOWN) && (atomE.nonHydrogens > atomB.nonHydrogens))) {
                            coords[i++] = (float) (ptB.getX());
                            coords[i++] = (float) (ptB.getY());
                            coords[i++] = (float) (ptB.getZ());
                            coords[i++] = (float) (ptE.getX() + x3);
                            coords[i++] = (float) (ptE.getY() + y3);
                            coords[i++] = (float) (ptE.getZ() + z3);

                            coords[i++] = (float) (ptB.getX());
                            coords[i++] = (float) (ptB.getY());
                            coords[i++] = (float) (ptB.getZ());
                            coords[i++] = (float) (ptE.getX() - x3);
                            coords[i++] = (float) (ptE.getY() - y3);
                            coords[i++] = (float) (ptE.getZ() - z3);

                            coords[i++] = (float) (ptE.getX() - x3);
                            coords[i++] = (float) (ptE.getY() - y3);
                            coords[i++] = (float) (ptE.getZ() - z3);
                            coords[i++] = (float) (ptE.getX() + x3);
                            coords[i++] = (float) (ptE.getY() + y3);
                            coords[i++] = (float) (ptE.getZ() + z3);

                            colors[j++] = atomB.getRed();
                            colors[j++] = atomB.getGreen();
                            colors[j++] = atomB.getBlue();
                            colors[j++] = atomE.getRed();
                            colors[j++] = atomE.getGreen();
                            colors[j++] = atomE.getBlue();
                            colors[j++] = atomB.getRed();
                            colors[j++] = atomB.getGreen();
                            colors[j++] = atomB.getBlue();
                            colors[j++] = atomE.getRed();
                            colors[j++] = atomE.getGreen();
                            colors[j++] = atomE.getBlue();
                            colors[j++] = atomB.getRed();
                            colors[j++] = atomB.getGreen();
                            colors[j++] = atomB.getBlue();
                            colors[j++] = atomE.getRed();
                            colors[j++] = atomE.getGreen();
                            colors[j++] = atomE.getBlue();
                        } else if (((bond.stereo == Bond.STEREO_BOND_DOWN) && (atomE.nonHydrogens < atomB.nonHydrogens))
                                || ((bond.stereo == Bond.STEREO_BOND_UP) && (atomE.nonHydrogens > atomB.nonHydrogens))) {
                            coords[i++] = (float) (ptB.getX() + x3);
                            coords[i++] = (float) (ptB.getY() + y3);
                            coords[i++] = (float) (ptB.getZ() + z3);
                            coords[i++] = (float) (ptB.getX() - x3);
                            coords[i++] = (float) (ptB.getY() - y3);
                            coords[i++] = (float) (ptB.getZ() - z3);

                            coords[i++] = (float) (ptB.getX() + x3 + (dx / 5));
                            coords[i++] = (float) (ptB.getY() + y3 + (dy / 5));
                            coords[i++] = (float) (ptB.getZ() + z3 + (dz / 5));
                            coords[i++] = (float) (ptB.getX() - x3 + (dx / 5));
                            coords[i++] = (float) (ptB.getY() - y3 + (dy / 5));
                            coords[i++] = (float) (ptB.getZ() - z3 + (dz / 5));

                            coords[i++] = (float) (ptB.getX() + x3 + (dx / 5 * 2));
                            coords[i++] = (float) (ptB.getY() + y3 + (dy / 5 * 2));
                            coords[i++] = (float) (ptB.getZ() + z3 + (dz / 5 * 2));
                            coords[i++] = (float) (ptB.getX() - x3 + (dx / 5 * 2));
                            coords[i++] = (float) (ptB.getY() - y3 + (dy / 5 * 2));
                            coords[i++] = (float) (ptB.getZ() - z3 + (dz / 5 * 2));

                            coords[i++] = (float) (ptB.getX() + x3 + (dx / 5 * 3));
                            coords[i++] = (float) (ptB.getY() + y3 + (dy / 5 * 3));
                            coords[i++] = (float) (ptB.getZ() + z3 + (dz / 5 * 3));
                            coords[i++] = (float) (ptB.getX() - x3 + (dx / 5 * 3));
                            coords[i++] = (float) (ptB.getY() - y3 + (dy / 5 * 3));
                            coords[i++] = (float) (ptB.getZ() - z3 + (dz / 5 * 3));

                            coords[i++] = (float) (ptB.getX() + x3 + (dx / 5 * 4));
                            coords[i++] = (float) (ptB.getY() + y3 + (dy / 5 * 4));
                            coords[i++] = (float) (ptB.getZ() + z3 + (dz / 5 * 4));
                            coords[i++] = (float) (ptB.getX() - x3 + (dx / 5 * 4));
                            coords[i++] = (float) (ptB.getY() - y3 + (dy / 5 * 4));
                            coords[i++] = (float) (ptB.getZ() - z3 + (dz / 5 * 4));

                            colors[j++] = atomB.getRed();
                            colors[j++] = atomB.getGreen();
                            colors[j++] = atomB.getBlue();
                            colors[j++] = atomE.getRed();
                            colors[j++] = atomE.getGreen();
                            colors[j++] = atomE.getBlue();
                            colors[j++] = atomB.getRed();
                            colors[j++] = atomB.getGreen();
                            colors[j++] = atomB.getBlue();
                            colors[j++] = atomE.getRed();
                            colors[j++] = atomE.getGreen();
                            colors[j++] = atomE.getBlue();
                            colors[j++] = atomB.getRed();
                            colors[j++] = atomB.getGreen();
                            colors[j++] = atomB.getBlue();
                            colors[j++] = atomE.getRed();
                            colors[j++] = atomE.getGreen();
                            colors[j++] = atomE.getBlue();
                            colors[j++] = atomB.getRed();
                            colors[j++] = atomB.getGreen();
                            colors[j++] = atomB.getBlue();
                            colors[j++] = atomE.getRed();
                            colors[j++] = atomE.getGreen();
                            colors[j++] = atomE.getBlue();
                            colors[j++] = atomB.getRed();
                            colors[j++] = atomB.getGreen();
                            colors[j++] = atomB.getBlue();
                            colors[j++] = atomE.getRed();
                            colors[j++] = atomE.getGreen();
                            colors[j++] = atomE.getBlue();
                        } else {
                            if ((bond.order == Order.SINGLE) || (bond.order == Order.TRIPLE) || (bond.order.getOrderNum() == 7)
                                    || (bond.order.getOrderNum() == 9)) {
                                atomB.setProperty(Atom.LABEL);
                                atomE.setProperty(Atom.LABEL);

                                coords[i++] = (float) ptB.getX();
                                coords[i++] = (float) ptB.getY();
                                coords[i++] = (float) ptB.getZ();
                                coords[i++] = (float) ptE.getX();
                                coords[i++] = (float) ptE.getY();
                                coords[i++] = (float) ptE.getZ();
                                colors[j++] = atomB.getRed();
                                colors[j++] = atomB.getGreen();
                                colors[j++] = atomB.getBlue();
                                colors[j++] = atomE.getRed();
                                colors[j++] = atomE.getGreen();
                                colors[j++] = atomE.getBlue();
                            }

                            if ((bond.order == Order.DOUBLE) || (bond.order == Order.TRIPLE) || (bond.order.getOrderNum() == 8)) {
                                coords[i++] = (float) (ptB.getX() + x3);
                                coords[i++] = (float) (ptB.getY() + y3);
                                coords[i++] = (float) (ptB.getZ() + z3);
                                coords[i++] = (float) (ptE.getX() + x3);
                                coords[i++] = (float) (ptE.getY() + y3);
                                coords[i++] = (float) (ptE.getZ() + z3);

                                coords[i++] = (float) (ptB.getX() - x3);
                                coords[i++] = (float) (ptB.getY() - y3);
                                coords[i++] = (float) (ptB.getZ() - z3);
                                coords[i++] = (float) (ptE.getX() - x3);
                                coords[i++] = (float) (ptE.getY() - y3);
                                coords[i++] = (float) (ptE.getZ() - z3);

                                colors[j++] = atomB.getRed();
                                colors[j++] = atomB.getGreen();
                                colors[j++] = atomB.getBlue();
                                colors[j++] = atomE.getRed();
                                colors[j++] = atomE.getGreen();
                                colors[j++] = atomE.getBlue();
                                colors[j++] = atomB.getRed();
                                colors[j++] = atomB.getGreen();
                                colors[j++] = atomB.getBlue();
                                colors[j++] = atomE.getRed();
                                colors[j++] = atomE.getGreen();
                                colors[j++] = atomE.getBlue();
                            }
                        }
                    }
                }

            }
        }

        return i;
    }

    public int getBonds(int iStructure, Bond[] bondArray) {
        int k = 0;
        updateBondArray();
        for (Bond bond : bonds) {
            if (bond.getProperty(Bond.DISPLAY)) {
                Atom atomB = bond.begin;
                Atom atomE = bond.end;

                if ((atomB != null) && (atomE != null)) {

                    SpatialSet spatialSet = atomB.spatialSet;
                    Point3 ptB = atomB.getPoint(iStructure);
                    Point3 ptE = atomE.getPoint(iStructure);

                    if ((ptB != null) && (ptE != null)) {

                        if (((bond.stereo == Bond.STEREO_BOND_UP) && (atomE.nonHydrogens < atomB.nonHydrogens))
                                || ((bond.stereo == Bond.STEREO_BOND_DOWN) && (atomE.nonHydrogens > atomB.nonHydrogens))) {
                            bondArray[k++] = bond;
                            bondArray[k++] = bond;
                            bondArray[k++] = bond;

                        } else if (((bond.stereo == Bond.STEREO_BOND_DOWN) && (atomE.nonHydrogens < atomB.nonHydrogens))
                                || ((bond.stereo == Bond.STEREO_BOND_UP) && (atomE.nonHydrogens > atomB.nonHydrogens))) {
                            bondArray[k++] = bond;
                            bondArray[k++] = bond;
                            bondArray[k++] = bond;
                            bondArray[k++] = bond;
                            bondArray[k++] = bond;
                        } else {
                            if ((bond.order == Order.SINGLE) || (bond.order == Order.TRIPLE) || (bond.order.getOrderNum() == 7)
                                    || (bond.order.getOrderNum() == 9)) {
                                atomB.setProperty(Atom.LABEL);
                                atomE.setProperty(Atom.LABEL);
                                bondArray[k++] = bond;

                            }

                            if ((bond.order == Order.DOUBLE) || (bond.order == Order.TRIPLE) || (bond.order.getOrderNum() == 8)) {
                                bondArray[k++] = bond;
                                bondArray[k++] = bond;
                            }
                        }
                    }
                }

            }
        }

        return k;
    }

    public int getLineCount(int iStructure) {
        int i = 0;
        Atom atomB;
        Atom atomE;
        Point3 ptB;
        Point3 ptE;
        updateBondArray();
        for (Bond bond : bonds) {
            if (bond.getProperty(Bond.DISPLAY)) {
                atomB = bond.begin;
                atomE = bond.end;

                if ((atomB != null) && (atomE != null)) {
                    ptB = atomB.getPoint(iStructure);
                    ptE = atomE.getPoint(iStructure);

                    if ((ptB != null) && (ptE != null)) {
                        if (((bond.stereo == Bond.STEREO_BOND_UP) && (atomE.nonHydrogens < atomB.nonHydrogens))
                                || ((bond.stereo == Bond.STEREO_BOND_DOWN) && (atomE.nonHydrogens > atomB.nonHydrogens))) {
                            i += 3;
                        } else if (((bond.stereo == Bond.STEREO_BOND_DOWN) && (atomE.nonHydrogens < atomB.nonHydrogens))
                                || ((bond.stereo == Bond.STEREO_BOND_UP) && (atomE.nonHydrogens > atomB.nonHydrogens))) {
                            i += 5;
                        } else if (bond.order.getOrderNum() < 4) {
                            i += bond.order.getOrderNum();
                        } else if (bond.order.getOrderNum() == 8) {
                            i += 2;
                        } else {
                            i++;
                        }
                    }
                }
            }
        }

        return i;
    }

    public void calcCorner(int iStructure) throws MissingCoordinatesException {
        int n;
        double x = 0;
        double y = 0;
        double z = 0;
        Point3 pt;
        n = 0;
        updateAtomArray();
        for (Atom atom : atoms) {
            pt = atom.getPoint(iStructure);

            if (pt != null) {
                x += pt.getX();
                y += pt.getY();
                z += pt.getZ();
                n++;
            }

        }

        if (n == 0) {
            throw new MissingCoordinatesException("couldn't calculate center: no coordinates");
        }

        center[0] = x / n;
        center[1] = y / n;
        center[2] = z / n;
    }

    public void updateCenter(int iStructure) throws MissingCoordinatesException {
        center = getCenter(iStructure);
    }

    public double[] getCenter(int iStructure) throws MissingCoordinatesException {
        int n;
        double x = 0;
        double y = 0;
        double z = 0;
        Point3 pt;
        n = 0;

        updateAtomArray();
        for (Atom atom : atoms) {
            pt = atom.getPoint(iStructure);

            if (pt != null) {
                x += pt.getX();
                y += pt.getY();
                z += pt.getZ();
                n++;
            }
        }

        if (n == 0) {
            throw new MissingCoordinatesException("couldn't calculate center: no coordinates");
        }
        double[] mCenter = new double[3];
        mCenter[0] = x / n;
        mCenter[1] = y / n;
        mCenter[2] = z / n;
        return mCenter;
    }

    public double[] getCenterOfSelected(int iStructure) throws MissingCoordinatesException {
        double x = 0;
        double y = 0;
        double z = 0;
        int n = 0;
        for (int i = 0; i < globalSelected.size(); i++) {
            SpatialSet spatialSet = (SpatialSet) globalSelected.get(i);
            Point3 pt = spatialSet.getPoint(iStructure);
            if (pt != null) {
                x += pt.getX();
                y += pt.getY();
                z += pt.getZ();
                n++;
            }

        }

        if (n == 0) {
            return getCenter(iStructure);
        } else {
            double[] mCenter = new double[3];
            mCenter[0] = x / n;
            mCenter[1] = y / n;
            mCenter[2] = z / n;
            return mCenter;
        }
    }

    public Vector3D[] getCorner(int iStructure) throws MissingCoordinatesException {
        int n;
        Point3 pt;
        n = 0;
        double[] coords = new double[3];
        double[][] corner = new double[2][3];
        for (int iC = 0; iC < 3; iC++) {
            corner[0][iC] = Double.MAX_VALUE;
            corner[1][iC] = Double.NEGATIVE_INFINITY;
        }
        for (Atom atom : atoms) {
            pt = atom.getPoint(iStructure);

            if (pt != null) {
                coords[0] = pt.getX();
                coords[1] = pt.getY();
                coords[2] = pt.getZ();
                for (int iC = 0; iC < 3; iC++) {
                    if (coords[iC] < corner[0][iC]) {
                        corner[0][iC] = coords[iC];
                    }
                    if (coords[iC] > corner[1][iC]) {
                        corner[1][iC] = coords[iC];
                    }
                }
                n++;
            }
        }

        if (n == 0) {
            throw new MissingCoordinatesException("couldn't calculate center: no coordinates");
        }
        double atomRadius = 2.5;
        for (int i = 0; i < 3; i++) {
            corner[0][i] -= atomRadius;
            corner[1][i] += atomRadius;
        }
        Vector3D minCorner = new Vector3D(corner[0][0], corner[0][1], corner[0][2]);
        Vector3D maxCorner = new Vector3D(corner[1][0], corner[1][1], corner[1][2]);
        Vector3D[] result = {minCorner, maxCorner};
        return result;
    }

    /**
     * Rotates a given set of axes based on an SVD calculation.
     *
     * @param inputAxes double[][] coordinates of the orginal axes
     * @return RealMatrix coordinates of the rotated axes
     */
    public RealMatrix calcSVDAxes(double[][] inputAxes) {
        RealMatrix rotMat = getSVDRotationMatrix(true);
        RealMatrix inputAxesM = new Array2DRowRealMatrix(inputAxes);
        RealMatrix axes = rotMat.multiply(inputAxesM);

        return axes;
    }

    /**
     * Rotates a given set of axes based on a previously run RDC calculation.
     *
     * @param inputAxes double[][] coordinates of the orginal axes
     * @return RealMatrix coordinates of the rotated axes
     */
    public RealMatrix getRDCAxes(double[][] inputAxes) {
        RealMatrix rotMat = getRDCRotationMatrix(true);
        if (rotMat == null) {
            return null;
        } else {
            RealMatrix inputAxesM = new Array2DRowRealMatrix(inputAxes);
            RealMatrix axes = rotMat.multiply(inputAxesM);
            return axes;
        }
    }

    public RealMatrix getRDCRotationMatrix(boolean scaleMat) {
        EigenDecomposition rdcEig;
        if (alignmentMat == null) {
            AlignmentCalc aCalc = new AlignmentCalc(this, true, 2.0);
            aCalc.center();
            aCalc.genAngles(122, 18, 1.0);
            aCalc.findMinimums();
            double slabWidth = 0.2;
            double f = 0.01;
            double d = 40.0;
            aCalc.calcCylExclusions(slabWidth, f, d, "bicelle");
            aCalc.calcTensor(0.8);
            alignmentMat = aCalc.getAlignment();
            rdcEig = alignmentMat.getEig();
        } else {
            rdcEig = alignmentMat.getEig();
        }
        double[] eigValues = rdcEig.getRealEigenvalues();
        double maxEig = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(eigValues[i]) > maxEig) {
                maxEig = Math.abs(eigValues[i]);
            }
        }
        RealMatrix rotMat = alignmentMat.getEigenVectors();
        if (scaleMat) {
            for (int i = 0; i < 3; i++) {
                double scale = Math.abs(eigValues[i] / maxEig);
                rotMat.setEntry(i, 0, rotMat.getEntry(i, 0) * scale);
                rotMat.setEntry(i, 1, rotMat.getEntry(i, 1) * scale);
                rotMat.setEntry(i, 2, rotMat.getEntry(i, 2) * scale);
            }
        }
        return rotMat;

    }

    public RealMatrix getSVDRotationMatrix(boolean scaleMat) {
        Point3 pt;
        double[] c = new double[3];
        try {
            c = getCenter(0);
        } catch (MissingCoordinatesException ex) {
            log.warn(ex.getMessage(), ex);
        }
        List<double[]> molecCoords = new ArrayList<>();
        for (Atom atom : atoms) {
            pt = atom.getPoint();
            if (pt != null) {
                double[] aCoords = pt.toArray();
                for (int i = 0; i < aCoords.length; i++) {
                    aCoords[i] -= c[i];
                }
                molecCoords.add(aCoords);
            }
        }
        double[][] mCoords1 = new double[molecCoords.size()][3];
        for (int i = 0; i < mCoords1.length; i++) {
            mCoords1[i] = molecCoords.get(i);
        }
        RealMatrix mCoordsR = new Array2DRowRealMatrix(mCoords1);
        SingularValueDecomposition svd = new SingularValueDecomposition(mCoordsR);
        RealMatrix rotMat = svd.getVT();
        RealMatrix uMat = svd.getU();
        RealMatrix sMat = svd.getS();
        double[] s = svd.getSingularValues();
        double maxX = 0.0;
        for (int i = 0; i < uMat.getRowDimension(); i++) {
            double x = Math.abs(uMat.getEntry(i, 0));
            if (x > maxX) {
                maxX = x;
            }
        }
        for (int i = 0; i < s.length; i++) {
            sMat.setEntry(i, i, sMat.getEntry(i, i) * maxX);
        }
        if (scaleMat) {
            rotMat = rotMat.preMultiply(sMat);
        }
        return rotMat;
    }

    public void center(int iStructure) {
        Point3 pt;
        Point3 cPt = new Point3(center[0], center[1], center[2]);
        updateAtomArray();
        for (Atom atom : atoms) {
            pt = atom.getPoint(iStructure);

            if (pt != null) {
                Vector3D vpt = pt.subtract(cPt);
                atom.setPoint(iStructure, new Point3(vpt));
            }
        }
    }

    public void centerStructure(int iStructure) throws MissingCoordinatesException {
        Point3 pt;
        double[] mcenter = getCenter(iStructure);
        Point3 cPt = new Point3(mcenter[0], mcenter[1], mcenter[2]);
        updateAtomArray();
        for (Atom atom : atoms) {
            pt = atom.getPoint(iStructure);

            if (pt != null) {
                Vector3D vpt = pt.subtract(cPt);
                atom.setPoint(iStructure, new Point3(vpt));
            }
        }
    }

    public List<String> listAtomsWithProperty(int property) {

        List<String> list = new ArrayList<>();
        updateAtomArray();
        for (Atom atom : atoms) {
            if (atom.getProperty(property)) {
                SpatialSet spatialSet = atom.getSpatialSet();
                list.add(spatialSet.getFullName());
            }
        }

        return list;
    }

    public List<Atom> getAtomsWithProperty(String propertyName) {
        List<Atom> list = new ArrayList<>();
        updateAtomArray();
        for (Atom atom : atoms) {
            Object prop = atom.getProperty(propertyName);
            if ((prop instanceof Boolean) && ((Boolean) prop)) {
                SpatialSet spatialSet = atom.getSpatialSet();
                list.add(spatialSet.getAtom());
            }
        }
        return list;
    }

    public int createSphereArray(int iStructure, float[] coords, int i, float[] colors, float[] values) {
        int j;
        int k = 0;
        Point3 pt;
        updateAtomArray();
        for (Atom atom : atoms) {
            atom.unsetProperty(Atom.LABEL);

            if (atom.getProperty(Atom.DISPLAY)) {
                pt = atom.getPoint(iStructure);

                if (pt != null) {
                    atom.setProperty(Atom.LABEL);
                    j = i;
                    coords[i++] = (float) pt.getX();
                    coords[i++] = (float) pt.getY();
                    coords[i++] = (float) pt.getZ();
                    colors[j++] = atom.getRed();
                    colors[j++] = atom.getGreen();
                    colors[j++] = atom.getBlue();
                    values[k++] = atom.value;
                }
            }
        }

        return i;
    }

    public int createLabelArray(int iStructure, float[] coords, int i) {
        Point3 pt;
        Iterator e = coordSets.values().iterator();
        CoordSet coordSet;
        updateAtomArray();
        for (Atom atom : atoms) {
            if (atom.getProperty(Atom.LABEL)) {
                pt = atom.getPoint(iStructure);

                if (pt != null) {
                    coords[i++] = (float) pt.getX();
                    coords[i++] = (float) pt.getY();
                    coords[i++] = (float) pt.getZ();
                }
            }
        }

        return i;
    }

    public int createSelectionArray(int iStructure, float[] coords, int[] levels) {
        int i;
        int j;
        Point3 ptB = null;
        Point3 ptE = null;
        SpatialSet spatialSet1 = null;
        SpatialSet spatialSet2 = null;

        int n = globalSelected.size();
        j = 0;
        i = 0;

        for (int k = 0; k < n; k++) {
            spatialSet1 = globalSelected.get(k);

            int selected = spatialSet1.getSelected();

            if (selected > 0) {
                ptB = spatialSet1.getPoint(iStructure);

                if (ptB != null) {
                    if ((k + 1) < n) {
                        spatialSet2 = (SpatialSet) globalSelected.get(k + 1);
                    }

                    if ((spatialSet1 == spatialSet2) || (Molecule.selCycleCount == 0) || ((k + 1) >= n)
                            || ((Molecule.selCycleCount != 1) && (((k + 1) % Molecule.selCycleCount) == 0))) {
                        coords[i++] = (float) ptB.getX();
                        coords[i++] = (float) ptB.getY();
                        coords[i++] = (float) ptB.getZ();
                        coords[i++] = (float) ptB.getX() + 0.2f;
                        coords[i++] = (float) ptB.getY() - 0.2f;
                        coords[i++] = (float) ptB.getZ();
                        coords[i++] = (float) ptB.getX() - 0.2f;
                        coords[i++] = (float) ptB.getY() - 0.2f;
                        coords[i++] = (float) ptB.getZ();
                        coords[i++] = (float) ptB.getX();
                        coords[i++] = (float) ptB.getY();
                        coords[i++] = (float) ptB.getZ();
                        coords[i++] = (float) ptB.getX() + 0.2f;
                        coords[i++] = (float) ptB.getY() - 0.2f;
                        coords[i++] = (float) ptB.getZ();
                        coords[i++] = (float) ptB.getX() - 0.2f;
                        coords[i++] = (float) ptB.getY() - 0.2f;
                        coords[i++] = (float) ptB.getZ();
                        levels[j++] = selected;
                    } else {
                        ptE = spatialSet2.getPoint(iStructure);

                        if (ptE != null) {
                            float dx = (float) (ptE.getX() - ptB.getX());
                            float dy = (float) (ptE.getY() - ptB.getY());
                            float dz = (float) (ptE.getZ() - ptB.getZ());
                            float len = (float) Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
                            float xy3 = -dy / len * 0.2f;
                            float yx3 = dx / len * 0.2f;
                            float z3 = dz / len * 0.2f;
                            float xz3 = -dz / len * 0.2f;
                            float y3 = dy / len * 0.2f;
                            float zx3 = dx / len * 0.2f;
                            coords[i++] = (float) (ptB.getX() - xy3);
                            coords[i++] = (float) (ptB.getY() - yx3);
                            coords[i++] = (float) (ptB.getZ() - z3);
                            coords[i++] = (float) (ptB.getX() + xy3);
                            coords[i++] = (float) (ptB.getY() + yx3);
                            coords[i++] = (float) (ptB.getZ() + z3);
                            coords[i++] = (float) ptB.getX() + (dx / len * 0.5f);
                            coords[i++] = (float) ptB.getY() + (dy / len * 0.5f);
                            coords[i++] = (float) ptB.getZ() + (dz / len * 0.5f);
                            coords[i++] = (float) (ptB.getX() + xz3);
                            coords[i++] = (float) (ptB.getY() + y3);
                            coords[i++] = (float) (ptB.getZ() + zx3);
                            coords[i++] = (float) (ptB.getX() - xz3);
                            coords[i++] = (float) (ptB.getY() - y3);
                            coords[i++] = (float) (ptB.getZ() - zx3);
                            coords[i++] = (float) ptB.getX() + (dx / len * 0.5f);
                            coords[i++] = (float) ptB.getY() + (dy / len * 0.5f);
                            coords[i++] = (float) ptB.getZ() + (dz / len * 0.5f);
                            levels[j++] = selected;
                        }
                    }
                }
            }
        }

        return i;
    }

    public int getSphereCount(int iStructure) {
        int i = 0;
        Point3 pt;
        updateAtomArray();
        for (Atom atom : atoms) {
            if (atom.getProperty(Atom.DISPLAY)) {
                pt = atom.getPoint(iStructure);

                if (pt != null) {
                    i++;
                }
            }
        }

        return i;
    }

    public int getLabelCount(int iStructure) {
        int i = 0;
        Point3 pt;
        updateAtomArray();
        for (Atom atom : atoms) {
            if (atom.getProperty(Atom.LABEL)) {
                pt = atom.getPoint(iStructure);

                if (pt != null) {
                    i++;
                }
            }
        }

        return i;
    }

    public static List<Bond> matchBonds() throws IllegalArgumentException {
        List<Bond> selected = new ArrayList<>(32);
        Atom atomB;
        Atom atomE;
        Molecule molecule = (Molecule) MoleculeFactory.getActive();

        if (molecule == null) {
            throw new IllegalArgumentException("No active molecule ");
        }

        molecule.updateBondArray();
        for (Bond bond : molecule.bonds) {
            atomB = bond.begin;
            atomE = bond.end;

            if ((atomB != null) && (atomE != null)) {
                if ((atomB.getSelected() > 0) && (atomE.getSelected() > 0)) {
                    bond.setProperty(Bond.SELECT);
                    selected.add(bond);
                }
            }
        }

        return (selected);
    }

    public double guessMolecularWeight() {
        updateAtomArray();
        double weight = 0.0;
        for (Atom atom:atoms) {
            if (atom.getAtomicNumber() == 1) {
                weight += 1.0;
            } else {
                weight += atom.getAtomicNumber() * 2.0;
            }
        }
        return weight;
    }
    public List<Atom> getAtoms() {
        List<Atom> atomVector = new ArrayList<>(32);
        updateAtomArray();
        for (Atom atom : atoms) {
            atomVector.add(atom);
        }

        return atomVector;
    }

    public void calcRMSD() {
        updateAtomArray();
        for (Atom atom : atoms) {
            if (atom.entity == null) {
                log.warn("Null entity {}", atom.getFullName());
            } else {
                SpatialSet spatialSet = atom.getSpatialSet();

                if (spatialSet != null) {
                    spatialSet.setBFactor((float) atom.rmsAtom(spatialSet));
                }
            }
        }
    }

    public ArrayList<HydrogenBond> hydrogenBonds(int[] structures) throws InvalidMoleculeException {
        MolFilter hydrogenFilter = new MolFilter("*.H,HN,HA");
        MolFilter acceptorFilter = new MolFilter("*.O,O*");
        return hydrogenBonds(structures, hydrogenFilter, acceptorFilter);
    }

    public ArrayList<HydrogenBond> hydrogenBonds(final int[] structures, final MolFilter hydrogenFilter,
                                                 final MolFilter acceptorFilter) throws InvalidMoleculeException {
        List<SpatialSet> hydrogens = matchAtoms(hydrogenFilter);
        List<SpatialSet> acceptors = matchAtoms(acceptorFilter);
        ArrayList<HydrogenBond> hBonds = new ArrayList<HydrogenBond>();
        for (int i = 0, n = hydrogens.size(); i < n; i++) {
            SpatialSet hydrogen = (SpatialSet) hydrogens.get(i);
            for (int j = 0, m = acceptors.size(); j < m; j++) {
                SpatialSet acceptor = (SpatialSet) acceptors.get(j);
                HydrogenBond hBondBest = null;
                double bestShift = 0.0;
                int bestStructure = 0;
                for (int structureNum : structures) {
                    boolean valid = HydrogenBond.validate(hydrogen, acceptor, structureNum);
                    if (valid) {
                        HydrogenBond hBond = new HydrogenBond(hydrogen, acceptor);
                        hBonds.add(hBond);
                        break;
                    }
                }
            }
        }
        return hBonds;
    }

    public Map<String, HydrogenBond> hydrogenBondMap(final MolFilter hydrogenFilter, final MolFilter acceptorFilter,
                                                     int structureNum) throws InvalidMoleculeException {
        List<SpatialSet> hydrogens = matchAtoms(hydrogenFilter);
        List<SpatialSet> acceptors = matchAtoms(acceptorFilter);
        Map<String, HydrogenBond> hBondMap = new HashMap<>();
        Map<String, HydrogenBond> acceptorMap = new HashMap<>();
        for (int i = 0, n = hydrogens.size(); i < n; i++) {
            SpatialSet hydrogen = (SpatialSet) hydrogens.get(i);
            HydrogenBond hBondBest = null;
            double bestShift = -1.0e6;
            for (int j = 0, m = acceptors.size(); j < m; j++) {
                SpatialSet acceptor = (SpatialSet) acceptors.get(j);
                boolean valid = HydrogenBond.validate(hydrogen, acceptor, structureNum);
                if (valid) {
                    HydrogenBond hBond = new HydrogenBond(hydrogen, acceptor);
                    double shift = hBond.getShift(structureNum);
                    if ((hBondBest == null) || (shift > bestShift)) {
                        hBondBest = hBond;
                        bestShift = shift;
                    }
                }
            }
            if (hBondBest != null) {
                HydrogenBond testBond = acceptorMap.get(hBondBest.acceptor.atom.getFullName());
                if (testBond != null) {
                    if (testBond.getShift(structureNum) < hBondBest.getShift(structureNum)) {
                        hBondMap.put(hydrogen.atom.getFullName(), hBondBest);
                        acceptorMap.put(hBondBest.acceptor.atom.getFullName(), hBondBest);
                        hBondMap.remove(testBond.hydrogen.atom.getFullName());
                    }
                } else {
                    hBondMap.put(hydrogen.atom.getFullName(), hBondBest);
                    acceptorMap.put(hBondBest.acceptor.atom.getFullName(), hBondBest);
                }
            }
        }
        return hBondMap;
    }

    public Map<String, Double> electroStaticShiftMap(final MolFilter targetFilter, final MolFilter sourceFilter,
                                                     int structureNum) throws InvalidMoleculeException {
        List<SpatialSet> targets = matchAtoms(targetFilter);
        List<SpatialSet> sources = matchAtoms(sourceFilter);
        Map<String, Double> shiftMap = new HashMap<>();
        for (int i = 0, n = targets.size(); i < n; i++) {
            SpatialSet target = targets.get(i);
            double sumShift = 0.0;
            for (int j = 0, m = sources.size(); j < m; j++) {
                SpatialSet source = sources.get(j);
                boolean valid = ElectrostaticInteraction.validate(target, source, structureNum);
                if (valid) {
                    ElectrostaticInteraction eInteraction = new ElectrostaticInteraction(target, source);
                    sumShift += eInteraction.getShift(structureNum);
                }
            }
            shiftMap.put(target.atom.getFullName(), sumShift);
        }
        return shiftMap;
    }

    public void checkRNAPairs() {
        for (Polymer polymerA : getPolymers()) {
            for (Polymer polymerB : getPolymers()) {
                for (Residue residueA : polymerA.getResidues()) {
                    for (Residue residueB : polymerB.getResidues()) {
                        if (residueA != residueB) {

                            int paired = BasePair.getBasePairType(residueA, residueB);
                            if (paired != 0) {
                                log.info("{} {} {}", paired, residueA.getName(), residueB.getName());
                            }
                        }

                    }
                }
            }
        }
    }

    public void calcLCMB(final int iStruct) {
        calcLCMB(iStruct, true, false);
    }

    // Biophysical Journal 96(8) 3074–3081
    public Map<String, Double> calcLCMB(final int iStruct, boolean scaleEnds, boolean useMap) {
        double r0 = 3.0;
        double a = 39.3;
        updateAtomArray();
        Map<String, Double> lcmbMap = null;
        if (useMap) {
            lcmbMap = new HashMap<>();
        }
        for (Atom atom1 : atoms) {
            SpatialSet sp1 = atom1.spatialSet;
            sp1.setOrder(0.0f);
            Polymer polymer = null;
            List<Residue> residues = null;
            double endMultiplier = 1.0;
            if (atom1.entity instanceof Residue) {
                Residue residue = (Residue) atom1.entity;
                if ((polymer == null) || (polymer != residue.polymer)) {
                    polymer = residue.polymer;
                    residues = polymer.getResidues();
                }
                int nResidues = residues.size();
                int resNum = atom1.entity.entityID;
                if (scaleEnds) {
                    if (resNum < 4) {
                        endMultiplier = 1.6 - resNum / 10.0;
                    } else if ((nResidues - resNum + 1) < 4) {
                        endMultiplier = 1.6 - (nResidues - resNum + 1) / 10.0;
                    }
                }
            }
            if (atom1.getAtomicNumber() != 1) {
                Point3 pt1 = atom1.getPoint(iStruct);
                double fSum = 0.0;
                for (Atom atom2 : atoms) {
                    if ((atom1 != atom2) && (atom2.getAtomicNumber() != 1)) {
                        SpatialSet sp2 = atom2.spatialSet;
                        Point3 pt2 = atom2.getPoint(iStruct);
                        if ((pt1 != null) && (pt2 != null)) {
                            double r = Atom.calcDistance(pt1, pt2);
                            if (r < 15.0) {
                                fSum += a * Math.exp(-r / r0);
                            }
                        }
                    }
                }
                double bFactor = 1.0e4 / fSum * endMultiplier;
                if (lcmbMap != null) {
                    lcmbMap.put(atom1.getFullName(), bFactor);
                }
                sp1.setOrder((float) bFactor);
            }
        }
        return lcmbMap;
    }

    // Biophysical Journal 96(8) 3074–3081
    public Map<String, Double> calcContactSum(final int iStruct, boolean useMap) {
        double r0 = 3.0;
        double a = 39.3;
        updateAtomArray();
        Map<String, Double> lcmbMap = null;
        if (useMap) {
            lcmbMap = new HashMap<>();
        }
        for (Atom atom1 : atoms) {
            SpatialSet sp1 = atom1.spatialSet;
            sp1.setOrder(0.0f);
            Point3 pt1 = atom1.getPoint(iStruct);
            double fSum = 0.0;
            for (Atom atom2 : atoms) {
                if (atom1 != atom2) {
                    Point3 pt2 = atom2.getPoint(iStruct);
                    if ((pt1 != null) && (pt2 != null)) {
                        double r = Atom.calcDistance(pt1, pt2);
                        if (r < 15.0) {
                            fSum += a * Math.exp(-r / r0);
                        }
                    }
                }
            }
            double contactSum = fSum;
            if (lcmbMap != null) {
                lcmbMap.put(atom1.getFullName(), contactSum);
            }
            sp1.setOrder((float) contactSum);
        }
        return lcmbMap;
    }

    public double[] calcDistanceInputMatrixRow(final int iStruct, double distLim, Atom targetAtom) {
        return calcDistanceInputMatrixRow(iStruct, distLim, targetAtom, 1.0);

    }

    public double[] calcDistanceInputMatrixRow(final int iStruct, double distLim, Atom targetAtom, double intraScale) {
        List origAtomSources = RNAAttributes.getAtomSources();
        int numAtomSources = origAtomSources.size();
        int sepIntra = 0;
        double[] distValues = new double[numAtomSources * (1 + sepIntra)];

        Point3 targetPt = targetAtom.getPoint(iStruct);
        for (Atom sourceAtom : atoms) {
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
                    double r = Atom.calcDistance(targetPt, sourcePt);
                    if (r < distLim && origAtomSources.contains(key)) {
                        int keyInd = origAtomSources.indexOf(key);
                        double dis3 = Math.pow(r, -3);
                        if (r == 0) {
                            dis3 = 0.0;
                        }
                        if (sourceResID != targetResID) {
                            keyInd += numAtomSources * sepIntra;
                        } else {
                            dis3 *= intraScale;
                        }
                        distValues[keyInd] += dis3;
                    }
                }
            }
        }
        return distValues;
    }

    // J. AM. CHEM. SOC. 2002, 124, 12654-12655
    public void calcContactOrder(final int iStruct, boolean scaleEnds) {
        double r0 = 1.0;
        updateAtomArray();
        List<Polymer> polymers = getPolymers();
        for (Polymer polymer : polymers) {
            List<Residue> residues = polymer.getResidues();
            for (Residue residue : residues) {
                Residue previousResidue = residue.previous;
                if (previousResidue == null) {
                    continue;
                }
                Atom atomH = residue.getAtom("H");
                Atom atomO = previousResidue.getAtom("O");
                if ((atomH == null) || (atomO == null)) {
                    continue;
                }
                Point3 ptH = atomH.getPoint(iStruct);
                Point3 ptO = atomO.getPoint(iStruct);

                double fSum = 0.0;
                for (Atom atom2 : atoms) {
                    if (atom2.entity == residue) {
                        continue;
                    }
                    if (atom2.entity == previousResidue) {
                        continue;
                    }
                    if (atom2.getAtomicNumber() == 1) {
                        continue;
                    }
                    Point3 pt2 = atom2.getPoint(iStruct);
                    double rH = Atom.calcDistance(ptH, pt2);
                    double rO = Atom.calcDistance(ptO, pt2);
                    fSum += Math.exp(-rO / r0) + 0.8 * Math.exp(-rH / r0);
                }
                // note the paper has 0.8 insteand of 2.0, but 2.0 gives more reasonable numbers
                //  needs to be optimized
                double order = Math.tanh(2.0 * fSum) - 0.1;
                atomH.setOrder((float) order);
                atomH.setBFactor((float) order);
            }
        }

    }

    public ArrayList<Atom> getAtoms(String selection) {
        MolFilter molFilter = new MolFilter(selection);
        return getMatchedAtoms(molFilter, this);
    }

    public void updateNames() {
        Residue firstResidue = null;
        Residue lastResidue = null;
        Compound compound;
        Entity entity;
        Iterator e = entities.values().iterator();

        while (e.hasNext()) {
            entity = (Entity) e.next();

            if (entity instanceof Polymer) {
                Polymer polymer = (Polymer) entity;
                firstResidue = polymer.getFirstResidue();

                lastResidue = polymer.getLastResidue();
                compound = (Compound) firstResidue;
            } else {
                compound = (Compound) entity;
            }

            while (compound != null) {
                compound.updateNames();

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
    }

    @Override
    public void setupRotGroups() {
        int rotUnit = 0;
        List<Atom> atomList;
        if (treeAtoms == null) {
            atomList = atoms;
        } else {
            atomList = treeAtoms;
        }
        for (Atom iAtom : atomList) {
            iAtom.rotUnit = -1;
            iAtom.rotGroup = null;
            if ((iAtom.getParent() != null) && (iAtom.irpIndex > 0) && iAtom.rotActive) {
                //if (iAtom.irpIndex > 0) {
                iAtom.rotUnit = rotUnit++;
            }
            Atom jAtom = iAtom;

            // careful in following using jAtom.parent is different than jAtom.getParent
            // the latter will find parent using bonds if parent field null
            while ((jAtom = jAtom.parent) != null) {
                //if (jAtom.irpIndex != 0) {
                if ((jAtom.irpIndex > 0) && jAtom.rotActive) {
                    iAtom.rotGroup = jAtom;
                    break;
                }
            }
        }
        genAngleBranches();
        setupIRPTypes();
    }

    public void setupIRPTypes() {
        try {
            AtomEnergyProp.makeIrpMap();
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
        List<Atom> atomList;
        if (treeAtoms == null) {
            atomList = atoms;
        } else {
            atomList = treeAtoms;
        }
        for (Atom iAtom : atomList) {
            Atom parent = iAtom.getParent();
            Atom grandparent = parent != null ? parent.getParent() : null;
            Atom daughter = iAtom.daughterAtom;
            if ((parent != null) && (grandparent != null) && (daughter != null) && (iAtom.irpIndex > 0) && (iAtom.rotActive)) {
                String aType = iAtom.getType();
                String pType = parent.getType();
                String gType = grandparent.getType();
                String dType = daughter.getType();
                String torsionType = gType + "-" + pType + "-" + aType + "-" + dType;
                int irpIndex = AtomEnergyProp.getTorsionIndex(torsionType);
                if (irpIndex == 0) {
                    irpIndex = 9999;
                }
                iAtom.irpIndex = irpIndex;
            }
        }
    }

    public void setRingClosures(Map<Atom, Map<Atom, Double>> bonds) {
        if (ringClosures == null) {
            ringClosures = bonds;
        } else {
            for (Atom atom1 : bonds.keySet()) {
                if (ringClosures.containsKey(atom1)) {
                    for (Atom atom2 : bonds.get(atom1).keySet()) {
                        if (ringClosures.get(atom1).containsKey(atom2)) {
                            // This will prevent overwriting if a value exists
                            continue;
                        } else {
                            ringClosures.get(atom1).put(atom2, bonds.get(atom1).get(atom2));
                        }
                    }
                } else {
                    ringClosures.put(atom1, bonds.get(atom1));
                }
            }
        }
    }

    public Map<Atom, Map<Atom, Double>> getRingClosures() {
        if (ringClosures == null) {
            ringClosures = new HashMap<>();
        }
        return ringClosures;
    }

    public void setRiboseActive(boolean state) {
        updateAtomArray();
        atoms.stream().filter((iAtom) -> (iAtom.getName().equalsIgnoreCase("C3'") || iAtom.getName().equalsIgnoreCase("C2'")
                || iAtom.getName().equalsIgnoreCase("C1'"))).forEachOrdered((iAtom) -> {
            boolean current = iAtom.rotActive;
            iAtom.rotActive = state;
        });
        setupRotGroups();
        setupAngles();
    }

    @Override
    public void setMethylRotationActive(boolean state) {
        for (Atom iAtom : atoms) {
            if (iAtom.isMethyl()) {
                Atom parent = iAtom.getParent();
                if (parent != null) {
                    parent.rotActive = state;
                }
            }
        }
        setupRotGroups();
        setupAngles();
    }

    public List<Atom> getAngleAtoms() {
        return angleAtoms;
    }

    @Override
    public List<Atom> setupAngles() {
        if (genVecs == null) {
            setupGenCoords();
        }
        angleAtoms = new ArrayList<Atom>();

        for (int i = 0; i < genVecs.length; i++) {
            if (genVecs[i].length > 3) {
                Atom angleAtom = treeAtoms.get(genVecs[i][2]);
                if ((angleAtom.getParent() != null) && (angleAtom.irpIndex > 0) && angleAtom.rotActive) {
                    angleAtoms.add(angleAtom);
                }
            }

        }
        return angleAtoms;
    }

    public List<Atom> getAllAngleAtoms() {
        List<Atom> allAngleAtoms = new ArrayList<Atom>();

        for (int i = 0; i < genVecs.length; i++) {
            if (genVecs[i].length > 3) {
                Atom angleAtom = treeAtoms.get(genVecs[i][2]);
                if ((angleAtom.getParent() != null) && (angleAtom.irpIndex > 0)) {
                    allAngleAtoms.add(angleAtom);
                }
            }

        }
        return allAngleAtoms;
    }

    public ArrayList<Atom> getPseudoAngleAtoms() {
        return pseudoAngleAtoms;
    }

    public ArrayList<Atom> setupPseudoAngles() {
        pseudoAngleAtoms = new ArrayList<Atom>();
        for (Atom iAtom : atoms) {
            if (iAtom.getName().equals("O3'") || iAtom.getName().equals("C1'")) {
                pseudoAngleAtoms.add(iAtom);
            } else if ((iAtom.getName().charAt(0) == 'N') && (iAtom.getParent() != null)
                    && (iAtom.getParent().getName().equals("C1'"))) {
                pseudoAngleAtoms.add(iAtom);
            }
        }
        return pseudoAngleAtoms;
    }

    public void getBondsBroken() {
        /*
     Residue residue = null;
     int i;
     String atomName1;
     String atomName2;
     Atom atom1 = null;
     Atom atom2 = null;
     Vector residueBonds = null;
     Entity entity;

     Iterator e = entities.values().iterator();

     while (e.hasNext()) {
     entity = (Entity) e.next();

     if (entity instanceof Polymer) {
     Polymer polymer = (Polymer) entity;
     residue = polymer.firstResidue;
     } else {
     continue;
     }
     while (residue != null) {
     // fixme  need to get residueBonds
     //              residueBonds = (Vector) residueTable(residue.name);
     for (i = 0; i < residueBonds.size(); i += 2) {
     atomName1 = (String) residueBonds.elementAt(i);
     atomName2 = (String) residueBonds.elementAt(i + 1);
     atom1 = residue.getAtom(atomName1);

     if (atom1 == null) {
     continue;
     }

     atom2 = residue.getAtom(atomName2);

     if (atom2 == null) {
     continue;
     }

     Bond bond = new Bond(atom1, atom2);
     atom1.addBond(bond);

     if (residue.polymer.firstBond == null) {
     residue.polymer.firstBond = bond;
     }

     bond.previous = residue.polymer.lastBond;

     if (residue.polymer.lastBond != null) {
     residue.polymer.lastBond.next = bond;
     }

     bond.next = null;
     residue.polymer.lastBond = bond;
     }

     residue = residue.next;
     }
     }
     *
         */
    }

    public boolean isDisulfide(Atom sg1, int iStruct) {
        boolean result = false;
        if (sg1.getName().equals("SG")) {
            Point3 pt1 = sg1.getPoint(iStruct);
            for (Atom sg2 : atoms) {
                if ((sg1 != sg2) && sg2.getName().equals("SG")) {
                    Point3 pt2 = sg2.getPoint(iStruct);
                    if (pt2 != null) {
                        double distance = Atom.calcDistance(pt1, pt2);
                        if (distance < 3.0) {
                            result = true;
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }

    public ArrayList<AtomPairDistance> getDistancePairs(double tolerance, boolean requireActive) {
        int[] structures = getActiveStructures();
        if (structures.length == 0) {
            structures = new int[1];
        }
        Atom atom1 = null;
        Atom atom2 = null;
        int result;
        int nBonds = 0;
        ArrayList<AtomPairDistance> pairs = new ArrayList<AtomPairDistance>();
        for (int i = 0; i < globalSelected.size(); i++) {
            atom1 = globalSelected.get(i).atom;
            if (atom1.getSelected() != 1) {
                continue;
            }
            if (requireActive && !atom1.isActive()) {
                continue;
            }
            if (atom1.isMethyl()) {
                if (!atom1.isFirstInMethyl()) {
                    continue;
                }
            }
            // skip hydrogens that are likely to be in rapid exchange
            if (atom1.getParent() == null) {
                log.warn("atom1 parent null {}", atom1.getFullName());
            } else {
                if ((atom1.getAtomicNumber() == 1) && atom1.getParent().getType().equals("N+")) {
                    continue;
                }

                if ((atom1.getAtomicNumber() == 1) && atom1.getParent().getType().startsWith("O")) {
                    continue;
                }
            }
            for (int j = i + 1; j < globalSelected.size(); j++) {
                double extra = 0.0;
                if (atom1.isMethyl()) {
                    extra += 0.7;
                }
                atom2 = globalSelected.get(j).atom;
                if (atom2.getSelected() != 1) {
                    continue;
                }
                // skip hydrogens that are likely to be in rapid exchange
                if (atom2.getParent() == null) {
                    log.warn("atom2 parent null {}", atom2.getFullName());
                } else {

                    if ((atom2.getAtomicNumber() == 1) && atom2.getParent().getType().equals("N+")) {
                        continue;
                    }
                    if ((atom2.getAtomicNumber() == 1) && atom2.getParent().getType().startsWith("O")) {
                        continue;
                    }
                }
                if (requireActive && !atom2.isActive()) {
                    continue;
                }
                if (atom2.isMethyl()) {
                    if (!atom2.isFirstInMethyl()) {
                        continue;
                    }
                    extra += 0.7;
                }
                boolean foundPair = false;
                double minDis = Double.MAX_VALUE;
                for (int iStruct : structures) {
                    Point3 pt1;
                    if (atom1.isMethyl()) {
                        pt1 = atom1.getMethylCenter(iStruct);
                    } else {
                        pt1 = atom1.getPoint(iStruct);
                    }
                    Point3 pt2;
                    if (atom2.isMethyl()) {
                        pt2 = atom2.getMethylCenter(iStruct);
                    } else {
                        pt2 = atom2.getPoint(iStruct);
                    }
                    if (pt1 == null) {
                        log.warn("null point for {} {}", atom1.getShortName(), iStruct);
                        continue;
                    }
                    if (pt2 == null) {
                        log.warn("null point for {} {}", atom2.getShortName(), iStruct);
                        continue;
                    }
                    double distance = Atom.calcDistance(pt1, pt2);
                    if (distance < (tolerance + extra)) {
                        if (distance < minDis) {
                            minDis = distance;
                        }
                        foundPair = true;
                    }
                }
                if (foundPair) {
                    AtomPairDistance pair = new AtomPairDistance(atom1, atom2, minDis);
                    pairs.add(pair);
                }
            }
        }
        return pairs;
    }

    public static void getCouplings(final Entity entity, final ArrayList<JCoupling> jCouplings,
                                    final ArrayList<JCoupling> tocsyLinks, final ArrayList<JCoupling> hmbcLinks,
                                    int nShells, int minShells, int tocsyShells, int hmbcShells) {
        MoleculeBase molecule = entity.molecule;
        molecule.getAtomTypes();

        MTree mTree = new MTree();
        MTree mTreeJ = new MTree();
        HashMap<Atom, Integer> hash = new HashMap<>();
        HashMap<Atom, Integer> hashJ = new HashMap<>();
        List<Atom> eAtomList = new ArrayList<>();
        List<Atom> eAtomListJ = new ArrayList<>();
        int i = 0;

        for (Atom atom : entity.atoms) {
            // entity check ensures that only atoms in same residue are used
            if (atom.entity == entity) {
                if (atom.isMethyl() && !atom.isFirstInMethyl()) {
                    continue;
                }
                Atom parent = atom.getParent();
                if (parent != null) {
                    if ((parent.getAtomicNumber() == 7) && atom.isMethylene()) {
                        continue;
                    }
                    // skip hydroxyl protons
                    if ((atom.getAtomicNumber() == 1) && (parent.getAtomicNumber() == 8)) {
                        continue;
                    }
                }
                hash.put(atom, i);
                eAtomList.add(atom);
                MNode mNode = mTree.addNode();
                mNode.setAtom(atom);
                i++;
            }
        }
        for (Atom atom : entity.atoms) {
            for (int iBond = 0; iBond < atom.bonds.size(); iBond++) {
                Bond bond = atom.bonds.get(iBond);
                Integer iNodeBegin = hash.get(bond.begin);
                Integer iNodeEnd = hash.get(bond.end);

                if ((iNodeBegin != null) && (iNodeEnd != null)) {
                    mTree.addEdge(iNodeBegin, iNodeEnd);
                }
            }

        }

        // get breadth first path from each atom
        Atom[] atoms = new Atom[nShells + 1];
        int iAtom = 0;
        for (int j = 0, n = eAtomList.size(); j < n; j++) {
            Atom atomStart = eAtomList.get(j);
            if (atomStart.aNum != 1) {
                continue;
            }

            mTree.broad_path(j);
            ArrayList<MNode> pathNodes = mTree.getPathNodes();
            for (MNode mNode : pathNodes) {
                Atom atomEnd = mNode.getAtom();
                int shell = mNode.getShell();
                if (shell > nShells) {
                    break;
                } else if (shell < minShells) {
                    continue;
                }

                MNode nNode = mNode;
                int nOx = 0;
                for (int iShell = shell; iShell >= 0; iShell--) {
                    atoms[iShell] = nNode.getAtom();
                    if (atoms[iShell].getAtomicNumber() == 8) {
                        nOx++;
                    }
                    nNode = nNode.getParent();
                }
                boolean gotJ = false;

                if ((nOx == 0) && (shell > 1) && (atoms[shell].aNum == 1)) {
                    gotJ = true;
                    JCoupling jCoupling = JCoupling.couplingFromAtoms(atoms, shell + 1, shell);
                    jCouplings.add(jCoupling);
                } else if ((nOx < 2) && (shell > 1) && (atoms[0].aNum == 1) && (atoms[shell].aNum == 6) && (shell <= hmbcShells)) {
                    JCoupling jCoupling = JCoupling.couplingFromAtoms(atoms, shell + 1, shell);
                    hmbcLinks.add(jCoupling);
                }
                if (gotJ) {
                    if (!hashJ.containsKey(atomStart)) {
                        hashJ.put(atomStart, iAtom);
                        eAtomListJ.add(atomStart);
                        MNode jNode = mTreeJ.addNode();
                        jNode.setAtom(atomStart);
                        iAtom++;
                    }
                    if (!hashJ.containsKey(atomEnd)) {
                        hashJ.put(atomEnd, iAtom);
                        eAtomListJ.add(atomEnd);
                        MNode jNode = mTreeJ.addNode();
                        jNode.setAtom(atomEnd);
                        iAtom++;
                    }
                    Integer iNodeBegin = hashJ.get(atomStart);
                    Integer iNodeEnd = hashJ.get(atomEnd);

                    if ((iNodeBegin != null) && (iNodeEnd != null) && (iNodeBegin.intValue() != iNodeEnd.intValue()) && (iNodeBegin < iNodeEnd)) {
                        mTreeJ.addEdge(iNodeBegin, iNodeEnd);
                    }
                }
            }
        }

        for (int j = 0, n = eAtomListJ.size(); j < n; j++) {
            Atom atomStart = eAtomListJ.get(j);
            if (atomStart.aNum != 1) {
                continue;
            }

            mTreeJ.broad_path(j);
            List<MNode> pathNodes = mTreeJ.getPathNodes();
            int numNodes = pathNodes.size();
            int shell;

            for (int k = 1; k < numNodes; k++) {
                MNode cNode = pathNodes.get(k);
                if (cNode.isRingClosure()) {
                    continue;
                }
                Atom atomEnd = cNode.getAtom();
                shell = cNode.getShell();
                if ((shell > 0) && (shell <= tocsyShells)) {
                    atoms[0] = atomStart;
                    atoms[1] = atomEnd;
                    JCoupling jCoupling = JCoupling.couplingFromAtoms(atoms, 2, shell);
                    tocsyLinks.add(jCoupling);
                }
            }
        }
    }

    public void genAngleBranches() {
        getAtomTypes();
        List<Atom> atomList;
        if (treeAtoms == null) {
            atomList = atoms;
        } else {
            atomList = treeAtoms;
        }
        MTree mTree = new MTree();
        HashMap<Atom, Integer> hash = new HashMap<>();
        ArrayList<Atom> eAtomList = new ArrayList<>();
        int i = 0;

        for (Atom atom : atomList) {
            hash.put(atom, i);
            eAtomList.add(atom);

            MNode mNode = mTree.addNode();
            mNode.setAtom(atom);
            i++;
        }

        for (Atom atom : atomList) {
            for (int iBond = 0; iBond < atom.bonds.size(); iBond++) {
                Bond bond = (Bond) atom.bonds.get(iBond);
                if (((bond.begin == atom) && (bond.end.iAtom > atom.iAtom))
                        || ((bond.end == atom) && (bond.begin.iAtom > atom.iAtom))) {
                    Integer iNodeBegin = hash.get(bond.begin);
                    Integer iNodeEnd = hash.get(bond.end);
                    if ((iNodeBegin != null) && (iNodeEnd != null)) {
                        mTree.addEdge(iNodeBegin, iNodeEnd);
                    }
                }
            }
        }

        HashMap<Atom, ArrayList<Atom>> atomBranches = new HashMap<>();
        mTree.depthFirstPath(0);
        ArrayList<MNode> pathNodes = mTree.getPathNodes();
        for (MNode mNode : pathNodes) {
            Atom atomEnd = mNode.getAtom();
            if (mNode.getParent() != null) {
                Atom atomStart = mNode.getParent().getAtom();
                if (atomStart != null) {
                    if ((atomEnd.irpIndex > 0) && (atomEnd.rotActive)) {
                        Atom lastRot = mNode.getLastRotatableAtom();
                        String lastRotName = "na";
                        if (lastRot != null) {
                            if (!atomBranches.containsKey(lastRot)) {
                                ArrayList<Atom> branch = new ArrayList<>();
                                atomBranches.put(lastRot, branch);
                            }
                            ArrayList<Atom> branch = atomBranches.get(lastRot);
                            branch.add(atomEnd);
                            lastRotName = lastRot.getShortName();
                        }
                    }
                }
            }
        }
        for (Atom atom : atomList) {
            if (atomBranches.containsKey(atom)) {
                ArrayList<Atom> branch = atomBranches.get(atom);
                Collections.sort(branch, (a, b) -> Integer.compare(a.getIndex(), b.getIndex()));
                atom.branchAtoms = new Atom[branch.size()];
                branch.toArray(atom.branchAtoms);
            } else {
                atom.branchAtoms = new Atom[0];
            }
        }

    }

    public static Atom getStartAtom(Molecule molecule) {
        List<Atom> atoms = molecule.getAtoms();
        int maxValue = 0;
        int maxAtom = 0;

        for (int i = 0, n = atoms.size(); i < n; i++) {
            Atom atom = atoms.get(i);

            if (atom.canonValue > maxValue) {
                maxValue = atom.canonValue;
                maxAtom = i;
            }
        }

        return atoms.get(maxAtom);
    }

    public static int buildTree(Molecule molecule, Atom startAtom, List<Atom> atomList, MTree mTree) {
        Map<Atom, Integer> hash = new HashMap<>();

        Entity entity = startAtom.entity;
        int i = 0;
        int iStart = 0;

        for (Atom atom : entity.atoms) {
            if (atom == startAtom) {
                iStart = i;
            }

            if (atom.entity == entity) {
                hash.put(atom, i);
                atomList.add(atom);

                MNode mNode = mTree.addNode();
                mNode.setValue(atom.canonValue);
                mNode.setAtom(atom);

                i++;
            }

        }

        for (Atom atom : entity.atoms) {
            for (int iBond = 0; iBond < atom.bonds.size(); iBond++) {
                Bond bond = atom.bonds.get(iBond);
                Integer iNodeBegin = hash.get(bond.begin);
                Integer iNodeEnd = hash.get(bond.end);

                if ((iNodeBegin != null) && (iNodeEnd != null)) {
                    mTree.addEdge(iNodeBegin.intValue(), iNodeEnd.intValue());
                }
            }
        }

        mTree.sortNodes();

        return iStart;
    }

    static Bond findBond(Atom atomB, Atom atomE) {
        Bond bond = null;

        for (int iBond = 0; iBond < atomB.bonds.size(); iBond++) {
            bond = atomB.bonds.get(iBond);

            if (((bond.begin == atomB) && (bond.end == atomE)) || ((bond.begin == atomE) && (bond.end == atomB))) {
                return bond;
            }
        }

        for (int iBond = 0; iBond < atomE.bonds.size(); iBond++) {
            bond = atomE.bonds.get(iBond);

            if (((bond.begin == atomB) && (bond.end == atomE)) || ((bond.begin == atomE) && (bond.end == atomB))) {
                return bond;
            }
        }

        log.warn("no bond");

        return null;
    }

    public static List<String> getLabelTypes() {
        List<String> list = new ArrayList<>();

        for (int i = 0; i <= LABEL_PPM; i++) {
            list.add((String) labelTypes.get(i));
        }

        return list;
    }

    public static List<String> getDisplayTypes() {
        List<String> list = new ArrayList<>();
        Iterator iter = displayTypes.iterator();

        while (iter.hasNext()) {
            list.add((String) iter.next());
        }

        return list;
    }

    public static List<String> getShapeTypes() {
        List<String> list = new ArrayList<>();
        Iterator iter = shapeTypes.iterator();

        while (iter.hasNext()) {
            list.add((String) iter.next());
        }

        return list;
    }

    public static List<String> getColorTypes() {
        List<String> list = new ArrayList<>();
        Iterator iter = colorTypes.iterator();
        while (iter.hasNext()) {
            list.add((String) iter.next());
        }
        return list;
    }

    public void updateLabels() {
        updateAtomArray();
        for (Atom atom : atoms) {
            atom.label = "";

            switch (label) {
                case LABEL_NONE: {
                    atom.label = "";

                    break;
                }

                case LABEL_LABEL: {
                    atom.label = atom.getFullName();

                    break;
                }

                case LABEL_FC: {
                    if (atom.fcharge != 0.0) {
                        atom.label = String.valueOf(atom.fcharge);
                    }

                    break;
                }

                case LABEL_SYMBOL: {
                    atom.label = String.valueOf(Atom.getElementName(atom.aNum));

                    break;
                }

                case LABEL_NUMBER: {
                    atom.label = String.valueOf(atom.iAtom);

                    break;
                }

                case LABEL_SYMBOL_AND_NUMBER: {
                    atom.label = String.valueOf(Atom.getElementName(atom.aNum)) + " " + atom.iAtom;

                    break;
                }

                case LABEL_FFC: {
                    atom.label = String.valueOf(atom.forceFieldCode);

                    break;
                }

                case LABEL_SECONDARY_STRUCTURE: {
                    atom.label = "";

                    break;
                }

                case LABEL_RESIDUE:
                    break;

                case LABEL_CHARGE: {
                    if (atom.charge != 0.0) {
                        atom.label = String.valueOf(atom.charge);
                    }

                    break;
                }

                case LABEL_VALUE: {
                    atom.label = String.valueOf(atom.value);

                    break;
                }

                case LABEL_TITLE: {
                    atom.label = "";

                    break;
                }

                case LABEL_MOLECULE_NAME: {
                    atom.label = name;

                    break;
                }

                case LABEL_STRING: {
                    atom.label = "";

                    break;
                }

                case LABEL_CUSTOM: {
                    atom.label = "";

                    break;
                }

                case LABEL_NAME: {
                    if (atom.aNum == 6) {
                        atom.label = atom.getName().substring(1);
                    } else {
                        atom.label = atom.getName();
                    }

                    break;
                }
                case LABEL_NONHC: {
                    if (atom.aNum == 6) {
                        atom.label = "";
                    } else if (atom.aNum == 1) {
                        atom.label = "";
                    } else {
                        atom.label = Atom.getElementName(atom.aNum);
                    }

                    break;
                }
                case LABEL_HPPM: {
                    ArrayList<Atom> hydrogens = getAttachedHydrogens(atom);
                    int nH = hydrogens.size();
                    log.warn("nH {}", nH);
                    PPMv ppmV0 = null;
                    PPMv ppmV1 = null;
                    Atom hAtom = null;
                    if (nH > 0) {
                        hAtom = hydrogens.get(0);
                        ppmV0 = hAtom.getPPM(0);
                        if (nH == 2) {
                            hAtom = hydrogens.get(1);
                            ppmV1 = hAtom.getPPM(0);
                        }
                    }
                    if (ppmV0 == null) {
                        atom.label = "";
                    } else if (ppmV1 == null) {
                        atom.label = String.valueOf(ppmV0.getValue());
                    } else {
                        atom.label = String.valueOf(ppmV0.getValue()) + "," + String.valueOf(ppmV1.getValue());
                    }
                    break;
                }
                case LABEL_PPM: {
                    PPMv ppmV = atom.getPPM(0);
                    if (ppmV == null) {
                        atom.label = "";
                    } else {
                        atom.label = String.valueOf(ppmV.getValue());
                    }
                    break;
                }
                default:
                    atom.label = "";
            }
        }

        labelsCurrent = true;
    }

    public void copyStructure(int source, int target) {
        Point3 ptS;
        Point3 ptT;
        structures.add(Integer.valueOf(target));
        for (Atom atom : atoms) {
            ptS = atom.getPoint(source);
            ptT = new Point3(ptS.getX(), ptS.getY(), ptS.getZ());
            atom.setPointValidity(target, true);
            atom.setPoint(target, ptT);
        }
    }

    public void createLinker(Atom atom1, Atom atom2, String bondOrder, float bondLength) {
        Order bond;
        switch (bondOrder) {
            case "SINGLE":
                bond = Order.SINGLE;
                break;
            case "DOUBLE":
                bond = Order.DOUBLE;
                break;
            case "TRIPLE":
                bond = Order.TRIPLE;
                break;
            case "QUAD":
                bond = Order.QUAD;
                break;
            default:
                bond = Order.SINGLE;
                break;
        }
        if (atom1 == null || atom2 == null) {
            return;
        }
        Atom.addBond(atom1, atom2, bond, 0, false);
        if (bond.equals(Order.SINGLE)) {
            atom2.irpIndex = 9999;
        }
        atom2.parent = atom1;
        atom2.setProperty("linker", true);
        atom2.bondLength = bondLength;
        atom2.valanceAngle = 120.0f * ((float) Math.PI / 180f);
        atom2.dihedralAngle = 180.0f * ((float) Math.PI / 180f);
    }

    public void createLinker(Atom atom1, Atom atom2, int numLinks,
                             double linkLen, double valAngle, double dihAngle) {
        /**
         * createLinker is a method to create a link between atoms in two
         * separate entities
         *
         * @param numLinks number of linker atoms to use
         * @param atom1
         * @param atom2
         */

        if (atom1 == null || atom2 == null) {
            return;
        }
        Atom curAtom = atom1;
        Atom newAtom;
        String linkRoot = "X";
        for (int i = 1; i <= numLinks; i++) {
            newAtom = curAtom.add(linkRoot + Integer.toString(i), "X", Order.SINGLE);
            newAtom.bondLength = (float) linkLen;
            newAtom.dihedralAngle = (float) (dihAngle * Math.PI / 180.0);
            newAtom.valanceAngle = (float) (valAngle * Math.PI / 180.0);
            newAtom.irpIndex = 1;
            newAtom.rotActive = true;
            newAtom.setType("XX");

            curAtom = newAtom;
            if ((i == numLinks) && (atom2 != null)) {
                Atom.addBond(curAtom, atom2, Order.SINGLE, 0, false);
                atom2.parent = curAtom;
                curAtom.daughterAtom = atom2;
            }
        }

        if (atom2 != null) {
            invalidateAtomTree();
            invalidateAtomArray();
            updateVecCoords();
            resetGenCoords();
        }
    }

    public List<Atom> createLinker(Atom atom1, Atom atom2,
                                   double[] linkLen, double[] valAngle, String[] aNames, double dihAngle) {
        /*
         * createLinker is a method to create a link between atoms in two
         * separate entities
         *
         * @param numLinks number of linker atoms to use
         * @param atom1
         * @param atom2
         */
        List<Atom> newAtoms = new ArrayList<>();

        if (atom1 == null || atom2 == null) {
            return newAtoms;
        }
        Atom curAtom = atom1;
        Atom newAtom;
        int numLinks = linkLen.length;
        for (int i = 0; i < numLinks; i++) {
            newAtom = curAtom.add(aNames[i], "X", Order.SINGLE);
            newAtom.bondLength = (float) linkLen[i];
            newAtom.dihedralAngle = (float) (dihAngle * Math.PI / 180.0);
            newAtom.valanceAngle = (float) (valAngle[i] * Math.PI / 180.0);
            newAtom.irpIndex = 1;
            newAtom.rotActive = true;
            newAtom.setType("XX");
            newAtoms.add(newAtom);

            curAtom = newAtom;
            if ((i == (numLinks - 1)) && (atom2 != null)) {
                Atom.addBond(curAtom, atom2, Order.SINGLE, 0, false);
                atom2.parent = curAtom;
                curAtom.daughterAtom = atom2;
            }
        }

        if (atom2 != null) {
            invalidateAtomTree();
            invalidateAtomArray();
            updateVecCoords();
            resetGenCoords();
        }
        return newAtoms;
    }

    @Override
    public void addNonStandardResidue(Sequence sequence, Residue residue) {
        boolean isProtein = residue.polymer.getPolymerType().contains("polypeptide");
        residue.setNonStandard();
        Atom startAtom;
        if (residue.isCompliant()) {
            residue.addConnectors();
            startAtom = residue.getAtom("X");
        } else {
            if (residue.getFirstBackBoneAtom() != null) {
                startAtom = residue.getFirstBackBoneAtom();
            } else {
                startAtom = isProtein ? residue.getAtom("CAX") : residue.getAtom("C3'X");
            }
        }

        residue.getLastBackBoneAtom().setProperty("connector", true);
        PathIterator pI = new PathIterator(residue);
        NodeValidator nV = new NodeValidator();
        pI.init(nV);
        pI.processPatterns();
        pI.setProperties("ar", "AROMATIC");
        pI.setProperties("res", "RESONANT");
        pI.setProperties("r", "RING");
        pI.setHybridization();
        AngleTreeGenerator.genMeasuredTree(residue, startAtom);
        residue.removeConnectors();

        sequence.makeConnection(residue);

        // fixme this needs to be changed for non-amino acid residue atoms
        if (isProtein) {
            Atom refAtom = residue.getAtom("O");
            if (refAtom != null) {
                residue.getAtom("O").dihedralAngle = (float) Math.PI;
            } else {
                List<Atom> atoms = residue.getLastBackBoneAtom().getChildren();
                if (atoms.size() == 1) {
                    atoms.get(0).dihedralAngle = (float) Math.PI;
                }
            }
        }
    }

}
