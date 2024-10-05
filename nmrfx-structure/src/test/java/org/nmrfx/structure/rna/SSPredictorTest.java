package org.nmrfx.structure.rna;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SSPredictorTest {

    SSPredictor.Extent getExtent(int r, int c, int n, double p) {
        List<Integer> rows = new ArrayList<>();
        List<Integer> columns = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (int i=0;i<n;i++) {
            rows.add(r+i);
            columns.add(c-i);
            values.add(p);
        }
        return new SSPredictor.Extent(rows, columns, values);
    }
    @Test
    public void testOverlap() {
        SSPredictor.Extent extent1 = getExtent(8, 78, 5, 1.0);
        SSPredictor.Extent extent2 = getExtent(11, 70, 3, 1.0);
        boolean overlap1 = extent1.overlaps(extent2);
        boolean overlap2 = extent2.overlaps(extent1);
        Assert.assertTrue(overlap1);
        Assert.assertTrue(overlap2);
    }
    @Test
    public void testOverlap2() {
        SSPredictor.Extent extent1 = getExtent(14, 40, 4, 1.0);
        SSPredictor.Extent extent2 = getExtent(40, 86, 4, 1.0);
        boolean overlap1 = extent1.overlaps(extent2);
        boolean overlap2 = extent2.overlaps(extent1);
        Assert.assertTrue(overlap1);
        Assert.assertTrue(overlap2);
    }
}