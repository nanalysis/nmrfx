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

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.util.FastMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AtomEnergyProp {
    private static final Logger log = LoggerFactory.getLogger(AtomEnergyProp.class);

    // regexp patterns for parsing Amber parameter files
    static final Pattern atomPattern = Pattern.compile("(^\\S\\S?)\\s+([-\\.0-9]+)\\s+([-\\.0-9]+)\\s*(.*)");
    static final Pattern bondPattern = Pattern.compile("(^\\S.)-(\\S.)\\s+([-\\.0-9]+)\\s+([-\\.0-9]+)\\s*(.*)");
    static final Pattern angPattern = Pattern.compile("(^\\S.)-(\\S.)-(\\S.)\\s+([-\\.0-9]+)\\s+([-\\.0-9]+)\\s*(.*)");
    static final Pattern dihPattern = Pattern.compile("(^\\S.)-(\\S.)-(\\S.)-(\\S.)\\s+([\\d]+)\\s+([-\\.0-9]+)\\s+([-\\.0-9]+)\\s+([-\\.0-9]+)\\s*(.*)");
    static final Pattern imprPattern = Pattern.compile("(^\\S.)-(\\S.)-(\\S.)-(\\S.)\\s+([-\\.0-9]+)\\s+([-\\.0-9]+)\\s+([-\\.0-9]+)\\s*(.*)");
    public static double[][][] irpTable;

    private static boolean FILE_LOADED = false;
    private static boolean PARM_FILE_LOADED = false;

    final String name;
    private final int aNum;
    //leonard-jones a parameter
    private final double a;
    //leonard-jones b parameter
    private final double b;
    //ideal distance
    private final double r;
    //hard sphere repulsive distance
    private final double rh;
    //energy at r
    private final double e;
    //electric charge
    private final double c;
    //atomic mass
    private final double mass;
    // hbond donor (1), acceptor (-1)
    private final int hbondMode;
    //scaling factor
    private final static double rscale = 0.68;
    private static final HashMap<String, AtomEnergyProp> propMap = new HashMap<String, AtomEnergyProp>();
    private final static double hbondDelta = 0.30;
    private static final Map<Integer, AtomEnergyProp> DEFAULT_MAP = new HashMap<>();
    public static Map<String, Integer> torsionMap = new HashMap<>();

    public AtomEnergyProp(final String name, int aNum, final double a, final double b, final double r, final double rh, final double e, final double c, final double mass, final int hbondMode) {
        this.name = name;
        this.aNum = aNum;
        this.a = FastMath.sqrt(a);
        this.b = FastMath.sqrt(b);
        this.r = r;
        this.rh = rh;
        this.e = e;
        this.c = c;
        this.mass = mass;
        this.hbondMode = hbondMode;
    }

    public static void readPropFile() throws FileNotFoundException, IOException {
        if (!FILE_LOADED) {
            readPropFile("reslib_iu/params.txt");
        }
    }

    public static void readPropFile(String fileName) throws IOException {
        FILE_LOADED = true;
        Reader reader;
        if (fileName.startsWith("reslib_iu")) {
            ClassLoader cl = ClassLoader.getSystemClassLoader();
            InputStream istream = cl.getResourceAsStream(fileName);
            if (istream == null ) {
                throw new FileNotFoundException("Unable to find property file.");
            }
            reader = new InputStreamReader(istream);
        } else {
            reader = new FileReader(fileName);
        }

        AtomicReference<List<String>> headerS = new AtomicReference<>(Arrays.asList());
        //AtomType        HardRadius      RMin    E       Mass    HBondType
        try (BufferedReader bufReader = new BufferedReader(reader)) {
            bufReader.lines().forEach(line -> {
                List<String> stringS = Arrays.asList(line.split("\\s+"));
                if (line.startsWith("AtomType")) {
                    headerS.set(stringS);
                } else {
                    String aType = stringS.get(headerS.get().indexOf("AtomType"));
                    double r = Double.parseDouble(stringS.get(headerS.get().indexOf("RMin")));
                    int aNum = Integer.parseInt(stringS.get(headerS.get().indexOf("AtomicNumber")));
                    double rh = Double.parseDouble(stringS.get(headerS.get().indexOf("HardRadius")));
                    double e = Double.parseDouble(stringS.get(headerS.get().indexOf("E")));
                    double m = Double.parseDouble(stringS.get(headerS.get().indexOf("Mass")));
                    int hType = (int) Double.parseDouble(stringS.get(headerS.get().indexOf("HBondType")));

                    e = -e;
                    r = r * 2.0;
                    double a = 1.0;
                    double b = 1.0;
                    double c = 0.0;
                    AtomEnergyProp prop = new AtomEnergyProp(aType, aNum, a, b, r, rh, e, c, m, hType);
                    AtomEnergyProp.add(aType, prop);
                }
            });
        }
        DEFAULT_MAP.put(1, get("H"));
        DEFAULT_MAP.put(6, get("CT"));
        DEFAULT_MAP.put(7, get("N"));
        DEFAULT_MAP.put(8, get("O"));
        DEFAULT_MAP.put(9, get("F"));
        DEFAULT_MAP.put(12, get("MG"));
        DEFAULT_MAP.put(15, get("P"));
        DEFAULT_MAP.put(16, get("S"));
        DEFAULT_MAP.put(17, get("Cl"));
        DEFAULT_MAP.put(20, get("C0"));
        DEFAULT_MAP.put(26, get("FE"));
        DEFAULT_MAP.put(29, get("CU"));
        DEFAULT_MAP.put(30, get("Zn"));
        DEFAULT_MAP.put(35, get("Br"));
        DEFAULT_MAP.put(53, get("I"));

    }

    public static void makeIrpMap() throws FileNotFoundException, IOException {
        if (!PARM_FILE_LOADED) {
            makeIrpMap("reslib_iu/parm15ipq_10.3.dat");
        }
    }

    static String getAtomKey(Matcher matcher, int nAtoms) {
        StringBuilder sBuilder = new StringBuilder();
        for (int i = 0; i < nAtoms; i++) {
            if (i > 0) {
                sBuilder.append("-");
            }
            sBuilder.append(matcher.group(i + 1).trim());
        }
        return sBuilder.toString();
    }

    public static void makeIrpMap(String fileName) throws IOException {
        PARM_FILE_LOADED = true;
        Reader reader;
        if (fileName.startsWith("reslib_iu")) {
            ClassLoader cl = ClassLoader.getSystemClassLoader();
            InputStream istream = cl.getResourceAsStream(fileName);
            if (istream == null ) {
                throw new FileNotFoundException();
            }
            reader = new InputStreamReader(istream);
        } else {
            reader = new FileReader(fileName);
        }
        torsionMap.clear();
        List<List<double[]>> torsionMasterList = new ArrayList<>();
        List<double[]> torsionList;
        int nVals = 1;
        String lastKey = "";
        String line;
        try (BufferedReader bufReader = new BufferedReader(reader)) {
            while ((line = bufReader.readLine()) != null) {
                line = line.trim();
                Matcher matcher = dihPattern.matcher(line);
                if (matcher.matches()) {
                    String key = getAtomKey(matcher, 4);
                    double barrier = Double.parseDouble(matcher.group(6));
                    double phase = Double.parseDouble(matcher.group(7)) * Math.PI / 180.0;
                    phase = Util.reduceAngle(phase);
                    double periodicity = Math.abs(Double.parseDouble(matcher.group(8)));

                    if (!torsionMap.containsKey(key)) {
                        torsionList = new ArrayList<>();
                        torsionMasterList.add(torsionList);
                        torsionMap.put(key, torsionMasterList.size() - 1);
                    } else {
                        int index = torsionMap.get(key);
                        torsionList = torsionMasterList.get(index);
                        if (!key.equals(lastKey)) {
                            torsionList.clear();
                        }
                    }
                    lastKey = key;
                    double[] vals = {barrier, periodicity, phase};
                    nVals = vals.length;
                    torsionList.add(vals);
                }
            }
        }

        irpTable = new double[torsionMasterList.size()][][];

        for (int i = 0; i < irpTable.length; i++) {
            irpTable[i] = new double[torsionMasterList.get(i).size()][nVals];
            for (int j = 0; j < irpTable[i].length; j++) {
                System.arraycopy(torsionMasterList.get(i).get(j), 0, irpTable[i][j], 0, nVals);
            }
        }
    }

    public static int getTorsionIndex(String torsionType) {
        // H-N-CX-C
        // 012345678
        boolean[][] generics = {{false, false}, {true, false}, {false, true}, {true, true}};
        StringBuilder sBuilder = new StringBuilder();
        int firstDash = torsionType.indexOf('-');
        int lastDash = torsionType.lastIndexOf('-');
        String[] torsionFields = torsionType.split("-");
        for (boolean[] generic : generics) {
            sBuilder.setLength(0);
            if (generic[0]) {
                sBuilder.append(("X"));
            } else {
                sBuilder.append(torsionFields[0]);
            }
            sBuilder.append("-").append(torsionFields[1]).append("-").
                    append(torsionFields[2]).append("-");
            if (generic[1]) {
                sBuilder.append(("X"));
            } else {
                sBuilder.append(torsionFields[3]);
            }
            Integer index = torsionMap.get(sBuilder.toString());
            if (index != null) {
                return index + 1;
            }
        }
        return 0;
    }

    public void clear() {
        propMap.clear();
    }

    public static void add(final String atomType, final AtomEnergyProp prop) {
        propMap.put(atomType, prop);
    }

    public static AtomEnergyProp get(final String atomType) {
        try {
            readPropFile();
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
        return propMap.get(atomType);
    }

    public static AtomEnergyProp getDefault(final int aNum) {
        try {
            readPropFile();
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
        return DEFAULT_MAP.get(aNum);
    }

    public String getName() {
        return name;
    }

    public int getAtomNumber() {
        return aNum;
    }

    /**
     * @return the a
     */
    public double getA() {
        return a;
    }

    public double getB() {
        return b;
    }

    public double getC() {
        return c;
    }

    public double getR() {
        return r;
    }

    public double getRh() {
        return rh;
    }

    public double getE() {
        return e;
    }

    public double getMass() {
        return mass;
    }

    public int getHBondMode() {
        return hbondMode;
    }

    /**
     * computes interaction between two molecules based on table values
     * <p>
     * This method calculates the interation between 2 molecules. It retrieves
     * the ideal energy values from the table for both atoms It then calculates
     * the radius or distance between both atoms. The rh value is calculating by
     * simply adding both the radius. Hydrogen may be removed and substited by a
     * certain number of Angstrom's indicated by AtomEnergyProp
     *
     * @param AtomEnergy iProp Properties of atom 1
     * @param AtomEnergy jProp properties of atom 2
     * @param boolean hardSphere determines if you want to calculate rh w/out
     * hydrogen
     * @param double hardSphere determines the value you want to add in
     * substitution for hydrogen
     */
    public static EnergyPair getInteraction(final Atom atom1, final Atom atom2, double hardSphere,
            boolean usehardSphere, double shrinkValue, double shrinkHValue) {

        AtomEnergyProp iProp = (AtomEnergyProp) atom1.atomEnergyProp;
        AtomEnergyProp jProp = (AtomEnergyProp) atom2.atomEnergyProp;

        double a1 = iProp.getA() * jProp.getA();
        double b1 = iProp.getB() * jProp.getB();
        double c1 = 0.0;

        double r = FastMath.sqrt(iProp.getR() * jProp.getR());
        double ea = -FastMath.sqrt(iProp.getE() * jProp.getE());
        double r2 = (rscale * r) * (rscale * r);
        double rh1 = iProp.getRh();
        double rh2 = jProp.getRh();
        if ((atom1.getAtomicNumber() != 1) || (atom2.getAtomicNumber() != 1) || jProp.getName().equals("HO")) {
            Atom testAtom1;
            Atom testAtom2;
            if (atom1.rotUnit < atom2.rotUnit) {
                testAtom1 = atom1;
                testAtom2 = atom2;
            } else {
                testAtom2 = atom1;
                testAtom1 = atom2;
            }
            Atom rotGroup1 = testAtom1.rotGroup;
            Atom rotGroup2 = testAtom2.rotGroup;
            Atom parent2 = null;
            if (rotGroup2 != null) {
                parent2 = rotGroup2.parent;
            }
            // atoms in close groups are allowed to be a little closer to add a little more flexibility since we don't allow bond angles and lengths to change
            if (parent2 != null && (parent2.parent == testAtom1)) {
                rh1 -= 0.1;
                rh2 -= 0.1;
            } else if (rotGroup1 != null) {
                Atom parent1 = rotGroup1.parent;
                if (parent1 != null) {
                    if (parent1.parent == testAtom2) {
                        rh1 -= 0.1;
                        rh2 -= 0.1;
                    } else if (parent1 == testAtom2.parent) {
                        rh1 -= 0.1;
                        rh2 -= 0.1;
                    } else if (rotGroup1 == parent2) {
                        rh1 -= 0.1;
                        rh2 -= 0.1;
                    } else if (parent1 == testAtom2.parent) {
                        rh1 -= 0.1;
                        rh2 -= 0.1;
                    } else if (parent2 == testAtom1.parent) {
                        rh1 -= 0.1;
                        rh2 -= 0.1;
                    } else if (parent1 == parent2) {
                        //  rh1 -= 0.1;
                        //  rh2 -= 0.1;
                    }
                }
            }
        }

        int hbond = iProp.hbondMode * jProp.hbondMode;
        if (hbond < 0) {
            rh1 -= hbondDelta;
            rh2 -= hbondDelta;
        }
        if (atom1.getAtomicNumber() != 1) {
            rh1 -= shrinkValue;
        } else {
            rh1 -= shrinkHValue;
        }
        if (atom2.getAtomicNumber() != 1) {
            rh2 -= shrinkValue;
        } else {
            rh2 -= shrinkHValue;
        }

        if (usehardSphere) {
            if (atom1.hydrogens > 0) {
                rh1 += hardSphere;
            }
            if (atom2.hydrogens > 0) {
                rh2 += hardSphere;
            }
        }
        double rh = rh1 + rh2;

        EnergyPair ePair = new EnergyPair(a1, b1, c1, r, r2, rh, ea);
        return ePair;
    }

    public static boolean interact(final Atom atom1, final Atom atom2) {
        final boolean value;
        AtomEnergyProp prop1 = (AtomEnergyProp) atom1.atomEnergyProp;
        AtomEnergyProp prop2 = (AtomEnergyProp) atom2.atomEnergyProp;
// fixme  only appropriate for rh mode of repel function
        if ((prop2 != null) && (prop1 != null) && (prop1.rh > 1.0e-6) && (prop2.rh > 1.0e-6)) {
            value = true;
        } else {
            value = false;
        }
        return value;
    }

}
