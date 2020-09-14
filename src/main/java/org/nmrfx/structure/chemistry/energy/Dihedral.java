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

import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.Molecule;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleValueChecker;
import org.apache.commons.math3.util.FastMath;
import org.nmrfx.structure.chemistry.Residue;

public class Dihedral {

    final Molecule molecule;
    static Random rand = new Random();
    //ArrayList<Atom> angleAtoms;
    //static ArrayList<Atom> pseudoAngleAtoms;
    double[] angleValues;
    double[] savedAngles;
    double[] bestValues;
    double[] normValues;
    double[] sincosValues;
    boolean sinCosMode = true;
    boolean centerBoundaries = false;
    static final double toDeg = 180.0 / FastMath.PI;
    static final double toRad = FastMath.PI / 180;
    static final double deltaV1 = 121.8084 * FastMath.PI / 180.0;
    static final double deltaV3 = 121.8084 * FastMath.PI / 180.0;
    int nBackbone = 0;
    int nSidechain = 0;
    double[][] boundaries = null;
    double[][] normBoundaries = null;
    double[][] ranBoundaries = null;
    double[] inputSigma = null;
    public EnergyLists energyList;
    double lastEnergy = 0.0;
    static long startTime = 0;
    double bestEnergy = Double.MAX_VALUE;
    int nEvaluations = 0;
    int updateAt = 10;
    int reportAt = 100;
    boolean usePseudo = false;
    private boolean usePseudoAsDefault = false;
    static double initPuckerAmplitude = 45 * toRad;
    static double initPseudoAngle = 18 * toRad;
    public static double backBoneScale = 4.0;
    static Map<String, List<AngleBoundary>> angleBoundaries = new HashMap<>();
    static List<Map<Residue, AngleProp>> torsionAngles = new ArrayList<>();

    double maxSigma = 20;

    public Dihedral(final EnergyLists energyList, final boolean usePseudo) {
        this.energyList = energyList;
        this.molecule = energyList.getMolecule();
        molecule.setDihedrals(this);
        usePseudoAsDefault = usePseudo;
        prepareAngles(usePseudo);
        startTime = System.currentTimeMillis();
    }

    public Dihedral(final EnergyLists energyList) {
        this.energyList = energyList;
        this.molecule = energyList.getMolecule();
        molecule.setDihedrals(this);
        prepareAngles(usePseudo);
        startTime = System.currentTimeMillis();
    }

    /**
     * Used to determine if the value of function of the CMAES optimizer has
     * converged
     */
    public class Checker extends SimpleValueChecker {

        public Checker(double relativeThreshold, double absoluteThreshold, int maxIter) {
            super(relativeThreshold, absoluteThreshold, maxIter);
        }

        public boolean converged(final int iteration, final PointValuePair previous, final PointValuePair current) {
            boolean converged = super.converged(iteration, previous, current);
            if (converged || (iteration == 1) || ((iteration % reportAt) == 0)) {
                long time = System.currentTimeMillis();
                long deltaTime = time - startTime;
                report(iteration, nEvaluations, deltaTime, energyList.atomList.size(), current.getValue());
            }
            return converged;
        }
    }

    public void setPseudoParameters(double pseudoAngle, double puckerAnplitude) {
        initPseudoAngle = pseudoAngle * toRad;
        initPuckerAmplitude = puckerAnplitude * toDeg;
    }

    public void report(int iteration, int nEvaluations, long time, int nContacts, double energy) {
        System.out.printf("%6d %6d %8d %5d %9.2f\n", iteration, nEvaluations, time, nContacts, energy);
    }

    /**
     * Determines the array size of bestValues, angleValues, and normValues
     *
     * @param usePseudo
     */
    public void prepareAngles(final boolean usePseudo) {
        this.usePseudo = usePseudo;
        ArrayList<Atom> angleAtoms = molecule.setupAngles();
        ArrayList<Atom> pseudoAngleAtoms = molecule.setupPseudoAngles();
        if (!usePseudo) {
            pseudoAngleAtoms.clear();
        }
        int nAngles = angleAtoms.size() + 2 * pseudoAngleAtoms.size() / 3;

        boundaries = new double[2][nAngles];
        ranBoundaries = new double[2][nAngles];
        angleValues = new double[nAngles];
        bestValues = new double[nAngles];

        int nNorm = nAngles;
        if (sinCosMode) {
            nNorm *= 2;
        }
        normValues = new double[nNorm];
        inputSigma = new double[nNorm];
        normBoundaries = new double[2][nNorm];
    }

    public void updateAt(int updateAt) {
        this.updateAt = updateAt;
    }

    public static void seed(long seed) {
        rand.setSeed(seed);
    }

    public static Random getRandom() {
        return rand;
    }

    public void setSinCosMode(boolean state) {
        sinCosMode = state;
    }

    public void setCenterBoundaries(boolean state) {
        centerBoundaries = state;
    }

    public void setStart() {
        startTime = System.currentTimeMillis();
    }
//    /** if use psuedo is set, calls prepare angles which resizes the bestValues
//     * angleValues, and norm Values array
//     * @param usePseudo 
//     */

    public void setUsePseudo(boolean usePseudo) {
        usePseudoAsDefault = usePseudo;
        prepareAngles(usePseudo);
    }
//

    public void saveDihedrals() {
        getDihedrals();
        savedAngles = new double[angleValues.length];
        System.arraycopy(angleValues, 0, savedAngles, 0, angleValues.length);
    }

    public void restoreDihedrals() {
        if (savedAngles.length == angleValues.length) {
            System.arraycopy(savedAngles, 0, angleValues, 0, angleValues.length);
            putDihedrals();
        }
    }

    /**
     * This method generates a list of atoms that have rotatable angles. Atoms
     * in the sugar ring and the RNA bases do not have rotatable angles, and are
     * not included in the list of atoms. The method that loops through every
     * atom in this newly created list. Sets angleValues to their initial value
     * based of angle values.
     */
    public void getDihedrals() {
        int i = 0;
        if (usePseudo) {
            ArrayList<Atom> pseudoAngleAtoms = molecule.getPseudoAngleAtoms();
            double[] v = new double[4];
            double[] pseudoVals;
            for (int j = 0; j < pseudoAngleAtoms.size(); j += 3) {
                Atom atom = pseudoAngleAtoms.get(j);
                v[2] = atom.dihedralAngle;

                atom = pseudoAngleAtoms.get(j + 1);
                v[1] = atom.dihedralAngle - deltaV1;
                atom = pseudoAngleAtoms.get(j + 2);
                v[3] = atom.dihedralAngle - deltaV3;

                pseudoVals = calcPseudoAngle(v[2], v[3]);
                //System.out.println("Get Dihedral PseudoAngle" + angleValues[i]);
                angleValues[i++] = reduceAngle(pseudoVals[0]);
                //System.out.println("Get Dihedral PuckerValue: " + angleValues[i]);
                angleValues[i++] = reduceAngle(pseudoVals[1]);

                //angleValues[i++] = reduceAngle(calcPseudoAngle(v[2], v[3]));
                //angleValues[i++] = puckerAmplitude;
                //System.out.printf("pseudo angle is %7.3f %7.3f %7.3f\n", v[2] * toDeg, v[3] * toDeg, angleValues[i - 1] * toDeg);
                //System.out.println("getDihedrals: " + v[2] + ", " + v[1] + "," + v[3]);
            }
        }
        //List of all atoms with rotatable angles as specified in 
        ArrayList<Atom> angleAtoms = molecule.getAngleAtoms();
        for (Atom atom : angleAtoms) {
            //reducing angles so that value of each angle is >0 and <2pi 
            angleValues[i++] = reduceAngle(atom.daughterAtom.dihedralAngle);
        }
    }

    /**
     * writes dihedral angles to a specified file. The file has 2 values: atom
     * name and dihedral angle Value
     *
     * @param fileName
     */
    public void writeDihedrals(String fileName) {
        PrintStream fileOut;
        try {
            fileOut = new PrintStream(fileName);
        } catch (FileNotFoundException e) {
            return;
        }
        int i = 0;
        boolean includePseudo = true;
        if (includePseudo) {
            ArrayList<Atom> pseudoAngleAtoms = molecule.setupPseudoAngles();
            for (Atom atom : pseudoAngleAtoms) {
                fileOut.format("%s %.6f\n", atom.getFullName(), atom.dihedralAngle * toDeg);
            }
        }
        ArrayList<Atom> angleAtoms = molecule.getAngleAtoms();
        for (Atom atom : angleAtoms) {
            fileOut.format("%s %.6f\n", atom.getFullName(), atom.daughterAtom.dihedralAngle * toDeg);
        }
        fileOut.close();
    }
//

    /**
     * read dihedral angles from a specified file. The file has 2 values: atom
     * name and dihedral angle Value
     *
     * @param fileName
     */
    public void readDihedrals(String fileName) {
        try (LineNumberReader lineReader = new LineNumberReader(new BufferedReader(new FileReader(fileName)))) {
            while (true) {
                String string = lineReader.readLine();
                if (string == null) {
                    break;
                }
                String[] fields = string.split(" ");
                Atom atom = Molecule.getAtomByName(fields[0]);
                double dihedral = Double.parseDouble(fields[1]);
                atom.dihedralAngle = (float) (dihedral / toDeg);
            }
            molecule.genCoords();
        } catch (FileNotFoundException fnE) {
        } catch (IOException ioE) {
        }
    }

    /**
     * Given torsion angle and pucker amplitude, determines the value of all
     * dihedral angles in the ribose sugar.
     *
     * @param angle
     * @param maxTorsionAngle
     */
    public void putPseudoAngle(double angle, double maxTorsionAngle) {
        angle = angle * toRad;
        maxTorsionAngle = maxTorsionAngle * toRad;
        initPuckerAmplitude = maxTorsionAngle;
        ArrayList<Atom> pseudoAngleAtoms = molecule.setupPseudoAngles();
        for (int j = 0; j < pseudoAngleAtoms.size(); j += 3) {
            double[] v = setSugarBonds(reduceAngle(angle), maxTorsionAngle);
            Atom atom2 = pseudoAngleAtoms.get(j);
            atom2.dihedralAngle = (float) reduceAngle(v[2]);
            //atom2.dihAngle.angleValue = (float) reduceAngle(v[2]);
            Atom atom1 = pseudoAngleAtoms.get(j + 1);
            atom1.dihedralAngle = (float) reduceAngle(v[1] + deltaV1);
            //atom.dihAngle.angleValue = (float) reduceAngle(v[1] + deltaV1);
            Atom atom3 = pseudoAngleAtoms.get(j + 2);
            atom3.dihedralAngle = (float) reduceAngle(v[3] + deltaV3);
            //atom3.dihAngle.angleValue = (float) reduceAngle(v[3] + deltaV3);
        }
        molecule.genCoords(false, null);
    }

    public void checkAngles() {
        if (angleValues != null) {
            for (int i = 0; i < angleValues.length; i++) {
                if (Double.isNaN(angleValues[i])) {
                    System.out.println("check " + i);
                }
            }
        }
    }

    /**
     * based on the angleValues value, the dihedral angles of molecules are set
     *
     */
    public void putDihedrals() {
        int i = 0;
        if (usePseudo) {
            ArrayList<Atom> pseudoAngleAtoms = molecule.setupPseudoAngles();
            for (int j = 0; j < pseudoAngleAtoms.size(); j += 3) {
                double[] v = setSugarBonds(reduceAngle(angleValues[i++]),
                        angleValues[i++]);
                Atom atom2 = pseudoAngleAtoms.get(j);
                atom2.dihedralAngle = (float) reduceAngle(v[2]);
                //atom2.dihAngle.angleValue = (float) reduceAngle(v[2]);
                Atom atom1 = pseudoAngleAtoms.get(j + 1);
                atom1.dihedralAngle = (float) reduceAngle(v[1] + deltaV1);
                //atom1.dihAngle.angleValue = (float) reduceAngle(v[1] + deltaV1);
                Atom atom3 = pseudoAngleAtoms.get(j + 2);
                atom3.dihedralAngle = (float) reduceAngle(v[3] + deltaV3);
                //atom3.dihAngle.angleValue = (float) reduceAngle(v[3] + deltaV3);
                //System.out.println("Put PuckerValue: " + angleValues[i]);
                //System.out.println("Put Value: ")
            }
        }
        ArrayList<Atom> angleAtoms = molecule.getAngleAtoms();
        for (Atom atom : angleAtoms) {
            atom.daughterAtom.dihedralAngle = (float) reduceAngle(angleValues[i++]);
        }
        //molecule.genCoords(false, null);print

    }

    /**
     * NOT USED Adds an angleBoundaries object which has upper and lower angle
     * bounds to an angleBoundaries hashmap provided a molfilter/atom name. maps
     * spacial set with atom name with angleBoundary in the angleBoundaries
     * hashmap
     *
     * @param molFilterString
     * @param angleBoundary
     */
    public void addBoundary(final AngleBoundary angleBoundary) throws InvalidMoleculeException {
        String key = angleBoundary.getRefAtom().getFullName();
        if (!angleBoundaries.containsKey(key)) {
            angleBoundaries.put(key, new ArrayList<>());
        }
        List<AngleBoundary> angleBoundList = angleBoundaries.get(key);
        angleBoundList.add(angleBoundary);
    }

    public void addBoundary(final List<String> atomNames, double lower, double upper, double scale) throws InvalidMoleculeException {
        int arrayLength = atomNames.size();
        if (arrayLength != 4) {
            throw new IllegalArgumentException("Error adding dihedral boundary, must provide four atoms");
        }
        Atom[] atoms = new Atom[4];
        for (int i = 0; i < arrayLength; i++) {
            atoms[i] = Molecule.getAtomByName(atomNames.get(i));
            if (atoms[i] == null) {
                throw new IllegalArgumentException("Error adding dihedral boundary, invalid atom " + atomNames.get(i));
            }
        }
        addBoundary(atoms, lower, upper, scale);
    }

    public void addBoundary(final Atom[] atoms, double lower, double upper, double scale) throws InvalidMoleculeException {
        if (atoms.length != 4) {
            throw new IllegalArgumentException("Error adding dihedral boundary, must provide four atoms");
        }
        for (Atom atom : atoms) {
            if (atom == null) {
                throw new IllegalArgumentException("Error adding dihedral boundary, invalid atom");
            }
        }
        AngleBoundary angleBoundary = new AngleBoundary(atoms, lower, upper, scale);
        addBoundary(angleBoundary);
    }

    public void addTorsion(Map<Residue, AngleProp> torsionMap, final Residue res, double[] target, double[] sigma, double[] height) throws InvalidMoleculeException {
        if (res == null) {
            throw new IllegalArgumentException("Error adding torsion angle, invalid residue");
        }
        AngleProp angleProp = new AngleProp("torsion", target, sigma, height);
        torsionMap.put(res, angleProp);
    }

    public void addBoundary(final Atom[] atoms, double lower, double upper, double scale,
            double weight, double target, double targetErr, String name) throws InvalidMoleculeException {
        if (atoms.length != 4) {
            throw new IllegalArgumentException("Error adding dihedral boundary, must provide four atoms");
        }
        for (Atom atom : atoms) {
            if (atom == null) {
                throw new IllegalArgumentException("Error adding dihedral boundary, invalid atom");
            }
        }
        AngleBoundary angleBoundary = new AngleBoundary(atoms, lower, upper, scale, weight, target, targetErr, name);
        addBoundary(angleBoundary);
    }

    public Map<String, List<AngleBoundary>> getAngleBoundaries() {
        return angleBoundaries;
    }

    public List<Map<Residue, AngleProp>> getTorsionAngles() {
        return torsionAngles;
    }

    public void clearBoundaries() {
        angleBoundaries.clear();
    }

    public void setBoundaries(final double sigma, boolean useDegrees) {
        setBoundaries(sigma, useDegrees, 2.0 * Math.PI);
    }

    public void setBoundaries(final double sigma, boolean useDegrees, double maxRange) {
        ArrayList<Atom> angleAtoms = molecule.getAngleAtoms();
        ArrayList<Atom> pseudoAngleAtoms = molecule.getPseudoAngleAtoms();
        if (!usePseudo) {
            pseudoAngleAtoms.clear();
        }

        nBackbone = 0;
        nSidechain = 0;
        int nPseudoAngles = pseudoAngleAtoms.size() / 3;
        energyList.clearAngleBoundaries();
        int stepSize = 1;
        if (sinCosMode) {
            stepSize *= 2;
        }
        for (int i = 0; i < normBoundaries[0].length; i++) {
            if (centerBoundaries) {
                normBoundaries[0][i] = 0.0;
                normBoundaries[1][i] = 100.0;
            } else {
                normBoundaries[0][i] = Double.NEGATIVE_INFINITY;
                normBoundaries[1][i] = Double.MAX_VALUE;
            }
            inputSigma[i] = sigma;
        }
        int aStart = nPseudoAngles * 2 * stepSize;
        for (int iAtom = 0; iAtom < angleAtoms.size(); iAtom++) {
            Atom atom = angleAtoms.get(iAtom).daughterAtom;
            // temporarily set everything to use sigma, till we make sure counting is correct
            if (atom.parent.getName().equals("P") || atom.parent.getName().equals("O5'") || atom.parent.getName().equals("C5'") || atom.parent.getName().equals("C4'") || atom.parent.getName().equals("O3'")) {
                inputSigma[aStart++] = sigma / backBoneScale;
                if (sinCosMode) {
                    inputSigma[aStart++] = sigma / backBoneScale;
                }
            } else if (atom.parent.getName().equals("CA") || atom.parent.getName().equals("C")) {
                inputSigma[aStart++] = sigma / backBoneScale;
                if (sinCosMode) {
                    inputSigma[aStart++] = sigma / backBoneScale;
                }
            } else {
                inputSigma[aStart++] = sigma;
                if (sinCosMode) {
                    inputSigma[aStart++] = sigma;
                }
            }
        }

        /* works
        for (int i = 0; i < normBoundaries[0].length; i++) {
            Atom atom = angleAtoms.get(i / stepSize);
            if (atom.parent.getName().equals("P") || atom.parent.getName().equals("O5'") || atom.parent.getName().equals("C5'") || atom.parent.getName().equals("C4'") || atom.parent.getName().equals("O3'")) {
                inputSigma[i] = sigma/backBoneScale;
            } else if (atom.parent.getName().equals("CA") || atom.parent.getName().equals("C")) {
                inputSigma[i] = sigma/backBoneScale;
            } else {
                inputSigma[i] = sigma;
            }
        }
         */
        for (int i = 0; i < boundaries[0].length; i++) {
            if (centerBoundaries) {
                double angle = reduceAngle(angleValues[i]);
                boundaries[0][i] = reduceAngle(angle - FastMath.PI / 2);
                boundaries[1][i] = boundaries[0][i] + FastMath.PI;
            } else {
                boundaries[0][i] = -1.0 * FastMath.PI;
                boundaries[1][i] = 1.0 * FastMath.PI;
            }
            ranBoundaries[0][i] = -FastMath.PI;
            ranBoundaries[1][i] = FastMath.PI;
        }
        setupAngleRestraints();
        //for (int j=0;j<angleValues.length;j++) {
        //System.out.printf("j %3d bou %9.3f bou %9.3f sig %9.3f ang %9.3f nrm %9.3f nrm %9.3f\n",j,toDeg*boundaries[0][j],toDeg*boundaries[1][j],inputSigma[j],toDeg*angleValues[j],normBoundaries[0][j],normBoundaries[1][j]);
        //}
    }

    public void setupAngleRestraints() {
        ArrayList<Atom> angleAtoms = molecule.getAngleAtoms();
        energyList.clearAngleBoundaries();
        for (int i = 0; i < angleAtoms.size(); i++) {
            Atom atom = angleAtoms.get(i);
            atom.aAtom = i;
            String atomName = atom.getFullName();
            List<AngleBoundary> angleBoundaryList = angleBoundaries.get(atomName);
            //if angleBoundary is present for that atom, replace value at there respected indices

            if (angleBoundaryList != null) {
                for (AngleBoundary angleBoundary : angleBoundaryList) {
                    angleBoundary.setIndex(i);
                    energyList.addAngleBoundary(angleBoundary);
                }
            }
        }
    }

    /**
     * Generates random angles
     */
    public void randomizeAngles() {
        getDihedrals();
        setBoundaries(0.1, false, Math.PI);
        for (int i = 0; i < angleValues.length; i++) {
            angleValues[i] = 2.0 * Math.PI * (rand.nextDouble() - 0.5);
            angleValues[i] = reduceAngle(angleValues[i]);
        }
        putDihedrals();
        molecule.genCoords();
    }

    public double energy() {
        if (energyList == null) {
            energyList = new EnergyLists();
            energyList.makeCompoundList(molecule.name);
        }
        lastEnergy = energyList.energy();
        return lastEnergy;
    }

    public EnergyDeriv eDeriv() {
        if (energyList == null) {
            energyList = new EnergyLists();
            energyList.makeCompoundList(molecule.name);
        }
        EnergyDeriv eDeriv = energyList.energyAndDeriv();
        lastEnergy = eDeriv.getEnergy();
        return eDeriv;
    }

    public void toSinCos(double[] inValues, double[] outValues) {
        int j = 0;
        for (int i = 0; i < inValues.length; i++) {
            outValues[j++] = Math.sin(inValues[i]) * 100.0;
            outValues[j++] = Math.cos(inValues[i]) * 100.0;
        }
    }

    public void fromSinCos(double[] inValues, double[] outValues) {
        int j = 0;
        for (int i = 0; i < outValues.length; i++) {
            outValues[i] = Math.atan2(inValues[j++] / 100.0, inValues[j++] / 100.0);
        }
    }

    public void normalize(double[] inValues, double[] outValues) {
        if (sinCosMode) {
            toSinCos(inValues, outValues);
        } else {
            for (int i = 0; i < inValues.length; i++) {
                outValues[i] = toNormalized(inValues[i], i);
                //System.out.println("inValues:" + inValues[i] + "outValues" + outValues[i]);
                //System.out.println("inValues: Boundaries = : " + boundaries[0][i] + " , " + boundaries[1][i]);
                //System.out.println();

            }
        }
    }

    public void denormalize(double[] inValues, double[] outValues) {
        if (sinCosMode) {
            fromSinCos(inValues, outValues);
        } else {
            for (int i = 0; i < inValues.length; i++) {
                outValues[i] = fromNormalized(inValues[i], i);
            }
        }
    }

    public double fromNormalized(double value, int i) {
        //double f = (value - normBoundaries[0][i]) / (normBoundaries[1][i] - normBoundaries[0][i]);
        double f = value / 100.0;
        if ((f < 0.1) || (f > 0.9)) {
//            System.out.println("############################ " +f + " " + i);
        }
        double normValue = f * (boundaries[1][i] - boundaries[0][i]) + boundaries[0][i];
        if (boundaries[1][i] > FastMath.PI) {
            normValue = reduceAngle(normValue);
        }
        return normValue;
    }

    public double toNormalized(double value, int i) {
        if (boundaries[1][i] > FastMath.PI) {
            if (value < 0.0) {
                value += 2.0 * FastMath.PI;
            }
        }
        double f = (value - boundaries[0][i]) / (boundaries[1][i] - boundaries[0][i]);
        //double normValue = f * (normBoundaries[1][i] - normBoundaries[0][i]) + normBoundaries[0][i];
        double normValue = f * 100.0;
        return normValue;
    }

    public double nonNormValue(final double[] dihValues) {
        if ((nEvaluations % updateAt) == 0) {
            energyList.makeAtomListFast();
        }
        System.arraycopy(dihValues, 0, angleValues, 0, angleValues.length);
        putDihedrals();
        molecule.genCoordsFastVec3D(null);
        double energy = energy();
        if (energy < bestEnergy) {
            long time = System.currentTimeMillis();
            long deltaTime = time - startTime;
            bestEnergy = energy;
            System.arraycopy(angleValues, 0, bestValues, 0, angleValues.length);
            //System.out.println(nEvaluations + " " + deltaTime + " " + energyList.atomList.size() + " " + energy);
        }
        nEvaluations++;
        return energy;
    }

    public static double[] setSugarBonds(double pseudoRotationAngle,
            double maxTorsionAngle) {
        double[] sugarAngles = new double[5];

        for (int i = 0; i < sugarAngles.length; i++) {
            sugarAngles[i] = maxTorsionAngle
                    * FastMath.cos(pseudoRotationAngle + (i - 2) * 4.0 * FastMath.PI
                            / 5.0);
        }
        return sugarAngles;
    }

    public static double reduceAngle(double x) {
        if ((x > FastMath.PI) || (x < -FastMath.PI)) {
            double sine = FastMath.sin(x);
            double cosine = FastMath.cos(x);
            x = FastMath.atan2(sine, cosine);
        }
        return (x);
    }

    public static double[] calcPseudoAngle(double v2, double v3) {
        double puckerAmplitude = Math.sqrt(Math.pow(((v2 - v3 * Math.cos(4 * Math.PI / 5.0)) / (Math.sin(4 * Math.PI / 5.0))), 2) + v3 * v3);
        double cosValue = v2 / puckerAmplitude;
        double[] values = new double[2];
        double pseudoAngle;
        /* Checks if cosine of the psuedoAngle is between 1 and negative one */
        if (cosValue > 1.0) {
            pseudoAngle = 0.0;
        } else if (cosValue < -1.0) {
            pseudoAngle = FastMath.PI;
        } else {
            pseudoAngle = FastMath.acos(cosValue);
        }
        //System.out.println("PSEUDO ANGLE: " + pseudoAngle);
        double v3T1 = puckerAmplitude
                * FastMath.cos(pseudoAngle + 4.0 * FastMath.PI / 5.0);
        double v3T2 = puckerAmplitude
                * FastMath.cos(2.0 * FastMath.PI - pseudoAngle + 4.0 * FastMath.PI / 5.0);

        double delta1 = FastMath.abs(v3T1 - v3);
        double delta2 = FastMath.abs(v3T2 - v3);

        if (delta2 < delta1) {
            pseudoAngle = 2.0 * FastMath.PI - pseudoAngle;
        }
        values[0] = pseudoAngle;
        values[1] = puckerAmplitude;
        return values;
    }

    public void putInitialAngles(boolean useRandom) {
        ArrayList<Atom> angleAtoms = molecule.getAngleAtoms();
        ArrayList<Atom> pseudoAngleAtoms = molecule.getPseudoAngleAtoms();
        randomizeAngles();
        if (!useRandom) {
            int nPseudoAngles = pseudoAngleAtoms.size() / 3;
            for (int i = 0; i < angleValues.length;) {
                Atom atom;
                boolean incrementByTwo = false;
                if (i < (2 * nPseudoAngles)) {
                    atom = pseudoAngleAtoms.get(i / 2 * 3);
                    incrementByTwo = true;
                } else {
                    atom = angleAtoms.get(i - 2 * nPseudoAngles).daughterAtom;
                }
                String atomName = atom.getFullName();
//                AngleBoundary angleBoundary = angleBoundaries.get(atomName);
//                /* adds angleBoundary values to boundaries array */
//                if (angleBoundary != null) {
//                    if (angleBoundary.angleProp != null) {
//                        for (int j = 0; j < angleBoundary.angleProp.height.length; j++) {
//                            if (angleBoundary.angleProp.height[j] == 1) {
//                                angleValues[i] = angleBoundary.angleProp.target[j];
//                            }
//                        }
//                    }
//                }
                if (usePseudo == true && incrementByTwo == true) {
                    angleValues[i++] = reduceAngle(initPseudoAngle);
                    angleValues[i++] = initPuckerAmplitude;
                }
                if (incrementByTwo == true) {
                    i += 2;
                }
            }
        }
        putDihedrals();
        molecule.genCoords(false, null);
    }

    public void printAngleTest() {
        ArrayList<Atom> pseudoAngleAtoms = molecule.getPseudoAngleAtoms();
        ArrayList<Atom> angleAtoms = molecule.getAngleAtoms();

        System.out.println("Pseudo Angles + Boundaries");
        System.out.println("Pseudo Angle Atom Size: " + pseudoAngleAtoms.size());
        if (pseudoAngleAtoms == null) {
            System.out.println("pseudoAngleAtoms is Null");
        }
        for (int i = 0; i < pseudoAngleAtoms.size() / 3; i++) {
            Atom atom = pseudoAngleAtoms.get(i / 2 * 3);

            if (atom == null) {
                System.out.println("Atom is Null");
            }
            if (boundaries == null) {
                System.out.println("Boundaries is Null");
            }
            System.out.println(atom.getFullName() + ", Lower Boundary: " + boundaries[0][i * 2] + ", Upper Boundary: " + boundaries[1][i * 2] + ", currentValue " + angleValues[i * 2]);
            System.out.println(atom.getFullName() + ", Lower Boundary: " + boundaries[0][i * 2 + 1] + ", Upper Boundary: " + boundaries[1][i * 2 + 1] + ", currentValue " + angleValues[i * 2 + 1]);
        }
        for (int i = 0; i < angleAtoms.size(); i++) {
            Atom atom = angleAtoms.get(i).daughterAtom;
            System.out.println(atom.getFullName() + ", Lower Boundary: " + boundaries[0][i + 2 * pseudoAngleAtoms.size() / 3] + ", Upper Boundary: " + boundaries[1][i + 2 * pseudoAngleAtoms.size() / 3] + ", currentValue " + angleValues[i + 2 * pseudoAngleAtoms.size() / 3]);
        }
    }

    public RotationalDynamics getRotationalDyamics() {
        prepareAngles(false);
        setBoundaries(0.1, false);
        energyList.setupDihedrals();
        return new RotationalDynamics(this, rand);
    }

}
