/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.math;

import org.renjin.eval.Context;
import org.renjin.eval.EvalException;
import org.renjin.sexp.AtomicVector;
import org.renjin.sexp.AttributeMap;
import org.renjin.sexp.DoubleVector;
import static org.renjin.sexp.DoubleVector.NA;
import org.renjin.sexp.IntVector;
import org.renjin.sexp.Logical;
import org.renjin.sexp.SEXP;
import org.renjin.sexp.SexpVisitor;
import org.renjin.sexp.StringVector;
import org.renjin.sexp.Symbol;
import org.renjin.sexp.Vector;

/**
 *
 * @author Bruce Johnson
 */
public interface RAtomicVector extends AtomicVector {

    @Override
    public default int getElementAsInt(int index) {
        return (int) getElementAsDouble(index);
    }

    @Override
    public default String getElementAsString(int index) {
        return String.valueOf(getElementAsDouble(index));
    }

    @Override
    public default Logical getElementAsLogical(int index) {
        return Logical.valueOf(getElementAsRawLogical(index));
    }

    @Override
    public default int getElementAsRawLogical(int index) {
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
    public default boolean isElementTrue(int index) {
        return getElementAsRawLogical(index) == 1;
    }

    @Override
    public default byte getElementAsByte(int index) {
        int value = getElementAsInt(index);
        if (value < 0 || value > 255) {
            return 0;
        } else {
            return (byte) value;
        }
    }

    @Override
    public default int indexOf(Vector vector, int vectorIndex, int startIndex) {
        if (vector instanceof AtomicVector) {
            return indexOf((AtomicVector) vector, vectorIndex, startIndex);
        } else {
            SEXP element = vector.getElementAsSEXP(vectorIndex);
            if (element instanceof AtomicVector && element.length() == 1) {
                return indexOf((AtomicVector) element, 0, startIndex);
            } else {
                return -1;
            }
        }
    }

    @Override
    public default boolean contains(Vector vector, int i) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public default Builder newBuilderWithInitialSize(int index) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public default Builder newBuilderWithInitialCapacity(int index) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public default Type getVectorType() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public default boolean isWiderThan(Vector vector) {
        return getVectorType().isWiderThan(vector.getVectorType());
    }

    @Override
    public default Builder newCopyBuilder() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public default Builder newCopyBuilder(Type type) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public default boolean isElementNA(int index) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public default boolean isElementNaN(int index) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public default boolean isConstantAccessTime() {
        return true;
    }

    @Override
    public default boolean isDeferred() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public default Double getElementAsObject(int index) {
        // fixme what if Complex
        return getElementAsDouble(index);
    }

    @Override
    public default int getComputationDepth() {
        return 0;
    }

    @Override
    public default boolean hasAttributes() {
        return getAttributes() != AttributeMap.EMPTY;
    }

    @Override
    public default AttributeMap getAttributes() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public default String getTypeName() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public default void accept(SexpVisitor sv) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public default boolean isNumeric() {
        return true;
    }

    @Override
    public default Logical asLogical() {
        return getElementAsLogical(0);
    }

    @Override
    public default double asReal() {
        if (length() == 0) {
            return NA;
        } else {
            return getElementAsDouble(0);
        }
    }

    @Override
    public default boolean isObject() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public default StringVector getS3Class() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public default String getImplicitClass() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public default boolean inherits(String string) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public default boolean hasNames() {
        return getNames() instanceof StringVector;
    }

    @Override
    public default String getName(int index) {
        if (hasNames()) {
            return getNames().getElementAsString(index);
        }
        return StringVector.NA;
    }

    @Override
    public default int getIndexByName(String string) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public default SEXP getAttribute(Symbol symbol) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public default SEXP setAttribute(String attributeName, SEXP value) {
        return setAttribute(Symbol.get(attributeName), value);
    }

    @Override
    public default SEXP setAttribute(Symbol attributeName, SEXP value) {
        AttributeMap attributes = getAttributes();
        return setAttributes(attributes.copy().set(attributeName, value));
    }

    @Override
    public default SEXP setAttributes(AttributeMap attributes) {
        return cloneWithNewAttributes(attributes);
    }

    @Override
    public default SEXP setAttributes(AttributeMap.Builder attributes) {
        return cloneWithNewAttributes(attributes.validateAndBuildForVectorOfLength(length()));
    }

    default SEXP cloneWithNewAttributes(AttributeMap attributes) {
        if (attributes != AttributeMap.EMPTY) {
            throw new EvalException("cannot change/set attributes on " + getClass().getName());
        }
        return this;
    }

    @Override
    public default <S extends SEXP> S getElementAsSEXP(int index) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public default SEXP force(Context cntxt) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public default boolean containsNA() {
        return indexOfNA() != -1;
    }

    @Override
    public default boolean contains(AtomicVector vector, int vectorIndex) {
        if (vector instanceof AtomicVector) {
            return contains((AtomicVector) vector, vectorIndex);
        } else {
            return false;
        }
    }

    @Override
    public default int[] toIntArray() {
        int[] array = new int[length()];
        for (int i = 0; i != array.length; ++i) {
            array[i] = getElementAsInt(i);
        }
        return array;
    }

    @Override
    public default AtomicVector getNames() {
        AttributeMap attributes = getAttributes();
        if (attributes.getDim().length() == 1) {
            return attributes.getDimNames(0);
        } else {
            return attributes.getNamesOrNull();
        }
    }

}
