package org.nmrfx.chemistry;

import org.nmrfx.structure.chemistry.*;
import org.nmrfx.structure.project.StructureProject;

import java.io.*;
import java.util.*;

public class MoleculeBase implements Serializable, ITree {

    public static final List<String> conditions = new ArrayList<>();
    public static final int ENERGY = 0;
    public static final int SQ_SCORE = 1;
    public static final int GRADIENT = 2;
    public static final int R_FACTOR = 3;
    public static final int INTRA_ENERGY = 4;
    public static final int INTER_ENERGY = 5;
    public static final int LABEL_NONE = 0;
    public static final int LABEL_LABEL = 1;
    public static final int LABEL_FC = 2;
    public static final int LABEL_SYMBOL = 3;
    public static final int LABEL_NUMBER = 4;
    public static final int LABEL_SYMBOL_AND_NUMBER = 5;
    public static final int LABEL_FFC = 6;
    public static final int LABEL_SECONDARY_STRUCTURE = 7;
    public static final int LABEL_RESIDUE = 8;
    public static final int LABEL_CHARGE = 9;
    public static final int LABEL_VALUE = 10;
    public static final int LABEL_TITLE = 11;
    public static final int LABEL_MOLECULE_NAME = 12;
    public static final int LABEL_STRING = 13;
    public static final int LABEL_BOND = 14;
    public static final int LABEL_CUSTOM = 15;
    public static final int LABEL_NAME = 16;
    public static final int LABEL_HPPM = 17;
    public static final int LABEL_PPM = 18;
    public static final int LABEL_NONHC = 19;
    public static List<Atom> atomList = null;
    public final List<SpatialSet> globalSelected = new ArrayList<>(1024);
    protected final List<Bond> bselected = new ArrayList<>(1024);
    public Set<Integer> structures = new TreeSet();
    public String title = null;
    public byte label = 0;
    public LinkedHashMap<String, CoordSet> coordSets;
    protected List<Integer> activeStructures = null;
    Map<String, Atom> atomMap = new HashMap<>();
    protected List<Atom> atoms;
    protected List<Bond> bonds = new ArrayList<Bond>();
    //    ArrayList<Atom> atoms = new ArrayList<>();
    private boolean atomArrayValid = false;

    public MoleculeBase() {
        coordSets = new LinkedHashMap<>();
    }

    public static Molecule activeMol() {
        return StructureProject.getActive().activeMol;
    }

    public static Molecule getActive() {
        return StructureProject.getActive().activeMol;
    }

    public Atom getAtom(String name) {
        return atomMap.get(name);
    }

    public void calcAllBonds() {
        int result;
        int nBonds = 0;
        updateAtomArray();
        for (int i = 0; i < atomList.size(); i++) {
            for (int j = i + 1; j < atomList.size(); j++) {
                Atom atom1 = atomList.get(i);
                Atom atom2 = atomList.get(j);
                result = Atom.calcBond(atom1, atom2, Order.SINGLE);

                if (result == 2) {
                    break;
                }

                if (result == 0) {
                    nBonds++;
                }
            }
        }
    }

    public static void writeXYZ() {
        Molecule molecule = MoleculeBase.activeMol();

        if (molecule == null) {
            return;
        }
        molecule.updateAtomArray();
        int i = 0;
        for (Atom atom : molecule.atoms) {
            SpatialSet spSet = atom.spatialSet;
            atom.iAtom = i;
            String result = spSet.toPDBString(i + 1, 0);
            System.out.println(result);
            i++;
        }
    }

    public static void writeXYZToXML(FileWriter chan, int whichStruct) throws InvalidMoleculeException, IOException {
        int i;
        int iStruct = 0;
        String result = null;

        Molecule molecule = MoleculeBase.activeMol();

        if (molecule == null) {
            throw new InvalidMoleculeException("No active molecule");
        }
        molecule.updateAtomArray();

        int[] structureList = molecule.getActiveStructures();
        for (int jStruct = 0; jStruct < structureList.length; jStruct++) {
            iStruct = structureList[jStruct];
            if ((whichStruct >= 0) && (iStruct != whichStruct)) {
                continue;
            }

            i = 0;

            for (Atom atom : molecule.atoms) {
                result = atom.xyzToXMLString(iStruct, i);

                if (result != null) {
                    chan.write(result + "\n");
                    i++;
                }
            }
        }
    }

    public static void writePPMToXML(FileWriter chan, int whichStruct) throws IOException, InvalidMoleculeException {
        int i;
        String result = null;

        Molecule molecule = MoleculeBase.getActive();

        if (molecule == null) {
            throw new InvalidMoleculeException("No Active Molecule");
        }
        molecule.updateAtomArray();

        i = 0;

        for (Atom atom : molecule.atoms) {
            result = atom.ppmToXMLString(0, i);

            if (result != null) {
                chan.write(result + "\n");
                i++;
            }
        }
    }

    public static String writeXYZToPDBString(int whichStruct) throws InvalidMoleculeException, IOException {
        StringWriter stringWriter = new StringWriter();
        MoleculeBase.writeXYZToPDB(stringWriter, whichStruct);
        return stringWriter.toString();
    }

    public static void writeXYZToPDB(Writer chan, int whichStruct) throws InvalidMoleculeException, IOException {
        int i;

        Molecule molecule = MoleculeBase.activeMol();

        if (molecule == null) {
            throw new InvalidMoleculeException("No active molecule");
        }
        molecule.updateAtomArray();

        int[] structureList = molecule.getActiveStructures();
        if (structureList.length == 0) {
            structureList = new int[1];
            structureList[0] = 0;
        }
        ArrayList<Atom> bondList = new ArrayList<>();
        StringBuilder outString = new StringBuilder();
        ArrayList<Integer> iAtoms = new ArrayList<>();
        for (int iStruct : structureList) {
            if ((whichStruct >= 0) && (iStruct != whichStruct)) {
                continue;
            }
            bondList.clear();
            i = 0;
            for (Atom atom : molecule.atoms) {
                SpatialSet spSet = atom.spatialSet;
                if (atom.isCoarse()) {
                    continue;
                }
                atom.iAtom = i;
                String result = spSet.toPDBString(i + 1, iStruct);
                if (!(spSet.atom.entity instanceof Residue) || !((Residue) spSet.atom.entity).isStandard()) {
                    bondList.add(spSet.atom);
                }

                if (result != null) {
                    chan.write(result + "\n");
                    i++;
                }
            }
            for (Atom bAtom : bondList) {
                List<Atom> bondedAtoms = bAtom.getConnected();
                if (bondedAtoms.size() > 0) {
                    outString.setLength(0);
                    outString.append("CONECT");
                    outString.append(String.format("%5d", bAtom.iAtom + 1));
                    iAtoms.clear();
                    for (Object aObj : bondedAtoms) {
                        Atom bAtom2 = (Atom) aObj;
                        iAtoms.add(bAtom2.iAtom);
                    }
                    Collections.sort(iAtoms);
                    for (Integer iAtom : iAtoms) {
                        outString.append(String.format("%5d", iAtom + 1));
                    }
                    chan.write(outString.toString() + "\n");
                }
            }
        }
    }

    public int[] getActiveStructures() {
        if (activeStructures == null) {
            activeStructures = new ArrayList<>();
            ;
            for (int i = 0; i < structures.size(); i++) {
                activeStructures.add(i);
            }
        }
        int[] structs = new int[activeStructures.size()];
        for (int i = 0; i < structs.length; i++) {
            structs[i] = activeStructures.get(i);
        }
        return structs;
    }

    public void invalidateAtomArray() {
        atomArrayValid = false;
    }

    public void updateAtomArray() {
        if (!atomArrayValid) {
            atoms.clear();
            atomMap.clear();
            Iterator e = coordSets.values().iterator();
            CoordSet coordSet;
            while (e.hasNext()) {
                coordSet = (CoordSet) e.next();
                Iterator entIterator = coordSet.getEntities().values().iterator();
                while (entIterator.hasNext()) {
                    Entity entity = (Entity) entIterator.next();
                    for (Atom atom : entity.atoms) {
                        atoms.add(atom);
                        atomMap.put(atom.getFullName(), atom);
                    }
                }
            }
            atomArrayValid = true;
        }
    }

    public void updateBondArray() {
        bonds.clear();
        Iterator e = coordSets.values().iterator();
        CoordSet coordSet;
        while (e.hasNext()) {
            coordSet = (CoordSet) e.next();
            Iterator entIterator = coordSet.getEntities().values().iterator();
            while (entIterator.hasNext()) {
                Entity entity = (Entity) entIterator.next();
                for (Bond bond : entity.bonds) {
                    bonds.add(bond);
                }
            }
        }
    }

    public ArrayList<Bond> getBondList() {
        return new ArrayList<Bond>(bonds);
    }

    public ArrayList<Atom> getAtomList() {
        return new ArrayList<Atom>(atoms);
    }

    public int getAtoms(int iStructure, Atom[] atomArray) {
        Point3 pt;
        int i = 0;

        updateAtomArray();
        for (Atom atom : atoms) {
            if (atom.getProperty(Atom.DISPLAY)) {
                pt = atom.getPoint(iStructure);

                if (pt != null) {
                    atomArray[i] = atom;
                    i++;
                }
            }
        }

        return i;
    }

    public List<Bond> getMoleculeBonds() {

        List<Bond> bondVector = new ArrayList<>(32);
        Atom atomB;
        Atom atomE;
        updateBondArray();
        for (Bond bond : bonds) {
            atomB = bond.begin;
            atomE = bond.end;

            if ((atomB != null) && (atomE != null)) {
                bondVector.add(bond);
            }
        }

        return (bondVector);
    }

    public List<Atom> getAtomArray() {
        updateAtomArray();
        return atoms;
    }

    public SpatialSetIterator getSpatialSetIterator() {
        return new SpatialSetIterator(this);

    }

    public void calcBonds() {
        Atom atom1 = null;
        Atom atom2 = null;
        int result;
        int nBonds = 0;

        for (int i = 0; i < globalSelected.size(); i++) {
            for (int j = i + 1; j < globalSelected.size(); j++) {
                atom1 = globalSelected.get(i).atom;

                if (atom1.getSelected() != 1) {
                    continue;
                }

                atom2 = globalSelected.get(j).atom;

                if (atom2.getSelected() != 1) {
                    continue;
                }

                result = Atom.calcBond(atom1, atom2, Order.SINGLE);

                if (result == 2) {
                    break;
                }

                if (result == 0) {
                    nBonds++;
                }
            }
        }
    }

    public void writeXYZToPDB(String fileName, int whichStruct) throws IOException {
        int i;
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(fileName)))) {

            updateAtomArray();

            int[] structureList = getActiveStructures();
            if (structureList.length == 0) {
                structureList = new int[1];
                structureList[0] = 0;
            }
            ArrayList<Atom> bondList = new ArrayList<Atom>();
            StringBuilder outString = new StringBuilder();
            ArrayList<Integer> iAtoms = new ArrayList<Integer>();
            Atom lastAtom = null;
            for (int iStruct : structureList) {
                if ((whichStruct >= 0) && (iStruct != whichStruct)) {
                    continue;
                }
                bondList.clear();
                i = 0;
                for (int j = 0, n = atoms.size(); j < n; j++) {
                    Atom atom = atoms.get(j);
                    SpatialSet spSet = atom.spatialSet;
                    if (atom.isCoarse()) {
                        continue;
                    }
                    atom.iAtom = i;
                    String result = spSet.toPDBString(i + 1, iStruct);
                    if (result != null) {
                        if ((lastAtom != null) && (atom.getTopEntity() != lastAtom.getTopEntity())) {
                            out.print(lastAtom.spatialSet.toTERString(i + 1) + "\n");
                            i++;
                            result = spSet.toPDBString(i + 1, iStruct);
                        }
                        if (!(spSet.atom.entity instanceof Residue) || !((Residue) spSet.atom.entity).isStandard()) {
                            bondList.add(spSet.atom);
                        }
                        out.print(result + "\n");
                        i++;
                        lastAtom = atom;
                    }
                }
                out.print(lastAtom.spatialSet.toTERString(i + 1) + "\n");

                for (Atom bAtom : bondList) {
                    List<Atom> bondedAtoms = bAtom.getConnected();
                    if (bondedAtoms.size() > 0) {
                        outString.setLength(0);
                        outString.append("CONECT");
                        outString.append(String.format("%5d", bAtom.iAtom + 1));
                        iAtoms.clear();
                        for (Object aObj : bondedAtoms) {
                            Atom bAtom2 = (Atom) aObj;
                            if (bAtom2.getElementName() != null) {
                                iAtoms.add(bAtom2.iAtom);
                            }
                        }
                        Collections.sort(iAtoms);
                        for (Integer iAtom : iAtoms) {
                            outString.append(String.format("%5d", iAtom + 1));
                        }
                        out.print(outString.toString() + "\n");
                    }
                }
            }
        }
    }

    public List<SpatialSet> getAtomsByProp(int property) {
        List<SpatialSet> selected = new ArrayList<>(32);
        updateAtomArray();
        for (Atom atom : atoms) {
            SpatialSet spatialSet = atom.getSpatialSet();
            if ((spatialSet != null) && spatialSet.getProperty(property)) {
                selected.add(spatialSet);
            }
        }
        return selected;
    }

    public static class SpatialSetIterator implements Iterator {

        private final Iterator coordSetIterator;
        private Compound compound = null;
        private Residue firstResidue = null;
        private Residue lastResidue = null;
        private Iterator entIterator = null;
        private Entity entity = null;
        private CoordSet coordSet = null;
        private Atom atom = null;
        private Iterator<Atom> atomIterator;

        public SpatialSetIterator(MoleculeBase molecule) {
            coordSetIterator = molecule.coordSets.values().iterator();
            if (nextCoordSet()) {
                if (nextEntity()) {
                    nextAtom();
                }
            }
        }

        public boolean nextAtom() {
            if (!atomIterator.hasNext()) {
                if (!nextCompound()) {
                    atom = null;
                    return false;
                }
            }
            atom = atomIterator.next();
            return true;
        }

        public boolean nextCompound() {
            if (entity instanceof Polymer) {
                if (compound == lastResidue) {
                    if (!nextEntity()) {
                        return false;
                    }
                } else {
                    compound = ((Residue) compound).next;
                }

            } else if (!nextEntity()) {
                return false;
            }

            atomIterator = compound.atoms.iterator();
            return true;
        }

        public boolean nextEntity() {
            if (!entIterator.hasNext()) {
                if (!nextCoordSet()) {
                    return false;
                }
            }
            entity = (Entity) entIterator.next();
            if (entity instanceof Polymer) {
                Polymer polymer = (Polymer) entity;
                firstResidue = polymer.getFirstResidue();
                lastResidue = polymer.getLastResidue();
                compound = (Compound) firstResidue;
            } else {
                compound = (Compound) entity;
            }
            atomIterator = compound.atoms.iterator();
            return true;
        }

        public boolean nextCoordSet() {
            if (!coordSetIterator.hasNext()) {
                return false;
            }
            coordSet = (CoordSet) coordSetIterator.next();
            entIterator = coordSet.getEntities().values().iterator();
            return true;
        }

        public boolean hasNext() {
            return atom != null;
        }

        public SpatialSet next() {
            Atom currentAtom = atom;
            nextAtom();
            return currentAtom.getSpatialSet();
        }

        public void remove() {
        }
    }
}
