package org.nmrfx.processor.gui.spectra;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SpecRegionTest {

    @Test
    public void testEqualityContainingNullX() {
        SpecRegion r1 = new SpecRegion();
        SpecRegion r2 = new SpecRegion();
        assertNotEquals("If either or both objects have x=null they are not equal", r1, r2);
    }

    @Test
    public void testEqualityDifferentStartingX() {
        SpecRegion r1 = new SpecRegion(4, 6);
        SpecRegion r2 = new SpecRegion(3, 5);
        assertNotEquals("If x[0] is not equal, the objects are not equal", r1, r2);
        assertNotEquals("if x[0] is not equal, the hashcodes are not equal", r1.hashCode(), r2.hashCode());
    }

    @Test
    public void testEqualitySameStartingX() {
        SpecRegion r1 = new SpecRegion(4, 6);
        SpecRegion r2 = new SpecRegion(4, 5);
        assertEquals("If x[0] is equal, the objects are equal", r1, r2);
        assertEquals("if x[0] is equal, their hashCodes are equal", r1.hashCode(), r2.hashCode());
    }

    @Test
    public void testEqualityOneNull() {
        SpecRegion r1 = new SpecRegion(4, 6);
        SpecRegion r2 = null;
        assertNotEquals(r1, r2);
    }
}
