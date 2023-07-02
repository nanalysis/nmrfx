package org.nmrfx.structure.chemistry.miner;

import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.chemistry.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@PluginAPI("residuegen")
public class PathIterator implements Iterator<List<Integer>> {
    private static final Logger log = LoggerFactory.getLogger(PathIterator.class);

    AtomContainer ac;
    List<List<Integer>> pathAtoms = null;
    List<List<Integer>> pathBonds = null;
    Map<String, Integer> bondMap = new HashMap<>();
    List<Integer> path = null;
    public static boolean debug = false;
    int pathLength = 0;
    int pathPos = 0;
    int lastAtom;
    int currentAtom = -1;
    Map<IAtom, Integer> atomMap = new HashMap<>();
    PathVars pVars;
    NodeValidatorInterface nodeValidator;
    IAtom[] atoms;
    IBond[] bonds;
    int currentPattern = 0;
    int nPatterns = 0;

    public PathIterator(AtomContainer ac) {
        this.ac = ac;
        lastAtom = ac.getAtomCount() - 1;
        pVars = new PathVars(ac, "p");
    }

    public void init(NodeValidatorInterface nodeValidator) {
        this.nodeValidator = nodeValidator;

        atoms = new IAtom[ac.getAtomCount()];
        for (int i = 0; i < ac.getAtomCount(); i++) {
            atomMap.put(ac.getAtom(i), i);
            atoms[i] = ac.getAtom(i);
        }
        bonds = new IBond[ac.getBondCount()];
        for (int i = 0; i < ac.getBondCount(); i++) {
            bonds[i] = ac.getBond(i);
            IAtom atom0 = bonds[i].getAtom(0);
            IAtom atom1 = bonds[i].getAtom(1);
            if ((atom0.getAtomicNumber() >= 1) && (atom1.getAtomicNumber() >= 1)) {
                Integer iAtom0 = atomMap.get(atom0);
                Integer iAtom1 = atomMap.get(atom1);
                if (iAtom0 == null) {
                    log.debug("no atom0 " + atom0 + " " + atom1);
                    continue;
                }
                if (iAtom1 == null) {
                    log.debug("no atom1 " + atom0 + " " + atom1);
                    continue;
                }
                String key01 = iAtom0 + " " + iAtom1;
                String key10 = iAtom1 + " " + iAtom0;
                Integer order = getBondOrder(bonds[i]);
                bondMap.put(key01, order);
                bondMap.put(key10, order);
            }
        }
        this.nodeValidator.init(atoms.length);
        nPatterns = nodeValidator.patternCount();
    }

    public PathVars getPathVars() {
        return pVars;
    }

    boolean initialize() {
        for (IAtom atom : ac.atoms()) {
            atom.setFlag(Atom.VISITED, false);
        }
        if (currentAtom < lastAtom) {
            currentAtom++;

            return true;
        } else {
            return false;
        }
    }

    void initialize(int start) {
        IAtom startAtom;
        startAtom = ac.getAtom(start);

        for (IBond bond : ac.bonds()) {
            bond.setFlag(Atom.VISITED, false);
        }

        for (IAtom atom : ac.atoms()) {
            atom.setFlag(Atom.VISITED, false);
        }

        pathAtoms = new ArrayList<>();
        pathBonds = new ArrayList<>();

        for (int i = 0; i < nodeValidator.pathSize(currentPattern); i++) {
            pathAtoms.add(null);
            pathBonds.add(null);
        }

        path = new ArrayList<>();
        path.add(start);
        startAtom.setFlag(Atom.VISITED, true);
        pathLength = 1;
        pathPos = 0;
    }

    int getBondOrder(IBond bond) {
        Order cOrder = bond.getOrder();
        final int order;
        if (cOrder == Order.DOUBLE) {
            order = 2;
        } else if (cOrder == Order.TRIPLE) {
            order = 3;
        } else {
            order = 1;
        }
        return order;

    }

    public String scanPattern(String pattern, IAtom atom) {
        char[] cArray = pattern.toCharArray();
        StringBuilder sBuilder = new StringBuilder();
        for (int i = 0, sLen = cArray.length; i < sLen; i++) {
            char c1 = cArray[i];
            char c2 = cArray[i];
            String elemName = null;
            int nChar = 0;
            if (Character.isLetter(c1) && Character.isUpperCase(c1)) {
                nChar = 1;
                if ((i + 1) < sLen) {
                    c2 = cArray[i + 1];
                    if (Character.isLetter(c2) && Character.isLowerCase(c2)) {
                        nChar = 2;
                    }
                }
// fixme, what about 3 characters like Caa, 
                if (((i + nChar) == sLen) || !Character.isLetter(cArray[i + nChar])) {
                    if (nChar == 1) {
                        elemName = Character.toString(c1);
                    } else if (nChar == 2) {
                        elemName = "" + c1 + c2;
                    }
                }
            }
            if (elemName != null) {
                if (elemName.equals("X")) {
                    sBuilder.append('1');
                } else if (elemName.equals(atom.getSymbol())) {
                    sBuilder.append('1');
                } else {
                    sBuilder.append('0');
                }
                i += nChar - 1;
            } else {
                sBuilder.append(c1);
            }
        }
        return sBuilder.toString();
    }

    public boolean checkBonded(final String bondPattern, final IAtom atom1, final IAtom atom2) {
        IBond bond = ac.getBond(atom1, atom2);
        boolean bonded = false;
        if (bond != null) {
            if (checkBond(bondPattern, bond)) {
                bonded = true;
            }

        }
        return bonded;
    }
//     public boolean checkAtom(int aNum, boolean visited, final int[] currentPath,final int patternIndex, final int pathIndex, final int atomIndex, HashMap bondMap) {

    boolean checkAtom(int index, int iAtom) {
        IAtom atom = atoms[iAtom];
        boolean visited = atom.getFlag(Atom.VISITED);
        int aNum = atom.getAtomicNumber();
        int[] currentPath = null;
        if (path != null) {
            currentPath = new int[path.size()]; // fixme, need to populate
            for (int i = 0; i < currentPath.length; i++) {
                currentPath[i] = path.get(i);
            }
        }
        boolean aType = nodeValidator.checkAtom(aNum, visited, currentPath, currentPattern, index, iAtom, bondMap);
        if (log.isDebugEnabled()) {
            log.debug("check Atom index " + index + " iatom " + iAtom + " pattern " + currentPattern + " atype " + aType);
        }
        return aType;
    }
    //  public boolean checkBond(int order, final int[] currentPath,final int patternIndex, final int pathIndex, final int bondIndex) {

    boolean checkBond(int index, int iBond) {
        IBond bond = bonds[iBond];
        int order = getBondOrder(bond);
        int[] currentPath = new int[path.size()]; // fixme, need to populate
        return nodeValidator.checkBond(order, currentPath, currentPattern, index, iBond);
    }

    boolean checkBond(String testBond, IBond bond) {
        boolean aType = false;
        if (testBond.equals("~")) {
            aType = true;
        } else {
            Order order = bond.getOrder();
            if ((order == Order.SINGLE) && testBond.equals("-")) {
                aType = true;
            } else if ((order == Order.DOUBLE) && testBond.equals("=")) {
                aType = true;
            } else if ((order == Order.TRIPLE) && testBond.equals("#")) {
                aType = true;
            }
        }

        return aType;
    }

    int getAtomIndex(IAtom atom) {
        Integer value = atomMap.get(atom);
        int index = -1;

        if (value != null) {
            index = value;
        }

        return index;
    }

    public int getAtomIndex(int i) {
        return getAtomIndex(path, i);
    }

    int getAtomIndex(List<Integer> aList, int i) {
        return aList.get(i);
    }

    int getBondIndex(List<Integer> aList, int i) {
        int iBond = aList.get(i);
        if (iBond == -1) {
            log.debug(aList + " " + i);
        }

        return iBond;
    }

    String dumpPath() {
        int n = path.size();
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append("Current Path **************");
        for (int i = 0; i < n; i++) {
            int iAtom = getAtomIndex(i);
            sBuilder.append(iAtom + " ");
        }
        sBuilder.append("\n");
        for (int i = 0; i < n; i++) {
            IAtom atom = ac.getAtom(getAtomIndex(i));
            sBuilder.append(atom.getSymbol());
        }
        sBuilder.append("\n");
        return sBuilder.toString();
    }

    boolean dfIterate() {

        IAtom startAtom;
        int startAtomIndex = -1;

        if (log.isDebugEnabled()) {
            log.debug("dfIterate current Atom " + currentAtom + " pathLength " + pathLength
                    + " pathPos  " + pathPos);
        }

        if (pathLength == 0) {
            startAtomIndex = currentAtom;
            startAtom = ac.getAtom(currentAtom);

            if (checkAtom(0, startAtomIndex)) {
                initialize(currentAtom);
                log.debug("initialized <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                if (nodeValidator.pathSize(currentPattern) == 1) {
                    return true;
                }
                return dfIterate();
            } else {
                log.debug("initial atom failed");
                return false;
            }
        }

        if (pathPos < 0) {
            pathLength = 0;
            log.debug("pathLength zero");
            return false;
        }

        log.debug("path pos is " + pathPos);

        List<Integer> localSphere = pathAtoms.get(pathPos);
        List<Integer> localBonds = pathBonds.get(pathPos);
        if (log.isDebugEnabled()) {
            log.debug(dumpPath());
        }

        if (localSphere == null) {
            localSphere = new ArrayList<>();
            localBonds = new ArrayList<>();
            pathAtoms.set(pathPos, localSphere);
            pathBonds.set(pathPos, localBonds);

            log.debug(String.format("empty sphere pathLength %d pathPos %d", pathLength, pathPos));

            startAtomIndex = getAtomIndex(pathPos);
            startAtom = ac.getAtom(startAtomIndex);

            log.debug(String.format("look for atoms bonded to %d %s %d", getAtomIndex(startAtom), startAtom.getSymbol(), startAtom.getID()));

            if (pathLength < nodeValidator.pathSize(currentPattern)) {
                List<IBond> bonds = ac.getConnectedBondsList(startAtom);
                for (IBond bond : bonds) {
                    IAtom sAtom = bond.getConnectedAtom(startAtom);

                    log.debug(String.format("test atom %d", getAtomIndex(sAtom)));

                    if (sAtom.getAtomicNumber() > 0) {
                        if (!sAtom.getFlag(Atom.VISITED)) {
                            log.debug(String.format("add atom indexed %d with symbol %s to sphere at path pos %d",
                                    getAtomIndex(sAtom), sAtom.getSymbol(), pathPos));

                            int bondNumber = ac.getBondNumber(bond);
                            if (bondNumber >= 0) {
                                localSphere.add(ac.getAtomNumber(sAtom));
                                localBonds.add(ac.getBondNumber(bond));
                            }
                        }
                    }
                }
            }
        }

        IAtom nextAtom;
        int nextAtomIndex;
        int branchAtom = -1;
        log.debug("check for an atom in sphere");

        if (pathLength < nodeValidator.pathSize(currentPattern)) {
            for (int i = 0; i < localSphere.size(); i++) {
                int iAtom = getAtomIndex(localSphere, i);
                int iBond = getBondIndex(localBonds, i);
                boolean aType = checkAtom(pathLength, iAtom);
                boolean bType = checkBond(pathLength, iBond);

                if (aType && bType) {
                    log.debug(String.format("got sphere atom %d", i));
                    branchAtom = i;

                    break;
                }
            }
        }

        if (branchAtom == -1) {
// fixme cheap trick to turn off test
            if (pathPos < ((pathLength - 1) - 100)) {
                pathPos++;
                log.debug(String.format("pathPos incr %d %d", pathLength, pathPos));
            } else {
                path.remove(path.size() - 1);
                pathAtoms.set(pathPos, null);
                pathBonds.set(pathPos, null);
                pathLength--;
                pathPos--;
                log.debug(String.format("pathPos reduce %d %d", pathLength, pathPos));
            }
            if (pathLength == 0) {
                log.debug("pathLength zero return ");
                return false;
            }
        } else {
            nextAtomIndex = getAtomIndex(localSphere, branchAtom);
            nextAtom = ac.getAtom(nextAtomIndex);

            log.debug(String.format(" add to path branchAtom %d with Index %d %s", branchAtom, nextAtomIndex, nextAtom.getSymbol()));

            nextAtom.setFlag(Atom.VISITED, true);
            path.add(ac.getAtomNumber(nextAtom));
            pathLength++;
            pathPos++;
            if (debug) {
                dumpPath();
            }

            if (pathPos < nodeValidator.pathSize(currentPattern)) {
                int jumpPos = nodeValidator.getJump(currentPattern, pathPos);
                log.debug(String.format("check atomNode for jump %d", pathPos));
                if (jumpPos != -1) {
                    log.debug(String.format(" jump to %d", jumpPos));
                    pathPos = jumpPos;
                }
            }
        }

// fixme not correct for ring closures ??
        if (pathLength < nodeValidator.pathSize(currentPattern)) {
            log.debug(String.format("pathLength min return pathLength %d pathPos %d max %d", pathLength,
                    pathPos, nodeValidator.pathSize(currentPattern)));
            return dfIterate();
        }

        log.debug(String.format("end return  pathLength %d pathPos %d", pathLength, pathPos));

        return true;
    }

    public void remove() {
    }

    public List<Integer> next() {
        log.debug("############ next #################");
        if ((path == null) || (pathLength == 0)) {
            throw new NoSuchElementException();
        }
        return path;
    }

    public boolean hasNext() {
        while (true) {
            if (pathLength == 0) {
                if (!initialize()) {
                    path = null;
                    break;
                }
            }

            if (dfIterate()) {
                break;
            }
        }

        return ((path != null) && (pathLength != 0));
    }

    public void processPatterns() {
        for (currentPattern = 0; currentPattern < nPatterns; currentPattern++) {
            path = null;
            pathLength = 0;
            currentAtom = -1;
            while (hasNext()) {
                List<Integer> nextPath = next();
                int mode = nodeValidator.getMode(currentPattern);
                if (mode == 0) {
                    nodeValidator.assignProps(nextPath, currentPattern);
                } else if (mode == 2) {
                    var params = nodeValidator.getParams(nextPath, currentPattern);
                    int atomIndex = (Integer) params.get(0);
                    for (int i = 1; i < params.size(); i += 2) {
                        String name = (String) params.get(i);
                        String value = (String) params.get(i + 1);
                        atoms[atomIndex].setProperty(name, value);
                    }
                }
            }
        }
    }

    int getPropIndex(String[] propNames, String propName) {
        int index = -1;
        for (int i = 0; i < propNames.length; i++) {
            if (propNames[i].equals(propName)) {
                index = i;
                break;
            }
        }
        return index;
    }

    public void setHybridization() {
        String[] propNames = nodeValidator.getPropertyNames();
        int spIndex = getPropIndex(propNames, "sp");
        int sp2Index = getPropIndex(propNames, "sp2");
        if (spIndex != -1) {
            boolean[][] props = nodeValidator.getProperties();
            for (int i = 0; i < props.length; i++) {
                if (props[i][spIndex]) {
                    atoms[i].setProperty("hyb", 1);
                } else if (props[i][sp2Index]) {
                    atoms[i].setProperty("hyb", 2);
                } else {
                    atoms[i].setProperty("hyb", 3);
                }
            }
        }

    }

    public void setProperties(String propName, String propFlag) {
        int flag = Atom.ATOMFLAGS.valueOf(propFlag).getIndex();
        String[] propNames = nodeValidator.getPropertyNames();
        int arIndex = getPropIndex(propNames, propName);
        if (arIndex != -1) {
            boolean[][] props = nodeValidator.getProperties();
            for (int i = 0; i < props.length; i++) {
                if (flag == Atom.AROMATIC) {
                    if (!atoms[i].getFlag(flag)) {
                        atoms[i].setFlag(flag, props[i][arIndex]);
                    }
                } else {
                    atoms[i].setFlag(flag, props[i][arIndex]);
                }
            }
        }
    }

    public void setProperties(String propName, String propFlag, Boolean value) {
        String[] propNames = nodeValidator.getPropertyNames();
        int arIndex = getPropIndex(propNames, propName);
        if (arIndex != -1) {
            boolean[][] props = nodeValidator.getProperties();
            for (int i = 0; i < props.length; i++) {
                if (props[i][arIndex]) {
                    atoms[i].setProperty(propFlag, value);
                }
            }
        }
    }
}
