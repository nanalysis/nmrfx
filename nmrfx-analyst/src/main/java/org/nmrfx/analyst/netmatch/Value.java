package org.nmrfx.analyst.netmatch;

/**
 *
 * @author brucejohnson
 */
abstract class Value {

    private final int index;
    final double[] values;
    final double[] tvalues;

    Value(int index, double[] values, double[] tvalues) {
        this.index = index;
        this.values = values;
        this.tvalues = tvalues;
    }
    public int getIndex() {
        return index;
    }
}
