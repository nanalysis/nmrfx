package org.nmrfx.chemistry;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AtomPropertyTest {

    @Test
    public void testNameNormalization() {
        assertEquals("H", AtomProperty.normalizeName("H"));
        assertEquals("H", AtomProperty.normalizeName("h"));
        assertEquals("Si", AtomProperty.normalizeName("Si"));
        assertEquals("Si", AtomProperty.normalizeName("SI"));
        assertEquals("Si", AtomProperty.normalizeName("si"));
    }

    @Test
    public void elementLookupIsCaseInsensitive() {
        assertEquals(AtomProperty.He, AtomProperty.get("He"));
        assertEquals(AtomProperty.He, AtomProperty.get("he"));
        assertEquals(AtomProperty.He, AtomProperty.get("HE"));
    }

    @Test
    public void invalidElementDefaultsToX() {
        assertEquals(AtomProperty.X, AtomProperty.get("This is nonsense"));
    }
}