/*
 * NMRFx Processor : A Program for Processing NMR Data 
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

 /*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.math;

import com.google.common.collect.UnmodifiableIterator;
import java.util.Arrays;
import org.renjin.sexp.AtomicVector;
import org.renjin.sexp.DoubleVector;
import org.renjin.sexp.AttributeMap;
import org.renjin.sexp.IntVector;
import org.renjin.sexp.Vector;
import org.renjin.sexp.Logical;
import java.util.Iterator;
import org.renjin.eval.Profiler;
import org.renjin.sexp.SEXP;

/**
 * A class for wrapping a Vec object so that it can be used 
 * as an Renjin DoubleVector
 *
 * @author michael
 */
public class RVec extends DoubleVector implements Iterable<Double> {

    public static final String TYPE_NAME = "double";
    public static final Type VECTOR_TYPE = new DoubleType();

    //   protected AttributeMap attributes;
    Vec vec;

    /*
     * Create a new named RVec object with the specified size and complex mode.
     *
     * @param name Name of the vector. Used for retrieving vectors by name.
     * @param size Size of vector.
     * @param complex true if the data stored in vector is Complex
     */
    public RVec() {
        super(AttributeMap.EMPTY);

    }

    public RVec(double[] values, int size, AttributeMap attributes) {
        super(attributes);
        this.vec = new Vec(size);
        this.vec.rvec = values;

    }

    public RVec(Vec vec) {
        super(AttributeMap.EMPTY);
        this.vec = vec;
    }

    public RVec(Vec vec, AttributeMap attributes) {
        super(attributes);
        this.vec = vec;
    }

    public RVec(AttributeMap attributes) {
        super(attributes);
    }

    @Override
    public double getElementAsDouble(int index) {
        return vec.getReal(index);
    }

    @Override
    public org.apache.commons.math.complex.Complex getElementAsComplex(int i) {
        return new org.apache.commons.math.complex.Complex(vec.getReal(i), vec.getImag(i));
    }

    @Override
    public double[] toDoubleArray() {
        double[] d = new double[length()];
        for (int i = 0; i != d.length; ++i) {
            d[i] = vec.getReal(i);
        }
        return d;
    }

    @Override
    public int length() {
        return vec.getSize();
    }

    @Override
    public boolean isNumeric() {
        return true;
    }

    @Override
    public int indexOfNA() {
        for (int i = 0, len = length(); i < len; i++) {
            if (DoubleVector.isNA(vec.getReal(i))) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int indexOf(AtomicVector vector, int vectorIndex, int startIndex) {
        if (!vec.isComplex()) {
            double value = vector.getElementAsDouble(vectorIndex);
            for (int i = startIndex; i < length(); ++i) {
                if (value == vec.getReal(i)) {
                    return i;
                }
            }
            return -1;

        } else {
            org.apache.commons.math.complex.Complex value = vector.getElementAsComplex(vectorIndex);
            for (int i = startIndex; i < length(); ++i) {
                if (getElementAsComplex(i).equals(value)) {
                    return i;
                }
            }
            return -1;
        }
    }

    @Override
    public int compare(int index1, int index2) {
        if (!vec.isComplex()) {
            return Double.compare(getElementAsDouble(index1), getElementAsDouble(index2));
        } else {
            throw new UnsupportedOperationException("implement me");

        }
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public Iterator<Double> iterator() {
        return new UnmodifiableIterator<Double>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index != length();
            }

            @Override
            public Double next() {
                return getElementAsDouble(index++);
            }
        };
    }

    @Override
    public int getElementAsInt(int index) {
        return (int) getElementAsDouble(index);
    }

    @Override
    public String getElementAsString(int index) {
        return String.valueOf(getElementAsDouble(index));
    }

    @Override
    public Logical getElementAsLogical(int index) {
        return Logical.valueOf(getElementAsRawLogical(index));
    }

    @Override
    public int getElementAsRawLogical(int index) {
        double value = getElementAsDouble(index);
        if (value == 0) {
            return 0;
        } else if (DoubleVector.isNA(value)) {
            return IntVector.NA;
        } else {
            return 1;
        }
    }

    @Override
    public boolean isElementTrue(int index) {
        return getElementAsRawLogical(index) == 1;
    }

    @Override
    public byte getElementAsByte(int index) {
        int value = getElementAsInt(index);
        if (value < 0 || value > 255) {
            return 0;
        } else {
            return (byte) value;
        }
    }

    @Override
    protected SEXP cloneWithNewAttributes(AttributeMap attributes) {
        RVec clone = new RVec(attributes);
        clone.vec = vec;
        return clone;
    }

    @Override
    public boolean isConstantAccessTime() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public static class Builder extends AbstractAtomicBuilder {

        private static final int MIN_INITIAL_CAPACITY = 50;
        private double values[];
        private int size;

        public Builder(int initialSize, int initialCapacity) {
            if (initialCapacity < MIN_INITIAL_CAPACITY) {
                initialCapacity = MIN_INITIAL_CAPACITY;
            }
            if (initialSize > initialCapacity) {
                initialCapacity = initialSize;
            }
            values = new double[initialCapacity];
            size = initialSize;
            Arrays.fill(values, NA);
        }

        public Builder() {
            this(0, MIN_INITIAL_CAPACITY);
        }

        public Builder(int initialSize) {
            this(initialSize, initialSize);
        }

        public static Builder withInitialSize(int size) {
            return new Builder(size, size);
        }

        public static Builder withInitialCapacity(int capacity) {
            return new Builder(0, capacity);
        }

        public Builder(DoubleVector exp) {
            this.values = exp.toDoubleArray();
            this.size = this.values.length;

            copyAttributesFrom(exp);
        }

        public Builder set(int index, double value) {
            ensureCapacity(index + 1);
            if (index + 1 > size) {
                size = index + 1;
            }
            values[index] = value;
            return this;
        }

        public Builder add(double value) {
            return set(size, value);
        }

        @Override
        public Builder add(Number value) {
            return add(value.doubleValue());
        }

        @Override
        public Builder setNA(int index) {
            return set(index, Double.longBitsToDouble(NA_BITS));
        }

        @Override
        public Builder setFrom(int destinationIndex, Vector source, int sourceIndex) {
            if (source.isElementNA(sourceIndex)) {
                return setNA(destinationIndex);
            } else {
                return set(destinationIndex, source.getElementAsDouble(sourceIndex));
            }
        }

        public Builder set(int index, Double value) {
            return set(index, (double) value);
        }

        @Override
        public int length() {
            return size;
        }

        public void ensureCapacity(int minCapacity) {
            int oldCapacity = values.length;
            if (minCapacity > oldCapacity) {
                double oldData[] = values;
                int newCapacity = (oldCapacity * 3) / 2 + 1;
                if (newCapacity < minCapacity) {
                    newCapacity = minCapacity;
                }
                // minCapacity is usually close to size, so this is a win:
                values = Arrays.copyOf(oldData, newCapacity);
                Arrays.fill(values, oldCapacity, values.length, NA);
            }
        }

        @Override
        public RVec build() {
            if (values.length == size) {
                if (Profiler.ENABLED) {
                    Profiler.memoryAllocated(Double.SIZE, values.length);
                }

                // Do not make an extra copy of the array
                // fixme this RVec version is copying data
                RVec vector = new RVec(buildAttributes());
                vector.vec = new Vec(values);
                values = null; // will trigger an error if the caller attempts subsequent modification
                return vector;
            } else {
                return new RVec(values, size, buildAttributes());
            }
        }
    }

}
