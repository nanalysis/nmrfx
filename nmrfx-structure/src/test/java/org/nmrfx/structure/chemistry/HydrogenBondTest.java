package org.nmrfx.structure.chemistry;


import org.junit.Assert;
import org.junit.Test;

public class HydrogenBondTest {

    @Test
    public void hshiftTest() {
        double shift3 = HydrogenBond.getHShift(2.1, Math.PI, 7, 3.0);
        double shift3_1_7 = HydrogenBond.getHShift(1.7, Math.PI, 7, 3.0);
        Assert.assertTrue(shift3 < shift3_1_7);
    }
}