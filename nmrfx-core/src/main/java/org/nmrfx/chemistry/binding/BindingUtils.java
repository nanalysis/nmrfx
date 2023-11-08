package org.nmrfx.chemistry.binding;

public class BindingUtils {
    private BindingUtils() {
    }
    public static double boundLigand(double targetTotal, double ligandTotal, double kD) {
        double b = ligandTotal + targetTotal + kD;
        double c = ligandTotal * targetTotal;
        return (b - Math.sqrt(b * b - 4.0 * c)) / 2.0;
    }
}
