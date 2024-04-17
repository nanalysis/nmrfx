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

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.AtomEnergyProp;
import org.nmrfx.chemistry.Util;
import org.nmrfx.structure.chemistry.MissingCoordinatesException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.SuperMol;
import org.nmrfx.structure.chemistry.io.TrajectoryWriter;
import org.nmrfx.structure.fastlinear.FastMatrix;
import org.nmrfx.structure.fastlinear.FastVector;
import org.nmrfx.structure.fastlinear.FastVector3D;
import org.nmrfx.utilities.ProgressUpdater;
import org.python.core.PyFloat;
import org.python.core.PyFunction;
import org.python.core.PyObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static java.util.Objects.requireNonNull;

/**
 * @author brucejohnson
 */
public class RotationalDynamics {

    ArrayList<AtomBranch> branches;
    Molecule molecule;
    Dihedral dihedrals;
    double lastKineticEnergy = 0.0;
    double lastPotentialEnergy = 0.0;
    double lastTotalEnergy = 0.0;
    double deltaEnergy = 0.0;
    double totalTime = 0.0;
    double currentTemp = 0.0;
    double bathTemp = 100.0;
    double timeStep = 1.0e-4;
    double kineticEnergyScale = 50.0;
    double lambda;
    int currentStep = 0;
    int nSteps = 1;
    double eRef = 0.005;
    double tau = 10.0;
    double tempTimeConstant = 5.0;
    double minTimeStep = 0.0001;
    double maxTimeStep = 1000.0;
    double sumDeltaSq = 0.0;
    double sumMaxDelta = 0.0;
    double sumERef = 0.0;
    Random rand;
    public static boolean firstRun = true;
    public TrajectoryWriter trajectoryWriter = null;
    private static ProgressUpdater progressUpdater = null;
    PyObject tempFunction;
    PyObject econFunction;

    double[][] velStoreLin;
    double[][] accStore;
    double[] velStoreAng;
    DynState dynState = null;

    public RotationalDynamics(Dihedral dihedrals, Random rand) {
        this.dihedrals = dihedrals;
        this.molecule = dihedrals.energyList.getMolecule();
        this.branches = new ArrayList<>();
        Collections.addAll(this.branches, dihedrals.energyList.branches);
        this.rand = rand;
        getBranchAtoms();
        // makeInertias now
    }

    public static void setUpdater(ProgressUpdater updater) {
        progressUpdater = updater;
    }

    public void setTrajectoryWriter(TrajectoryWriter trajWriter) {
        this.trajectoryWriter = trajWriter;
    }

    public void setKinEScale(double value) {
        kineticEnergyScale = value;
    }

    public double getTimeStep() {
        return timeStep;
    }

    final void getBranchAtoms() {
        List<Atom> atoms = molecule.getAtomArray();
        int i = 0;
        for (AtomBranch branch : branches) {
            if (branch == null) {
                System.out.println("Rotational Dynamcis has a null branch");
                break;
            }
            for (Atom atom : atoms) {
                if (atom.rotGroup == branch.atom) {
                    branch.atoms.add(atom);
                }
            }
            if (branch.atoms.isEmpty()) {
                System.out.println("empty " + branch.atom.getFullName());
            }
            branch.index = i;
            i++;
        }
        double totalMass = 0.0;
        for (Atom atom : atoms) {
            AtomEnergyProp prop = AtomEnergyProp.get(atom.getType());
            if (prop != null) {
                atom.mass = prop.getMass();
                totalMass += atom.mass;
            }
        }
        System.out.printf("mass %.1f\n", totalMass);
        findEndGroup(branches.get(0));
    }

    int findEndGroup(AtomBranch startBranch) {
        if (startBranch == null) {
            return -1;
        }
        int endMax = startBranch.index;
        for (AtomBranch branch : startBranch.branches) {
            int end = findEndGroup(branch);
            if (end > endMax) {
                endMax = end;
            }
        }
        startBranch.lastGroup = endMax;
        return endMax;
    }

    public void calcRotInertia2() {
        for (AtomBranch iBranch : branches) {
            iBranch.inertialTensorSetup();
        }
    }

    public void initVelocities2(double temp) {
        for (AtomBranch branch : branches) {
            if (branch.mass < 1.0e-6) {
                branch.rotVel = 0.0;
            } else {
                double a1 = Math.sqrt(1.38e-8 * temp / 100000.0 * branch.mass);
                branch.rotVel = a1 * rand.nextGaussian();
            }

        }
        updateVelocitiesRecursive();
        double outtemp = calcTemp2();
        for (int i = 0; i < 2; i++) {
            double currentLambda = getLambda(temp, outtemp, 1);
            adjustTemp(currentLambda);
            updateVelocitiesRecursive();
            outtemp = calcTemp2();
        }
        System.out.println("init vel " + temp + " " + outtemp);
    }

    public void advanceDihedrals2(double timestep) {
        double delAngle;
        double sumSq = 0.0;
        double max = 0.0;
        for (AtomBranch branch : branches) {
            double v = branch.rotVel;
            double a0 = branch.rotAccel.getEntry(1);
            double a1 = branch.rotAccel.getEntry(2);
            delAngle = v * timestep + (4.0 * a1 - a0) * timestep * timestep / 6.0;
            Atom diAtom = branch.atom;
            Atom daughter = requireNonNull(diAtom.daughterAtom, "daughter atom is null " + diAtom.getShortName());
            double absDelta = Math.abs(delAngle);
            if (absDelta > max) {
                max = absDelta;
            }
            sumSq += delAngle * delAngle;
            daughter.dihedralAngle += (float) delAngle;
            daughter.dihedralAngle = (float) Util.reduceAngle(daughter.dihedralAngle);
        }
        sumDeltaSq += Math.sqrt(sumSq / branches.size());
        sumMaxDelta += max;
    }

    public void saveVelocities2() {
        if ((velStoreLin == null) || (velStoreLin.length != branches.size())) {
            velStoreLin = new double[branches.size()][3];
            accStore = new double[branches.size()][3];
        }
        if ((velStoreAng == null) || (velStoreAng.length != branches.size())) {
            velStoreAng = new double[branches.size()];
        }
        for (int k = 0; k < branches.size(); k++) {
            AtomBranch iBranch = branches.get(k);
            velStoreLin[k][0] = iBranch.linVelF.getEntry(0);
            velStoreLin[k][1] = iBranch.linVelF.getEntry(1);
            velStoreLin[k][2] = iBranch.linVelF.getEntry(2);
            velStoreAng[k] = iBranch.rotVel;
            accStore[k][0] = iBranch.rotAccel.getEntry(0);
            accStore[k][1] = iBranch.rotAccel.getEntry(1);
            accStore[k][2] = iBranch.rotAccel.getEntry(2);
        }
    }

    public void restoreVelocities2() {
        for (int k = 0; k < branches.size(); k++) {
            AtomBranch iBranch = branches.get(k);
            iBranch.linVelF.setEntry(0, velStoreLin[k][0]);
            iBranch.linVelF.setEntry(1, velStoreLin[k][1]);
            iBranch.linVelF.setEntry(2, velStoreLin[k][2]);
            iBranch.rotVel = velStoreAng[k];
            iBranch.rotAccel.setEntry(0, accStore[k][0]);
            iBranch.rotAccel.setEntry(1, accStore[k][1]);
            iBranch.rotAccel.setEntry(2, accStore[k][2]);
        }
    }

    public void advanceVelocities2(double timestep) {
        double maxChange = 0.0;
        for (AtomBranch branch : branches) {
            double a0 = branch.rotAccel.getEntry(0);
            double a1 = branch.rotAccel.getEntry(1);
            double a2 = branch.rotAccel.getEntry(2);
            double delVelocity = lambda * (2.0 * a2 + 5.0 * a1 - a0) * timestep / 6.0;
            branch.rotVel += delVelocity;
            if (Math.abs(delVelocity) > maxChange) {
                maxChange = Math.abs(delVelocity);
            }
        }
    }

    double calcKineticEnergy() {
        double sum = 0.0;
        FastVector3D tempF = new FastVector3D();
        for (AtomBranch branch : branches) {
            sum += 0.5 * branch.mass * branch.linVelF.sumSq();
            branch.inertialTensorF.operate(branch.angVelF, tempF);
            sum += 0.5 * branch.angVelF.dotProduct(tempF);
        }
        return kineticEnergyScale * sum;
    }

    double calcTemp2(double kineticEnergy) {
        kineticEnergy /= kineticEnergyScale;
        return (2.0 * kineticEnergy / ((branches.size() - 1) * 1.38e-6));
    }

    double calcTemp2() {
        double kineticEnergy = calcKineticEnergy();
        return calcTemp2(kineticEnergy);
    }

    double getLambda(double temp0, double temp) {
        return getLambda(temp0, temp, tempTimeConstant);
    }

    double getLambda(double temp0, double temp, double timeC) {
        if (temp < 1.0e-6) {
            lambda = 4.0;
        } else {
            lambda = Math.sqrt(1.0 + (1.0 / timeC) * ((temp0 / temp) - 1.0));
        }
        if (temp > (2.0 * temp0)) {
            lambda = Math.sqrt(0.01 + temp0 / temp);
        }
        if (lambda > 4.0) {
            lambda = 4.0;
        }
        return lambda;
    }

    void adjustTemp(double lambda) {
        for (AtomBranch branch : branches) {
            branch.rotVel = branch.rotVel * lambda;
        }
    }

    void updateTimeStep(int iStep) {
        eRef = getPyEcon(1.0 * iStep / nSteps);
        if (Math.abs(deltaEnergy) < 1.0e-9) {
            deltaEnergy = 1.0e-9;
        }

        double currentLambda = Math.sqrt(1.0 + (eRef - Math.abs(deltaEnergy)) / (Math.abs(deltaEnergy) * tau));
        if (currentLambda > 1.025) {
            currentLambda = 1.025;
        }
        timeStep = currentLambda * timeStep;
        if (timeStep < minTimeStep) {
            timeStep = minTimeStep;
        }
        if (timeStep > maxTimeStep) {
            timeStep = maxTimeStep;
        }
    }

    void updateBathTemp(int iStep) {
        bathTemp = getPyTemp(1.0 * iStep / nSteps);
    }

    double getPyTemp(double f) {
        double temp;
        if (tempFunction instanceof PyFunction pyFunction) {
            PyObject[] objs = new PyObject[1];
            objs[0] = new PyFloat(f);
            PyObject pyResult = pyFunction.__call__(objs);
            temp = pyResult.asDouble();
        } else {
            temp = tempFunction.asDouble();
        }
        return temp;
    }

    double getPyEcon(double f) {
        double eCon;
        if (econFunction instanceof PyFunction) {
            PyObject[] objs = new PyObject[1];
            objs[0] = new PyFloat(f);
            PyObject pyResult = econFunction.__call__(objs);
            eCon = pyResult.asDouble();
        } else {
            eCon = econFunction.asDouble();
        }
        return eCon;
    }

    public void initDynamics2(PyObject tempFunction, PyObject econFunction, int nSteps, double timeStep) {
        this.tempFunction = tempFunction;
        this.econFunction = econFunction;
        continueDynamics2(tempFunction, econFunction, nSteps, timeStep);
        this.bathTemp = getPyTemp(0.0);
        eRef = getPyEcon(0.0);
        currentStep = 0;
        calcRotInertia2();
        initVelocities2(this.bathTemp);
        EnergyDeriv eDeriv = dihedrals.eDeriv();
        calcAcceleration2(2);
        advanceDihedrals2(timeStep);
        molecule.genCoordsFastVec3D(null);
        calcRotInertia2();
        lastKineticEnergy = calcKineticEnergy();
        lastPotentialEnergy = eDeriv.getEnergy();
        totalTime = 0.0;
        calcTemp2();
    }

    public void continueDynamics2(PyObject tempFunction, PyObject econFunction, int nSteps, double timeStep) {
        this.tempFunction = tempFunction;
        this.econFunction = econFunction;
        this.nSteps = nSteps;
        this.timeStep = timeStep;
        currentStep = 0;
        dihedrals.energyList.makeAtomListFast();

    }

    public void continueDynamics2(double timeStep) {
        this.timeStep = timeStep;
    }

    public void calcAcceleration2(int prev) {
        if (prev < 0) {
            prev = 0;
        }
        if (prev > 2) {
            prev = 2;
        }
        for (AtomBranch branch : branches) {
            branch.rotAccel.setEntry(0, branch.rotAccel.getEntry(1));
            branch.rotAccel.setEntry(1, branch.rotAccel.getEntry(2));
        }
        int n = branches.size();
        for (AtomBranch iBranch : branches) {
            iBranch.eVecSetup();
            iBranch.zVecSetup();
            iBranch.pMatSetup();
            iBranch.phiMatSetup();
            iBranch.aVecSetup();
            iBranch.alphaVecSetup();
        }
        FastMatrix tempMat1F = new FastMatrix(6, 6);
        FastMatrix tempMat3F = new FastMatrix(6, 6);
        FastMatrix tempMat2F = new FastMatrix(6, 6);
        FastVector gKepKF = new FastVector(6);
        FastVector tempVec1F = new FastVector(6);
        FastVector tempVec2F = new FastVector(6);
        for (int i = n - 1; i >= 0; i--) {
            AtomBranch iBranch = branches.get(i);
            iBranch.dkSetup();
            iBranch.gkVecSetup();
            iBranch.epskSetup();
            AtomBranch prevBranch = iBranch.prev;
            if (prevBranch != null) {

                iBranch.gkVecF.outerProduct(iBranch.eVecF, tempMat1F);
                tempMat1F.multiply(iBranch.pMatF, tempMat2F);
                iBranch.pMatF.subtract(tempMat2F, tempMat1F);
                tempMat1F.mMm(iBranch.phiMatF, tempMat2F, tempMat3F);

                iBranch.gkVecF.multiply(iBranch.epskF, gKepKF);

                iBranch.pMatF.operate(iBranch.aVecF, tempVec1F);
                iBranch.phiMatF.operate(tempVec1F, tempVec2F);
                iBranch.zVecF.add(gKepKF, gKepKF);
                prevBranch.pMatF.add(tempMat3F, prevBranch.pMatF);
                iBranch.phiMatF.operate(gKepKF, tempVec1F);
                prevBranch.zVecF.add(tempVec1F, prevBranch.zVecF);
                prevBranch.zVecF.add(tempVec2F, prevBranch.zVecF);
            }
        }
        FastVector alphaPreF = new FastVector(6);
        FastVector tempVecF = new FastVector(6);
        for (AtomBranch branch : branches) {
            if (branch.prev != null) {
                alphaPreF.copyFrom(branch.prev.alphaVecF);
            } else {
                alphaPreF.zero();
            }
            branch.phiMatF.transOperate(alphaPreF, branch.alphaVecF);
            double accelF = branch.epskF / branch.dkF - branch.gkVecF.dotProduct(branch.alphaVecF);
            branch.rotAccel.setEntry(2, accelF);
            branch.eVecF.multiply(accelF, tempVecF);
            tempVecF.add(branch.aVecF, tempVecF);
            branch.alphaVecF.add(tempVecF, branch.alphaVecF);
        }

        for (AtomBranch branch : branches) {
            for (int j = 0; j < prev; j++) {
                branch.rotAccel.setEntry(j, branch.rotAccel.getEntry(prev));
            }
        }
    }

    public void updateVelocitiesRecursive() {
        for (AtomBranch iBranch : branches) {
            AtomBranch prevBranch = iBranch.prev;
            FastVector unitVecF = iBranch.getUnitVecF();
            if (null != prevBranch) {
                unitVecF.multiply(iBranch.rotVel);
                prevBranch.angVelF.add(unitVecF, iBranch.angVelF);
                FastVector iVec = iBranch.getVectorF();
                FastVector pVec = prevBranch.getVectorF();
                iVec.subtract(pVec, pVec);
                pVec.crossProduct(prevBranch.angVelF, iVec);
                prevBranch.linVelF.subtract(iVec, iBranch.linVelF);
            } else {
                unitVecF.multiply(iBranch.rotVel);
                iBranch.angVelF.copyFrom(unitVecF);
                iBranch.linVelF.zero();
            }
        }
    }

    public double step2(int iStep) {
        updateBathTemp(iStep);
        updateVelocitiesRecursive();
        double kineticEnergy = calcKineticEnergy();
        currentTemp = calcTemp2(kineticEnergy);
        updateTimeStep(iStep);
        double currentLambda = getLambda(bathTemp, currentTemp);
        adjustTemp(currentLambda);
        updateVelocitiesRecursive();
        if (iStep == 0) {
            calcAcceleration2(1);
        } else {
            calcAcceleration2(0);
        }

        advanceVelocities2(timeStep);
        advanceDihedrals2(timeStep);
        molecule.genCoordsFastVec3D(null);
        updateVelocitiesRecursive();

        double newKineticEnergy = calcKineticEnergy();

        EnergyDeriv eDeriv = dihedrals.eDeriv();
        double potentialEnergy = eDeriv.getEnergy();
        double total = potentialEnergy + kineticEnergy;

        deltaEnergy = Math.abs((total - lastTotalEnergy));
        if (Math.abs(total) > 1.0e-9) {
            deltaEnergy /= Math.abs(total);
        }

        lastKineticEnergy = newKineticEnergy;
        lastPotentialEnergy = potentialEnergy;
        lastTotalEnergy = lastKineticEnergy + lastPotentialEnergy;

        totalTime += timeStep;
        sumERef += deltaEnergy;
        return deltaEnergy;
    }

    class DynState {

        double lastTimeStep;
        double lastDelta;
        double lastLastTotalEnergy;
        double lastTotalTime;
        double lastSumDeltaSq;
        double lastMaxDelta;
        double lastSumERef;

        DynState() {
            lastTimeStep = timeStep;
            lastDelta = deltaEnergy;
            lastLastTotalEnergy = lastTotalEnergy;
            lastTotalTime = totalTime;
            lastSumDeltaSq = sumDeltaSq;
            lastMaxDelta = sumMaxDelta;
            lastSumERef = sumERef;
        }

        void restore() {
            deltaEnergy = lastDelta;
            lastTotalEnergy = lastLastTotalEnergy;
            totalTime = lastTotalTime;
            sumDeltaSq = lastSumDeltaSq;
            sumMaxDelta = lastMaxDelta;
            sumERef = lastSumERef;
        }
    }

    private void saveState() {
        dihedrals.getDihedrals();
        saveVelocities2();
        dynState = new DynState();
    }

    private void restoreState() {
        dihedrals.putDihedrals();
        molecule.genCoordsFastVec3D(null);
        restoreVelocities2();
        dynState.restore();
        // need to do this to get gradients before calling step2.  Could save/restore gradients
        dihedrals.eDeriv();
    }

    public void run() throws MissingCoordinatesException {
        run(1.0);
    }

    public void run(double runFraction) throws MissingCoordinatesException {
        calcRotInertia2();
        sumDeltaSq = 0.0;
        sumMaxDelta = 0.0;
        sumERef = 0.0;
        double lastTime = totalTime;
        int lastSteps = 0;
        int reportAt = nSteps / 20;
        double eScale = 1.0;
        int stepsToRun = (int) (runFraction * nSteps);
        if ((currentStep + stepsToRun) > nSteps) {
            stepsToRun = nSteps - currentStep;
        }

        dihedrals.getDihedrals();
        molecule.genCoordsFastVec3D(null);
        dihedrals.energyList.makeAtomListFast();
        updateVelocitiesRecursive();
        EnergyDeriv eDeriv = dihedrals.eDeriv();
        lastPotentialEnergy = eDeriv.getEnergy();
        lastKineticEnergy = calcKineticEnergy();
        currentTemp = calcTemp2(lastKineticEnergy);
        lastTotalEnergy = lastKineticEnergy + lastPotentialEnergy;
        deltaEnergy = 0.0;

        firstRun = false;
        SuperMol superMol = new SuperMol(molecule);

        System.out.printf("RDYN %6s %10s %8s %8s %8s %8s %10s %10s %8s %8s %8s\n",
                "step", "time", "temp", "kinE", "potE", "totE", "deltaE",
                "timeStep", "rmsAngle", "maxAngle", "contacts");
        System.out.printf("RDYN %6d %10.3f %8.1f %8.1f %8.1f %8.1f %10.6f %10.6f %8.3f %8.3f %8d\n", currentStep, totalTime, currentTemp, lastKineticEnergy * eScale, lastPotentialEnergy * eScale, lastTotalEnergy * eScale, deltaEnergy, 0.0, 0.0, 0.0, molecule.getEnergyCoords().getNContacts());
        for (int iStep = 0; iStep < stepsToRun; iStep++) {
            if (((iStep + 1) % dihedrals.energyList.getUpdateAt()) == 0) {
                dihedrals.energyList.makeAtomListFast();
            }
            if ((dihedrals.energyList.getSwap() != 0) && (((iStep + 1) % dihedrals.energyList.getSwap()) == 0)) {
                molecule.getEnergyCoords().doSwaps();
            }
            lastSteps++;
            saveState();

            step2(currentStep);
            double currentTimeStep = timeStep;
            int nTries = 0;
            while (deltaEnergy > 0.1) {
                restoreState();
                timeStep /= 2.0;
                step2(currentStep);
                nTries++;
                if (nTries > 3) {
                    break;
                }
            }
            timeStep = currentTimeStep;
            //if ((((iStep + 1) % reportAt) == 0) || (deltaEnergy > 0.01)) {
            if (((iStep + 1) % reportAt) == 0) {
                if ((progressUpdater != null) || (trajectoryWriter != null)) {
                    molecule.updateFromVecCoords();

                    if (trajectoryWriter != null) {
                        trajectoryWriter.writeStructure();
                    }
                    if (progressUpdater != null) {
                        progressUpdater.updateStatus(String.format("Step: %6d Energy: %7.1f", currentStep, lastTotalEnergy));
                    }
                }
                double rms = 180.0 * (sumDeltaSq / lastSteps) / Math.PI;
                double maxDelta = 180.0 * (sumMaxDelta / lastSteps) / Math.PI;
                double avgStep = (totalTime - lastTime) / lastSteps;
                System.out.printf("RDYN %6d %10.3f %8.1f %8.1f %8.1f %8.1f %10.6f %10.6f %8.3f %8.3f %8d\n",
                        currentStep, totalTime, currentTemp,
                        lastKineticEnergy * eScale, lastPotentialEnergy * eScale,
                        lastTotalEnergy * eScale, sumERef / lastSteps, avgStep,
                        rms, maxDelta, molecule.getEnergyCoords().getNContacts());
                sumDeltaSq = 0.0;
                sumMaxDelta = 0.0;
                sumERef = 0.0;
                lastTime = totalTime;
                lastSteps = 0;
            }
            currentStep++;
        }
        molecule.updateFromVecCoords();
    }

}
