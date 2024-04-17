package org.nmrfx.processor.optimization;

public class FitUtils {

    private FitUtils() {

    }

    public static double getYAtMaxX(double[] x, double[] y) {
        double maxVal = Double.NEGATIVE_INFINITY;
        double yValue = 0;
        for (int i = 0; i < x.length; i++) {
            if (x[i] > maxVal) {
                maxVal = x[i];
                yValue = y[i];
            }
        }
        return yValue;
    }

    public static double getYAtMaxX(double[] x, double[] y, int[] indices, int index) {
        double maxVal = Double.NEGATIVE_INFINITY;
        double yValue = 0;
        for (int i = 0; i < x.length; i++) {
            if ((indices[i] == index) && (x[i] > maxVal)) {
                maxVal = x[i];
                yValue = y[i];
            }
        }
        return yValue;
    }

    public static double getYAtMinX(double[] x, double[] y) {
        double minVal = Double.MAX_VALUE;
        double yValue = 0;
        for (int i = 0; i < x.length; i++) {
            if (x[i] < minVal) {
                minVal = x[i];
                yValue = y[i];
            }
        }
        return yValue;
    }

    public static double getYAtMinX(double[] x, double[] y, int[] indices, int index) {
        double minVal = Double.MAX_VALUE;
        double yValue = 0;
        for (int i = 0; i < x.length; i++) {
            if ((indices[i] == index) && (x[i] < minVal)) {
                minVal = x[i];
                yValue = y[i];
            }
        }
        return yValue;
    }

    public static double getMinValue(double[] v) {
        double minVal = Double.MAX_VALUE;
        for (double value : v) {
            if (value < minVal) {
                minVal = value;
            }
        }
        return minVal;
    }

    public static double getMinValue(double[] v, int[] indices, int index) {
        double minVal = Double.MAX_VALUE;
        for (int i = 0; i < v.length; i++) {
            if ((indices[i] == index) && (v[i] < minVal)) {
                minVal = v[i];
            }
        }
        return minVal;
    }

    public static double getMaxValue(double[] v) {
        double maxVal = Double.NEGATIVE_INFINITY;
        for (double value : v) {
            if (value > maxVal) {
                maxVal = value;
            }
        }
        return maxVal;
    }

    public static double getMaxValue(double[] v, int[] indices, int index) {
        double maxVal = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < v.length; i++) {
            if ((indices[i] == index) && (v[i] > maxVal)) {
                maxVal = v[i];
            }
        }
        return maxVal;
    }

    public static double getMidY0(double[] x, double[] y) {
        double yMax = getMaxValue(y);
        double yMin = getMinValue(y);
        double hh = (yMin + yMax) / 2.0;
        double deltaMin = Double.MAX_VALUE;
        double iMid = 0;

        for (int i = 0; i < x.length; i++) {
            double dvar = y[i];
            double ivar = x[i];
            double ddvar = Math.abs(dvar - hh);

            if (ddvar < deltaMin) {
                deltaMin = ddvar;
                iMid = ivar;
            }
        }
        return iMid;
    }

    public static double getMidY0(double[] x, double[] y, int[] indices, int index) {
        double yMax = getMaxValue(y);
        double yMin = getMinValue(y);
        double hh = (yMin + yMax) / 2.0;
        double deltaMin = Double.MAX_VALUE;
        double upY = Double.MAX_VALUE;
        double upX = 0.0;
        double downY = Double.MAX_VALUE;
        double downX = 0.0;

        for (int i = 0; i < x.length; i++) {
            if (indices[i] == index) {
                double dvar = y[i];
                double ivar = x[i];
                double ddvar = Math.abs(dvar - hh);
                if (dvar > hh) {
                    if (ddvar < Math.abs(upY - hh)) {
                        upX = ivar;
                        upY = dvar;
                    }
                }
                if (dvar < hh) {
                    if (ddvar < Math.abs(downY - hh)) {
                        downX = ivar;
                        downY = dvar;
                    }
                }
            }
        }
        double f = (hh - downY) / (upY - downY);
        double iMid = f * (upX - downX) + downX;

        return iMid;
    }

}
