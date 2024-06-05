package org.nmrfx.processor.datasets.peaks;

import org.nmrfx.processor.optimization.FitUtils;

class PathFunctionSingleState extends PathFunction {

    private final boolean fit0;
    private final boolean fitLog ;
    public PathFunctionSingleState(boolean fit0, boolean fitLog, int nPaths) {
        this.fit0 =fit0;
        this.fitLog = fitLog;
        this.nPaths = nPaths;

    }

    double yCalc(double a, double b, double c, double x, double p) {
        double dP = c - a;
        double kD = fitLog ? Math.pow(10.0, b) : b;
        double n1 = p + x + kD;
        double s1 = Math.sqrt(n1 * n1 - 4.0 * x * p);
        return a + dP * (n1 - s1) / (2.0 * p);

    }

    @Override
    double ligCon(double kd1, double kd2, double x, double p) {
        return 0;
    }

    @Override
    public Double apply(double[] pars, double[][] values) {
        double a = fit0 ? pars[0] : 0.0;
        double b = fit0 ? pars[1] : pars[0];
        double sum = 0.0;
        int n = values[0].length;
        for (int i = 0; i < n; i++) {
            int iOff = (int) Math.round(values[2][i]);
            double c = fit0 ? pars[2 + iOff] : pars[1 + iOff];
            double x = values[0][i];
            double p = values[1][i];
            double y = values[3][i];
            double yCalc = yCalc(a, b, c, x, p);

            double delta = yCalc - y;
            sum += delta * delta;

        }
        return Math.sqrt(sum / n);
    }

    @Override
    public double[] getGuess(double[] x, double[] y, int[] indices) {
        int nPars = 1 + nPaths;

        double[] result = new double[nPars];
        for (int iPath = 0; iPath < nPaths; iPath++) {
            double yMax = FitUtils.getMaxValue(y, indices, iPath);
            double xMid = FitUtils.getMidY0(x, y, indices, iPath);
            result[1 + iPath] = yMax;
            result[0] += fitLog ? Math.log10(xMid) : xMid;
        }
        result[0] /= nPaths;
        return result;
    }

    public double[][] getSimValues(double[] pars, double first, double last, int n, double p) {
        double a = fit0 ? pars[0] : 0.0;
        double b = fit0 ? pars[1] : pars[0];
        double c = fit0 ? pars[2] : pars[1];

        double[][] result = new double[2][n];
        double delta = (last - first) / (n - 1);
        for (int i = 0; i < n; i++) {
            double x = first + delta * i;
            double y = yCalc(a, b, c, x, p);
            result[0][i] = x;
            result[1][i] = y;
        }
        return result;
    }

}
