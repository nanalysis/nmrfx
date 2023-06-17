/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.chemistry;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import java.util.HashMap;
import java.util.Map;

/**
 * @author brucejohnson
 */
public class RDC {
    private static final boolean CALC_MAX_RDC = true;
    private static final double HBAR = 1.054E-34;
    private static final double MU0 = 4.0E-7 * Math.PI;
    private static final double PREFACTOR = -(MU0 * HBAR) / (4 * (Math.PI * Math.PI));
    private static final double GAMMA_N = -2.71E7;
    private static final double GAMMA_H = 2.68e8;
    private static final double SCALE_HN = (GAMMA_H * GAMMA_N) / ((1.0E-10) * (1.0E-10) * (1.0E-10));

    private static final Map<String, Double> disDict = new HashMap<>();
    private static final Map<String, Double> maxRDCDict = new HashMap<>();
    private static final Map<String, Double> gammaIDict = new HashMap<>();
    private static final Map<String, Double> gammaSDict = new HashMap<>();

    static {
        disDict.put("HN", 1.04);
        disDict.put("NH", 1.04);
        maxRDCDict.put("HN", 24350.0);
        maxRDCDict.put("HC", -60400.0);
        maxRDCDict.put("HH", -240200.0);
        maxRDCDict.put("CN", 6125.0);
        maxRDCDict.put("CC", -15200.0);
        maxRDCDict.put("NH", 24350.0);
        maxRDCDict.put("CH", -60400.0);
        maxRDCDict.put("NC", 6125.0);
        gammaIDict.put("N", -2.71e7);
        gammaIDict.put("C", 6.73e7);
        gammaIDict.put("H", 2.68e8);
        gammaSDict.put("N", -2.71e7);
        gammaSDict.put("C", 6.73e7);
        gammaSDict.put("H", 2.68e8);
    }

    /**
     * Calculates the maximum RDCConstraint value associated with two atoms in a
     * Molecule object.
     *
     * @param vector     Vector3D object that represents the vector associated with
     *                   the two atoms.
     * @param aType1     String of the type of the first atom of the vector.
     * @param aType2     String of the type of the second atom of the vector.
     * @param calcMaxRDC Boolean of whether to calculate the max RDCConstraint
     *                   value based on the vector distance.
     * @param scale      Boolean of whether to calculate the max RDCConstraint value
     *                   with the scaling method used in CYANA.
     * @return double parameter that is the maxRDC value.
     */
    public static double calcMaxRDC(Vector3D vector, String aType1, String aType2, boolean calcMaxRDC, boolean scale) {
        String type = aType1 + aType2;
        double r;
        if (disDict.containsKey(type)) {
            r = disDict.get(type) * 1.0E-10;
        } else {
            r = vector.getNorm() * 1.0E-10;
        }
        double maxRDC = 1.0;
        if (!calcMaxRDC && maxRDCDict.containsKey(type)) {
            maxRDC = maxRDCDict.get(type);
        } else {
            double gammaI = gammaIDict.get(aType1);
            double gammaS = gammaSDict.get(aType2);
            if (r != 0) {
                if (calcMaxRDC) {
                    maxRDC = PREFACTOR * ((gammaI * gammaS) / (r * r * r));
                } else if (scale) {
                    maxRDC = 24350.0 * (gammaI * gammaS) / ((r * r * r) * SCALE_HN);
                }
            } else {
                if (maxRDCDict.containsKey(type)) {
                    maxRDC = maxRDCDict.get(type);
                }
            }
        }
        return maxRDC;
    }

    Atom atom1;
    Atom atom2;
    Vector3D vector;
    Double rdcPred;
    Double rdcExp;
    Double error;
    double maxRDC;

    public RDC(Atom atom1, Atom atom2) {
        this.atom1 = atom1;
        this.atom2 = atom2;
        this.vector = atom2.getPoint().subtract(atom1.getPoint());
        String elemName1 = atom1.getElementName();
        String elemName2 = atom2.getElementName();
        maxRDC = calcMaxRDC(vector, elemName1, elemName2, CALC_MAX_RDC, false);
    }

    public RDC(Atom atom1, Atom atom2, Vector3D vector) {
        this.atom1 = atom1;
        this.atom2 = atom2;
        this.vector = vector;
        String elemName1 = atom1.getElementName();
        String elemName2 = atom2.getElementName();
        maxRDC = calcMaxRDC(vector, elemName1, elemName2, CALC_MAX_RDC, false);
    }

    public Double getRDC() {
        return rdcPred;
    }

    public Double getExpRDC() {
        return rdcExp;
    }

    public Double getNormRDC() {
        return rdcPred / maxRDC;
    }

    public Double getNormExpRDC() {
        return rdcExp / maxRDC;
    }

    public Double getError() {
        return error;
    }

    public void setRDC(double rdc) {
        this.rdcPred = rdc;
    }

    public void setExpRDC(double rdc) {
        this.rdcExp = rdc;
    }

    public void setError(double err) {
        this.error = err;
    }

    public Atom getAtom1() {
        return atom1;
    }

    public Atom getAtom2() {
        return atom2;
    }

    public double getMaxRDC() {
        return maxRDC;
    }

    public Vector3D getVector() {
        return vector;
    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(atom1.getFullName()).append(" ").append(atom1.getEntity().getName()).append(" ");
        sBuilder.append(atom2.getFullName()).append(" ").append(atom2.getEntity().getName()).append(" ");
        sBuilder.append(String.format("%.2f", rdcPred)).append(" ");
        sBuilder.append(String.format("%.2f", rdcExp)).append(" ");
        sBuilder.append(String.format("%.2f", error));
        return sBuilder.toString();
    }

}
