package org.nmrfx.processor.processing;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Objects;

public record ProcessingSection(int order, int[] dimensions, String name) {
    public int nDims() {
        return dimensions == null ? -1 : dimensions.length;
    }

    public boolean isRef() {
        return dimensions == null;
    }

    public boolean isDataset() {
        return nDims() == 0;
    }
    public boolean isMatrix() {
        return nDims() > 1;
    }

    public boolean is1D() {
        return nDims() == 1;
    }

    public int getFirstDimension() {
        return dimensions[0];
    }

    public String dimString() {
        if (dimensions == null) {
            return "";
        } else {
            StringBuilder stringBuilder = new StringBuilder();
            for (int i=0;i<dimensions.length;i++) {
                if (i > 0) {
                    stringBuilder.append(",");
                }
                stringBuilder.append(dimensions[i] + 1);
            }
            return stringBuilder.toString();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ProcessingSection that = (ProcessingSection) o;

        if (order != that.order) return false;
        if (!Arrays.equals(dimensions, that.dimensions)) return false;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        int result = order;
        result = 31 * result + Arrays.hashCode(dimensions);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(name).append(" ").append(order);
        if (dimensions != null) {
            for (int dimension:dimensions) {
                stringBuilder.append(" ");
                stringBuilder.append(dimension);
            }
        }
        return stringBuilder.toString();
    }
}
