package org.nmrfx.processor.gui;


import org.junit.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class ChartProcessorDimensionComparatorTest {
    @Test
    public void sort() {
        var sorted = Stream.of("D4", "D2", "D3", "D2,3,4", "D1", "D2,3")
                .sorted(new ChartProcessor.DimensionComparator())
                .toList();

        //This is different to the expected natural order: dimensions with comma ends up before their prefix:
        // "D2,3" < "D2"
        // This is necessary for NUS to reconstruct the missing data before processing the reconstructed dimensions
        assertEquals(List.of("D1", "D2,3", "D2,3,4", "D2", "D3", "D4"), sorted);
    }
}