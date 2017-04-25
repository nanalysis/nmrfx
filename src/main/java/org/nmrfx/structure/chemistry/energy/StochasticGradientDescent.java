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

//import org.apache.commons.math3.optimization.direct.CMAESOptimizer;
import org.nmrfx.structure.chemistry.MissingCoordinatesException;
import org.nmrfx.structure.chemistry.io.TrajectoryWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.optim.PointValuePair;
//import org.apache.commons.math3.optimization.SimpleValueChecker;
import org.apache.commons.math3.optim.SimpleValueChecker;
import org.apache.commons.math3.random.RandomDataGenerator;

/**
 *
 * @author johnsonb
 */
public class StochasticGradientDescent extends GradientRefinement {

    public TrajectoryWriter trajectoryWriter = null;
    RandomDataGenerator randomData = new RandomDataGenerator();

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
                if (trajectoryWriter != null) {
                    try {
                        trajectoryWriter.writeStructure();
                    } catch (MissingCoordinatesException ex) {
                        Logger.getLogger(GradientRefinement.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            return converged;
        }
    }

    public StochasticGradientDescent(final Dihedral dihedrals) {
        super(dihedrals);
        this.molecule = dihedrals.molecule;
        startTime = System.currentTimeMillis();
    }

    public void setTrajectoryWriter(TrajectoryWriter trajectoryWriter) {
        this.trajectoryWriter = trajectoryWriter;
    }

    public void gradMinimize(int nSteps, double tolerance) {
        prepareAngles(false);
        dihedrals.setBoundaries(0.1, false);
        PointValuePair result = null;
        getDihedrals();

        dihedrals.energyList.makeAtomListFast();
        DihedralEnergy dihEnergy = new DihedralEnergy(dihedrals);
        DihedralGradient dihGradient = new DihedralGradient(this);
        report(0, 0, 0, dihedrals.energyList.atomList.size(), dihEnergy.value(dihedrals.angleValues));
        reportAt = 20;
        double[] point = new double[dihedrals.angleValues.length];
        double[] delta = new double[dihedrals.angleValues.length];
        double[] lastDelta = new double[dihedrals.angleValues.length];
        System.arraycopy(dihedrals.angleValues, 0, point, 0, point.length);
        double alpha = 0.000013;
        double beta = 0.5;
        boolean[] state = new boolean[77];
        for (int i = 0; i < nSteps; i++) {
            for (int j = 0; j < state.length; j++) {
                if (randomData.nextUniform(0.0, 1.0) < 0.8) {
                    state[j] = true;
                } else {
                    state[j] = false;
                }
            }
            dihedrals.energyList.setStochasticResidues(state);

            double[] grad = dihGradient.value(point);
            double energy = dihEnergy.value(point);
            System.out.printf("%5d %10.6g %7.1f\n", i, alpha, energy);
            for (int j = 0; j < point.length; j++) {
                delta[j] = -alpha * grad[j] + beta * lastDelta[j];
                point[j] += delta[j];
                lastDelta[j] = delta[j];
            }
            alpha *= 0.99997;

        }
        System.arraycopy(point, 0, dihedrals.angleValues, 0, point.length);
        putDihedrals();
        molecule.genCoords(false, null);
    }

}
