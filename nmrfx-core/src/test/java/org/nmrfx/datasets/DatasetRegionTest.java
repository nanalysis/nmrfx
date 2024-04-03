package org.nmrfx.datasets;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class DatasetRegionTest {

    @Test
    public void testEqualityContainingNullX() {
        DatasetRegion r1 = new DatasetRegion();
        DatasetRegion r2 = new DatasetRegion();
        assertNotEquals("If either or both objects have x=null they are not equal", r1, r2);
    }

    @Test
    public void testEqualityDifferentStartingX() {
        DatasetRegion r1 = new DatasetRegion(4, 6);
        DatasetRegion r2 = new DatasetRegion(3, 5);
        assertNotEquals("If x[0] is not equal, the objects are not equal", r1, r2);
        assertNotEquals("if x[0] is not equal, the hashcodes are not equal", r1.hashCode(), r2.hashCode());
    }

    @Test
    public void testEqualitySameStartingX() {
        DatasetRegion r1 = new DatasetRegion(4, 6);
        DatasetRegion r2 = new DatasetRegion(4, 5);
        assertEquals("If x[0] is equal, the objects are equal", r1, r2);
        assertEquals("if x[0] is equal, their hashCodes are equal", r1.hashCode(), r2.hashCode());
    }

    @Test
    public void testEqualityOneNull() {
        DatasetRegion r1 = new DatasetRegion(4, 6);
        DatasetRegion r2 = null;
        assertNotEquals(r1, r2);
    }

}
