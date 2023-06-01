/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.datasets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.nmrfx.datasets.DatasetBase.*;

/**
 * @author brucejohnson
 */
public class DatasetHeaderIO {

    private static final Logger log = LoggerFactory.getLogger(DatasetHeaderIO.class);

    /**
     * Read the header of an NMRView format dataset file into the fields of this
     * Dataset object.
     *
     * @return 0 if successful, 1 if there was an error
     */
    DatasetBase d;

    public DatasetHeaderIO(DatasetBase d) {
        this.d = d;
    }


    public final synchronized DatasetLayout readHeader(RandomAccessFile raFile) {
        int i;
        int nDim;
        int rdims;

        boolean checkSwap;

        byte[] buffer;
        buffer = new byte[NV_HEADER_SIZE];

        DataUtilities.readBytes(raFile, buffer, 0, NV_HEADER_SIZE);

        ByteArrayInputStream bis = new ByteArrayInputStream(buffer);
        DataInputStream dis;
        try {
            dis = new DataInputStream(bis);
            checkSwap = false;
            int magic = DataUtilities.readSwapInt(dis, checkSwap);

            if (magic != 874032077) {
                bis = new ByteArrayInputStream(buffer);
                dis = new DataInputStream(bis);
                checkSwap = true;

                magic = DataUtilities.readSwapInt(dis, checkSwap);

                if (magic != 874032077) {
                    log.warn("couldn't read header");

                    return null;
                }
            }
        } catch (IOException e) {
            log.warn(e.getMessage(), e);

            return null;
        }

        if (checkSwap) {
            d.setLittleEndian();
        } else {
            d.setBigEndian();
        }
        DatasetLayout lay = null;
        try {
            //read spare1
            DataUtilities.readSwapInt(dis, checkSwap);
            // read spare2
            DataUtilities.readSwapInt(dis, checkSwap);
            int fileHeaderSize = DataUtilities.readSwapInt(dis, checkSwap);
            int blockHeaderSize = DataUtilities.readSwapInt(dis, checkSwap);
            int blockElements = DataUtilities.readSwapInt(dis, checkSwap) * 4;
            nDim = DataUtilities.readSwapInt(dis, checkSwap);
            d.setNDim(nDim);
            lay = new DatasetLayout(nDim);
            lay.setFileHeaderSize(fileHeaderSize);
            lay.setBlockHeaderSize(blockHeaderSize);
            lay.blockElements = blockElements;
            lay.blockPoints = blockElements / 4;

            rdims = DataUtilities.readSwapInt(dis, checkSwap);
            if (rdims == 0) {
                rdims = nDim;
            }
            d.setFreqDims(rdims);
            d.setTempK(DataUtilities.readSwapFloat(dis, checkSwap));
            byte[] solventBytes = new byte[DatasetBase.SOLVENT_MAX_BYTES];
            dis.read(solventBytes);

            StringBuilder solventBuffer = new StringBuilder();

            for (int j = 0; (j < DatasetBase.SOLVENT_MAX_BYTES) && (solventBytes[j] != '\0'); j++) {
                solventBuffer.append((char) solventBytes[j]);
            }

            d.setSolvent(solventBuffer.toString());

            dis.skip(992 - 4 - DatasetBase.SOLVENT_MAX_BYTES);

            byte[] labelBytes = new byte[DatasetBase.LABEL_MAX_BYTES];
            StringBuilder labelBuffer = new StringBuilder();
            lay.offsetPoints[0] = 1;

            lay.offsetBlocks[0] = 1;
            for (i = 0; i < nDim; i++) {
                lay.setSize(i, DataUtilities.readSwapInt(dis, checkSwap));
                lay.blockSize[i] = DataUtilities.readSwapInt(dis, checkSwap);
                lay.nBlocks[i] = DataUtilities.readSwapInt(dis, checkSwap);
                lay.nBlocks[i] = lay.getSize(i) / lay.blockSize[i];

                if ((lay.blockSize[i] * lay.nBlocks[i]) < lay.getSize(i)) {
                    lay.nBlocks[i] += 1;
                }
                if (lay.nBlocks[i] != 1) {
                    lay.subMatrix = true;
                }

                if (i > 0) {
                    lay.offsetPoints[i] = lay.offsetPoints[i - 1] * lay.blockSize[i - 1];
                    lay.offsetBlocks[i] = lay.offsetBlocks[i - 1] * lay.nBlocks[i - 1];
                }

                // read offblk
                DataUtilities.readSwapInt(dis, checkSwap);
                // read blkmask
                DataUtilities.readSwapInt(dis, checkSwap);
                // read offpt
                DataUtilities.readSwapInt(dis, checkSwap);
                d.setSf(i, DataUtilities.readSwapFloat(dis, checkSwap));
                d.setSw(i, DataUtilities.readSwapFloat(dis, checkSwap));
                d.setSw_r(i, d.getSw(i));
                double refPt = DataUtilities.readSwapFloat(dis, checkSwap);
                d.setRefPt(i, refPt);
                d.setRefPt_r(i, refPt);
                d.setRefValue(i, DataUtilities.readSwapFloat(dis, checkSwap));
                d.setRefValue_r(i, d.getRefValue(i));
                d.setRefUnits(i, DataUtilities.readSwapInt(dis, checkSwap));
                d.setFoldUp(i, DataUtilities.readSwapFloat(dis, checkSwap));
                d.setFoldDown(i, DataUtilities.readSwapFloat(dis, checkSwap));

                dis.read(labelBytes);

                int j;
                labelBuffer.setLength(0);

                for (j = 0; (j < DatasetBase.LABEL_MAX_BYTES) && (labelBytes[j] != '\0'); j++) {
                    labelBuffer.append((char) labelBytes[j]);
                }

                d.setLabel(i, labelBuffer.toString());
                d.setNucleus(i, (Nuclei) null);

                d.setComplex(i, DataUtilities.readSwapInt(dis, checkSwap) == 1);
                d.setComplex_r(i, d.getComplex(i));
                d.setFreqDomain(i, DataUtilities.readSwapInt(dis, checkSwap) == 1);
                d.setFreqDomain_r(i, d.getFreqDomain(i));
                d.setPh0(i, DataUtilities.readSwapFloat(dis, checkSwap));
                d.setPh0_r(i, d.getPh0(i));
                d.setPh1(i, DataUtilities.readSwapFloat(dis, checkSwap));
                d.setPh1_r(i, d.getPh1(i));
                d.setVSize(i, DataUtilities.readSwapInt(dis, checkSwap));
                d.setVSize_r(i, d.getVSize(i));
                d.setTDSize(i, DataUtilities.readSwapInt(dis, checkSwap));
                d.setZFSize(i, DataUtilities.readSwapInt(dis, checkSwap));
                d.setExtFirst(i, DataUtilities.readSwapInt(dis, checkSwap));
                d.setExtLast(i, DataUtilities.readSwapInt(dis, checkSwap));
                dis.skip(6 * 4);
            }
            lay.dimDataset();
        } catch (IOException e) {
            log.warn(e.getMessage(), e);

            return null;
        }

        return lay;
    }

    /**
     * Read the header of an UCSF format dataset file into the fields of this
     * Dataset object.
     *
     * @return 0 if successful, 1 if there was an error
     */
    public final synchronized DatasetLayout readHeaderUCSF(RandomAccessFile raFile) {
        d.fFormat = DatasetBase.FFORMAT.UCSF;

        byte[] buffer;
        buffer = new byte[UCSF_HEADER_SIZE];

        DataUtilities.readBytes(raFile, buffer, 0, UCSF_HEADER_SIZE);

        ByteArrayInputStream bis = new ByteArrayInputStream(buffer);
        DataInputStream dis;

        try {
            byte[] magicBytes = new byte[10];
            dis = new DataInputStream(bis);
            dis.read(magicBytes);

            for (int j = 0; j < 8; j++) {
                if (magicBytes[j] != (byte) "UCSF NMR".charAt(j)) {
                    return null;
                }
            }

        } catch (IOException e) {
            log.warn(e.getMessage(), e);

            return null;
        }
        DatasetLayout lay = null;
        try {
            int nDim = dis.readByte();
            int nDataComp = dis.readByte();
            dis.readByte();
            int version = dis.readByte();
            if ((nDim < 1) || (nDim > 4)) {
                return null;
            }
            d.setNDim(nDim);
            d.setFreqDims(nDim);
            lay = new DatasetLayout(nDim);
            lay.setFileHeaderSize(DatasetBase.UCSF_HEADER_SIZE + 128 * nDim);
            lay.setBlockHeaderSize(0);

            dis.skip(DatasetBase.UCSF_HEADER_SIZE - 14);

            byte[] labelBytes = new byte[8];
            StringBuilder labelBuffer = new StringBuilder();
            lay.offsetPoints[0] = 1;

            lay.offsetBlocks[0] = 1;
            boolean checkSwap = false;

            for (int iDim = 0; iDim < nDim; iDim++) {
                int i = nDim - iDim - 1;
                buffer = new byte[128];

                DataUtilities.readBytes(raFile, buffer, DatasetBase.UCSF_HEADER_SIZE + i * 128, 128);
                bis = new ByteArrayInputStream(buffer);
                dis = new DataInputStream(bis);
                labelBuffer.setLength(0);
                dis.read(labelBytes);

                for (int j = 0; (j < 8) && (labelBytes[j] != '\0'); j++) {
                    labelBuffer.append((char) labelBytes[j]);
                }

                d.setLabel(iDim, labelBuffer.toString());

                lay.setSize(iDim, DataUtilities.readSwapInt(dis, checkSwap));
                // skip empty entry
                DataUtilities.readSwapInt(dis, checkSwap);
                lay.blockSize[iDim] = DataUtilities.readSwapInt(dis, checkSwap);
                lay.nBlocks[iDim] = lay.getSize(iDim) / lay.blockSize[iDim];

                if ((lay.blockSize[iDim] * lay.nBlocks[iDim]) < lay.getSize(iDim)) {
                    lay.nBlocks[iDim] += 1;
                }

                if (iDim > 0) {
                    lay.offsetPoints[iDim] = lay.offsetPoints[iDim - 1] * lay.blockSize[iDim - 1];
                    lay.offsetBlocks[iDim] = lay.offsetBlocks[iDim - 1] * lay.nBlocks[iDim - 1];
                }

                d.setSf(iDim, DataUtilities.readSwapFloat(dis, checkSwap));
                d.setSw(iDim, DataUtilities.readSwapFloat(dis, checkSwap));
                d.setSw_r(iDim, d.getSw(iDim));
                d.setRefPt(iDim, lay.getSize(iDim) / 2 + 1);
                d.setRefPt_r(iDim, d.getRefPt(iDim));
                d.setRefValue(iDim, DataUtilities.readSwapFloat(dis, checkSwap));
                d.setRefValue_r(iDim, d.getRefValue(iDim));

                if (version == 87) {
                    d.setComplex(i, DataUtilities.readSwapInt(dis, checkSwap) == 1);
                    d.setComplex_r(i, d.getComplex(i));
                    d.setFreqDomain(i, DataUtilities.readSwapInt(dis, checkSwap) == 1);
                    d.setFreqDomain_r(i, d.getFreqDomain(i));
                    d.setPh0(i, DataUtilities.readSwapFloat(dis, checkSwap));
                    d.setPh0_r(i, d.getPh0(i));
                    d.setPh1(i, DataUtilities.readSwapFloat(dis, checkSwap));
                    d.setPh1_r(i, d.getPh1(i));
                    d.setVSize(i, DataUtilities.readSwapInt(dis, checkSwap));
                    d.setVSize_r(i, d.getVSize(i));
                    d.setRefUnits(iDim, 3);

                } else {
                    d.setComplex(iDim, false);
                    d.setRefUnits(iDim, 3);
                    d.setVSize_r(iDim, lay.getSize(iDim));
                    d.setVSize(iDim, lay.getSize(iDim));
                    d.setFreqDomain(iDim, true);
                    d.setFreqDomain_r(iDim, true);
                }
            }
            lay.dimDataset();
            d.setDataType(0);
        } catch (IOException e) {
            log.error("Can't read header ", e);
            return null;
        }

        return lay;
    }

    /**
     * Flush the header values out to the specified file.
     *
     * @param outFile The file to write to.
     */
    synchronized public void writeHeader(DatasetLayout lay, RandomAccessFile outFile) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(NV_HEADER_SIZE);
        if (d.isLittleEndian()) {
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        }

        byte[] labelBytes = new byte[LABEL_MAX_BYTES];
        byte[] solventBytes = new byte[SOLVENT_MAX_BYTES];
        try {
            int magic = 0x3418abcd;
            byteBuffer.putInt(magic);
            byteBuffer.putInt(0);
            byteBuffer.putInt(0);
            byteBuffer.putInt(lay.getFileHeaderSize());
            byteBuffer.putInt(lay.getBlockHeaderSize());
            byteBuffer.putInt((int) (lay.getBlockElements() / 4));
            byteBuffer.putInt(d.getNDim());
            byteBuffer.putInt(d.getNFreqDims());
            byteBuffer.putFloat((float) d.getTempK());
            String solventString = d.getSolvent();
            int nBytes = solventString.length();
            for (int j = 0; j < SOLVENT_MAX_BYTES; j++) {
                if (j < nBytes) {
                    solventBytes[j] = (byte) solventString.charAt(j);
                } else {
                    solventBytes[j] = 0;
                }
            }
            byteBuffer.put(solventBytes);
            int nDim = d.getNDim();
            for (int i = 0; i < nDim; i++) {
                byteBuffer.position(1024 + i * 128);
                byteBuffer.putInt(lay.getSize(i));
                byteBuffer.putInt(lay.getBlockSize(i));
                byteBuffer.putInt(lay.getNBlocks(i));
                byteBuffer.putInt(0);
                byteBuffer.putInt(0);
                byteBuffer.putInt(0);
                byteBuffer.putFloat((float) d.getSf(i));
                byteBuffer.putFloat((float) d.getSw(i));
                byteBuffer.putFloat((float) d.getRefPt(i));
                byteBuffer.putFloat((float) d.getRefValue(i));
                byteBuffer.putInt(d.getRefUnits(i));
                byteBuffer.putFloat((float) d.getFoldUp(i));
                byteBuffer.putFloat((float) d.getFoldDown(i));

                String labelString = d.getLabel(i);
                nBytes = labelString.length();
                for (int j = 0; j < LABEL_MAX_BYTES; j++) {
                    if (j < nBytes) {
                        labelBytes[j] = (byte) labelString.charAt(j);
                    } else {
                        labelBytes[j] = 0;
                    }
                }

                byteBuffer.put(labelBytes);

                if (d.getComplex(i)) {
                    byteBuffer.putInt(1);
                } else {
                    byteBuffer.putInt(0);
                }

                if (d.getFreqDomain(i)) {
                    byteBuffer.putInt(1);
                } else {
                    byteBuffer.putInt(0);
                }
                byteBuffer.putFloat((float) d.getPh0(i));
                byteBuffer.putFloat((float) d.getPh1(i));
                byteBuffer.putInt(d.getVSize(i));
                byteBuffer.putInt(d.getTDSize(i));
                byteBuffer.putInt(d.getZFSize(i));
                byteBuffer.putInt(d.getExtFirst(i));
                byteBuffer.putInt(d.getExtLast(i));
            }

            if (outFile != null) {
                DataUtilities.writeBytes(outFile, byteBuffer.array(), 0, NV_HEADER_SIZE);
            }
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }
    }

    //    The 180 byte header contains:
//
//position	bytes	contents	required value
//0	10	file type	= UCSF NMR (8 character null terminated string)
//10	1	dimension of spectrum
//11	1	number of data components	= 1 for real data
//13	1	format version number	= 2 for current format
//The first byte in the file is position 0. A complex spectrum has two components. Sparky only reads real data and I will only describe below the layout for real data so set the number of components to 1. Use format version number 2.
//
//For each axis of the spectrum write a 128 byte header of the following form:
//
//position	bytes	contents
//0	6	nucleus name (1H, 13C, 15N, 31P, ...) null terminated ASCII
//8	4	integer number of data points along this axis
//16	4	integer tile size along this axis
//20	4	float spectrometer frequency for this nucleus (MHz)
//24	4	float spectral width (Hz)
//28	4	float center of data (ppm)
    synchronized public void writeHeaderUCSF(DatasetLayout lay, RandomAccessFile outFile, boolean nvExtra) {
        int nDim = d.getNDim();
        int ucsfFileHeaderSize = UCSF_HEADER_SIZE + nDim * 128;
        ByteBuffer byteBuffer = ByteBuffer.allocate(ucsfFileHeaderSize);
        if (d.isLittleEndian()) {
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        }

        int version = nvExtra ? 87 : 2;

        try {
            byte[] magicBytes = new byte[10];
            for (int i = 0; i < 8; i++) {
                magicBytes[i] = (byte) "UCSF NMR".charAt(i);
            }

            byteBuffer.put(magicBytes);
            byteBuffer.put((byte) nDim);
            byteBuffer.put((byte) 1);  // real data
            byteBuffer.put((byte) 0);
            byteBuffer.put((byte) version);  // version number

            for (int i = 0; i < nDim; i++) {
                int iDim = nDim - i - 1;
                byteBuffer.position(UCSF_HEADER_SIZE + i * 128);
                String nucName = d.getNucleus(iDim).getNumberName();
                for (int j = 0; j < nucName.length(); j++) {
                    byteBuffer.put((byte) nucName.charAt(j));
                }
                for (int j = nucName.length(); j < 8; j++) {
                    byteBuffer.put((byte) 0);
                }
                byteBuffer.putInt(lay.getSize(iDim));
                byteBuffer.putInt(0);
                byteBuffer.putInt(lay.getBlockSize(iDim));
                byteBuffer.putFloat((float) d.getSf(iDim));
                byteBuffer.putFloat((float) d.getSw(iDim));
                byteBuffer.putFloat((float) d.getRefValue(iDim));

                if (version == 87) {
                    if (d.getComplex(i)) {
                        byteBuffer.putInt(1);
                    } else {
                        byteBuffer.putInt(0);
                    }

                    if (d.getFreqDomain(i)) {
                        byteBuffer.putInt(1);
                    } else {
                        byteBuffer.putInt(0);
                    }
                    byteBuffer.putFloat((float) d.getPh0(i));
                    byteBuffer.putFloat((float) d.getPh1(i));
                    byteBuffer.putInt(d.getVSize(i));
                }
            }

            if (outFile != null) {
                DataUtilities.writeBytes(outFile, byteBuffer.array(), 0, ucsfFileHeaderSize);
            }
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }
    }
}
