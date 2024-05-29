package org.nmrfx.chemistry;

import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.chemistry.constraints.MolecularConstraints;
import org.nmrfx.chemistry.io.Sequence;
import org.nmrfx.chemistry.relax.OrderParSet;
import org.nmrfx.chemistry.relax.RelaxationSet;
import org.nmrfx.chemistry.search.MNode;
import org.nmrfx.chemistry.search.MTree;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.utilities.Updater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@PluginAPI("ring")
public class MoleculeBase implements Serializable, ITree {

    private static final Logger log = LoggerFactory.getLogger(MoleculeBase.class);

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
    private static final String ATOM_MATCH_WARN_MSG_TEMPLATE = "null spatialset while matching atom {} in coordset {}";
    public AtomicBoolean atomUpdated = new AtomicBoolean(false);
    Updater atomUpdater = null;
    MoleculeListener atomChangeListener;

    public static ArrayList<Atom> getMatchedAtoms(MolFilter molFilter, MoleculeBase molecule) {
        ArrayList<Atom> selected = new ArrayList<>(32);
        if (molecule == null) {
            return selected;
        }
        Residue firstResidue;
        Residue lastResidue = null;
        CoordSet coordSet;
        boolean checkAll = false;
        for (int iAtom = 0; iAtom < molFilter.atomNames.size(); iAtom++) {
            String atomName = (String) molFilter.atomNames.elementAt(iAtom);
            if (atomName.charAt(0) == '!') {
                checkAll = true;
            }
        }
        Iterator e = molecule.coordSets.values().iterator();
        while (e.hasNext()) {
            coordSet = (CoordSet) e.next();
            Iterator entIterator = coordSet.getEntities().values().iterator();
            while (entIterator.hasNext()) {
                Entity entity = (Entity) entIterator.next();
                Compound compound;
                if (!molFilter.matchCoordSetAndEntity(coordSet, entity)) {
                    continue;
                }
                if (entity instanceof Polymer) {
                    Polymer polymer = (Polymer) entity;
                    if (molFilter.firstRes.equals("*")) {
                        firstResidue = polymer.getFirstResidue();
                    } else {
                        firstResidue = (Residue) polymer.getResidue(molFilter.firstRes);
                    }
                    if (molFilter.lastRes.equals("*")) {
                        lastResidue = polymer.getLastResidue();
                    } else {
                        lastResidue = (Residue) polymer.getResidue(molFilter.lastRes);
                    }
                    compound = (Compound) firstResidue;
                } else {
                    compound = (Compound) entity;
                }
                if (compound == null) {
                    continue;
                }
                String atomName;
                while (compound != null) {
                    String rNum = compound.getNumber();
                    for (Atom atom : compound.atoms) {
                        boolean validRes = true;
                        // fixme why is this inside atom loop
                        if (!(entity instanceof Polymer)) {
                            if (!molFilter.firstRes.equals("*") && (!molFilter.firstRes.equals(compound.number))) {
                                validRes = false;
                            }
                        }
                        if (validRes) {
                            boolean validAtom = false;
                            for (int iAtom = 0; iAtom < molFilter.atomNames.size(); iAtom++) {
                                atomName = ((String) molFilter.atomNames.elementAt(iAtom)).toLowerCase();
                                boolean isInverse = false;
                                if (atomName.charAt(0) == '!') {
                                    atomName = atomName.substring(1);
                                    isInverse = true;
                                }
                                boolean isPseudo = false;
                                if ((atomName.charAt(0) == 'm') || (atomName.charAt(0) == 'q')) {
                                    if (compound instanceof Residue) {
                                        Residue residue = (Residue) compound;
                                        Atom[] pseudoAtoms = residue.getPseudo(atomName.toUpperCase());
                                        if (pseudoAtoms == null) {
                                            log.error("{} {}", residue.getName(), atomName);
                                            System.exit(1);
                                        }
                                        for (Atom atom2 : pseudoAtoms) {
                                            if (atom.name.equalsIgnoreCase(atom2.name)) {
                                                if (!atom.isMethyl() || atom.isFirstInMethyl()) {
                                                    selected.add(atom);
                                                    break;
                                                }
                                            }
                                        }
                                        isPseudo = true;
                                    }
                                }
                                if (isPseudo) {
                                    continue;
                                }
                                boolean nameMatches = Util.stringMatch(atom.name.toLowerCase(), atomName);
                                if (isInverse) {
                                    if (!nameMatches) {
                                        SpatialSet spatialSet = atom.getSpatialSet();
                                        if (spatialSet != null) {
                                            validAtom = true;
                                        } else {
                                            validAtom = false;
                                        }
                                    } else {
                                        validAtom = false;
                                        break;
                                    }
                                } else if (nameMatches) {
                                    SpatialSet spatialSet = atom.getSpatialSet();
                                    if (spatialSet != null) {
                                        validAtom = true;
                                    } else {
                                        validAtom = false;
                                        log.warn(ATOM_MATCH_WARN_MSG_TEMPLATE, atomName, coordSet.getName());
                                    }
                                    if (!checkAll) {
                                        break;
                                    }
                                }
                            }
                            if (validAtom) {
                                selected.add(atom);
                            }
                        }
                    }
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
        return selected;
    }

    public static ArrayList<Atom> getNEFMatchedAtoms(MolFilter molFilter, MoleculeBase molecule) {
        ArrayList<Atom> selected = new ArrayList<>(32);
        if (molecule == null) {
            return selected;
        }
        Residue firstResidue;
        Residue lastResidue = null;
        CoordSet coordSet;
        boolean checkAll = false;
        for (int iAtom = 0; iAtom < molFilter.atomNames.size(); iAtom++) {
            String atomName = (String) molFilter.atomNames.elementAt(iAtom);
            if (atomName.charAt(0) == '!') {
                checkAll = true;
            }
        }
        Iterator e = molecule.coordSets.values().iterator();
        while (e.hasNext()) {
            coordSet = (CoordSet) e.next();
            Iterator entIterator = coordSet.getEntities().values().iterator();
            while (entIterator.hasNext()) {
                Entity entity = (Entity) entIterator.next();
                Compound compound;
                if (!molFilter.matchCoordSetAndEntity(coordSet, entity)) {
                    continue;
                }
                if (entity instanceof Polymer) {
                    Polymer polymer = (Polymer) entity;
                    if (molFilter.firstRes.equals("*")) {
                        firstResidue = polymer.getFirstResidue();
                    } else {
                        firstResidue = (Residue) polymer.getResidue(molFilter.firstRes);
                    }
                    if (molFilter.lastRes.equals("*")) {
                        lastResidue = polymer.getLastResidue();
                    } else {
                        lastResidue = (Residue) polymer.getResidue(molFilter.lastRes);
                    }
                    compound = (Compound) firstResidue;
                } else {
                    compound = (Compound) entity;
                }
                if (compound == null) {
                    continue;
                }
                String atomName;
                while (compound != null) {
                    boolean validRes = true;
                    if (!(entity instanceof Polymer)) {
                        if (!molFilter.firstRes.equals("*") && (!molFilter.firstRes.equals(compound.number))) {
                            validRes = false;
                        }
                    }
                    for (Atom atom : compound.atoms) {
                        if (validRes) {
                            boolean validAtom = false;
                            for (int iAtom = 0; iAtom < molFilter.atomNames.size(); iAtom++) {
                                atomName = ((String) molFilter.atomNames.elementAt(iAtom)).toLowerCase();
                                boolean isInverse = false;
                                if (atomName.charAt(0) == '!') {
                                    atomName = atomName.substring(1);
                                    isInverse = true;
                                }
                                boolean isPseudo = false;
                                if ((atomName.charAt(0) == 'm') || (atomName.charAt(0) == 'q')) {
                                    if (compound instanceof Residue) {
                                        Residue residue = (Residue) compound;
                                        Atom[] pseudoAtoms = residue.getPseudo(atomName.toUpperCase());
                                        if (pseudoAtoms == null) {
                                            log.error("{} {}", residue.getName(), atomName);
                                            System.exit(1);
                                        }
                                        for (Atom atom2 : pseudoAtoms) {
                                            if (atom.name.equalsIgnoreCase(atom2.name)) {
                                                selected.add(atom);
                                                break;
                                            }
                                        }
                                        isPseudo = true;
                                    }
                                }
                                if (isPseudo) {
                                    continue;
                                }
                                boolean nameMatches = Util.nefMatch(atom, atomName);
                                if (isInverse) {
                                    if (!nameMatches) {
                                        SpatialSet spatialSet = atom.getSpatialSet();
                                        if (spatialSet != null) {
                                            validAtom = true;
                                        } else {
                                            validAtom = false;
                                        }
                                    } else {
                                        validAtom = false;
                                        break;
                                    }
                                } else if (nameMatches) {
                                    SpatialSet spatialSet = atom.getSpatialSet();
                                    if (spatialSet != null) {
                                        validAtom = true;
                                    } else {
                                        validAtom = false;
                                        log.warn(ATOM_MATCH_WARN_MSG_TEMPLATE, atomName, coordSet.getName());
                                    }
                                    if (!checkAll) {
                                        break;
                                    }
                                }
                            }
                            if (validAtom) {
                                selected.add(atom);
                            }
                        }
                    }
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
        return selected;
    }

    public final List<SpatialSet> globalSelected = new ArrayList<>(1024);
    protected final List<Bond> bselected = new ArrayList<>(1024);
    public Set<Integer> structures = new TreeSet();
    public String name;

    public String title = null;
    public byte label = 0;
    public LinkedHashMap<String, CoordSet> coordSets;
    public boolean changed = false;
    public LinkedHashMap<String, Entity> entities;
    public LinkedHashMap<String, Entity> chains;
    public LinkedHashMap<String, Entity> entityLabels = null;
    protected List<Integer> activeStructures = null;
    Map<String, Atom> atomMap = new HashMap<>();
    protected List<Atom> atoms = new ArrayList<>();
    protected List<Bond> bonds = new ArrayList<Bond>();
    private boolean atomArrayValid = false;
    protected HashMap<String, String> propertyMap = new HashMap<String, String>();
    MolecularConstraints molecularConstraints = new MolecularConstraints(this);
    List<SecondaryStructure> secondaryStructure = new ArrayList<>();
    Map<String, RelaxationSet> relaxationSetMap = new HashMap<>();
    Map<String, OrderParSet> orderParSetMap = new HashMap<>();

    public MoleculeBase(String name) {
        this.name = name;
        coordSets = new LinkedHashMap<>();
        entities = new LinkedHashMap<>();
        chains = new LinkedHashMap<>();
        entityLabels = new LinkedHashMap();
    }

    public static void removeAll() {
        // fixme need to remove each molecule from list, rather than just settng molecules to new Hashtable?
        // should at least just clear molecules
        MoleculeFactory.clearAllMolecules();

        conditions.clear();
        MoleculeFactory.setActive(null);
    }

    public static final Map<String, Compound> compoundMap() {
        return ProjectBase.getActive().getCompoundMap();
    }

    public void buildCompoundMap() {
        Map<String, Compound> map = compoundMap();
        for (Polymer polymer : getPolymers()) {
            for (Residue residue : polymer.getResidues()) {
                String mapID = polymer.getIDNum() + "." + polymer.getIDNum() + "." + residue.getIDNum();
                map.put(mapID, residue);
            }
        }
        for (Compound compound : getLigands()) {
            String mapID = 1 + "." + compound.getIDNum() + "." + compound.getIDNum();
            map.put(mapID, compound);
        }
    }

    public MolecularConstraints getMolecularConstraints() {
        return molecularConstraints;
    }

    public static void findEquivalentAtoms(String atomName) throws IllegalArgumentException {
        Atom atom = MoleculeBase.getAtomByName(atomName);

        if (atom == null) {
            throw new IllegalArgumentException("Can't find atom \"" + atomName + "\"");
        }

        atom.getEquivalency();
    }

    public static void findEquivalentAtoms(Entity entity) {
        MoleculeBase molecule = entity.molecule;
        molecule.getAtomTypes();

        MTree mTree = new MTree();
        Map<Atom, Integer> hash = new HashMap<>();
        List<Atom> eAtomList = new ArrayList<>();
        int i = 0;

        for (Atom atom : entity.atoms) {
            // entity check ensures that only atoms in same residue are used
            if (atom.entity == entity) {
                hash.put(atom, i);
                eAtomList.add(atom);

                MNode mNode = mTree.addNode();
                mNode.setAtom(atom);
                atom.equivAtoms = null;

                i++;
            }
        }

        entity.atoms.forEach((atom) -> {
            for (int iBond = 0; iBond < atom.bonds.size(); iBond++) {
                Bond bond = atom.bonds.get(iBond);
                Integer iNodeBegin = hash.get(bond.begin);
                Integer iNodeEnd = hash.get(bond.end);

                if ((iNodeBegin != null) && (iNodeEnd != null)) {
                    mTree.addEdge(iNodeBegin, iNodeEnd);

                }
            }
        });

        class TreeGroup {

            int iAtom = 0;
            List<MNode> pathNodes = new ArrayList<>();
            List<Integer> treeValues = new ArrayList<>();
            List<Integer> shells = new ArrayList<>();

            TreeGroup(int iAtom, List<MNode> pathNodes, List<Integer> treeValues, List<Integer> shells) {
                this.iAtom = iAtom;
                this.pathNodes.addAll(pathNodes);
                this.treeValues.addAll(treeValues);
                this.shells.addAll(shells);
            }

            public String toString() {
                StringBuilder sBuilder = new StringBuilder();
                sBuilder.append(iAtom).append("\n");
                pathNodes.forEach((node) -> {
                    sBuilder.append(" ").append(node.getAtom().getName());
                });
                sBuilder.append("\n");
                treeValues.forEach((treeValue) -> {
                    sBuilder.append(" ").append(treeValue);
                });
                sBuilder.append("\n");
                shells.forEach((shell) -> {
                    sBuilder.append(" ").append(shell);
                });
                return sBuilder.toString();
            }
        }

        ArrayList treeGroups = new ArrayList();

        // get breadth first path from each atom
        for (int j = 0, n = eAtomList.size(); j < n; j++) {
            mTree.broad_path(j);
            List<MNode> pathNodes = mTree.getPathNodes();
            int numNodes = pathNodes.size();
            int value;
            int shell;
            List<Integer> treeValues = new ArrayList<>(numNodes);
            List<Integer> shells = new ArrayList<>(numNodes);

            for (int k = 0; k < numNodes; k++) {
                MNode cNode = pathNodes.get(k);
                if (cNode.isRingClosure()) {
                    continue;
                }
                Atom atom = cNode.getAtom();
                shell = cNode.getShell();
                value = (shell * 4096) + (16 * atom.aNum) + ((4 * atom.nPiBonds) / 2);
                treeValues.add(value);
                shells.add(shell);
            }
            Collections.sort(treeValues);
            treeGroups.add(new TreeGroup(j, pathNodes, treeValues, shells));
        }

        ArrayList equivAtoms = new ArrayList();
        Map<String, AtomEquivalency> groupHash = new TreeMap<>();
        Map<String, Integer> uniqueMap = new TreeMap<>();
        int nGroups = 0;

        for (int j = 0; j < treeGroups.size(); j++) {
            equivAtoms.clear();

            TreeGroup jGroup = (TreeGroup) treeGroups.get(j);

            for (int k = 0; k < treeGroups.size(); k++) {
                if (j == k) {
                    continue;
                }

                TreeGroup kGroup = (TreeGroup) treeGroups.get(k);
                boolean ok = false;

                // atoms are equivalent only if all the atoms on each atoms tree are the same type
                if (kGroup.treeValues.size() == jGroup.treeValues.size()) {
                    ok = true;

                    for (int kj = 0; kj < jGroup.treeValues.size(); kj++) {
                        int kVal = (kGroup.treeValues.get(kj));
                        int jVal = (jGroup.treeValues.get(kj));

                        if (kVal != jVal) {
                            ok = false;

                            break;
                        }
                    }
                }

                if (ok) {
                    Atom jAtom = jGroup.pathNodes.get(0).getAtom();
                    Atom kAtom = kGroup.pathNodes.get(0).getAtom();
                    int shell = -1;

                    for (int jj = 0; jj < kGroup.pathNodes.size(); jj++) {
                        MNode nodeTest = kGroup.pathNodes.get(jj);
                        Atom atomTest = nodeTest.getAtom();

                        if (atomTest != null && atomTest.getName().equals(jAtom.getName())) {
                            shell = kGroup.shells.get(jj);
                            break;
                        }
                    }

                    String groupName = shell + "_" + jAtom.getName();
                    String jUniq = shell + "_" + jAtom.getName();
                    String kUniq = shell + "_" + kAtom.getName();

                    if (!uniqueMap.containsKey(jUniq) && !uniqueMap.containsKey(kUniq)) {
                        nGroups++;
                        uniqueMap.put(jUniq, nGroups);
                        uniqueMap.put(kUniq, nGroups);
                    } else if (!uniqueMap.containsKey(jUniq)) {
                        uniqueMap.put(jUniq, uniqueMap.get(kUniq));
                    } else if (!uniqueMap.containsKey(kUniq)) {
                        uniqueMap.put(kUniq, uniqueMap.get(jUniq));
                    }

                    AtomEquivalency atomEquiv = groupHash.get(groupName);

                    if (atomEquiv == null) {
                        atomEquiv = new AtomEquivalency();
                        atomEquiv.setShell(shell);
                        atomEquiv.setIndex((uniqueMap.get(jUniq)));
                        atomEquiv.setAtoms(new ArrayList<>());
                        atomEquiv.getAtoms().add(jAtom);
                        groupHash.put(groupName, atomEquiv);
                    }

                    atomEquiv.getAtoms().add(kAtom);
                }
            }
        }

        for (Map.Entry<String, AtomEquivalency> entry : groupHash.entrySet()) {

            nGroups++;
            String key = entry.getKey();
            AtomEquivalency atomEquiv = entry.getValue();
            Atom eAtom = atomEquiv.getAtoms().get(0);

            if (eAtom.equivAtoms == null) {
                eAtom.equivAtoms = new ArrayList(2);
            }

            eAtom.equivAtoms.add(atomEquiv);
        }

        entity.setHasEquivalentAtoms(true);
    }

    public List<SpatialSet> selectedSpatialSets() {
        return globalSelected;
    }
    public static Atom getAtomByName(String name) throws IllegalArgumentException {
        MoleculeBase molecule = MoleculeFactory.getActive();

        if (molecule == null) {
            throw new IllegalArgumentException("No active molecule");
        }

        return molecule.findAtom(name);

    }

    public static List<SpatialSet> matchAtoms(MolFilter molFilter) throws InvalidMoleculeException {
        MoleculeBase molecule = MoleculeFactory.getActive();

        if (molecule == null) {
            throw new InvalidMoleculeException("No active molecule");
        }
        return MoleculeBase.matchAtoms(molFilter, molecule);
    }

    public static List<SpatialSet> matchAtoms(MolFilter molFilter, MoleculeBase molecule) {

        List<SpatialSet> selected = new ArrayList<>(32);
        if (molecule == null) {
            return selected;
        }

        Residue firstResidue;
        Residue lastResidue = null;
        CoordSet coordSet;

        boolean checkAll = false;

        for (int iAtom = 0; iAtom < molFilter.atomNames.size(); iAtom++) {
            String atomName = ((String) molFilter.atomNames.elementAt(iAtom));

            if (atomName.charAt(0) == '!') {
                checkAll = true;
            }
        }

        Iterator e = molecule.coordSets.values().iterator();

        while (e.hasNext()) {
            coordSet = (CoordSet) e.next();
            Iterator entIterator = coordSet.getEntities().values().iterator();

            while (entIterator.hasNext()) {
                Entity entity = (Entity) entIterator.next();
                Compound compound;
                if (!molFilter.matchCoordSetAndEntity(coordSet, entity)) {
                    continue;
                }
                if (entity instanceof Polymer) {
                    Polymer polymer = (Polymer) entity;
                    if (molFilter.firstRes.equals("*")) {
                        firstResidue = polymer.getFirstResidue();
                    } else {
                        firstResidue = (Residue) polymer.getResidue(molFilter.firstRes);
                    }

                    if (molFilter.lastRes.equals("*")) {
                        lastResidue = polymer.getLastResidue();
                    } else {
                        lastResidue = (Residue) polymer.getResidue(molFilter.lastRes);
                    }

                    compound = (Compound) firstResidue;
                } else {
                    compound = (Compound) entity;
                }

                if (compound == null) {
                    continue;
                }

                String atomName;

                while (compound != null) {
                    for (Atom atom : compound.atoms) {
                        boolean validRes = true;

                        if (!(entity instanceof Polymer)) {
                            if (!molFilter.firstRes.equals("*") && (!molFilter.firstRes.equals(compound.number))) {
                                validRes = false;
                            }
                        }
                        if (!molFilter.firstResType.equals("*")) {
                            if (compound instanceof Residue residue) {
                                String snglChar = String.valueOf(residue.getOneLetter());
                                String resType = molFilter.firstResType;
                                if (!snglChar.equals(resType)) {
                                    validRes = false;
                                }
                            }
                        }
                        if (validRes) {
                            boolean validAtom = false;

                            for (int iAtom = 0; iAtom < molFilter.atomNames.size(); iAtom++) {
                                atomName = ((String) molFilter.atomNames.elementAt(iAtom)).toLowerCase();

                                if (atomName.charAt(0) == '!') {
                                    if (!Util.stringMatch(atom.name.toLowerCase(), atomName.substring(1))) {
                                        SpatialSet spatialSet = atom.getSpatialSet();

                                        if (spatialSet != null) {
                                            validAtom = true;
                                        } else {
                                            validAtom = false;
                                        }
                                    } else {
                                        validAtom = false;

                                        break;
                                    }
                                } else if (Util.stringMatch(atom.name.toLowerCase(), atomName)) {
                                    SpatialSet spatialSet = atom.getSpatialSet();

                                    if (spatialSet != null) {
                                        validAtom = true;
                                    } else {
                                        validAtom = false;
                                        log.warn(ATOM_MATCH_WARN_MSG_TEMPLATE, atomName, coordSet.getName());
                                    }

                                    if (!checkAll) {
                                        break;
                                    }
                                }
                            }

                            if (validAtom) {
                                SpatialSet spatialSet = atom.getSpatialSet();
                                selected.add(spatialSet);
                            }
                        }
                    }

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

        return (selected);
    }

    public static Atom getAtom(MolFilter molFilter) throws InvalidMoleculeException {
        ArrayList spatialSets = new ArrayList();
        MoleculeBase.selectAtomsForTable(molFilter, spatialSets);
        SpatialSet spSet = MoleculeBase.getSpatialSet(molFilter);
        Atom atom = null;
        if (spSet != null) {
            atom = spSet.atom;
        }

        return atom;
    }

    public static SpatialSet getSpatialSet(MolFilter molFilter) throws IllegalArgumentException {
        MoleculeBase molecule = MoleculeFactory.getActive();

        if (molecule == null) {
            throw new IllegalArgumentException("No active molecule");
        }
        return molecule.findSpatialSet(molFilter);

    }

    public static void selectAtomsForTable(MolFilter molFilter, List<Atom> selected) throws InvalidMoleculeException {
        selected.clear();
        List<SpatialSet> fselected = MoleculeBase.matchAtoms(molFilter);

        for (SpatialSet spatialSet : fselected) {
            if (spatialSet.atom.isMethyl() && !spatialSet.atom.isFirstInMethyl()) {
                continue;
            }
            selected.add(spatialSet.atom);
        }
    }

    public Atom getAtom(String name) {
        return atomMap.get(name);
    }

    public void calcAllBonds() {
        int result;
        int nBonds = 0;
        updateAtomArray();
        for (int i = 0; i < atoms.size(); i++) {
            for (int j = i + 1; j < atoms.size(); j++) {
                Atom atom1 = atoms.get(i);
                Atom atom2 = atoms.get(j);
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

    public void writeXYZ() {
        updateAtomArray();
        int i = 0;
        for (Atom atom : atoms) {
            SpatialSet spSet = atom.spatialSet;
            atom.iAtom = i;
            String result = spSet.toPDBString(i + 1, 0);
            log.info(result);
            i++;
        }
    }

    public void writeXYZToXML(FileWriter chan, int whichStruct) throws InvalidMoleculeException, IOException {
        int i;
        int iStruct;
        String result;

        updateAtomArray();

        int[] structureList = getActiveStructures();
        for (int jStruct = 0; jStruct < structureList.length; jStruct++) {
            iStruct = structureList[jStruct];
            if ((whichStruct >= 0) && (iStruct != whichStruct)) {
                continue;
            }

            i = 0;

            for (Atom atom : atoms) {
                result = atom.xyzToXMLString(iStruct, i);

                if (result != null) {
                    chan.write(result + "\n");
                    i++;
                }
            }
        }
    }

    public void writePPMToXML(FileWriter chan, int whichStruct) throws IOException, InvalidMoleculeException {
        int i;
        String result;

        updateAtomArray();

        i = 0;

        for (Atom atom : atoms) {
            result = atom.ppmToXMLString(0, i);

            if (result != null) {
                chan.write(result + "\n");
                i++;
            }
        }
    }

    public String writeXYZToPDBString(int whichStruct) throws InvalidMoleculeException, IOException {
        StringWriter stringWriter = new StringWriter();
        writeXYZToPDB(stringWriter, whichStruct);
        return stringWriter.toString();
    }

    public void writeXYZToPDB(Writer chan, int whichStruct) throws InvalidMoleculeException, IOException {
        int i;

        updateAtomArray();

        int[] structureList = getActiveStructures();
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
            for (Atom atom : atoms) {
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
                    iAtoms.forEach((iAtom) -> {
                        outString.append(String.format("%5d", iAtom + 1));
                    });
                    chan.write(outString.toString() + "\n");
                }
            }
        }
    }

    public int[] getActiveStructures() {
        if (activeStructures == null) {
            activeStructures = new ArrayList<>();
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
                entity.bonds.forEach((bond) -> {
                    bonds.add(bond);
                });
            }
        }
    }

    @Override
    public ArrayList<Bond> getBondList() {
        return new ArrayList<>(bonds);
    }

    public ArrayList<Atom> getAtomList() {
        return new ArrayList<>(atoms);
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

    @Override
    public List<Atom> getAtomArray() {
        updateAtomArray();
        return atoms;
    }

    public SpatialSetIterator getSpatialSetIterator() {
        return new SpatialSetIterator(this);

    }

    public void calcBonds() {
        Atom atom1;
        Atom atom2;
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
            ArrayList<Atom> bondList = new ArrayList<>();
            StringBuilder outString = new StringBuilder();
            ArrayList<Integer> iAtoms = new ArrayList<>();
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
                if (lastAtom != null) {
                    out.print(lastAtom.spatialSet.toTERString(i + 1) + "\n");
                }
                bondList.forEach(bAtom -> {
                    List<Atom> bondedAtoms = bAtom.getConnected();
                    if (!bondedAtoms.isEmpty()) {
                        outString.setLength(0);
                        outString.append("CONECT");
                        outString.append(String.format("%5d", bAtom.iAtom + 1));
                        iAtoms.clear();
                        for (Atom bAtom2 : bondedAtoms) {
                            if (bAtom2.getElementName() != null) {
                                iAtoms.add(bAtom2.iAtom);
                            }
                        }
                        Collections.sort(iAtoms);
                        iAtoms.forEach((iAtom) -> {
                            outString.append(String.format("%5d", iAtom + 1));
                        });
                        out.print(outString.toString() + "\n");
                    }
                });
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

    public void findEquivalentAtoms() {
        updateAtomArray();
        ArrayList<Atom> atoms2 = new ArrayList<>(atoms);
        for (Atom atom : atoms2) {
            Entity entity = atom.entity;
            if (!entity.hasEquivalentAtoms()) {
                MoleculeBase.findEquivalentAtoms(entity);
            }
        }
    }

    public void getAtomTypes() {

        updateAtomArray();
        for (Atom atom : atoms) {
            atom.nPiBonds = 0;
            atom.nonHydrogens = 0;
            atom.hydrogens = 0;
        }

        atoms.forEach((atom) -> {
            for (int iBond = 0; iBond < atom.bonds.size(); iBond++) {
                Bond bond = atom.bonds.get(iBond);
                if ((bond.begin == atom) && (bond.end.aNum == 1)) {
                    atom.hydrogens++;
                } else if ((bond.end == atom) && (bond.begin.aNum == 1)) {
                    atom.hydrogens++;
                }

                if (bond.begin.aNum > 1) {
                    bond.end.nonHydrogens++;
                }

                if (bond.end.aNum > 1) {
                    bond.begin.nonHydrogens++;
                }

                if (bond.order.getOrderNum() < 5) {
                    bond.begin.nPiBonds += (2 * (bond.order.getOrderNum() - 1));
                    bond.end.nPiBonds += (2 * (bond.order.getOrderNum() - 1));
                } else if ((bond.order.getOrderNum() == 8)) {
                    bond.begin.nPiBonds += 1;
                    bond.end.nPiBonds += 1;
                } else if ((bond.order.getOrderNum() == 7)) {
                    bond.begin.nPiBonds += 1;
                    bond.end.nPiBonds += 1;
                }

            }

        });

    }

    public Atom findAtom(String name) {
        MolFilter molFilter;
        molFilter = new MolFilter(name);
        Atom atom = null;
        SpatialSet spSet = findSpatialSet(molFilter);
        if (spSet != null) {
            atom = spSet.atom;
        }
        return atom;
    }

    public SpatialSet findSpatialSet(MolFilter molFilter) throws IllegalArgumentException {
        Residue firstResidue;
        Compound compound;
        CoordSet coordSet;

        Iterator e = coordSets.values().iterator();

        while (e.hasNext()) {
            coordSet = (CoordSet) e.next();
            Iterator entIterator = coordSet.getEntities().values().iterator();

            while (entIterator.hasNext()) {
                Entity entity = (Entity) entIterator.next();
                if (!molFilter.matchCoordSetAndEntity(coordSet, entity)) {
                    continue;
                }

                if (entity instanceof Polymer) {
                    Polymer polymer = (Polymer) entity;
                    firstResidue = (Residue) polymer.getResidue(molFilter.firstRes);
                    compound = (Compound) firstResidue;
                } else {
                    compound = (Compound) entity;

                    if (!molFilter.firstRes.equals("*") && (!molFilter.firstRes.equals(compound.number))) {
                        continue;
                    }
                }

                Atom atom;

                if (compound != null) {
                    atom = compound.getAtomLoose((String) molFilter.atomNames.elementAt(0));
                    if (atom != null) {
                        return atom.getSpatialSet();
                    } else {
                        return null;
                    }
                }
            }
        }

        return (null);
    }

    public void changed(Atom atom) {
        changed = true;
        if (atomUpdater != null) {
            atomUpdater.update(atom);
        }
    }
    public void registerUpdater(Updater atomUpdater) {
        this.atomUpdater = atomUpdater;
    }
    public void registerAtomChangeListener(MoleculeListener newListener){
        this.atomChangeListener = newListener;
    }

    public void notifyAtomChangeListener() {
        atomChangeListener.moleculeChanged(new MoleculeEvent(this));
    }

    public void clearChanged() {
        changed = false;
    }

    public boolean isChanged() {
        return changed;
    }

    public void addCoordSet(String setName, Entity entity) {
        int id = coordSets.size() + 1;
        addCoordSet(setName, id, entity);
    }

    public void addCoordSet(String setName, int id, Entity entity) {
        CoordSet coordSet = (CoordSet) coordSets.get(setName);

        if (coordSet == null) {
            coordSet = new CoordSet(setName, id, entity);
            coordSets.put(setName, coordSet);
            coordSet.addEntity(entity);
        } else {
            coordSet.addEntity(entity);
        }
    }

    public CoordSet getFirstCoordSet() {
        Iterator e = coordSets.values().iterator();
        CoordSet coordSet = null;
        while (e.hasNext()) {
            coordSet = (CoordSet) e.next();
            break;
        }
        return coordSet;
    }

    public void addEntity(Entity entity) {
        CoordSet coordSet = getFirstCoordSet();
        int coordID;
        final String coordSetName;
        if (coordSet != null) {
            coordSetName = coordSet.getName();
            coordID = coordSet.getID();
        } else {
            coordSetName = "A";
            coordID = 1;
        }
        addEntity(entity, coordSetName, coordID);
    }

    public void addEntity(Entity entity, String coordSetName, int coordID) {
        entities.put(entity.name, entity);
        entityLabels.put(entity.label, entity);
        if (entity.entityID == 0) {
            entity.setIDNum(entities.size());
        }
        entity.molecule = this;
        addCoordSet(coordSetName, coordID, entity);
        chains.put(entity.getPDBChain(), entity);
    }

    public void addEntity(Entity entity, String coordSetName) {
        entities.put(entity.name, entity);
        entityLabels.put(entity.label, entity);
        if (entity.entityID == 0) {
            entity.setIDNum(entities.size());
        }
        entity.molecule = this;
        addCoordSet(coordSetName, entity);
        chains.put(entity.getPDBChain(), entity);
    }

    public Entity getEntity(String name) {
        if (name == null) {
            return null;
        } else {
            return ((Entity) entities.get(name));
        }
    }

    public Entity getChain(String name) {
        if (name == null) {
            return null;
        } else {
            return ((Entity) chains.get(name));
        }
    }

    public ArrayList<Entity> getEntities() {
        return new ArrayList(entities.values());
    }

    public List<Polymer> getPolymers() {
        List<Polymer> polymers = new ArrayList<>();
        for (Entity entity : entities.values()) {
            if (entity instanceof Polymer) {
                polymers.add((Polymer) entity);
            }
        }
        return polymers;
    }

    public Polymer getPolymer(String polymerName) {
        List<Polymer> polymers = new ArrayList<>();
        Entity entity = getEntity(polymerName);
        Polymer polymer = null;
        if (entity instanceof Polymer) {
            polymer = (Polymer) entity;
        }
        return polymer;
    }

    public List<Entity> getCompoundsAndResidues() {
        var entities = new ArrayList<Entity>();
        for (var polymer : getPolymers()) {
            entities.addAll(polymer.getResidues());
        }
        entities.addAll(getLigands());
        return entities;
    }

    public String getName() {
        return name;
    }

    public boolean coordSetExists(String setName) {
        CoordSet coordSet = (CoordSet) coordSets.get(setName);
        return coordSet != null;
    }

    public CoordSet getCoordSet(String name) {
        if (name == null) {
            return null;
        } else {
            return (CoordSet) coordSets.get(name);
        }
    }

    public Set<String> getCoordSetNames() {
        return coordSets.keySet();
    }

    public List<Atom> setupAngles() {
        return Collections.EMPTY_LIST;
    }

    public void addSecondaryStructure(SecondaryStructure secStruct) {
        secondaryStructure.add(secStruct);
    }

    public List<SecondaryStructure> getSecondaryStructure() {
        return secondaryStructure;
    }

    public void setupRotGroups() {
    }

    public void setMethylRotationActive(boolean state) {

    }

    public void fillEntityCoords() {
    }

    public int genCoords(boolean fillCoords) throws RuntimeException {
        return 0;
    }

    public int genCoords(int structureNumber, boolean fillCoords) throws RuntimeException {
        return 0;
    }

    public void nullCoords(int iStructure) {
        updateAtomArray();
        atoms.forEach((atom) -> {
            atom.setPointValidity(iStructure, false);
        });

    }

    public void nullCoords() {
        updateAtomArray();
        int iStructure = 0;
        atoms.forEach((atom) -> {
            atom.setPointValidity(iStructure, false);
        });
    }

    public int checkType() {
        Set<String> atomSet = new TreeSet<>();
        Iterator e = entities.values().iterator();
        while (e.hasNext()) {
            Entity entity = (Entity) e.next();
            Residue firstResidue = null;
            Residue lastResidue = null;
            Compound compound = null;
            if (entity instanceof Polymer) {
                Polymer polymer = (Polymer) entity;
                firstResidue = polymer.getFirstResidue();
                lastResidue = polymer.getLastResidue();
                compound = (Compound) firstResidue;
            } else {
                compound = (Compound) entity;
            }
            while (compound != null) {
                atomSet.clear();
                String resName = compound.getName();
                if (!resName.equals("ALA") && !resName.equals("GLY")) {
                    for (Atom atom : compound.atoms) {
                        atomSet.add(atom.getName());
                    }
                    if (atomSet.contains("CA") && atomSet.contains("CG")) {
                        if (atomSet.contains("HB1") && atomSet.contains("HB2")) {
                            return 1;
                        } else if (atomSet.contains("HB2") && atomSet.contains("HB3")) {
                            return 2;
                        }
                    }
                }
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
        return 0;
    }

    public String getProperty(String propName) {
        return propertyMap.get(propName);
    }

    public Set<String> getPropertyNames() {
        return propertyMap.keySet();
    }

    public void setProperty(String propName, String propValue) {
        propertyMap.put(propName, propValue);
    }

    public int getPPMSetCount() {
        Iterator e = getSpatialSetIterator();
        int maxCount = 1;
        while (e.hasNext()) {
            SpatialSet spatialSet = (SpatialSet) e.next();
            if (spatialSet == null) {
                continue;
            }
            int nSets = spatialSet.getPPMSetCount();
            if (nSets > maxCount) {
                maxCount = nSets;
            }
        }
        return maxCount;
    }

    public int getRefPPMSetCount() {
        Iterator e = getSpatialSetIterator();
        int maxCount = 1;
        while (e.hasNext()) {
            SpatialSet spatialSet = (SpatialSet) e.next();
            if (spatialSet == null) {
                continue;
            }
            int nSets = spatialSet.getRefPPMSetCount();
            if (nSets > maxCount) {
                maxCount = nSets;
            }
        }
        return maxCount;
    }

    public void reName(Compound compound, String name1, String name2) {
        name = name2;
        MoleculeFactory.removeMolecule(name1);
        compound.name = name;
        MoleculeFactory.setActive(this);
    }

    public ArrayList<Compound> getLigands() {
        ArrayList<Compound> compounds = new ArrayList<>();
        for (Entity entity : entities.values()) {
            if (entity instanceof Compound) {
                if (!entity.getName().equals("HOH")) {
                    compounds.add((Compound) entity);
                }
            }
        }
        return compounds;
    }

    public void updateSpatialSets() {
    }

    public void clearStructures() {
        structures.clear();
        activeStructures = null;
    }

    public void resetActiveStructures() {
        activeStructures = null;
    }

    public void clearActiveStructure(int iStruct) {
        activeStructures.remove(iStruct);
    }

    public void setActiveStructures(TreeSet selSet) {
        if (activeStructures == null) {
            activeStructures = new ArrayList<>();
        }
        activeStructures.clear();
        for (Object obj : selSet) {
            activeStructures.add((Integer) obj);
        }
    }

    public void setActiveStructures() {
        activeStructures = new ArrayList<>();
        structures.forEach((istruct) -> {
            activeStructures.add(istruct);
        });
    }

    public void setDotBracket(String value) {
        setProperty("vienna", value);
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
            return atomIterator.hasNext();
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

        @Override
        public boolean hasNext() {
            return atom != null;
        }

        @Override
        public SpatialSet next() {
            Atom currentAtom = atom;
            nextAtom();
            return currentAtom.getSpatialSet();
        }

        @Override
        public void remove() {
        }
    }

    public void addNonStandardResidue(Sequence sequence, Residue residue) {
    }

    public Map<String, RelaxationSet> relaxationSetMap() {
        return relaxationSetMap;
    }
    public Map<String, OrderParSet> orderParSetMap() {
        return orderParSetMap;
    }
}
