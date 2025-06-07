package org.nmrfx.processor.math;

import java.util.Arrays;

public class BooleanMatrixND {
    private boolean[] data;
    private int[] sizes;
    private int[] strides;
    final int nDim;
    private int nElems;

    public BooleanMatrixND(int... sizes) {
        this.sizes = sizes.clone();
        this.strides = calcStrides(sizes);
        nDim = sizes.length;
        int n = 1;
        for (int i = 0; i < nDim; i++) {
            n *= sizes[i];
        }
        nElems = n;
        data = new boolean[n];
    }


    public BooleanMatrixND(BooleanMatrixND source) {
        this(source.sizes);
        System.arraycopy(source.data, 0, data, 0, data.length);
    }

    public final int getOffset(int... index) {
        int offset = 0;
        for (int i = 0; i < strides.length; i++) {
            offset += index[i] * strides[i];
        }
        return offset;
    }

    private static int[] calcStrides(int[] shape) {
        int[] strides = new int[shape.length];
        int stride = 1;
        for (int i = shape.length - 1; i >= 0; i--) {
            strides[i] = stride;
            stride *= shape[i];
        }
        return strides;
    }

    ArrayIndexOutOfBoundsException getOffsetException(int offset, int[] indices) {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append("Out of bounds for offset ").append(offset);
        int i = 0;

        for (int index : indices) {
            sBuilder.append(" ").append(index);
            sBuilder.append(" ").append(sizes[i++]);
        }
        return new ArrayIndexOutOfBoundsException(sBuilder.toString());
    }


    public void setValue(boolean value, int... indices) {
        int offset = getOffset(indices);
        try {
            data[offset] = value;
        } catch (ArrayIndexOutOfBoundsException aE) {
            throw getOffsetException(offset, indices);
        }
    }

    public boolean getValue(int... indices) {
        int offset = getOffset(indices);
        return data[offset];
    }

    public void set(boolean value) {
        Arrays.fill(data, value);
    }
}
