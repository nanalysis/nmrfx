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

import java.util.ArrayList;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optimization.GoalType;
import org.apache.commons.math3.optimization.PointValuePair;
import org.apache.commons.math3.optimization.direct.CMAESOptimizer;
import org.apache.commons.math3.util.FastMath;

public class LayoutMolecule implements MultivariateFunction {

    ArrayList<Connector> connectors = new ArrayList<Connector>();
    boolean[][] bondMap;
    double[] atomXY = new double[0];

    public class Connector {

        final int start;
        final int end;
        final double weight;
        final double distance;

        public Connector(final int iStart, final int iEnd, final double distance, final double weight) {
            this.start = iStart;
            this.end = iEnd;
            this.distance = distance;
            this.weight = weight;
        }
    }

    public void connect(final int iStart, final int iEnd, final double distance, final double weight) {
        Connector connector = new Connector(iStart, iEnd, distance, weight);
        connectors.add(connector);
        bondMap = null;
    }

    public void clearConnectors() {
        connectors.clear();
        bondMap = null;
    }

    public void start(final double[] atomXY) {
        int nAtoms = atomXY.length / 2;
        this.atomXY = atomXY;
        makeBondMap();
    }

    void makeBondMap() {
        int nAtoms = atomXY.length / 2;
        bondMap = new boolean[nAtoms][nAtoms];
        for (Connector connector : connectors) {
            int iStart = connector.start;
            int iEnd = connector.end;
            bondMap[iStart][iEnd] = true;
            bondMap[iEnd][iStart] = true;
        }
    }

    public PointValuePair optimize(final int nSteps) {
        PointValuePair value = optimize(nSteps, atomXY, 0.4, 50);
        atomXY = value.getPoint();
        return value;
    }

    public PointValuePair test() {
        double[] atomXY = {0.0, 0.0, 2.0, 2.0, 4.0, 4.0, 6.0, 6.0, 8.0, 8.0};
        connect(0, 4, 2, 3);
        PointValuePair value = optimize(1000, atomXY, 0.4, 10);
        return value;
    }

    public PointValuePair optimize(final int nSteps, final double[] start, final double sigma, final double boxRadius) {
        double[][] boundaries = new double[2][];
        boundaries[0] = new double[start.length];
        boundaries[1] = new double[start.length];
        double[] inputSigma = new double[start.length];
        for (int i = 0; i < boundaries[0].length; i++) {
            boundaries[0][i] = -boxRadius;
            boundaries[1][i] = boxRadius;
            inputSigma[i] = sigma;
        }
        int nAtoms = start.length / 2;
        if (bondMap == null) {
            makeBondMap();
        }
        CMAESOptimizer optimizer = new CMAESOptimizer(0, inputSigma);
        PointValuePair result = optimizer.optimize(nSteps, this, GoalType.MINIMIZE, start, boundaries[0], boundaries[1]);
        return result;
    }

    public double value(final double[] xy) {
        double minDis = 2.0;
        double repelWeight = 2.0;
        double energy = 0.0;
        if (bondMap == null) {
            makeBondMap();
        }
        for (Connector connector : connectors) {
            double x1 = xy[connector.start * 2];
            double y1 = xy[connector.start * 2 + 1];
            double x2 = xy[connector.end * 2];
            double y2 = xy[connector.end * 2 + 1];
            double distance = FastMath.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
            double delta = (connector.distance - distance);
            energy += delta * delta * connector.weight;
        }
        for (int i = 0; i < xy.length; i += 2) {
            double x1 = xy[i];
            double y1 = xy[i + 1];
            double eContrib = FastMath.sqrt(x1 * x1 + y1 * y1) * 0.001;
            energy += eContrib;
            for (int j = (i + 2); j < xy.length; j += 2) {
                if (bondMap[i / 2][j / 2]) {
                    continue;
                }
                double x2 = xy[j];
                double y2 = xy[j + 1];
                double distance = FastMath.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
                if (distance < minDis) {
                    double delta = (distance - minDis);
                    energy += delta * delta * delta * delta * repelWeight;
                }
            }
        }
        return energy;
    }

    public double[] getXY() {
        return atomXY;
    }

    public double energy() {
        double minDis = 10.0;
        double repelWeight = 2.0;
        double energy = 0.0;
        if (bondMap == null) {
            makeBondMap();
        }
        for (Connector connector : connectors) {
            double x1 = atomXY[connector.start * 2];
            double y1 = atomXY[connector.start * 2 + 1];
            double x2 = atomXY[connector.end * 2];
            double y2 = atomXY[connector.end * 2 + 1];
            double distance = FastMath.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
            double delta = (connector.distance - distance);
            double eContrib = delta * delta * connector.weight;
            System.out.println(connector.start + " " + connector.end + " " + distance + " " + delta + " " + eContrib);
            energy += eContrib;
        }
        for (int i = 0; i < atomXY.length; i += 2) {
            double x1 = atomXY[i];
            double y1 = atomXY[i + 1];
            double eContrib = FastMath.sqrt(x1 * x1 + y1 * y1) * 0.001;
            energy += eContrib;
            System.out.println(i + " " + x1 + " " + y1 + " " + eContrib);
            for (int j = (i + 2); j < atomXY.length; j += 2) {
                if (bondMap[i / 2][j / 2]) {
                    continue;
                }
                double x2 = atomXY[j];
                double y2 = atomXY[j + 1];
                double distance = FastMath.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
                if (distance < minDis) {
                    double delta = (distance - minDis);
                    energy += delta * delta * delta * delta * repelWeight;
                    eContrib = delta * delta * delta * delta * repelWeight;
                    System.out.println(i + " " + j + " " + distance + " " + delta + " " + eContrib);
                    energy += eContrib;
                }
            }
        }
        return energy;
    }
}
