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

import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleValueChecker;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunctionGradient;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.structure.chemistry.MissingCoordinatesException;
import org.nmrfx.structure.chemistry.io.TrajectoryWriter;
import org.nmrfx.utilities.ProgressUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author johnsonb
 */
public class GradientRefinement extends Refinement {
    private static final Logger log = LoggerFactory.getLogger(GradientRefinement.class);

    public static boolean NLCG = true;
    private boolean useNumericDerivatives = false;
    public TrajectoryWriter trajectoryWriter = null;
    private static ProgressUpdater progressUpdater = null;

    public class Checker extends SimpleValueChecker {

        public Checker(double relativeThreshold, double absoluteThreshold, int maxIter) {
            super(relativeThreshold, absoluteThreshold, maxIter);
        }

        public boolean converged(final int iteration, final PointValuePair previous, final PointValuePair current) {
            boolean converged = super.converged(iteration, previous, current);
            if (converged || (iteration == 1) || ((iteration % reportAt) == 0)) {
                long time = System.currentTimeMillis();
                long deltaTime = time - startTime;
                int nContacts = molecule.getEnergyCoords().getNContacts();
                report("GMIN", iteration, nContacts, current.getValue());
                if (trajectoryWriter != null) {
                    if ((progressUpdater != null) || (trajectoryWriter != null)) {
                        molecule.updateFromVecCoords();

                        if (trajectoryWriter != null) {
                            try {
                                trajectoryWriter.writeStructure();
                            } catch (MissingCoordinatesException ex) {
                                log.warn(ex.getMessage(), ex);
                            }
                        }
                        if (progressUpdater != null) {
                            progressUpdater.updateStatus(String.format("Step: %6d Energy: %7.1f", iteration, current.getValue()));
                        }
                    }
                }
            }
            return converged;
        }
    }

    public GradientRefinement(final Dihedral dihedrals) {
        super(dihedrals);
        this.molecule = dihedrals.molecule;
        startTime = System.currentTimeMillis();
    }

    public void setTrajectoryWriter(TrajectoryWriter trajectoryWriter) {
        this.trajectoryWriter = trajectoryWriter;
    }

    public static void setUpdater(ProgressUpdater updater) {
        progressUpdater = updater;
    }

    public void gradMinimize(int nSteps, double tolerance) {
        gradMinimizeNLCG(nSteps, tolerance);
    }

    public void gradMinimizeNLCG(int nSteps, double tolerance) {
        NonLinearConjugateGradientOptimizer optimizer = new NonLinearConjugateGradientOptimizer(
                NonLinearConjugateGradientOptimizer.Formula.POLAK_RIBIERE,
                new Checker(tolerance, tolerance, nSteps), tolerance, tolerance, 1.0e-5);
        //new SimpleValueChecker(100 * Precision.EPSILON, 100 * Precision.SAFE_MIN));
        System.out.printf("%-6s %6s %8s %9s\n", "GMIN", "iter", "contacts", "energy");

        prepareAngles(false);
        dihedrals.setBoundaries(0.1, false);
        getDihedrals();

        dihedrals.energyList.makeAtomListFast();
        DihedralEnergy dihEnergy = new DihedralEnergy(dihedrals);
        DihedralGradient dihGradient = new DihedralGradient(this);
        int nContacts = molecule.getEnergyCoords().getNContacts();
        report("GMIN", 0, nContacts, dihEnergy.value(dihedrals.angleValues));
        reportAt = 20;
        if (trajectoryWriter != null) {
            try {
                trajectoryWriter.writeStructure();
            } catch (MissingCoordinatesException ex) {
                log.error(ex.getMessage(), ex);
            }
        }
        PointValuePair result = optimizer.optimize(
                new ObjectiveFunctionGradient(dihGradient),
                new ObjectiveFunction(dihEnergy),
                new MaxEval(nSteps * 1000),
                GoalType.MINIMIZE,
                new InitialGuess(dihedrals.angleValues));
        System.arraycopy(result.getPoint(), 0, dihedrals.angleValues, 0, dihedrals.angleValues.length);
        putDihedrals();
        molecule.genCoords(false, null);
    }

    public double[] nonNormDeriv(final double[] dihAngles) {
        System.arraycopy(dihAngles, 0, dihedrals.angleValues, 0, dihedrals.angleValues.length);
        putDihedrals();
        molecule.genCoords(false, null);
        EnergyDeriv eDeriv = eDeriv();
        if (useNumericDerivatives) {
            double[] nDerivatives = numericalDerivatives(1.0e-6, false);
            eDeriv = new EnergyDeriv(eDeriv.getEnergy(), nDerivatives);
        }
        nEvaluations++;
        return eDeriv.getDerivatives();
    }

    public double[] nonNormDeriv(final double[] dihAngles, final double[] derivs) {
        System.arraycopy(dihAngles, 0, dihedrals.angleValues, 0, dihedrals.angleValues.length);
        putDihedrals();
        molecule.genCoords(false, null);
        EnergyDeriv eDeriv = eDeriv();
        if (useNumericDerivatives) {
            double[] nDerivatives = numericalDerivatives(1.0e-6, false);
            eDeriv = new EnergyDeriv(eDeriv.getEnergy(), nDerivatives);
            System.arraycopy(nDerivatives, 0, derivs, 0, nDerivatives.length);
        }
        nEvaluations++;
        return eDeriv.getDerivatives();
    }

    public double deriv(final double[] dihValues) {
        if ((nEvaluations % updateAt) == 0) {
            dihedrals.energyList.makeAtomListFast();
        }
        dihedrals.denormalize(dihValues, dihedrals.angleValues);
        putDihedrals();
        molecule.genCoords(false, null);
        EnergyDeriv eDeriv = eDeriv();
        double energy = eDeriv.getEnergy();
        if (energy < bestEnergy) {
            bestEnergy = energy;
            System.arraycopy(dihedrals.angleValues, 0, dihedrals.bestValues, 0, dihedrals.angleValues.length);
            long time = System.currentTimeMillis();
            long deltaTime = time - startTime;
            System.out.println(nEvaluations + " " + deltaTime + " " + dihedrals.energyList.atomList.size() + " " + energy);
        }
        nEvaluations++;
        return energy;
    }

    public double calcDeriv(final double delta, int i) {
        double origValue = dihedrals.angleValues[i];

        dihedrals.angleValues[i] = origValue - 2.0 * delta;
        putDihedrals();
        molecule.genCoords(false, null);
        molecule.updateVecCoords();
        double energy1 = energy();

        dihedrals.angleValues[i] = origValue - delta;
        putDihedrals();
        molecule.genCoords(false, null);
        molecule.updateVecCoords();
        double energy2 = energy();

        dihedrals.angleValues[i] = origValue + delta;
        putDihedrals();
        molecule.genCoords(false, null);
        molecule.updateVecCoords();
        double energy3 = energy();

        dihedrals.angleValues[i] = origValue + 2.0 * delta;
        putDihedrals();
        molecule.genCoords(false, null);
        molecule.updateVecCoords();
        double energy4 = energy();

        dihedrals.angleValues[i] = origValue;
        putDihedrals();
        molecule.genCoords(false, null);
        molecule.updateVecCoords();
        double deriv = (energy1 / 12.0 - 2.0 * energy2 / 3.0 + 2.0 * energy3 / 3.0 - energy4 / 12.0) / delta;
        return deriv;

    }

    public double[] numericalDerivatives(final double delta, boolean report) {
        prepareAngles(false);
        getDihedrals();
        int nAngles = dihedrals.angleValues.length;
        EnergyDeriv eDeriv = eDeriv();
        double[] derivatives = eDeriv.getDerivatives();
        double[] nDerivatives = new double[nAngles];
        if (report) {
            System.out.println("nAnalytical " + derivatives.length + " nNumeric " + nDerivatives.length);
            System.out.printf("%4s %10s %9s %9s %9s %9s\n", "i", "name", "e1", "e2", "nDer", "aDer");
        }
        for (int i = 0; i < nAngles; i++) {
            double deriv = calcDeriv(delta, i);
            nDerivatives[i] = deriv;
            if (report) {
                System.out.printf("%4d %10s %12.7f %12.7f %12.7f %12.7f\n", i, dihedrals.energyList.branches[i].atom.getFullName(), 0.0, 0.0, deriv, derivatives[i]);
            }
        }
        return nDerivatives;
    }

    public void dumpAngles() {
        int nAngles = dihedrals.angleValues.length;
        for (int i = 0; i < nAngles; i++) {
            Atom atom = dihedrals.energyList.branches[i].atom;
            System.out.println("Angles " + atom.getFullName() + " " + atom.daughterAtom.getFullName() + " " + Math.toDegrees(atom.daughterAtom.dihedralAngle));
        }
    }

    public double[] calcDerivError(final double delta) {
        prepareAngles(false);
        getDihedrals();
        int nAngles = dihedrals.angleValues.length;
        EnergyDeriv eDeriv = eDeriv();
        double[] derivatives = eDeriv.getDerivatives();
        double maxError = Double.NEGATIVE_INFINITY;
        double maxDeriv = Double.NEGATIVE_INFINITY;
        molecule.updateVecCoords();
        molecule.genCoords(false);
        for (int i = 0; i < nAngles; i++) {
            Atom atom = dihedrals.energyList.branches[i].atom;
            double deriv = calcDeriv(delta, i);
            if (Math.abs(deriv) > maxDeriv) {
                maxDeriv = Math.abs(deriv);
            }
            double deltaDeriv = Math.abs(derivatives[i] - deriv);
            if (deltaDeriv > maxError) {
                maxError = deltaDeriv;
            }
        }
        double[] result = {maxDeriv, maxError};
        return result;
    }

}
