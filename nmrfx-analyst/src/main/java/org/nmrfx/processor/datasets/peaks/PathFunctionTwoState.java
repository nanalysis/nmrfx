package org.nmrfx.processor.datasets.peaks;

import org.nmrfx.processor.optimization.FitUtils;

class PathFunctionTwoState extends PathFunction {

    public PathFunctionTwoState(int nPaths) {
        this.nPaths = nPaths;
    }

    @Override
    double ligCon(double kd1, double kd2, double x, double p) {
        double a = 2.0 * p - x + kd1 + kd2;
        double b = (p - x) * (kd1 + kd2) + kd1 * kd2;
        double c = -kd1 * kd2 * x;
        double a3b = a * a - 3.0 * b;
        double theta = Math.acos((-2.0 * a * a * a + 9.0 * a * b - 27.0 * c) / (2.0 * Math.sqrt(a3b * a3b * a3b)));
        return -(a / 3.0) + (2.0 / 3.0) * Math.sqrt(a3b) * Math.cos(theta / 3.0);
    }

    double yCalc(double kd1, double kd2, double dB1, double dB2, double x, double p) {

        double lig = ligCon(kd1, kd2, x, p);

        double kd1Tkd2 = kd1 * kd2;
        double kd1Pkd2 = kd1 + kd2;
        double denom = ((kd1Tkd2) + kd1Pkd2 * lig + lig * lig);

        double fb1 = kd2 * lig / denom;

        double fb2 = kd1 * lig / denom;

        double fb12 = lig * lig / denom;

        return (fb1 + fb12) * dB1 + (fb2 + fb12) * dB2;

    }

    @Override
    public Double apply(double[] pars, double[][] values) {
        double kd1 = pars[0];
        double kd2 = pars[1];
        double sum = 0.0;
        int n = values[0].length;
        for (int i = 0; i < n; i++) {
            int iOff = (int) Math.round(values[2][i]);
            double dB1 = pars[2 + iOff * 2];
            double dB2 = pars[3 + iOff * 2];
            double x = values[0][i];
            double p = values[1][i];
            double y = values[3][i];
            double yCalc = yCalc(kd1, kd2, dB1, dB2, x, p);

            double delta = yCalc - y;
            sum += delta * delta;
        }
        if (kd2 < kd1) {
            sum *= 10.0;
        }
        return Math.sqrt(sum / n);
    }

    @Override
    public double[] getGuess(double[] x, double[] y, int[] indices) {
        int nPars = 2 + 2 * nPaths;

        double[] result = new double[nPars];
        for (int iPath = 0; iPath < nPaths; iPath++) {
            double yMax = FitUtils.getMaxValue(y, indices, iPath);
            double xMid = FitUtils.getMidY0(x, y, indices, iPath);
            result[0] += xMid / 2.0;
            result[1] += xMid * 1.5;
            result[2 + iPath * 2] = yMax / 2.0;
            result[3 + iPath * 2] = yMax / 2.0;
        }
        result[0] /= nPaths;
        result[1] /= nPaths;
        return result;
    }

    @Override
    public double[][] getSimValues(double[] pars, double first, double last, int n, double p) {
        double kd1 = pars[0];
        double kd2 = pars[1];
        double dB1 = pars[2];
        double dB2 = pars[3];

        double[][] result = new double[2][n];
        double delta = (last - first) / (n - 1);
        for (int i = 0; i < n; i++) {
            double x = first + delta * i;
            double y = yCalc(kd1, kd2, dB1, dB2, x, p);
            result[0][i] = x;
            result[1][i] = y;
        }
        return result;
    }

}
