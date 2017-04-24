/*
 * NMRFx Processor : A Program for Processing NMR Data 
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

 /*
 * Contour.java
 *
 * Created on December 15, 2002, 6:13 PM
 */
package org.nmrfx.processor.gui.spectra;

/**
 *
 * @author johnbruc
 */
public class Contour extends java.lang.Object {

    private static final float scaleFac = 256.0f;
    /**
     * Creates a new instance of Contour
     */
//    GeneralPath gPath = new GeneralPath(GeneralPath.WIND_NON_ZERO, 256);
    private final int[] linesCount;
    public short[][] coords = null;
    public double xOffset = 0;
    public double yOffset = 0;
    float[][] pt = new float[2][2];
    float[][] z = null;
    float rampEnd = 4;
    float plateauEnd = 8;

    public Contour() {
        pt[0][0] = 0;
        pt[0][1] = 180;
        pt[1][0] = 0;
        pt[1][1] = 180;
        linesCount = new int[1];
    }

    public Contour(final int nLevels) {
        pt[0][0] = 0;
        pt[0][1] = 180;
        pt[1][0] = 0;
        pt[1][1] = 180;
        coords = new short[nLevels][65536 * 2];
        linesCount = new int[nLevels];
    }

    public static float getScaleFac() {
        return scaleFac;
    }

    public void setPoints(float x1, float y1, float x2, float y2) {
        pt[0][0] = x1;
        pt[0][1] = x2;
        pt[1][0] = y1;
        pt[1][1] = y2;
    }

//    public void setRamp(float ramp) {
//        rampEnd = ramp;
//        plateauEnd = 2.0f * rampEnd;
//    }
//
//    public void genSimulation() {
//        int nRows = 180;
//        int nColumns = 180;
//        float cx = 45.0f;
//        float cy = 100.f;
//        float w = 10.0f;
//        z = new float[nRows][nColumns];
//
//        float[] levels = new float[8];
//        levels[0] = 0.0f;
//
//        for (int k = 1; k < levels.length; k++) {
//            levels[k] = levels[k - 1] * 1.5f;
//        }
//
//        for (int i = 0; i < nRows; i++) {
//            for (int j = 0; j < nColumns; j++) {
//                float delta = (float) Math.sqrt(((i - cx) * (i - cx))
//                        + ((j - cy) * (j - cy)));
//                z[j][i] = (float) (Math.exp((-delta * delta) / w) * 10.0f);
//            }
//        }
//
//        linesCount[0] = 0;
//        contour(levels, z);
//    }
//
//    public void renderSegment(Graphics2D g2) {
//        float scale = scaleFac / Short.MAX_VALUE;
//        gPath.reset();
//
//        for (int iLine = 0; iLine < linesCount[0]; iLine += 4) {
//            gPath.moveTo((coords[0][iLine] * scale) + (float) xOffset,
//                    (coords[0][iLine + 1] * scale) + (float) yOffset);
//            gPath.lineTo((coords[0][iLine + 2] * scale) + (float) xOffset,
//                    (coords[0][iLine + 3] * scale) + (float) yOffset);
//
//            //System.out.println((coords[0][iLine] * scale + xOffset)+" "+(coords[0][iLine + 1] * scale + (float) yOffset));
//            // System.out.println((coords[0][iLine + 2] * scale + xOffset)+" "+(coords[0][iLine + 3] * scale + (float) yOffset));
//        }
//
//        AffineTransform at = new AffineTransform();
//
//
//        if ((pt[0][1] != pt[0][0]) && (pt[1][1] != pt[1][0])) {
//            double sx = ((double) chart.corner[0][1] - chart.corner[0][0]) / (pt[0][1]
//                    - pt[0][0]);
//            double sy = ((double) chart.corner[1][1] - chart.corner[1][0]) / (pt[1][1]
//                    - pt[1][0]);
//            at.translate(chart.corner[0][0], chart.corner[1][1]);
//            at.scale(sx, sy);
//            at.translate((double) -pt[0][0], (double) -pt[1][1]);
//
//            //at.translate(0.0, 0.0);
//            g2.draw(at.createTransformedShape(gPath));
//
//            //g2.draw(gPath);
//        }
//    }
//
//    public void renderHeat(float[][] z, Graphics2D g2) {
//        gPath.reset();
//
//        int ny = z.length;
//        int nx = z[0].length;
//        Rectangle2D.Float rect = new Rectangle2D.Float();
//        AffineTransform at = new AffineTransform();
//
//        if ((pt[0][1] != pt[0][0]) && (pt[1][1] != pt[1][0])) {
//            double sx = ((double) chart.corner[0][1] - chart.corner[0][0]) / (pt[0][1]
//                    - pt[0][0]);
//            double sy = ((double) chart.corner[1][1] - chart.corner[1][0]) / (pt[1][1]
//                    - pt[1][0]);
//            at.translate(chart.corner[0][0], chart.corner[1][1]);
//            at.scale(sx, sy);
//            at.translate((double) -pt[0][0], (double) -pt[1][1]);
//        } else {
//            return;
//        }
//
//        float zf;
//
//        // fixme preallocate a range of colors
//        Color color = null;
//
//        for (int j = 0; j < ny; j++) {
//            for (int i = 0; i < nx; i++) {
//                float zval = z[j][i];
//
//                if (zval < rampEnd) {
//                    zf = (float) (zval / rampEnd);
//                    color = new Color(zf, zf, 1.0f - zf);
//                } else if (zval < plateauEnd) {
//                    zf = (float) ((zval - rampEnd) / (plateauEnd - rampEnd));
//                    color = new Color(1.0f, 1.0f - zf, 0.0f);
//                } else {
//                    color = Color.white;
//                }
//
//                rect.setFrameFromDiagonal((i + (float) xOffset) - 0.5f,
//                        (j + (float) yOffset) - 0.5f, i + (float) xOffset + 0.5f,
//                        j + (float) yOffset + 0.5f);
//
//                //at.translate(0.0, 0.0);
//                g2.setColor(color);
//                g2.draw(at.createTransformedShape(rect));
//                g2.fill(at.createTransformedShape(rect));
//
//                //g2.draw(gPath);
//            }
//        }
//    }
    public synchronized boolean contour(float[] levels, float[][] z) {
        int npass;
        float[] x1;
        float[] y1;
        float atemp;
        float btemp;
        float a;
        float clevel;
        int[] ii1 = {0, 1, 1, 0};
        int[] ii2 = {1, 1, 0, 0};
        int[] jj1 = {0, 0, 1, 1};
        int[] jj2 = {0, 1, 1, 0};
        float scale = Short.MAX_VALUE / scaleFac;

        x1 = new float[4];
        y1 = new float[4];

        int ny = z.length;
        int nx = z[0].length;
        int coordLevel;
        for (int l = 0; l < linesCount.length; l++) {
            linesCount[l] = 0;
        }
        if (coords == null) {
            coords = new short[1][65536 * 2];
        }
        for (int j = 0; j < (ny - 1); j++) {
            for (int i = 0; i < (nx - 1); i++) {
                for (int level = 0; level < levels.length; level++) {
                    if (coords.length != 1) {
                        coordLevel = level;
                    } else {
                        coordLevel = 0;
                    }
                    int lineCount = linesCount[coordLevel];
                    clevel = (float) levels[level];

                    //Nv_SetColor(colrs[level + 1]);
                    if ((lineCount + 8) >= coords[coordLevel].length) {
                        System.out.println("Contour region too dense: "
                                + lineCount + " lines");

                        return true;
                    }

                    npass = 0;

                    if ((z[j][i] < clevel) && (z[j + 1][i] < clevel)
                            && (z[j + 1][i + 1] < clevel)
                            && (z[j][i + 1] < clevel)) {
                        break;
                    }

                    for (int k = 0; k < 4; k++) {
                        int i1 = i + ii1[k];
                        int i2 = i + ii2[k];
                        int j1 = j + jj1[k];
                        int j2 = j + jj2[k];

                        atemp = z[j1][i1] - clevel;
                        btemp = z[j2][i2] - clevel;

                        if (((atemp < 0.0) && (btemp >= 0.0))
                                || ((atemp > 0.0) && (btemp <= 0.0))) {
                            a = z[j2][i2] - z[j1][i1];

                            if (a != 0) {
                                a = (clevel - z[j1][i1]) / a;
                            }

                            x1[npass] = (a * (float) (i2 - i1)) + (float) i1;
                            y1[npass] = (a * (float) (j2 - j1)) + (float) j1;
                            npass++;
                        }
                    }

                    if (npass == 2) {
                        coords[coordLevel][lineCount++] = (short) (x1[0] * scale);
                        coords[coordLevel][lineCount++] = (short) (y1[0] * scale);
                        coords[coordLevel][lineCount++] = (short) (x1[1] * scale);
                        coords[coordLevel][lineCount++] = (short) (y1[1] * scale);
                    }

                    if (npass == 4) {
                        int i1 = i + ii1[3];
                        int i2 = i + ii2[3];
                        int j1 = j + jj1[3];
                        int j2 = j + jj2[3];
                        if (z[j1][i1] > z[j2][i1]) {  // fixme should second i1 actually be i2
                            coords[coordLevel][lineCount++] = (short) (x1[0] * scale);
                            coords[coordLevel][lineCount++] = (short) (y1[0] * scale);
                            coords[coordLevel][lineCount++] = (short) (x1[1] * scale);
                            coords[coordLevel][lineCount++] = (short) (y1[1] * scale);
                            coords[coordLevel][lineCount++] = (short) (x1[2] * scale);
                            coords[coordLevel][lineCount++] = (short) (y1[2] * scale);
                            coords[coordLevel][lineCount++] = (short) (x1[3] * scale);
                            coords[coordLevel][lineCount++] = (short) (y1[3] * scale);
                        } else {
                            coords[coordLevel][lineCount++] = (short) (x1[1] * scale);
                            coords[coordLevel][lineCount++] = (short) (y1[1] * scale);
                            coords[coordLevel][lineCount++] = (short) (x1[2] * scale);
                            coords[coordLevel][lineCount++] = (short) (y1[2] * scale);
                            coords[coordLevel][lineCount++] = (short) (x1[3] * scale);
                            coords[coordLevel][lineCount++] = (short) (y1[3] * scale);
                            coords[coordLevel][lineCount++] = (short) (x1[0] * scale);
                            coords[coordLevel][lineCount++] = (short) (y1[0] * scale);
                        }
                    }
                    linesCount[coordLevel] = lineCount;
                }
            }
        }

        return false;
    }

    public int getLineCount() {
        return this.linesCount[0];
    }

    public int getLineCount(final int coordLevel) {
        return this.linesCount[coordLevel];
    }

    public void setLineCount(final int lineCount) {
        for (int i = 0; i < linesCount.length; i++) {
            linesCount[i] = lineCount;
        }
    }
}
