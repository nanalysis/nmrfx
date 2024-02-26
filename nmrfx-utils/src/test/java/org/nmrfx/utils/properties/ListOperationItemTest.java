package org.nmrfx.utils.properties;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import static org.junit.Assert.*;

public class ListOperationItemTest {
    private ListOperationItemTypeSelector typeSelector;
    private ChangeListener<String> listListener;
    private int changeListenerCallCount;

    @Before
    public void init() {
        typeSelector = null;
        changeListenerCallCount = 0;
        listListener = new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String c1, String c2) {
                changeListenerCallCount++;
            }
        };

    }

    @Test
    public void testConstructorWithNullDefault() {
        ListOperationItem lstItem = new ListOperationItem(null, listListener, null, "COADD", "coef", "List of coefficients to scale each vector by.", typeSelector);
        assertTrue(lstItem.defaultValue instanceof ArrayList);
        assertTrue(lstItem.value instanceof ArrayList);
        assertTrue(lstItem.defaultValue.isEmpty());
        assertTrue(lstItem.value.isEmpty());
    }

    @Test
    public void testConstructorWithArrayListString() {
        ArrayList<String> defaultList = new ArrayList<>(List.of("one", "two", "three", "four"));
        ListOperationItem lstItem = new ListOperationItem(null, listListener, defaultList, "COADD", "coef", "List of coefficients to scale each vector by.", typeSelector);
        assertTrue(lstItem.defaultValue instanceof ArrayList);
        assertTrue(lstItem.value instanceof ArrayList);
        assertEquals(lstItem.value, lstItem.defaultValue);
        assertEquals(lstItem.value, List.of("one", "two", "three", "four"));
    }

    @Test
    public void testConstructorWithArrayListNumber() {
        ArrayList<Double> defaultList = new ArrayList<>(List.of(1.0, 2.0, 3.0, 4.0));
        ListOperationItem lstItem = new ListOperationItem(null, listListener, defaultList, "COADD", "coef", "List of coefficients to scale each vector by.", typeSelector);
        assertTrue(lstItem.defaultValue instanceof ArrayList);
        assertTrue(lstItem.value instanceof ArrayList);
        assertEquals(lstItem.value, lstItem.defaultValue);
        assertEquals(lstItem.value, List.of(1.0, 2.0, 3.0, 4.0));
    }

    @Test
    public void testConstructorWithVectorNumber() {
        Vector<Double> defaultList = new Vector<>(List.of(1.0, 2.0, 3.0, 4.0));
        ListOperationItem lstItem = new ListOperationItem(null, listListener, defaultList, "COADD", "coef", "List of coefficients to scale each vector by.", typeSelector);
        assertTrue(lstItem.defaultValue instanceof Vector);
        assertTrue(lstItem.value instanceof Vector);
        assertEquals(lstItem.value, lstItem.defaultValue);
        assertEquals(lstItem.value, List.of(1.0, 2.0, 3.0, 4.0));
    }

    @Test
    public void testGetValueArrayListNumber() {
        ArrayList<Double> defaultList = new ArrayList<>(List.of(1.0, 2.0, 3.0, 4.0));
        ListOperationItem lstItem = new ListOperationItem(null, listListener, defaultList, "COADD", "coef", "List of coefficients to scale each vector by.", typeSelector);
        String value = lstItem.getValue();
        assertEquals("[1.0, 2.0, 3.0, 4.0]", value);
    }

    @Test
    public void testGetValueVectorNumber() {
        Vector<Double> defaultList = new Vector<>(List.of(1.0, 2.0, 3.0, 4.0));
        ListOperationItem lstItem = new ListOperationItem(null, listListener, defaultList, "COADD", "coef", "List of coefficients to scale each vector by.", typeSelector);
        String value = lstItem.getValue();
        assertEquals("[1.0, 2.0, 3.0, 4.0]", value);
    }

    @Test
    public void testSetValueString() {
        ArrayList<Double> defaultList = new ArrayList<>(List.of(1.0, 2.0, 3.0, 4.0));
        ListOperationItem lstItem = new ListOperationItem(null, listListener, defaultList, "COADD", "coef", "List of coefficients to scale each vector by.", typeSelector);
        String newValue = "[5.0, 6.0, 7.0, 8.0]";
        lstItem.setValue(newValue);
        assertEquals(List.of(5.0, 6.0, 7.0, 8.0), lstItem.value);
        assertNotEquals(lstItem.value, lstItem.defaultValue);
        assertEquals("Listener is updated if the values have changed", 1, changeListenerCallCount);
    }

    @Test
    public void testSetValueArrayList() {
        ArrayList<Double> defaultList = new ArrayList<>(List.of(1.0, 2.0, 3.0, 4.0));
        ListOperationItem lstItem = new ListOperationItem(null, listListener, defaultList, "COADD", "coef", "List of coefficients to scale each vector by.", typeSelector);
        ArrayList<Double> newValue = new ArrayList<>(List.of(5.0, 6.0, 7.0, 8.0));
        lstItem.setValue(newValue);
        assertEquals(List.of(5.0, 6.0, 7.0, 8.0), lstItem.value);
        assertNotEquals(lstItem.value, lstItem.defaultValue);
        assertEquals("Listener is updated if the values have changed", 1, changeListenerCallCount);
    }

    @Test
    public void testSetValueArrayListSameValues() {
        ArrayList<Double> defaultList = new ArrayList<>(List.of(1.0, 2.0, 3.0, 4.0));
        ListOperationItem lstItem = new ListOperationItem(null, listListener, defaultList, "COADD", "coef", "List of coefficients to scale each vector by.", typeSelector);
        ArrayList<Double> newValue = new ArrayList<>(List.of(1.0, 2.0, 3.0, 4.0));
        lstItem.setValue(newValue);
        assertEquals(List.of(1.0, 2.0, 3.0, 4.0), lstItem.value);
        assertEquals(lstItem.value, lstItem.defaultValue);
        assertEquals("Listener is not updated if the values have not changed", 0, changeListenerCallCount);
    }

    @Test
    public void testSetValueIllegalValue() {
        ArrayList<Double> defaultList = new ArrayList<>(List.of(1.0, 2.0, 3.0, 4.0));
        ListOperationItem lstItem = new ListOperationItem(null, listListener, defaultList, "COADD", "coef", "List of coefficients to scale each vector by.", typeSelector);
        Double newValue = 10.0;
        lstItem.setValue(newValue);
        // value will not be updated
        assertEquals(List.of(1.0, 2.0, 3.0, 4.0), lstItem.value);
        assertEquals(lstItem.value, lstItem.defaultValue);
        assertEquals("Listener is not updated if the values have not changed", 0, changeListenerCallCount);
    }

    @Test
    public void testGetStringRepEmptyList() {
        ListOperationItem lstItem = new ListOperationItem(null, listListener, null, "COADD", "coef", "List of coefficients to scale each vector by.", typeSelector);
        String listAsString = lstItem.getStringRep();
        assertEquals("[]", listAsString);
    }

    @Test
    public void testGetStringRepArrayListNumber() {
        ArrayList<Double> defaultList = new ArrayList<>(List.of(1.0, 2.0, 3.0, 4.0));
        ListOperationItem lstItem = new ListOperationItem(null, listListener, defaultList, "COADD", "coef", "List of coefficients to scale each vector by.", typeSelector);
        String listAsString = lstItem.getStringRep();
        assertEquals("[1.0,2.0,3.0,4.0]", listAsString);
    }

    @Test
    public void testGetStringRepArrayListString() {
        ArrayList<String> defaultList = new ArrayList<>(List.of("one", "two", "three", "four"));
        ListOperationItem lstItem = new ListOperationItem(null, listListener, defaultList, "COADD", "coef", "List of coefficients to scale each vector by.", typeSelector);
        String listAsString = lstItem.getStringRep();
        assertEquals("[]", listAsString);
    }

    @Test
    public void testGetArrayListNumber() {
        ArrayList<Double> defaultList = new ArrayList<>(List.of(1.0, 2.0, 3.0, 4.0));
        ListOperationItem lstItem = new ListOperationItem(null, listListener, defaultList, "COADD", "coef", "List of coefficients to scale each vector by.", typeSelector);
        String listAsString = lstItem.get();
        assertEquals("1.0,2.0,3.0,4.0", listAsString);
    }

    @Test
    public void testGetArrayListString() {
        ArrayList<String> defaultList = new ArrayList<>(List.of("one", "two", "three", "four"));
        ListOperationItem lstItem = new ListOperationItem(null, listListener, defaultList, "COADD", "coef", "List of coefficients to scale each vector by.", typeSelector);
        String listAsString = lstItem.get();
        assertEquals("", listAsString);
    }
}
