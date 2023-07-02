/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.predict;

import org.apache.commons.math3.geometry.enclosing.EnclosingBall;
import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.structure.rdc.AlignmentCalc;

/**
 * @author brucejohnson
 */
public class AlignmentTest {

    @Test
    public void testVectors() {
        int n = 100;
        AlignmentCalc aCalc = new AlignmentCalc();
        int result = aCalc.genAngles(n, 1, 1.0);
        Assert.assertEquals((double) n, (double) result, 2);
    }

    @Test
    public void testVectors2() {
        int n = 1000;
        AlignmentCalc aCalc = new AlignmentCalc();
        int result = aCalc.genAngles(n, 1, 1.0);
        aCalc.genVectors();
        double[] minMax = aCalc.minMax();
        System.out.println(minMax[0] + " " + minMax[1]);
        Assert.assertEquals(minMax[0], minMax[1], 0.05);
    }

    @Test
    public void testVectors3() {
        int n = 1000;
        AlignmentCalc aCalc = new AlignmentCalc();
        int result = aCalc.genAngles(n, 1, 1.0);
        aCalc.genVectors();
        aCalc.makeEnclosingSphere();
        EnclosingBall ball = aCalc.getBall();
        System.out.println("ball radius " + ball.getRadius());
        Assert.assertEquals(ball.getRadius(), 1.0, 1.0e-3);
    }

}
