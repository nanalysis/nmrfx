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

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.util.Precision;
import org.nmrfx.chemistry.Atom;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * @author johnsonb
 */
public class CmaesRefinement extends Refinement implements MultivariateFunction {

    public static final RandomGenerator DEFAULT_RANDOMGENERATOR = new MersenneTwister(1);
    Random rand = new Random(1);
    List<Atom>[] linkedAtoms = null;
    double[][] linkedValues;

    public class Checker extends SimpleValueChecker {

        public Checker(double relativeThreshold, double absoluteThreshold, int maxIter) {
            super(relativeThreshold, absoluteThreshold, maxIter);
        }

        public boolean converged(final int iteration, final PointValuePair previous, final PointValuePair current) {
            boolean converged = super.converged(iteration, previous, current);
            if (converged || (iteration == 1) || ((iteration % reportAt) == 0)) {
                long time = System.currentTimeMillis();
                long deltaTime = time - startTime;
                report(iteration, nEvaluations, deltaTime, dihedrals.energyList.atomList.size(), current.getValue());
            }
            return converged;
        }
    }

    public CmaesRefinement(final Dihedral dihedrals) {
        super(dihedrals);
        this.molecule = dihedrals.molecule;
        startTime = System.currentTimeMillis();
    }

    public CmaesRefinement(final Dihedral dihedrals, List<Atom>[] linkedAtoms) {
        super(dihedrals);
        this.molecule = dihedrals.molecule;
        this.linkedAtoms = linkedAtoms;
    }

    public double refineCMAES(final int nSteps, final double stopFitness, final double sigma, final double lambdaMul, final int diagOnly, final boolean useDegrees) {
        prepareAngles(dihedrals.usePseudo);
        reportAt = 100;
        bestEnergy = Double.MAX_VALUE;
        double energy = energy();
        getDihedrals();
        setBoundaries(sigma, useDegrees);
        molecule.genCoords(false, null);
        dihedrals.energyList.makeAtomListFast();
        energy = energy();
        putDihedrals();
        molecule.genCoords(false, null);
        dihedrals.energyList.makeAtomListFast();
        energy = energy();

        long time = System.currentTimeMillis();
        long deltaTime = time - startTime;
        report(0, nEvaluations, deltaTime, dihedrals.energyList.atomList.size(), energy);

        DEFAULT_RANDOMGENERATOR.setSeed(1);
        //suggested default value for population size represented by variable 'lambda'
        //anglesValue.length represents the number of parameters
        int lambda = (int) (lambdaMul * Math.round(4 + 3 * Math.log(dihedrals.angleValues.length)));
        CMAESOptimizer optimizer = new CMAESOptimizer(nSteps, stopFitness, true, diagOnly, 0,
                DEFAULT_RANDOMGENERATOR, true,
                new Checker(100 * Precision.EPSILON, 100 * Precision.SAFE_MIN, nSteps));
        dihedrals.normalize(dihedrals.angleValues, dihedrals.normValues);

        double ranfact = optimizeScale(dihedrals.normValues, dihedrals.inputSigma, 1.0);

        for (int i = 0; i < dihedrals.inputSigma.length; i++) {
            dihedrals.inputSigma[i] *= ranfact;
        }

        PointValuePair result = optimizer.optimize(
                new CMAESOptimizer.PopulationSize(lambda),
                new CMAESOptimizer.Sigma(dihedrals.inputSigma),
                new MaxEval(2000000),
                new ObjectiveFunction(this), GoalType.MINIMIZE,
                new SimpleBounds(dihedrals.normBoundaries[0], dihedrals.normBoundaries[1]),
                new InitialGuess(dihedrals.normValues));

        System.arraycopy(dihedrals.bestValues, 0, dihedrals.angleValues, 0, dihedrals.angleValues.length);
        putDihedrals();
        molecule.genCoords(false, null);
        List<Double> sigmaHistory = optimizer.getStatisticsSigmaHistory();
        int nStat = sigmaHistory.size();
        if (nStat > 0) {
            System.out.println("finished " + optimizer.getIterations() + " " + sigmaHistory.get(nStat - 1));
        }
        return result.getValue();
    }

    public double refineCMAESWithLinkedAtoms(final int nSteps, final double stopFitness,
                                             final double sigma, final double lambdaMul, final int diagOnly,
                                             final boolean useDegrees, final double dev1, final double dev2) {
        reportAt = 10;
        bestEnergy = Double.MAX_VALUE;
        double energy = energy();
        molecule.genCoords(false, null);
        dihedrals.energyList.makeAtomListFast();
        energy = energy();
        molecule.genCoords(false, null);
        dihedrals.energyList.makeAtomListFast();
        energy = energy();
        double[][] values = getLinkedValues(dev1, dev2);
        double[][] normBoundaries = new double[3][values[0].length];
        double[] sigmaValues = new double[values[0].length];
        Arrays.fill(normBoundaries[0], 0.0);
        Arrays.fill(normBoundaries[1], 100.0);
        Arrays.fill(normBoundaries[2], 50.0);
        Arrays.fill(sigmaValues, 10.0);

        long time = System.currentTimeMillis();
        long deltaTime = time - startTime;
        report(0, nEvaluations, deltaTime, dihedrals.energyList.atomList.size(), energy);

        DEFAULT_RANDOMGENERATOR.setSeed(1);
        //suggested default value for population size represented by variable 'labda'
        //anglesValue.length represents the number of parameters
        int lambda = (int) (lambdaMul * Math.round(4 + 3 * Math.log(values[0].length)));
        CMAESOptimizer optimizer = new CMAESOptimizer(nSteps, stopFitness, true, diagOnly, 0,
                DEFAULT_RANDOMGENERATOR, true,
                new Checker(100 * Precision.EPSILON, 100 * Precision.SAFE_MIN, nSteps));

        PointValuePair result = optimizer.optimize(
                new CMAESOptimizer.PopulationSize(lambda),
                new CMAESOptimizer.Sigma(sigmaValues),
                new MaxEval(2000000),
                new ObjectiveFunction(this), GoalType.MINIMIZE,
                new SimpleBounds(normBoundaries[0], normBoundaries[1]),
                new InitialGuess(normBoundaries[2]));
        updateWithLinkedValues(linkedValues[3]);

        molecule.genCoords(false, null);
        List<Double> sigmaHistory = optimizer.getStatisticsSigmaHistory();
        int nStat = sigmaHistory.size();
        if (nStat > 0) {
            System.out.println("finished " + optimizer.getIterations() + " " + sigmaHistory.get(nStat - 1));
        }
        return result.getValue();
    }

    public double[][] getLinkedValues(double dev1, double dev2) {
        int nVars = linkedAtoms.length - 1 + linkedAtoms[linkedAtoms.length - 1].size();
        linkedValues = new double[4][nVars];
        int i = 0;
        for (List<Atom> atomArray : linkedAtoms) {
            if (i < linkedAtoms.length - 1) {
                double sumCos = 0.0;
                double sumSin = 0.0;
                for (Atom atom : atomArray) {
                    sumCos += Math.cos(atom.dihedralAngle);
                    sumSin += Math.sin(atom.dihedralAngle);
                }
                int n = atomArray.size();
                double avg = Math.atan2(sumSin / n, sumCos / n);
                for (Atom atom : atomArray) {
                    System.out.println(atom.getFullName() + " " + Math.toDegrees(atom.dihedralAngle) + " " + Math.toDegrees(avg));
                }

                linkedValues[2][i] = avg;
                linkedValues[0][i] = linkedValues[2][i] - Math.toRadians(dev1);
                linkedValues[1][i] = linkedValues[2][i] + Math.toRadians(dev1);
            } else {
                for (Atom atom : atomArray) {
                    linkedValues[2][i] = atom.dihedralAngle;
                    linkedValues[0][i] = linkedValues[2][i] - Math.toRadians(dev2);
                    linkedValues[1][i] = linkedValues[2][i] + Math.toRadians(dev2);
                    i++;
                }
            }
            i++;
        }
        return linkedValues;
    }

    double[] denormLinkedValues(final double[] values) {

        for (int i = 0; i < values.length; i++) {
            double f = values[i] / 100.0;
            linkedValues[2][i] = f * (linkedValues[1][i] - linkedValues[0][i]) + linkedValues[0][i];
        }
        return linkedValues[2];
    }

    void updateWithLinkedValues(final double[] denormValues) {
        int i = 0;
        for (List<Atom> atomArray : linkedAtoms) {
            if (i < linkedAtoms.length - 1) {
                for (Atom atom : atomArray) {
                    atom.dihedralAngle = (float) denormValues[i];
                }
            } else {
                for (Atom atom : atomArray) {
                    atom.dihedralAngle = (float) denormValues[i];
                    i++;
                }
            }
            i++;
        }
        molecule.genCoords(false, null);
    }

    void dumpLinkedValues() {
        int i = 0;
        for (List<Atom> atomArray : linkedAtoms) {
            if (i < linkedAtoms.length - 1) {
                for (Atom atom : atomArray) {
                    System.out.printf("%10s %12.6s\n", atom.getShortName(), Math.toDegrees(atom.dihedralAngle));
                }
            } else {
                for (Atom atom : atomArray) {
                    System.out.printf("%10s %12.6s\n", atom.getShortName(), Math.toDegrees(atom.dihedralAngle));
                    i++;
                }
            }
            i++;
        }
        molecule.genCoords(false, null);
    }

    public double linkedValue(final double[] values) {
        double[] denormValues = denormLinkedValues(values);
        updateWithLinkedValues(denormValues);
        if ((nEvaluations % updateAt) == 0) {
            dihedrals.energyList.makeAtomListFast();
        }
        double energy = energy();
        if (energy < bestEnergy) {
            bestEnergy = energy;
            System.arraycopy(denormValues, 0, linkedValues[3], 0, denormValues.length);
            nEvaluations++;
        }
        return energy;
    }

    public double value(final double[] dihValues) {
        if (linkedValues != null) {
            return linkedValue(dihValues);
        }
        dihedrals.denormalize(dihValues, dihedrals.angleValues);
        putDihedrals();
        molecule.genCoords(false, null);

        if ((nEvaluations % updateAt) == 0) {
            dihedrals.energyList.makeAtomListFast();
        }
        double energy = energy();
        if (energy < bestEnergy) {
            bestEnergy = energy;
            System.arraycopy(dihedrals.angleValues, 0, dihedrals.bestValues, 0, dihedrals.angleValues.length);
        }
        nEvaluations++;
        return energy;
    }

    public void printAtomValues() {
        List<Atom> pseudoAngleAtoms = molecule.getPseudoAngleAtoms();
        List<Atom> angleAtoms = molecule.getAngleAtoms();

        int nPseudoAngles = pseudoAngleAtoms.size() / 3;
        for (int i = 0; i < dihedrals.angleValues.length; ) {
            Atom atom;
            boolean incrementByTwo = false;
            if (i < (2 * nPseudoAngles)) {
                atom = pseudoAngleAtoms.get(i / 2 * 3);
                incrementByTwo = true;
            } else {
                atom = angleAtoms.get(i - 2 * nPseudoAngles).daughterAtom;
            }
            System.out.println(i + ": " + "Atom name: " + atom.getFullName() + " Angle Value = " + dihedrals.angleValues[i]);

            if (incrementByTwo == true) {
                i += 2;
            } else {
                i++;
            }

        }

        System.out.println("\n----------------");
        for (int i = 0; i < dihedrals.angleValues.length; i++) {
            System.out.println("i: " + i + " angleValue: " + dihedrals.angleValues[i]);
        }
    }

    public double optimizeScale(final double[] dihedrals, final double[] sigmaValues, double limMul) {
        double[] testValues = new double[dihedrals.length];

        double energyStart = value(dihedrals);
        System.out.println("energyStart: " + energyStart);
        double energyDelta = Math.abs(energyStart) < 0.2 ? 0.2 : energyStart;
        double energyLim = energyStart + Math.abs(energyDelta * limMul);
        double ranfact = 1.0;
        int nSamples = 10;
        int nTries = 20;
        double energyMin = energyStart;
        double energyMax = energyStart;
        for (int k = 0; k < nTries; k++) {
            int nAbove = 0;
            energyMin = energyStart;
            energyMax = energyStart;
            for (int j = 0; j < nSamples; j++) {
                for (int i = 0; i < testValues.length; i++) {
                    double delta = 1.0 * ranfact * rand.nextGaussian();
                    testValues[i] = dihedrals[i] + sigmaValues[i] * delta;
                }
                double energy = value(testValues);
                if (energy > energyLim) {
                    nAbove++;
                }
                if (energy < energyMin) {
                    energyMin = energy;
                }
                if (energy > energyMax) {
                    energyMax = energy;
                }
            }
            double f = (double) nAbove / nSamples;
            System.out.printf("%.2f %.3f %8.2f %8.2f %8.2f\n", f, ranfact, energyStart, energyMin, energyMax);
            if (f > 0.5) {
                ranfact *= 0.64;
            } else {
                break;
            }
        }
        return ranfact;
    }

}
