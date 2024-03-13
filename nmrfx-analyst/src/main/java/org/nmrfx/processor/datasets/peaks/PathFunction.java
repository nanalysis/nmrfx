package org.nmrfx.processor.datasets.peaks;

import java.util.function.BiFunction;

public abstract class PathFunction implements BiFunction<double[], double[][], Double> {
     int nPaths;

    abstract double ligCon(double kd1, double kd2, double x, double p);

    public abstract double[] getGuess(double[] x, double[] y, int[] indices);

    public abstract double[][] getSimValues(double[] pars, double first, double last, int n, double p);
}
