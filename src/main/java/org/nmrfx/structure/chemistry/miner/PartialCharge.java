package org.nmrfx.structure.chemistry.miner;

import com.joptimizer.functions.ConvexMultivariateRealFunction;
import com.joptimizer.functions.LinearMultivariateRealFunction;
import com.joptimizer.functions.PDQuadraticMultivariateRealFunction;
import com.joptimizer.optimizers.JOptimizer;
import com.joptimizer.optimizers.OptimizationRequest;
import java.util.ArrayList;

public class PartialCharge {

    double[] alphas = {1.0, 1.74, 1.67, 0.86, 0.057};
    double beta = 1.378;
    double delta = 0.545;

    public double[] test() {

        double[][] P = new double[][]{{1., 0.4}, {0.4, 1.}};
        PDQuadraticMultivariateRealFunction objectiveFunction = new PDQuadraticMultivariateRealFunction(P, null, 0);

        //equalities
        double[][] A = new double[][]{{1, 1}};
        double[] b = new double[]{1};

        //inequalities
        ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[2];
        inequalities[0] = new LinearMultivariateRealFunction(new double[]{-1, 0}, 0);
        inequalities[1] = new LinearMultivariateRealFunction(new double[]{0, -1}, 0);

        //optimization problem
        OptimizationRequest or = new OptimizationRequest();
        or.setF0(objectiveFunction);
        or.setInitialPoint(new double[]{0.1, 0.9});
        //or.setFi(inequalities); //if you want x>0 and y>0
        or.setA(A);
        or.setB(b);
        or.setToleranceFeas(1.E-6);
        or.setTolerance(1.E-5);

        //optimization
        JOptimizer opt = new JOptimizer();
        opt.setOptimizationRequest(or);
        try {
            int returnCode = opt.optimize();
            double[] sol = opt.getOptimizationResponse().getSolution();
            return sol;
        } catch (Exception e) {
        }
        return null;
    }

    private double[] adjustElectronegativities(double[] electroNegativities, ArrayList<ArrayList<PCBond>> pcBonds) {
        double[] newElectronegativities = new double[electroNegativities.length];
        int nAtoms = electroNegativities.length;
        for (int i = 0; i < nAtoms; i++) {
            double elecI = electroNegativities[i];
            ArrayList<PCBond> pcBonds2 = pcBonds.get(i);
            double value = elecI;
            for (PCBond pcBond : pcBonds2) {
                double elecJ = electroNegativities[pcBond.partner];
                double diff = (elecI - elecJ);
                //System.out.println("elecI " + elecI + " elecJ " + elecJ + " diff " + diff + " " + pcBond.bondTypeIndex);
                if (elecI != elecJ) {
                    double s = diff / Math.abs(diff);
                    value += alphas[pcBond.bondTypeIndex] * s * Math.pow(Math.abs(diff), beta);
                }
            }
            newElectronegativities[i] = value;
            //System.out.println(electroNegativities[i] + " " + newElectronegativities[i]);
        }
        return newElectronegativities;
    }

    public double[] chargeH2O() {
        double[] electroNegativities = {27.4, 45.7, 27.4};
        double[] hardnesses = {73.9, 92.6, 73.9};
        double[] formalCharges = {0, 0, 0};
        int[] chargeGroups = {-1, -1, -1};
        ArrayList<ArrayList<PCBond>> pcBonds = new ArrayList<>();

        ArrayList<PCBond> pcBonds2 = new ArrayList<>();
        pcBonds2.add(new PCBond(1, 0));
        pcBonds.add(pcBonds2);

        pcBonds2 = new ArrayList<>();
        pcBonds2.add(new PCBond(0, 0));
        pcBonds2.add(new PCBond(2, 0));
        pcBonds.add(pcBonds2);

        pcBonds2 = new ArrayList<>();
        pcBonds2.add(new PCBond(1, 0));
        pcBonds.add(pcBonds2);



        return optimizeCharges(electroNegativities, hardnesses, formalCharges, chargeGroups, pcBonds,2.1);
    }

    public double[] optimizeCharges(double[] electroNegativities, final double[] hardnesses, final double[] formalCharges, final int[] chargeGroups, ArrayList<ArrayList<PCBond>> pcBonds, final double hardnessScale) {
        int nAtoms = electroNegativities.length;
        double[][] P = new double[nAtoms][nAtoms];
        double[] q = new double[nAtoms];
        double[][] A = new double[1][nAtoms];
        double[] b = {0.0};
        double[] initialCharges = new double[nAtoms];
        electroNegativities = adjustElectronegativities(electroNegativities, pcBonds);
        for (int i = 0; i < nAtoms; i++) {
            // Factor of 2.0 seems necessary to give agreement with vcharge paper
            //  presumably a difference in the calculation done by quadratic function here
            P[i][i] = hardnesses[i] * hardnessScale;
            q[i] = electroNegativities[i];
            A[0][i] = 1.0;
            initialCharges[i] = formalCharges[i];
        }
        int maxGroup = -1;
        for (int i = 0; i < nAtoms; i++) {
            if (chargeGroups[i] > maxGroup) {
                maxGroup = chargeGroups[i];
            }
        }
        int nGroups = maxGroup + 1;
        ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[nGroups * 2];
        for (int j = 0; j < nGroups; j++) {
            double groupCharge = 0.0;
            double[] constraintsLower = new double[nAtoms];
            double[] constraintsUpper = new double[nAtoms];
            for (int i = 0; i < nAtoms; i++) {
                if (chargeGroups[i] == j) {
                    groupCharge += formalCharges[i];
                    constraintsLower[i] = 1.0;
                    constraintsUpper[i] = 1.0;
                }
            }
            inequalities[j * 2] = new LinearMultivariateRealFunction(constraintsLower, groupCharge - delta);
            inequalities[j * 2 + 1] = new LinearMultivariateRealFunction(constraintsUpper, groupCharge + delta);
        }

        PDQuadraticMultivariateRealFunction objectiveFunction = new PDQuadraticMultivariateRealFunction(P, q, 0);

        //optimization problem
        OptimizationRequest or = new OptimizationRequest();
        or.setF0(objectiveFunction);
        or.setInitialPoint(initialCharges);
        //or.setFi(inequalities); //if you want x>0 and y>0
        or.setA(A);
        or.setB(b);
        or.setToleranceFeas(1.E-6);
        or.setTolerance(1.E-5);

        //optimization
        JOptimizer opt = new JOptimizer();
        opt.setOptimizationRequest(or);
        try {
            int returnCode = opt.optimize();
            double[] sol = opt.getOptimizationResponse().getSolution();
            double value = objectiveFunction.value(sol);
            //System.out.println(returnCode + " " + value);
            return sol;
        } catch (Exception e) {
            System.out.println("optimizer error " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
