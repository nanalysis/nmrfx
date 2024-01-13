/*
 * Copyright (c) 2004-2014 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 */
package org.nmrfx.structure.rna;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.geometry.euclidean.twod.Line;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.optim.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.util.Precision;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.structure.chemistry.Molecule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SSLayout implements MultivariateFunction {

    private static final Logger log = LoggerFactory.getLogger(SSLayout.class);
    private final int[][] interactions;
    private final int[] basePairs;
    private final int[] basePairs2;
    private final double[] baseBondLength;
    StructureType[] structureTypes;
    public int[] ssClass;
    private int nFree = 0;
    private final double[] values;
    private final double[][] coords;
    private final boolean[] coordsSet;
    private final double[] angleTargets;
    private final double[] angleValues;
    private final boolean[] angleFixed;
    private final int[] angleRelations;
    private final int[] nAngles;
    private double[] inputSigma;
    private double[][] boundaries = null;
    private final double targetSeqDistance = 1.0;
    private final double targetPairDistance = 1.0;
    private final double targetPair2Distance = Math.sqrt(targetSeqDistance * targetSeqDistance + targetPairDistance * targetPairDistance);
    private final double targetNBDistance = 1.2;
    private final int nNuc;
    private int nSet;
    private final int[] nucChain;
    int limit = 10;
    int nHelices = 0;
    int nLoops = 0;
    List<List<String>> sequences;
    public static final RandomGenerator DEFAULT_RANDOMGENERATOR = new MersenneTwister(1);

    public SSLayout(int... nValues) {
        int n = 0;
        for (int nValue : nValues) {
            n += nValue;
        }
        nucChain = new int[n];
        int k = 0;
        for (int i = 0; i < nValues.length; i++) {
            for (int j = 0; j < nValues[i]; j++) {
                nucChain[k++] = i;
            }
        }
        nNuc = n;
        interactions = new int[n][n];
        basePairs = new int[n];
        basePairs2 = new int[n];
        baseBondLength = new double[n];
        values = new double[nNuc * 2];
        coords = new double[2][nNuc];
        coordsSet = new boolean[nNuc];
        angleTargets = new double[nNuc - 2];
        angleFixed = new boolean[nNuc - 2];
        angleRelations = new int[nNuc - 2];
        angleValues = new double[nNuc - 2];
        nAngles = new int[nNuc - 2];
        structureTypes = new StructureType[nNuc];

        ssClass = new int[nNuc];

        for (int i = 0; i < n; i++) {
            basePairs[i] = -1;
            basePairs2[i] = -1;
            baseBondLength[i] = targetSeqDistance;
        }
        for (int i = 0; i < angleTargets.length; i++) {
            angleTargets[i] = -2000.0;
            angleRelations[i] = 0;
        }
    }


    public static SSLayout createLayout(Molecule mol) throws InvalidMoleculeException {
        List<List<String>> sequences = setupSequence(mol);
        int[] seqLens = new int[sequences.size()];
        int i = 0;
        for (List<String> sequence : sequences) {
            seqLens[i++] = sequence.size();
        }
        SSLayout ssLayout = new SSLayout(seqLens);
        ssLayout.sequences = sequences;
        return ssLayout;
    }

    interface StructureType {

        int getID();

    }

    class Loop implements StructureType {

        final List<Integer> bases;
        final List<Integer> helices;
        final int id;

        Loop(List<Integer> bases, List<Integer> helices, int iLoop) {
            this.bases = new ArrayList<>();
            this.helices = new ArrayList<>();
            this.bases.addAll(bases);
            this.helices.addAll(helices);
            id = iLoop;
        }

        public int getID() {
            return id;
        }
    }

    class Helix implements StructureType {

        final List<Integer> bases;
        final int id;

        Helix(List<Integer> bases, int iHelix) {
            this.bases = new ArrayList<>();
            this.bases.addAll(bases);
            id = iHelix;
        }

        public int getID() {
            return id;
        }
    }

    public static List<List<String>> setupSequence(Molecule mol) throws InvalidMoleculeException {
        List<List<String>> sequences = new ArrayList<>();
        for (Polymer polymer : mol.getPolymers()) {
            if (polymer.isRNA()) {
                List<String> sequence = new ArrayList<>();
                sequences.add(sequence);
                for (Residue residue : polymer.getResidues()) {
                    String resName = residue.getName().substring(0, 1);
                    sequence.add(resName + residue.getNumber());
                }
            }
        }
        return sequences;

    }

    public List<List<String>> getSequences() {
        return sequences;
    }

    public void addPair(int i, int j) {
        interactions[i][j] = 1;
        interactions[j][i] = 1;
        basePairs[i] = j;
        basePairs[j] = i;
    }

    public void dumpPairs() {
        if (log.isDebugEnabled()) {
            StringBuilder pairStr = new StringBuilder();
            for (int i = 0; i < basePairs.length; i++) {
                pairStr.append(String.format("%4d %4d%n", i, basePairs[i]));
            }
            log.debug(pairStr.toString());
        }
    }

    public void fillPairs() {
        try {
            for (int i = 0; i < (nNuc - 1); i++) {
                for (int j = i + 2; j < nNuc; j++) {
                    if (interactions[i][j] == 1) {
                        if (interactions[i + 1][j - 1] == 1) {
                            interactions[i + 1][j] = 2;
                            interactions[j][i + 1] = 2;
                            basePairs2[i + 1] = j;
                            interactions[i][j - 1] = 2;
                            interactions[j - 1][i] = 2;
                            basePairs2[j - 1] = i;
                        }
                    }
                }
            }

            for (int i = 0; i < (nNuc - 1); i++) {
                int pos = 0;
                int loopSize = 0;
                int gapSize = 0;
                int bulgeSize = 0;
                int loopStart = -1;
                if (basePairs[i] == -1) {
                    for (int j = i - 1; j >= 0; j--) {
                        if (basePairs[j] != -1) {
                            pos = i - j - 1;
                            loopStart = j;
                            break;
                        }
                    }
                    for (int j = i + 1; j < nNuc; j++) {
                        if ((basePairs[j] != -1) && (loopStart != -1)) {
                            if (basePairs[loopStart] == j) {
                                loopSize = j - loopStart - 1;
                            } else if (basePairs[loopStart] == (basePairs[j] + 1)) {
                                bulgeSize = j - loopStart - 1;
                                if (bulgeSize == 2) {
                                    baseBondLength[i - 1] = targetSeqDistance * 0.75;
                                    if (i > 1) {
                                        baseBondLength[i - 2] = targetSeqDistance * 0.75;
                                    }
                                } else {
                                    baseBondLength[i - 1] = targetSeqDistance * 0.8;
                                    if (i > 1) {
                                        baseBondLength[i - 2] = targetSeqDistance * 0.8;
                                    }
                                }
                            } else {
                                gapSize = j - loopStart - 1;
                            }
                            break;
                        }
                    }
                }
                if (loopSize != 0) {
                    double interiorAngle = Math.PI * loopSize / (loopSize + 2);
                    double target;
                    if (pos == 0) {
                        target = (interiorAngle - Math.PI / 2.0);
                    } else {
                        target = -(Math.PI - interiorAngle);
                    }
                    if (pos == (loopSize - 1)) {
                        double endTarget = -(Math.PI - interiorAngle);
                        angleTargets[i - 1] = endTarget;
                        angleFixed[i - 1] = true;
                        endTarget = (interiorAngle - Math.PI / 2.0);
                        if (i < angleTargets.length) {
                            angleTargets[i] = endTarget;
                            angleFixed[i] = true;
                        }
                    }
                    if (i > 1) {
                        angleTargets[i - 2] = target;
                        angleFixed[i - 2] = true;
                    }
                } else if (gapSize > 4) {
                    boolean free = false;
                    if ((pos % 3) == 0) {
                        free = true;
                    }
                    if ((pos % 3) == 1) {
                        angleTargets[i - 2] = 0.0;
                        angleFixed[i - 2] = true;
                        angleRelations[i - 2] = -1;
                    }
                    if ((pos % 3) == 2) {
                        angleTargets[i - 2] = 0.0;
                        angleFixed[i - 2] = true;
                        angleRelations[i - 2] = 1;
                    }
                    log.warn("gap {} {}", pos, free);
                }
            }
        } catch (ArrayIndexOutOfBoundsException aiE) {
            log.warn(aiE.getMessage(), aiE);
        }
    }

    private void doNoBasePairs() {
        double a = nNuc / 2;
        double b = a / 1.5;
        double angle = Math.PI - Math.PI / 4;
        double deltaAngle = (2.0 * Math.PI - Math.PI / 2) / nNuc;
        for (int i = 0; i < nNuc; i++) {
            double x = a * Math.cos(angle);
            double y = b * Math.sin(angle);
            setXY(i, x, y);
            angle -= deltaAngle;
        }
        for (int i = 0; i < nNuc; i++) {
            values[i * 2] = coords[0][i];
            values[i * 2 + 1] = coords[1][i];
        }
    }

    public void fillPairsNew() {
        int nBP = 0;
        for (int i = 0; i < nNuc; i++) {
            if (basePairs[i] != -1) {
                nBP++;
            }
        }
        if (nBP == 0) {
            Arrays.fill(coordsSet, false);
            Arrays.fill(structureTypes, null);
            Arrays.fill(ssClass, 0);
            doNoBasePairs();
            return;
        }
        nSet = 0;
        Arrays.fill(coordsSet, false);
        Arrays.fill(structureTypes, null);
        Arrays.fill(ssClass, 0);
        try {
            for (int i = 0; i < (nNuc - 1); i++) {
                for (int j = i + 2; j < nNuc; j++) {
                    if (interactions[i][j] == 1) {
                        if (interactions[i + 1][j - 1] == 1) {
                            interactions[i + 1][j] = 2;
                            interactions[j][i + 1] = 2;
                            basePairs2[i + 1] = j;
                            interactions[i][j - 1] = 2;
                            interactions[j - 1][i] = 2;
                            basePairs2[j - 1] = i;
                        }
                    }
                }
            }
            int startI = -1;
            int startJ = -1;
            int maxDelta = 0;
            for (int i = 0; i < (nNuc - 1); i++) {
                if (basePairs[i] != -1) {
                    int j = basePairs[i];
                    int delta = j - i;
                    if (delta > maxDelta) {
                        maxDelta = delta;
                        startI = i;
                        startJ = j;
                    }
                }

            }
            List<Integer> helixStarts = new ArrayList<>();
            helixStarts.add(startI);
            helixStarts.add(startJ);

            setXY(startI, 0.0, 0.0);
            setXY(startJ, 0.0, -targetPairDistance);
            ssClass[startI] = 1;
            ssClass[startJ] = 1;

            while (!helixStarts.isEmpty()) {
                int i = helixStarts.get(0);
                int j = helixStarts.get(1);
                List<Integer> thisList = analyzeHelix(i, j);
                helixStarts.remove(0);
                helixStarts.remove(0);
                helixStarts.addAll(thisList);
            }
            fillEnds();
            for (int i = 0; i < nNuc; i++) {
                values[i * 2] = coords[0][i];
                values[i * 2 + 1] = coords[1][i];
            }

        } catch (ArrayIndexOutOfBoundsException aiE) {
            log.warn(aiE.getMessage(), aiE);
        }
    }

    void setXY(int i, double x, double y) {
        coords[0][i] = x;
        coords[1][i] = y;
        nSet++;
        coordsSet[i] = true;
    }

    List<Integer> analyzeHelix(int startI, int startJ) {
        List<Integer> loopBPList = new ArrayList<>();
        int j = startJ;
        for (int i = startI; i < (nNuc - 1); i++) {
            if (nSet >= nNuc) {
                break;
            }
            if ((basePairs[i + 1] != -1) && (basePairs[j - 1] != -1)) {
                if (coordsSet[i + 1]) {
                    break;
                }
                setHelixCoords(i + 1, j - 1);
                j--;
                continue;
            } else {
                Loop loop = findLoop(i, loopBPList);
                setLoopCoords(loop.bases);
                break;
            }
        }

        return loopBPList;
    }

    Loop findLoop(int i, List<Integer> loopBPList) {
        List<Integer> bases = new ArrayList<>();
        loopBPList.clear();
        int loopSize = 0;
        bases.add(i);
        int k = i + 1;
        while (basePairs[k] != i) {
            bases.add(k);
            if (basePairs[k] != -1) {
                loopBPList.add(k);
                loopBPList.add(basePairs[k]);
                bases.add(basePairs[k]);
                loopSize++;
                k = basePairs[k];
            }
            loopSize++;
            k++;
        }
        bases.add(k);
        Loop loop = new Loop(bases, loopBPList, nLoops);
        nLoops++;
        return loop;
    }

    void setHelixCoords(int i, int j) {
        double ix1 = coords[0][i - 1];
        double iy1 = coords[1][i - 1];
        double jx1 = coords[0][j + 1];
        double jy1 = coords[1][j + 1];
        double dX = ix1 - jx1;
        double dY = iy1 - jy1;
        if (!coordsSet[i]) {
            ssClass[i] = 1;
            setXY(i, ix1 + dY, iy1 - dX);
        }
        if (!coordsSet[j]) {
            ssClass[j] = 1;
            setXY(j, jx1 + dY, jy1 - dX);
        }
    }

    void setLoopCoords(List<Integer> bases) {
        int first = bases.get(0);
        int last = bases.get(bases.size() - 1);
        int loopSize = bases.size();
        for (int i = 0, iL = bases.size() - 1; i < iL; i++) {
            int b0 = i == 0 ? bases.get(loopSize - 1) : bases.get(i - 1);
            int b1 = bases.get(i);
            int b2 = bases.get(i + 1);

            double bx0 = coords[0][b0];
            double by0 = coords[1][b0];
            double bx1 = coords[0][b1];
            double by1 = coords[1][b1];

            double dX = bx0 - bx1;
            double dY = by0 - by1;
            double curAngle = Math.atan2(dY, dX);
            double interiorAngle = Math.PI * (loopSize - 2) / loopSize;
            double angle = interiorAngle + curAngle;
            double sin = Math.sin(angle);
            double cos = Math.cos(angle);
            dX = cos * targetSeqDistance;
            dY = sin * targetSeqDistance;
            coords[0][b2] = bx1 + dX;
            coords[1][b2] = by1 + dY;
            if (!coordsSet[b2]) {
                setXY(b2, bx1 + dX, by1 + dY);
                ssClass[b2] = 2;
            }
        }
    }

    void fillEnds() {
        if ((nSet < nNuc)) {
            int firstSet = 0;
            for (int i = 0; i < nNuc - 1; i++) {
                if (coordsSet[i]) {
                    firstSet = i;
                    break;
                }
            }
            for (int j = firstSet - 1; j >= 0; j--) {
                double x0 = coords[0][j + 2];
                double x1 = coords[0][j + 1];
                double x2 = x1 + (x1 - x0);
                double y0 = coords[1][j + 2];
                double y1 = coords[1][j + 1];
                double y2 = y1 + (y1 - y0);
                setXY(j, x2, y2);

            }
            int lastSet = nNuc - 1;
            for (int i = nNuc - 1; i > 0; i--) {
                if (coordsSet[i]) {
                    lastSet = i;
                    break;
                }
            }
            for (int j = lastSet + 1; j < nNuc; j++) {
                double x0 = coords[0][j - 2];
                double x1 = coords[0][j - 1];
                double x2 = x1 + (x1 - x0);
                double y0 = coords[1][j - 2];
                double y1 = coords[1][j - 1];
                double y2 = y1 + (y1 - y0);
                setXY(j, x2, y2);
            }
        }
    }

    private void setBoundaries(double sigma) {
        for (int i = 1; i < (nNuc - 1); i++) {
            for (int j = i + 2; j < nNuc; j++) {
                if (interactions[i][j] == 1) {
                    if ((interactions[i + 1][j - 1] == 1) && (interactions[i - 1][j + 1] == 1)) {
                        angleTargets[i - 1] = 0.0;
                        angleFixed[i - 1] = true;
                        angleTargets[j - 1] = 0.0;
                        angleFixed[j - 1] = true;
                    }
                    break;
                }
            }
        }
        nFree = 0;
        int j = 0;
        for (boolean fixed : angleFixed) {
            if (!fixed) {
                nFree++;
            }
            nAngles[j++] = nFree;
        }
        int[] iNucs = new int[nFree];
        j = 0;
        int k = 0;
        for (boolean fixed : angleFixed) {
            if (!fixed) {
                iNucs[k++] = j;
            }
            j++;
        }
        boundaries = new double[2][nFree];
        inputSigma = new double[nFree];
        for (int i = 0; i < nFree; i++) {
            boolean sBreak = false;
            if (i > 1) {
                int iNuc = iNucs[i - 2];
                if (nucChain[iNuc] != nucChain[iNuc + 1]) {
                    sBreak = true;
                }
            }
            boundaries[0][i] = -Math.PI / 2;
            boundaries[1][i] = Math.PI / 2;
            inputSigma[i] = 0.1;
            if (sBreak) {
                int nAng = 9;
                for (int kk = 0; kk < nAng; kk++) {
                    boundaries[0][i + kk - nAng / 2] = -0.4;
                    boundaries[1][i + kk - nAng / 2] = 2.0;
                    inputSigma[i + kk - nAng / 2] = 0.3;
                }
            }
        }
    }

    private void setSigma(double sigma) {
        for (int i = 0; i < inputSigma.length; i++) {
            inputSigma[i] = sigma;
        }
    }

    public void dumpAngles(double[] pars) {
        if (log.isDebugEnabled()) {
            StringBuilder angleStr = new StringBuilder();
            for (int i = 0; i < pars.length; i++) {
                angleStr.append(String.format("%3d %.1f %.1f %.1f %.1f 5 %.1f%n", i, boundaries[0][i] * 180.0 / Math.PI, boundaries[1][i] * 180.0 / Math.PI, pars[i] * 180.0 / Math.PI, (inputSigma[i] * 180.0 / Math.PI), (angleTargets[i] * 180.0 / Math.PI)));
            }
            log.debug(angleStr.toString());
        }
    }

    public void getFullCoordinates(double[] pars) {
        int j = 0;
        for (int i = 0; i < angleValues.length; i++) {
            angleValues[i] = angleTargets[i];
            if (!angleFixed[i]) {
                if (j < pars.length) {
                    angleValues[i] = pars[j++];
                }
            }
        }
        for (int i = 0; i < angleValues.length; i++) {
            if (angleFixed[i]) {
                if (angleRelations[i] == -1) {
                    angleValues[i] = (2.0 * angleValues[i - 1] + angleValues[i + 2]) / 3.0;
                } else if (angleRelations[i] == 1) {
                    angleValues[i] = (angleValues[i - 2] + 2.0 * angleValues[i + 1]) / 3.0;
                }
            }
        }
        values[0] = 0.0;
        values[1] = 0.0;
        values[2] = targetSeqDistance;
        values[3] = 0.0;
        for (int i = 0; i < (nNuc - 2); i++) {
            double dx = values[i * 2 + 2] - values[i * 2];
            double dy = values[i * 2 + 3] - values[i * 2 + 1];
            double angle = Math.atan2(dy, dx);
            angle += angleValues[i];
            double sin = Math.sin(angle);
            double cos = Math.cos(angle);
            values[i * 2 + 4] = values[i * 2 + 2] + cos * baseBondLength[i];
            values[i * 2 + 5] = values[i * 2 + 3] + sin * baseBondLength[i];
        }
    }

    public boolean nIntersections(int i, int j) {
        boolean intersects = false;
        double xi1 = values[i * 2];
        double yi1 = values[i * 2 + 1];
        double xi2 = values[i * 2 + 2];
        double yi2 = values[i * 2 + 3];
        Vector2D pi1 = new Vector2D(xi1, yi1);
        Vector2D pi2 = new Vector2D(xi2, yi2);
        double xj1 = values[j * 2];
        double yj1 = values[j * 2 + 1];
        double xj2 = values[j * 2 + 2];
        double yj2 = values[j * 2 + 3];
        Vector2D pj1 = new Vector2D(xj1, yj1);
        Vector2D pj2 = new Vector2D(xj2, yj2);
        Line lineI = new Line(pi1, pi2);
        Line lineJ = new Line(pj1, pj2);
        Vector2D vI = lineI.intersection(lineJ);
        if (vI != null) {
            double disi1 = vI.distance(pi1);
            double disi2 = vI.distance(pi2);
            double disj1 = vI.distance(pj1);
            double disj2 = vI.distance(pj2);
            double leni = pi1.distance(pi2);
            double lenj = pj1.distance(pj2);
            if ((disi1 < leni) && (disi2 < leni) && (disj1 < lenj) && (disj2 < lenj)) {
                intersects = true;
            }
        }
        return intersects;
    }

    @Override
    public double value(final double[] pars) {
        getFullCoordinates(pars);
        double sumPairError = 0.0;
        double sumNBError = 0.0;
        int nIntersections = 0;
        for (int i = 0; i < limit; i++) {
            if (basePairs[i] != -1) {
                int j = basePairs[i];
                if (j < limit) {
                    if (i < j) {
                        double x1 = values[i * 2];
                        double y1 = values[i * 2 + 1];
                        double x2 = values[j * 2];
                        double y2 = values[j * 2 + 1];
                        double deltaX = x2 - x1;
                        double deltaY = y2 - y1;
                        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                        double delta = Math.abs(distance - targetPairDistance);
                        sumPairError += delta * delta;
                    }
                }
            }
            if (basePairs2[i] != -1) {
                int j = basePairs2[i];
                if (j < limit) {
                    double x1 = values[i * 2];
                    double y1 = values[i * 2 + 1];
                    double x2 = values[j * 2];
                    double y2 = values[j * 2 + 1];
                    double deltaX = x2 - x1;
                    double deltaY = y2 - y1;
                    double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                    double delta = Math.abs(distance - targetPair2Distance);
                    sumPairError += delta * delta;
                }
            }

        }
        double xSum = 0.0;
        double ySum = 0.0;
        for (int i = 0; i < limit; i++) {
            boolean intersects = false;
            double x1 = values[i * 2];
            double y1 = values[i * 2 + 1];
            xSum += x1;
            ySum += y1;
            for (int j = i + 3; j < limit; j++) {
                double x2 = values[j * 2];
                double deltaX = x2 - x1;
                if (Math.abs(deltaX) < 10.0 * targetNBDistance) {
                    double y2 = values[j * 2 + 1];
                    double deltaY = y2 - y1;
                    if (Math.abs(deltaY) < 10.0 * targetNBDistance) {
                        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                        if ((distance < 2 * targetSeqDistance) && !intersects && (j < (limit - 1))) {
                            if (nIntersections(i, j)) {
                                intersects = true;
                                nIntersections++;
                            }
                        }
                        if (interactions[i][j] == 0) {
                            if (distance < targetNBDistance) {
                                double delta = Math.abs(distance - targetNBDistance);
                                sumNBError += delta * delta;
                            }
                        }
                    }
                }
            }
        }
        double xCenter = xSum / limit;
        double yCenter = ySum / limit;
        for (int i = 0; i < limit; i++) {
            double x1 = values[i * 2];
            double y1 = values[i * 2 + 1];
            double deltaX = x1 - xCenter;
            double deltaY = y1 - yCenter;
            double dis2 = (deltaX * deltaX + deltaY * deltaY);
        }
        double sumAngle = 0.0;
        double value = sumPairError + sumNBError + sumAngle + nIntersections * 100.0;
        return value;
    }

    public PointValuePair refineCMAES(int nSteps, double stopFitness, final double sigma, final double lambdaMul, final int diagOnly) {
        setBoundaries(0.1);
        double[] guess = new double[nFree];
        for (int i = 0; i < guess.length; i++) {
            guess[i] = -1.0 * Math.PI / angleValues.length / 5;
        }
        double value = value(guess);
        log.info("start value {} free {}", value, nFree);
        PointValuePair result = null;
        if (nFree > 0) {
            int startLimit = ((nNuc % 2) == 1) ? 5 : 6;
            double lastValue = 0.0;
            for (limit = startLimit; limit <= nNuc; limit += 8) {
                if (limit > nNuc) {
                    limit = nNuc;
                }
                if (limit == nNuc) {
                    stopFitness = stopFitness / 12.0;
                    nSteps = nSteps * 4;
                }
                DEFAULT_RANDOMGENERATOR.setSeed(1);
                if (nAngles[limit - 3] == 0) {
                    continue;
                }
                if ((limit != startLimit) && (nAngles[limit - 3] == nAngles[limit - 3 - 2])) {
                    setSigma(0.02);
                } else {
                    setSigma(0.10);
                }
                double[] lguess = new double[nAngles[limit - 3]];
                value = value(lguess);
                if ((limit != nNuc) && ((value < 1.0e-6) || (((value - lastValue) / value) < 0.3))) {
                    continue;
                }
                double[][] lboundaries = new double[2][nAngles[limit - 3]];
                double[] lSigma = new double[nAngles[limit - 3]];
                System.arraycopy(guess, 0, lguess, 0, lguess.length);
                System.arraycopy(boundaries[0], 0, lboundaries[0], 0, lguess.length);
                System.arraycopy(boundaries[1], 0, lboundaries[1], 0, lguess.length);
                System.arraycopy(inputSigma, 0, lSigma, 0, lguess.length);

                //suggested default value for population size represented by variable 'lambda'
                //anglesValue.length represents the number of parameters
                int lambda = (int) (lambdaMul * Math.round(4 + 3 * Math.log(lguess.length)));

                CMAESOptimizer optimizer = new CMAESOptimizer(nSteps, stopFitness, true, diagOnly, 0,
                        DEFAULT_RANDOMGENERATOR, true,
                        new SimpleValueChecker(100 * Precision.EPSILON, 100 * Precision.SAFE_MIN));

                result = optimizer.optimize(
                        new CMAESOptimizer.PopulationSize(lambda),
                        new CMAESOptimizer.Sigma(lSigma), new MaxEval(2000000),
                        new ObjectiveFunction(this), GoalType.MINIMIZE,
                        new SimpleBounds(lboundaries[0], lboundaries[1]),
                        new InitialGuess(lguess));
                log.info("{} {} {}", limit, optimizer.getIterations(), result.getValue());
                System.arraycopy(result.getPoint(), 0, guess, 0, result.getPoint().length);
                value = result.getValue();
            }
        }

        return result != null ? result : new PointValuePair(guess, value);
    }

    public void calcLayout(int nSteps) {
        PointValuePair result = refineCMAES(nSteps, 0.0, 0.5, 1.0, 0);
        getFullCoordinates(result.getPoint());
    }

    public void interpVienna(String vienna) {
        String leftBrackets = "({[";
        String rightBrackets = ")}]";
        int[][] levelMap = new int[vienna.length()][leftBrackets.length()];
        int[] levels = new int[leftBrackets.length()];
        for (int i = 0; i < vienna.length(); i++) {
            char curChar = vienna.charAt(i);
            try {
                boolean dot = (curChar == '.') || (Character.isLetter(curChar));
                if (!dot) {
                    int leftIndex = leftBrackets.indexOf(curChar);
                    int rightIndex = rightBrackets.indexOf(curChar);
                    if (leftIndex != -1) {
                        levelMap[levels[leftIndex]][leftIndex] = i;
                        levels[leftIndex]++;
                    } else if (rightIndex != -1) {
                        levels[rightIndex]--;
                        int start = levelMap[levels[rightIndex]][rightIndex];
                        int end = i;
                        addPair(start, end);

                    }
                }
            } catch (ArrayIndexOutOfBoundsException aiE) {
                log.warn(aiE.getMessage(), aiE);
                return;
            }
        }
    }

    public List<Residue> interpVienna(String vienna, List<Residue> res) {
        String leftBrackets = "({[";
        String rightBrackets = ")}]";
        int[][] levelMap = new int[vienna.length()][leftBrackets.length()];
        int[] levels = new int[leftBrackets.length()];
        for (int i = 0; i < vienna.length(); i++) {
            char curChar = vienna.charAt(i);
            try {
                boolean dot = (curChar == '.') || (Character.isLetter(curChar));
                if (!dot) {
                    int leftIndex = leftBrackets.indexOf(curChar);
                    int rightIndex = rightBrackets.indexOf(curChar);
                    if (leftIndex != -1) {
                        levelMap[levels[leftIndex]][leftIndex] = i;
                        levels[leftIndex]++;
                    } else if (rightIndex != -1) {
                        levels[rightIndex]--;
                        int start = levelMap[levels[rightIndex]][rightIndex];
                        int end = i;
                        addPair(start, end);
                        res.get(start).pairedTo = res.get(end);
                        res.get(end).pairedTo = res.get(start);

                    }
                }
            } catch (ArrayIndexOutOfBoundsException aiE) {
                log.warn(aiE.getMessage(), aiE);
                break;
            }
        }
        return res;
    }

    public double[] getValues() {
        return values.clone();
    }

    public int[] getBasePairs() {
        return basePairs.clone();
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.exit(1);
        }
        String vienna = args[0];

        SSLayout ssLayout = new SSLayout(vienna.length());
        ssLayout.interpVienna(vienna);
        ssLayout.dumpPairs();
        ssLayout.fillPairs();
        System.out.println("nf " + ssLayout.nFree);

    }
}
