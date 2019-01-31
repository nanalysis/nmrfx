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
package org.nmrfx.processor.datasets.vendor;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * This class implements the "JeolDelta" command in Vendor.
 */
public class JeolDelta {

    private final JeolDeltaAxis[] axes;
    private final RandomAccessFile raFile;
    private final int dataStart;
    private byte[] buffer = null;
    private Strip[] strips;
    private int subMatrixPointCount;
    private int sectionByteCount;
    static final Logger LOGGER = Logger.getLogger("org.nmrfx.processor.datasets.Dataset");

    class Strip {

        ByteBuffer byteBuffer;
        DoubleBuffer doubleBuffer;
        int start = -1;

        Strip() {
        }
    }

    public JeolDelta(final String fileName, final int dataStart, final JeolDeltaAxis axis1) throws IOException {
        File file = new File(fileName);
        if (!file.exists()) {
            throw new IOException("File " + fileName + " doesn't exist");
        }

        raFile = new RandomAccessFile(file, "r");
        this.dataStart = dataStart;
        axes = new JeolDeltaAxis[1];
        axes[0] = axis1;
        setup();
    }

    public void close() {
        try {
            raFile.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e.getMessage());
        }
    }

    public JeolDelta(final String fileName, final int dataStart, final JeolDeltaAxis axis1, final JeolDeltaAxis axis2) throws IOException {
        File file = new File(fileName);
        if (!file.exists()) {
            throw new IOException("File " + fileName + " doesn't exist");
        }

        raFile = new RandomAccessFile(file, "r");
        this.dataStart = dataStart;
        axes = new JeolDeltaAxis[2];
        axes[0] = axis1;
        axes[1] = axis2;
        setup();
    }

    private void setup() {
        int nSections = 1;
        sectionByteCount = 8;
        subMatrixPointCount = 1;
        for (JeolDeltaAxis axe : axes) {
            if (axe.nPoints != 0) {
                subMatrixPointCount *= axe.subMatrixEdge;
                sectionByteCount *= axe.nPoints;
            }
            nSections *= axe.type.getSectionCount();
        }
        strips = new Strip[nSections];
        for (int i = 0; i < nSections; i++) {
            strips[i] = new Strip();
        }

    }

    public void readBytes(byte[] dataBytes, long newPos, int length) {
        try {
            raFile.seek(newPos);
            raFile.read(dataBytes, 0, length);
        } catch (IOException e) {
            System.err.println("Unable to read from dataset.");
            System.err.println(e.getMessage());
        }
    }

    void getStrip(final int[] startPos, final int[] endPos, final int iDim, final int iSection) {
        int start = getPositionInFile(startPos, iSection);
        int end = getPositionInFile(endPos, iSection);
        int nBytes = start - end + 1;

        if ((buffer == null) || (buffer.length != nBytes)) {
            buffer = new byte[nBytes];
        }
        Strip strip = strips[iSection];
        if ((strip.byteBuffer == null) || (strip.byteBuffer.capacity() != nBytes)) {
            strip.byteBuffer = ByteBuffer.allocate(nBytes);
            strip.byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        readBytes(strip.byteBuffer.array(), start, nBytes);
        strip.doubleBuffer = strip.byteBuffer.asDoubleBuffer();
    }

    void getStrip(final int[] position, final int iSection) {
        int start = getSubmatrixStart(position);
        start = dataStart + sectionByteCount * iSection + 8 * start;
        Strip strip = strips[iSection];
        if (strip.start != start) {
            int nBytes = subMatrixPointCount * 8 * axes[0].nSubMatrices;
            if ((buffer == null) || (buffer.length != nBytes)) {
                buffer = new byte[nBytes];
            }
            if ((strip.byteBuffer == null) || (strip.byteBuffer.capacity() != nBytes)) {
                strip.byteBuffer = ByteBuffer.allocate(nBytes);
                strip.byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            }
            System.out.println(start + " " + nBytes);
            readBytes(strip.byteBuffer.array(), start, nBytes);
            strip.doubleBuffer = strip.byteBuffer.asDoubleBuffer();
            strip.start = start;
        }
    }

    public double[] getVector(final int iSection) {
        int[] position = new int[1];
        return getVector(position, 0, iSection);
    }

    public double[] getVector(final int iVec, final int iSection) {
        int[] position = new int[2];
        position[1] = iVec;
        return getVector(position, 0, iSection);
    }

    double[] getVector(final int[] position, final int iDim, final int iSection) {
        int n = axes[iDim].stop - axes[iDim].start + 1;
        double[] vector = new double[n];
        int j = 0;
        getStrip(position, iSection);
        for (int i = axes[iDim].start; i <= axes[iDim].stop; i++) {
            position[iDim] = i;
            int pos = getPositionInStrip(position);
            vector[j++] = strips[iSection].doubleBuffer.get(pos);
        }
        return vector;
    }

    int getPositionInStrip(final int[] position) {
        int nPositions = position.length;
        int pnt_off = 0;
        int sub_off = 0;
        for (int i = (nPositions - 1); i >= 1; i--) {
            int posi = position[i] + axes[i].start;
            pnt_off = (pnt_off + posi % axes[i].subMatrixEdge) * axes[i].subMatrixEdge;
        }
        int posi = position[0] + axes[0].start;
        pnt_off = pnt_off + posi % axes[0].subMatrixEdge;
        sub_off = sub_off + posi / axes[0].subMatrixEdge;
        int offset = sub_off * subMatrixPointCount + pnt_off;
        return offset;
    }

    int getPositionInSection(final int[] position) {
        int nPositions = position.length;
        int pnt_off = 0;
        int sub_off = 0;
        for (int i = (nPositions - 1); i >= 1; i--) {
            int posi = position[i] + axes[i].start;
            pnt_off = (pnt_off + posi % axes[i].subMatrixEdge) * axes[i].subMatrixEdge;
            sub_off = (sub_off + posi / axes[i].subMatrixEdge) * axes[i - 1].nSubMatrices;
        }
        int posi = position[0] + axes[0].start;
        pnt_off = pnt_off + posi % axes[0].subMatrixEdge;
        sub_off = sub_off + posi / axes[0].subMatrixEdge;
        int offset = sub_off * subMatrixPointCount + pnt_off;
        return offset;
    }

    int getSubmatrixStart(final int[] position) {
        int nPositions = position.length;
        int sub_off = 0;
        for (int i = (nPositions - 1); i >= 1; i--) {
            int posi = position[i] + axes[i].start;
            sub_off = (sub_off + posi / axes[i].subMatrixEdge) * axes[i - 1].nSubMatrices;
        }
        int posi = position[0] + axes[0].start;
        sub_off = sub_off + posi / axes[0].subMatrixEdge;
        int offset = sub_off * subMatrixPointCount;
        return offset;
    }

    int getPositionInFile(final int[] position, final int iSection) {
        int offset = getPositionInSection(position);
        offset = dataStart + sectionByteCount * iSection + 8 * offset;
        return offset;
    }
}
