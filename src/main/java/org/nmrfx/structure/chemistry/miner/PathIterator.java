package org.nmrfx.structure.chemistry.miner;

import java.util.*;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Atom.ATOMFLAGS;
import org.nmrfx.structure.chemistry.Order;

public class PathIterator implements Iterator {

    AtomContainer ac = null;
    ArrayList pathAtoms = null;
    ArrayList pathBonds = null;
    HashMap bondMap = new HashMap();
    ArrayList path = null;
    public static boolean debug = false;
    int pathLength = 0;
    int pathPos = 0;
    int firstAtom = 0;
    int lastAtom = 0;
    int min = 3;
    int currentAtom = -1;
    Map atomMap = new HashMap();
    PathVars pVars = null;
    NodeValidator nodeValidator;
    IAtom[] atoms;
    IBond[] bonds;
    int currentPattern = 0;
    int nPatterns = 0;

    public PathIterator(AtomContainer ac) {
        this.ac = ac;
        lastAtom = ac.getAtomCount() - 1;
        pVars = new PathVars(ac, "p");
    }

    public void init(NodeValidator nodeValidator) {
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
            int iAtom0 = (Integer) atomMap.get(atom0);
            int iAtom1 = (Integer) atomMap.get(atom1);
            String key01 = iAtom0 + " " + iAtom1;
            String key10 = iAtom1 + " " + iAtom0;
            Integer order = getBondOrder(bonds[i]);
            bondMap.put(key01, order);
            bondMap.put(key10, order);
        }
        this.nodeValidator.init(atoms.length);
        nPatterns = nodeValidator.patternCount();
    }

    public PathVars getPathVars() {
        return pVars;
    }

    void setOutputMode(int outputMode) {
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
        IAtom startAtom = null;
        startAtom = ac.getAtom(start);

        for (IBond bond : ac.bonds()) {
            bond.setFlag(Atom.VISITED, false);
        }

        for (IAtom atom : ac.atoms()) {
            atom.setFlag(Atom.VISITED, false);
        }

        pathAtoms = new ArrayList();
        pathBonds = new ArrayList();

        for (int i = 0; i < nodeValidator.pathSize(currentPattern); i++) {
            pathAtoms.add(null);
            pathBonds.add(null);
        }

        path = new ArrayList();
        path.add(new Integer(start));
        startAtom.setFlag(Atom.VISITED, true);
        pathLength = 1;
        pathPos = 0;
    }

    int getBondOrder(IBond bond) {
        Order cOrder = bond.getOrder();
        int order = 1;
        if (cOrder == Order.SINGLE) {
            order = 1;
        } else if (cOrder == Order.DOUBLE) {
            order = 2;
        } else if (cOrder == Order.TRIPLE) {
            order = 3;
        }
        return order;

    }

    public String scanPattern(String pattern, IAtom atom) {
        char[] cArray = pattern.toCharArray();
        StringBuilder sBuilder = new StringBuilder();
        for (int i = 0, sLen = cArray.length; i < sLen; i++) {
            char c1 = cArray[i];
            char c2 = cArray[i];
            String elemName = null;;
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
//System.out.println(pattern);
//System.out.println(sBuilder.toString());
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
                currentPath[i] = ((Integer) path.get(i));
            }
        }
        boolean aType = nodeValidator.checkAtom(aNum, visited, currentPath, currentPattern, index, iAtom, bondMap);
        if (debug) {
            System.out.println("check Atom index " + index + " iatom " + iAtom + " pattern " + currentPattern + " atype " + aType);
        }
        return aType;
    }
    //  public boolean checkBond(int order, final int[] currentPath,final int patternIndex, final int pathIndex, final int bondIndex) {

    boolean checkBond(int index, int iBond) {
        IBond bond = bonds[iBond];
        int order = getBondOrder(bond);
        int[] currentPath = new int[path.size()]; // fixme, need to populate
        boolean bType = nodeValidator.checkBond(order, currentPath, currentPattern, index, iBond);
        //System.out.println("check Bond index " + index + " iatom " + iBond + " pattern " + currentPattern + " btype "  + bType);
        return bType;
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
        //System.out.println(testBond + " " + bond.getOrder() + " " + aType);

        return aType;
    }

    int getAtomIndex(IAtom atom) {
        Integer value = (Integer) atomMap.get(atom);
        int index = -1;

        if (value != null) {
            index = value.intValue();
        }

        return index;
    }

    public int getAtomIndex(int i) {
        return getAtomIndex(path, i);
    }

    int getAtomIndex(ArrayList aList, int i) {
        int iAtom = ((Integer) aList.get(i)).intValue();
        return iAtom;
    }

    int getBondIndex(ArrayList aList, int i) {
        int iBond = ((Integer) aList.get(i)).intValue();
        if (iBond == -1) {
            System.out.println(aList.toString() + " " + i);
        }

        return iBond;
    }

    void dumpPath() {
        int n = path.size();
        System.out.println("Current Path **************");
        for (int i = 0; i < n; i++) {
            int iAtom = getAtomIndex(i);
            System.out.print(iAtom + " ");
        }
        System.out.println("");
        for (int i = 0; i < n; i++) {
            IAtom atom = ac.getAtom(getAtomIndex(i));
            System.out.print(atom.getSymbol());
        }
        System.out.println("");
    }

    boolean dfIterate() {
        boolean ok = true;
        IAtom startAtom = null;
        int startAtomIndex = -1;

        if (debug) {
            System.out.println("dfIterate current Atom " + currentAtom + " pathLength " + pathLength
                    + " pathPos  " + pathPos);
        }

        if (pathLength == 0) {
            startAtomIndex = currentAtom;
            startAtom = ac.getAtom(currentAtom);

            if (checkAtom(0, startAtomIndex)) {
                initialize(currentAtom);

                if (debug) {
                    System.out.println(
                            "initialized <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                }
                if (nodeValidator.pathSize(currentPattern) == 1) {
                    return true;
                }
                return dfIterate();
            } else {
                if (debug) {
                    System.out.println("initial atom failed");
                }

                return false;
            }
        }

        if ((pathLength == 0) || (pathPos < 0)) {
            pathLength = 0;

            if (debug) {
                System.out.println("pathLength zero");
            }

            return false;
        }

        if (debug) {
            //dumpPath();
        }

        if (debug) {
            System.out.println("path pos is " + pathPos);
        }

        ArrayList localSphere = null;
        ArrayList localBonds = null;
        localSphere = (ArrayList) pathAtoms.get(pathPos);
        localBonds = (ArrayList) pathBonds.get(pathPos);

        if (localSphere == null) {
            localSphere = new ArrayList();
            localBonds = new ArrayList();
            pathAtoms.set(pathPos, localSphere);
            pathBonds.set(pathPos, localBonds);

            if (debug) {
                System.out.println("empty sphere pathLength " + pathLength + " pathPos " + pathPos);
            }

            boolean addedAtom = false;
            startAtomIndex = getAtomIndex(pathPos);
            startAtom = ac.getAtom(startAtomIndex);

            if (debug) {
                System.out.println("look for atoms bonded to " + getAtomIndex(startAtom) + " " + startAtom.getSymbol() + " " + startAtom.getID());
            }

            if (pathLength < nodeValidator.pathSize(currentPattern)) {
                List<IBond> bonds = ac.getConnectedBondsList(startAtom);
                for (IBond bond : bonds) {
                    IAtom atom = bond.getConnectedAtom(startAtom);

                    if (debug) {
                        System.out.println("test atom " + getAtomIndex(atom));
                    }
                    if (!atom.getFlag(Atom.VISITED)) {
                        if (debug) {
                            System.out.println("add atom indexed " + getAtomIndex(atom) + " with symbol " + atom.getSymbol() + " to sphere at path pos " + pathPos);
                        }

                        localSphere.add(ac.getAtomNumber(atom));
                        localBonds.add(ac.getBondNumber(bond));
                        addedAtom = true;
                    }
                }
            }
        }

        IAtom nextAtom = null;
        int nextAtomIndex = -1;
        int branchAtom = -1;
        if (debug) {
            System.out.println("check for an atom in sphere");
        }

        if (pathLength < nodeValidator.pathSize(currentPattern)) {
            for (int i = 0; i < localSphere.size(); i++) {
                int iAtom = getAtomIndex(localSphere, i);
                int iBond = getBondIndex(localBonds, i);
                boolean aType = checkAtom(pathLength, iAtom);
                boolean bType = checkBond(pathLength, iBond);

                if (aType && bType) {
                    if (debug) {
                        System.out.println("got sphere atom " + i);
                    }
                    branchAtom = i;

                    break;
                }
            }
        }

        if (branchAtom == -1) {
// fixme cheap trick to turn off test
            if (pathPos < ((pathLength - 1) - 100)) {
                pathPos++;
                if (debug) {
                    System.out.println("pathPos incr  " + pathLength + " " + pathPos);
                }
            } else {
                path.remove(path.size() - 1);
                pathAtoms.set(pathPos, null);
                pathBonds.set(pathPos, null);
                pathLength--;
                pathPos--;
                if (debug) {
                    System.out.println("pathLength reduce  " + pathLength + " " + pathPos);
                }
            }
            if (pathLength == 0) {
                if (debug) {
                    System.out.println("pathLength zero return ");
                }

                return false;
            }
        } else {
            nextAtomIndex = getAtomIndex(localSphere, branchAtom);
            nextAtom = ac.getAtom(nextAtomIndex);

            if (debug) {
                System.out.println(" add to path branchAtom " + branchAtom + " with Index  " + nextAtomIndex + " " + nextAtom.getSymbol());
            }
            nextAtom.setFlag(Atom.VISITED, true);
            path.add(new Integer(ac.getAtomNumber(nextAtom)));
            pathLength++;
            pathPos++;
            if (debug) {
                dumpPath();
            }

            if (pathPos < nodeValidator.pathSize(currentPattern)) {
                int jumpPos = nodeValidator.getJump(currentPattern, pathPos);

                if (debug) {
                    System.out.println("check atomNode for jump" + pathPos);
                }

                if (jumpPos != -1) {
                    if (debug) {
                        System.out.println(" jump to " + jumpPos);
                    }

                    pathPos = jumpPos;
                }
            }
        }

// fixme not correct for ring closures ??
        if (pathLength < nodeValidator.pathSize(currentPattern)) {
            if (debug) {
                System.out.println("pathLength min return pathLength " + pathLength + " pathPos "
                        + pathPos + " max " + nodeValidator.pathSize(currentPattern));
            }

            return dfIterate();
        }

        if (debug) {
            System.out.println("end return  pathLength " + pathLength + " pathPos " + pathPos);
        }

        return true;
    }

    public void remove() {
    }

    public Object next() {
        if (debug) {
            System.out.println("############ next #################");
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
        //if ((path != null) && (pathLength > 1)) {
        //System.out.println("gotpath " + path.toString() + " len " + pathLength + " pat " + currentPattern);
        //}

        return ((path != null) && (pathLength != 0));
    }

    public void processPatterns() {
        for (currentPattern = 0; currentPattern < nPatterns; currentPattern++) {
            //System.out.println("current pattern " + currentPattern + " of " + nPatterns);
            path = null;
            pathLength = 0;
            currentAtom = -1;
            while (hasNext()) {
                ArrayList nextPath = (ArrayList) next();
                int mode = nodeValidator.getMode(currentPattern);
                if (mode == 0) {
                    nodeValidator.assignProps(nextPath, currentPattern);
                } else if (mode == 1) {
                    //System.out.println("is type " + currentPattern);
                } else if (mode == 2) {
                    ArrayList params = nodeValidator.getParams(nextPath, currentPattern);
                    //System.out.println("params " + params);
                    int atomIndex = (Integer) params.get(0);
                    for (int i = 1; i < params.size(); i += 2) {
                        String name = (String) params.get(i);
                        String value = (String) params.get(i + 1);
                        atoms[atomIndex].setProperty(name, value);
                        Atom atom = (Atom) atoms[atomIndex];
//                        System.out.println(atom.getFullName() + " " + name + " " + value);
                    }
                }
            }
        }
//        nodeValidator.dumpProps();
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
            boolean[][] props = nodeValidator.p;
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
            boolean[][] props = nodeValidator.p;
            for (int i = 0; i < props.length; i++) {
                atoms[i].setFlag(flag, props[i][arIndex]);
            }
        }

    }
}
