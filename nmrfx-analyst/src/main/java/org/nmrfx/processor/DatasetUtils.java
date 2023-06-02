package org.nmrfx.processor;

public class DatasetUtils {

    private DatasetUtils() {
    }

    /**
     * Converts real/complex indices into raw indices and returns a new array.
     *
     * @param pt        The real/complex indices to convert.
     * @param isComplex Whether pt contains complex indices.
     * @return A new array of raw indices.
     */
    public static int[][] generateRawIndices(int[][] pt, boolean isComplex) {
        int[][] ptRaw = new int[pt.length][2];
        for (int i = 0; i < pt.length; i++) {
            if (i == 0) {
                if (isComplex) {
                    ptRaw[i][0] = pt[i][0] * 2;
                    ptRaw[i][1] = (pt[i][1] * 2) + 1;
                } else {
                    ptRaw[i][0] = pt[i][0];
                    ptRaw[i][1] = pt[i][1];
                }
            } else {
                ptRaw[i][0] = pt[i][0];
                ptRaw[i][1] = pt[i][1];
            }
        }
        return ptRaw;
    }
}
