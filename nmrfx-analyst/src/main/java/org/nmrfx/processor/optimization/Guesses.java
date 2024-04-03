package org.nmrfx.processor.optimization;

import java.util.Arrays;

record Guesses(double[] start, double[] lower, double[] upper) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Guesses guesses = (Guesses) o;
        return Arrays.equals(start, guesses.start) && Arrays.equals(lower, guesses.lower) && Arrays.equals(upper, guesses.upper);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(start);
        result = 31 * result + Arrays.hashCode(lower);
        result = 31 * result + Arrays.hashCode(upper);
        return result;
    }

    @Override
    public String toString() {
        return "Guesses{" +
                "start=" + Arrays.toString(start) +
                "\nlower=" + Arrays.toString(lower) +
                "\nupper=" + Arrays.toString(upper) +
                '}';
    }
}
