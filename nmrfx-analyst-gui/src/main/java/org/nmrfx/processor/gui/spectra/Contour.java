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

import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.graphicsio.GraphicsIOException;

/**
 * @author johnbruc
 */
public class Contour extends java.lang.Object {

    GraphicsContextInterface g2;
    final double[][] pix;
    final double[][] pts;
    final double scaleX;
    final double scaleY;
    double lineWidth = 0.5;
    Color color = Color.BLACK;

    private static final float scaleFac = 256.0f;
    /**
     * Creates a new instance of Contour
     */
    private final int[] linesCount;
    public short[][] coords = null;
    public double xOffset = 0;
    public double yOffset = 0;
    float[][] z = null;
    float rampEnd = 4;
    float plateauEnd = 8;
    int[] offsets = {
            0, 1, 6, 2, 11, 5, 7, 3, 12, 13, 10, 14, 8, 9, 4, 15
    };
    int[][] cells;

    public Contour(double[][] pts, double[][] pix) {
        this.pts = new double[2][2];
        this.pts[0] = pts[0].clone();
        this.pts[1] = pts[1].clone();
        this.pix = new double[2][2];
        this.pix[0] = pix[0].clone();
        this.pix[1] = pix[1].clone();
        linesCount = new int[1];
        scaleX = (pix[0][1] - pix[0][0]) / (pts[0][1] - pts[0][0]);
        scaleY = (pix[1][1] - pix[1][0]) / (pts[1][1] - pts[1][0]);
    }

    public void setAttributes(double lineWidth, Color color) {
        this.lineWidth = lineWidth;
        this.color = color;
    }

    public static float getScaleFac() {
        return scaleFac;
    }

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

    public synchronized boolean marchSquares(float level, float[][] data, int[][] cells) {
        boolean result = false;
        z = data;
        int ny = z.length;
        int nx = z[0].length;
        this.cells = cells;
        for (int i = 0; i < ny; i++) {
            for (int j = 0; j < nx; j++) {
                cells[i][j] = 0;
            }
        }
        boolean lastI = false;
        double edgeValue = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < ny; i++) {
            lastI = i == ny - 1;
            for (int j = 0; j < nx; j++) {
                boolean lastJ = j == nx - 1;
                final double corner00 = z[i][j];
                final double corner10 = lastI ? edgeValue : z[i + 1][j];
                final double corner01 = lastJ ? edgeValue : z[i][j + 1];
                final double corner11 = lastJ || lastI ? edgeValue : z[i + 1][j + 1];

                int cellStatus = 0;
                if (corner00 > level) {
                    cellStatus += 1;
                }
                if (corner01 > level) {
                    cellStatus += 2;
                }
                if (corner11 > level) {
                    cellStatus += 4;
                }
                if (corner10 > level) {
                    cellStatus += 8;
                }
                boolean flipped = false;
                int cellValue = 0;
                if ((cellStatus != 0) && (cellStatus != 15)) {
                    result = false;
                    if (cellStatus == 5 || cellStatus == 10) {
                        double centerAvg = (corner10 + corner11 + corner01 + corner00) / 4;
                        if (cellStatus == 5 && centerAvg < level) {
                            flipped = true;
                        } else if (cellStatus == 10 && centerAvg < level) {
                            flipped = true;
                        }
                    }
                    int offset = offsets[cellStatus];
                    if (flipped) {
                        offset += 16;
                    }
                    double f0 = 0.0;
                    double f1 = 0.0;
                    if (((cellStatus & 9) == 1) || ((cellStatus & 9) == 8)) {
                        f0 = (level - corner00) / (corner10 - corner00);
                    }
                    if (((cellStatus & 3) == 1) || ((cellStatus & 3) == 2)) {
                        f1 = (level - corner00) / (corner01 - corner00);

                    }

                    int edge0 = (int) Math.round(255 * f0);
                    int edge1 = (int) Math.round(255 * f1);
                    cellValue |= (edge1 << 16);
                    cellValue |= (edge0 << 8);
                    cellValue |= offset;
                }
                cells[i][j] = cellValue;
            }
        }
        return result;
    }

    /*                      0
    1:lb 0 1  1
    2:br  1 2  6
    3:lr  0 2  2
    4:rt    2 3  11
    5:ltrb  0 3 2 1  5: 3 9
    6:bt   1 3   7
    7:lt  0 3   3
    8:tl  3 0  12
    9:tb  3 1  13
    10:bltr  1 0 3 2  4 14
    11:tr  3 2  14
    12:rl  2 0  8
    13:rb   2 1  9
    14:bl 1 0  4
    15
    0,1,6,2,11,5,7,3,12,13,10,14,8,9,4,15

    0:3 1:4 2:9 3:14
    
     */
    public void drawSquares(GraphicsContextInterface g2) throws GraphicsIOException {
        g2.setGlobalAlpha(1.0);
        g2.setLineCap(StrokeLineCap.BUTT);
        g2.setEffect(null);
        g2.setLineWidth(lineWidth);
        g2.setStroke(color);

        this.g2 = g2;
        int ny = cells.length;
        int nx = cells[0].length;
        final double[] x = new double[4];
        final double[] y = new double[4];
        int[] nextX = {-1, 0, 1, 0};
        int[] nextY = {0, -1, 0, 1};
        for (int iy = 0; iy < ny - 1; iy++) {
            for (int ix = 0; ix < nx - 1; ix++) {
                // skip over already drawn cells
                if ((cells[iy][ix] & 32) == 32) {
                    continue;
                }
                int offset = cells[iy][ix] & 255;
                // skip over empty cells and saddle cells
                // we'll hit saddle cells when looping from cell to cell
                if ((offset != 0) && (offset != 15) && (offset != 5) && (offset != 10)) {
                    int cX = ix;
                    int cY = iy;
                    boolean start = true;
                    int nCells = 0;
                    int lastSide = 0;
                    while (true) {
                        if ((cells[cY][cX] & 32) == 32) {
                            if (nCells > 0) {
                                g2.stroke();
                            }
                            break;
                        }
                        offset = cells[cY][cX] & 255;
                        int nextSide = drawCell(lastSide, offset, cX, cY, start);
                        lastSide = nextSide;
                        nCells++;
                        start = false;
                        cX += nextX[nextSide];
                        cY += nextY[nextSide];
                        if ((cX < 0) || (cX >= (nx - 1)) || (cY < 0) || (cY >= (ny - 1))) {
                            g2.stroke();
                            break;
                        }
                        if ((cX == ix) && (cY == iy)) {
                            g2.closePath();
                            g2.stroke();
                            break;
                        }
                    }

                }
            }
        }
    }

    int drawCell(int lastSide, int offset, int ix, int iy, boolean start) throws GraphicsIOException {
        final double[] x = new double[4];
        final double[] y = new double[4];
        boolean flipped = (offset & 16) == 16;
        offset = offset & 15;
        //    0:3 1:4 2:9 3:14

        int[] saddleSides = {3, 4, 9, 14};
        if ((offset == 5) || (offset == 10)) {
            offset = saddleSides[lastSide];
        }

        int edge0y = (cells[iy][ix] >> 8) & 255;
        int edge1x = ((cells[iy][ix] >> 16) & 255);
        int edge2y = (cells[iy][ix + 1] >> 8) & 255;
        int edge3x = (cells[iy + 1][ix] >> 16) & 255;
        x[0] = ix;
        y[0] = edge0y / 255.0 + iy;
        x[1] = edge1x / 255.0 + ix;
        y[1] = iy;
        x[2] = ix + 1;
        y[2] = edge2y / 255.0 + iy;
        x[3] = edge3x / 255.0 + ix;
        y[3] = iy + 1;

        int nextSide = 0;
        int i0 = (offset >> 2) & 3;
        int i1 = offset & 3;
        if (start) {
            double xp1 = (x[i0] + xOffset) * scaleX + pix[0][0];
            double yp1 = (y[i0] + yOffset) * scaleY + pix[1][0];
            if (xp1 < pix[0][0]) {
                xp1 = pix[0][0];
            } else if (xp1 > pix[0][1]) {
                xp1 = pix[0][1];
            }
            if (yp1 > pix[1][0]) {
                yp1 = pix[1][0];
            } else if (yp1 < pix[1][1]) {
                yp1 = pix[1][1];
            }
            g2.beginPath();
            g2.moveTo(xp1, yp1);
        }
        double xp2 = (x[i1] + xOffset) * scaleX + pix[0][0];
        double yp2 = (y[i1] + yOffset) * scaleY + pix[1][0];
        if (xp2 < pix[0][0]) {
            xp2 = pix[0][0];
        } else if (xp2 > pix[0][1]) {
            xp2 = pix[0][1];

        }
        if (yp2 > pix[1][0]) {
            yp2 = pix[1][0];
        } else if (yp2 < pix[1][1]) {
            yp2 = pix[1][1];
        }

        g2.lineTo(xp2, yp2);
        nextSide = i1;
        cells[iy][ix] |= 32;

        return nextSide;

    }

    int drawCell(int offset, int ix, int iy, boolean start) throws GraphicsIOException {
        final double[] x = new double[4];
        final double[] y = new double[4];
        boolean flipped = (offset & 16) == 16;
        offset = offset & 15;
        offsets[0] = offset;
        int nOffsets = 1;
        if (offset == 5) {
            nOffsets = 2;
            if (flipped) {
                offsets[0] = 4;
                offsets[1] = 14;
            } else {
                offsets[0] = 3;
                offsets[1] = 9;
            }
        } else if (offset == 10) {
            nOffsets = 2;
            if (flipped) {
                offsets[0] = 3;
                offsets[1] = 9;
            } else {
                offsets[0] = 4;
                offsets[1] = 14;
            }
        }
        int edge0y = (cells[iy][ix] >> 8) & 255;
        int edge1x = ((cells[iy][ix] >> 16) & 255);
        int edge2y = (cells[iy][ix + 1] >> 8) & 255;
        int edge3x = (cells[iy + 1][ix] >> 16) & 255;
        x[0] = ix;
        y[0] = edge0y / 255.0 + iy;
        x[1] = edge1x / 255.0 + ix;
        y[1] = iy;
        x[2] = ix + 1;
        y[2] = edge2y / 255.0 + iy;
        x[3] = edge3x / 255.0 + ix;
        y[3] = iy + 1;
        int nextSide = 0;
        for (int iOff = 0; iOff < nOffsets; iOff++) {
            offset = offsets[iOff];
            int i0 = (offset >> 2) & 3;
            int i1 = offset & 3;
            if (start) {
                double xp1 = (x[i0] + xOffset - pts[0][0])
                        / (pts[0][1] - pts[0][0]) * (pix[0][1] - pix[0][0]) + pix[0][0];
                double yp1 = (y[i0] + yOffset - pts[1][0])
                        / (pts[1][1] - pts[1][0]) * (pix[1][1] - pix[1][0]) + pix[1][0];
                g2.beginPath();
                g2.moveTo(xp1, yp1);
            }
            double xp2 = (x[i1] + xOffset - pts[0][0])
                    / (pts[0][1] - pts[0][0]) * (pix[0][1] - pix[0][0]) + pix[0][0];
            double yp2 = (y[i1] + yOffset - pts[1][0])
                    / (pts[1][1] - pts[1][0]) * (pix[1][1] - pix[1][0]) + pix[1][0];
            g2.lineTo(xp2, yp2);
            nextSide = i1;
            cells[iy][ix] |= 32;
        }
        return nextSide;

    }

}
