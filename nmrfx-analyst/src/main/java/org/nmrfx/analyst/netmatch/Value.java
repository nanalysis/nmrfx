package org.nmrfx.analyst.netmatch;

/**
 *
 * @author brucejohnson
 */
abstract class Value {

    private final int index;
    final Double[] values;
    final Double[] tvalues;

    Value(int index, Double[] values, Double[] tvalues) {
        this.index = index;
        this.values = values;
        this.tvalues = tvalues;
    }
    public int getIndex() {
        return index;
    }
}
