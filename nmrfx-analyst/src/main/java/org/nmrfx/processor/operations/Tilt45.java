package org.nmrfx.processor.operations;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

import org.nmrfx.annotations.PythonAPI;

/**
 * @author Simon Hulse
 */
@PythonAPI("pyproc")
public class Tilt45 extends Operation {
    // Given a vector's index is n2, the required shift is computed
    // using round(m * n2 + c). See `shiftValue`'s assignment in `eval`.
    private final double m;
    private final double c;

    public Tilt45(double m, double c) {
        super();
        this.m = m;
        this.c = c;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        int index = vector.getIndex();
        if (!vector.isComplex()) {
            index = Math.floorDiv(index, 2);
        }
        int shiftValue = (int) Math.round(m * index + c);
        CShift.shift(vector, shiftValue, false);
        return this;
    }
}