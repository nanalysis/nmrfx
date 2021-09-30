package org.nmrfx.structure.chemistry.miner;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.zip.Adler32;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;

import org.nmrfx.chemistry.AtomContainer;
import org.nmrfx.chemistry.IAtom;
import org.nmrfx.chemistry.IBond;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.AtomProperty;
import org.nmrfx.chemistry.Bond;
import org.nmrfx.chemistry.Compound;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.chemistry.Order;

public class NanoMol {

    NanoBond[] nanoBonds = null;
    int nAtoms = 0;
    int nBonds = 0;
    boolean mode3D = false;
    float firstX = 0.0f;
    float firstY = 0.0f;
    float firstZ = 0.0f;
    double maxX = 0.0;
    double maxY = 0.0;
    double maxZ = 0.0;
    double centerX = 0.0;
    double centerY = 0.0;
    double centerZ = 0.0;
    double deltaX = 0.0;
    double deltaY = 0.0;
    double deltaZ = 0.0;
    int coordRes = 31;
    int[] nonCIndexes = null;
    int[] nonCElements = null;
    int[] atomElements = null;
    float[] values = null;
    BitSet bondBits = new BitSet();
    byte[] bondBytes = null;
    byte[] atomTypeBytes = null;
    byte[] atomDeltaBytes = null;
    byte[] coordDeltaBytes = null;

    // should canonically label, then branch uniquely to get unique nanoMol
    public int getBytesForBitCount(int nBits) {
        int nBytes = 1;

        if (nBits > 127) {
            nBytes = 2;
        }

        return nBytes;
    }

    public int getNBits(byte[] bytes) {
        return getNBits(bytes, 0);
    }

    public int getNBits(byte[] bytes, int start) {
        int byte1 = bytes[start++];
        int nBits = byte1 & 127;

        if ((byte1 >> 7) != 0) {
            int byte2 = bytes[start++];
            nBits = (nBits * 128) + byte2;
        }

        return nBits;
    }

    public int setNBits(byte[] bytes, int nBits) {
        final int nOffset;

        if (nBits <= 127) {
            bytes[0] = (byte) nBits;
            nOffset = 1;
        } else {
            bytes[0] = (byte) ((nBits / 128) | 128);
            bytes[1] = (byte) (nBits % 128);
            nOffset = 2;
        }

        return nOffset;
    }

    public BitSet fromBytesToBitSet(byte[] bytes) {
        int nBits = getNBits(bytes);
        int skip = getBytesForBitCount(nBits);
        BitSet bitSet = new BitSet(nBits);
        short bitVal = 1;
        int k = 0;
        int i = 0;

        while (i < nBits) {
            short value = bytes[skip + (i / 8)];

            for (int j = 0; j < 8; j++) {
                if (((value >> j) & 1) == 1) {
                    bitSet.set(i);
                } else {
                    bitSet.clear(i);
                }

                i++;
            }
        }

        return bitSet;
    }

    public byte[] fromBitSetToBytes(BitSet bitSet, int nBits) {
        int skip = getBytesForBitCount(nBits);
        short value = 0;
        short bitVal = 1;
        int j = 0;
        int k = skip;

        int nBytes = nBits / 8;

        if ((nBits % 8) != 0) {
            nBytes++;
        }

        byte[] bytes = new byte[nBytes + skip];
        setNBits(bytes, nBits);

        for (int i = 0; i < nBits; i++) {
            if (bitSet.get(i)) {
                value |= bitVal;
            }

            bitVal <<= 1;
            j++;

            if (j == 8) {
                bytes[k++] = (byte) value;
                j = 0;
                value = 0;
                bitVal = 1;
            }
        }

        if (j != 0) {
            bytes[k++] = (byte) value;
        }

        return bytes;
    }

    public byte[] makeCompXYZ(AtomContainer molecule, int startAtom, float valueScale)
            throws Exception {
        if (molecule == null) {
            throw new Exception("No Molecule");
        }

        boolean useType = true;

        atomElements = null;

        nAtoms = molecule.getAtomCount();

        if (nAtoms == 0) {
            throw new Exception("No atoms in molecule");
        }

        List<Integer> nonCarbonList = new ArrayList<>();
        float[] x = new float[nAtoms];
        float[] y = new float[nAtoms];
        float[] z = new float[nAtoms];

        int i = 0;
        for (IAtom atom : molecule.atoms()) {
            atom.setID(i);

            if (atom.getAtomicNumber() != 6) {
                nonCarbonList.add(i);
            }
            i++;
        }

        int shell;
        int value;
        Point3d pt1 = new Point3d();
        Point3d pt2 = new Point3d();
        Point2d pt12d = null;
        Point2d pt22d = null;
        IAtom atomB = null;
        IAtom atomE = null;
        double maxAbsX = -1.0e300;
        double minAbsX = 1.0e300;
        double maxAbsY = -1.0e300;
        double minAbsY = 1.0e300;
        double maxAbsZ = -1.0e300;
        double minAbsZ = 1.0e300;
        int maxAtom = nAtoms;

        int nBits = 3;
        int testMax = 8;

        while (maxAtom > testMax) {
            nBits++;
            testMax *= 2;
        }

        // The index gives the first occurence of an atom in the path
        int[] index = new int[maxAtom + 1];

        for (int k = 0; k < index.length; k++) {
            index[k] = -1;
        }

        int jPos = 0;
        for (IBond bond : molecule.bonds()) {
            atomB = bond.getAtom(0);
            atomE = bond.getAtom(1);

            int jAtom = atomB.getID();
            int kAtom = atomE.getID();
            int atom1 = 0;
            int atom2 = 0;

            if (jAtom < kAtom) {
                atom1 = jAtom;
                atom2 = kAtom;
            } else {
                atom1 = kAtom;
                atom2 = jAtom;
            }

            if (index[atom1] == -1) {
                index[atom1] = jPos;
                jPos++;
            }

            if (index[atom2] == -1) {
                index[atom2] = jPos;
                jPos++;
            }
        }

        boolean firstPoint = true;

        for (IBond bond : molecule.bonds()) {
            atomB = bond.getAtom(0);
            atomE = bond.getAtom(1);

            pt12d = atomB.getPoint2d();
            pt1.set(pt12d.x, pt12d.y, 0.0);
            pt22d = atomE.getPoint2d();
            pt2.set(pt22d.x, pt22d.y, 0.0);

            if ((pt1 != null) && (pt2 != null)) {
                if (firstPoint) {
                    firstPoint = false;
                    x[0] = firstX = (float) pt1.x;
                    y[0] = firstY = (float) pt1.y;
                    z[0] = firstZ = (float) pt1.z;
                }

                if (pt1.x < minAbsX) {
                    minAbsX = pt1.x;
                }

                if (pt1.x > maxAbsX) {
                    maxAbsX = pt1.x;
                }

                if (pt2.x < minAbsX) {
                    minAbsX = pt2.x;
                }

                if (pt2.x > maxAbsX) {
                    maxAbsX = pt2.x;
                }

                if (pt1.y < minAbsY) {
                    minAbsY = pt1.y;
                }

                if (pt1.y > maxAbsY) {
                    maxAbsY = pt1.y;
                }

                if (pt2.y < minAbsY) {
                    minAbsY = pt2.y;
                }

                if (pt2.y > maxAbsY) {
                    maxAbsY = pt2.y;
                }

                if (pt1.z < minAbsZ) {
                    minAbsZ = pt1.z;
                }

                if (pt1.z > maxAbsZ) {
                    maxAbsZ = pt1.z;
                }

                if (pt2.z < minAbsZ) {
                    minAbsZ = pt2.z;
                }

                if (pt2.z > maxAbsZ) {
                    maxAbsZ = pt2.z;
                }

                double dX = Math.abs(pt2.x - pt1.x);
                double dY = Math.abs(pt2.y - pt1.y);
                double dZ = Math.abs(pt2.z - pt1.z);

                if (dX > maxX) {
                    maxX = dX;
                }

                if (dY > maxY) {
                    maxY = dY;
                }

                if (dZ > maxZ) {
                    maxZ = dZ;
                }
            }
        }

        centerX = (maxAbsX + minAbsX) / 2.0;
        centerY = (maxAbsY + minAbsY) / 2.0;
        centerZ = (maxAbsZ + minAbsZ) / 2.0;

        boolean mode3D = true;

        if (maxZ < 0.01) {
            mode3D = false;
        }

        if (mode3D) {
            coordRes = 63;
        }

        nonCIndexes = new int[nonCarbonList.size()];
        nonCElements = new int[nonCarbonList.size()];

        for (int iNC = 0; iNC < nonCarbonList.size(); iNC++) {
            int iAtom = ((Integer) nonCarbonList.get(iNC)).intValue();
            Atom atom = (Atom) molecule.getAtom(iAtom);

            if (iAtom >= index.length) {
                throw new Exception("iAtom " + iAtom + " larger than index "
                        + index.length);
            }

            nonCIndexes[iNC] = index[iAtom];
            nonCElements[iNC] = atom.getAtomicNumber();
        }

        values = new float[nAtoms];

        nBonds = molecule.getBondCount();
        nanoBonds = new NanoBond[nBonds];

        int lastNode = 0;

        for (IBond bond : molecule.bonds()) {
            atomB = bond.getAtom(0);
            atomE = bond.getAtom(1);
            nanoBonds[i] = new NanoBond();

            int jAtom = atomB.getID();
            int kAtom = atomE.getID();
            int atom1 = 0;
            int atom2 = 0;
            if (jAtom < kAtom) {
                pt12d = atomB.getPoint2d();
                pt22d = atomE.getPoint2d();
                atom1 = jAtom;
                atom2 = kAtom;
            } else {
                atom1 = kAtom;
                atom2 = jAtom;
                pt12d = atomE.getPoint2d();
                pt22d = atomB.getPoint2d();
            }
//System.out.println(atom1+":"+index[atom1]+" "+atom2+":"+index[atom2]+" "+atomB.getAtomicNumber()+" "+atomE.getAtomicNumber());

            nanoBonds[i].atom1 = atom1;
            nanoBonds[i].atom2 = atom2;
            pt1.set(pt12d.x, pt12d.y, 0.0);
            pt2.set(pt22d.x, pt22d.y, 0.0);

            int order = 1;

            // FIXME   aromatic problem
            // FIXME   handle stereom
            Order bondOrder = bond.getOrder();

            order = (int) Math.round(bondOrder.ordinal());

            if (order == 7) {
                order = 1;
            } else if (order == 8) {
                order = 2;
            }

            nanoBonds[i].bondProp = order;

            if ((pt1 != null) && (pt2 != null)) {
                nanoBonds[i].dxValue = (float) (pt2.x - x[atom1]);
                nanoBonds[i].dyValue = (float) (pt2.y - y[atom1]);
                x[atom2] = unCompressCoord(compressCoord(nanoBonds[i].dxValue,
                        maxX), maxX) + x[atom1];
                y[atom2] = unCompressCoord(compressCoord(nanoBonds[i].dyValue,
                        maxY), maxY) + y[atom1];
                nanoBonds[i].dzValue = 0.0f;

                if (lastNode != atom1) {
                    lastNode = atom1;
                    nanoBonds[i].index = atom1;
                } else {
                    nanoBonds[i].index = -1;
                }

                if (mode3D) {
                    nanoBonds[i].dzValue = (float) (pt2.z - z[atom1]);
                    z[atom2] = unCompressCoord(compressCoord(
                            nanoBonds[i].dzValue, maxZ), maxZ) + z[atom1];
                }

                int iValue = 0;

                // atomDelta is number of elements between two atoms in index  (-1)
                nanoBonds[i].atomDelta = index[atom2] - index[atom1] - 1;
                if (nanoBonds[i].atomDelta > 15) {
                    //    System.out.println("dp = " + nanoBonds[i].atomDelta+" "+atom1+" "+index[atom1]+" "+atom2+" "+index[atom2]);
                    throw new Exception("dp = " + nanoBonds[i].atomDelta + " " + atom1 + " " + index[atom1] + " " + atom2 + " " + index[atom2] + " " + nAtoms);
                }
            }
        }

        return null;
    }

    public Molecule genMolecule(String name, String cmpdName) {
        Molecule mol = new Molecule(name);
        Compound compound = new Compound(cmpdName, "1");

        for (int i = 0; i < nAtoms; i++) {
            String elementSymbol = AtomProperty.getElementName(atomElements[i]);
            Atom atom = Atom.genAtomWithElement(elementSymbol + (i + 1), elementSymbol);
            compound.addAtom(atom);
        }

        float scale = 1.0f;
        float xOffset = firstX;
        float yOffset = firstY;
        float deltaX = 0.0f;
        float deltaY = 0.0f;

        float[] x = new float[nAtoms];
        float[] y = new float[nAtoms];
        float[] z = new float[nAtoms];
        int[] iAtoms = new int[nanoBonds.length];
        int[] jAtoms = new int[nanoBonds.length];
        int[] bondProps = new int[nanoBonds.length];
        int p1 = 0;

        for (int i = 0; i < nanoBonds.length; i++) {
            int index = nanoBonds[i].index;
            int dp = nanoBonds[i].atomDelta;
            float dX = nanoBonds[i].dxValue;
            float dY = nanoBonds[i].dyValue;

            if (index >= 0) {
                p1 = index;
            }

            int p2 = p1 + dp + 1;
            x[p2] = x[p1] + dX;
            y[p2] = y[p1] + dY;
            iAtoms[i] = p1;
            jAtoms[i] = p2;
            bondProps[i] = nanoBonds[i].bondProp;
        }

        for (int i = 0; i < nAtoms; i++) {
            double valx1 = ((x[i] - deltaX) * scale) + xOffset;
            double valy1 = ((y[i] - deltaY) * scale) + yOffset;
            IAtom atom = compound.getAtom(i);
            Point2d point2D = new Point2d(valx1, valy1);
            atom.setPoint2d(point2D);
        }

        for (int i = 0; i < nBonds; i++) {
            Atom atom1 = compound.getAtom(iAtoms[i]);
            Atom atom2 = compound.getAtom(jAtoms[i]);
            Order order = Order.SINGLE;
            if (bondProps[i] == 2) {
                order = Order.DOUBLE;
            } else if (bondProps[i] == 3) {
                order = Order.TRIPLE;
            }

            Bond bond = new Bond(atom1, atom2, order);
            compound.addBond(bond);
        }

        return mol;
    }

    public PMol genLinesSpheres(float xOffset, float yOffset) {
        float scale = 10.0f;
        float deltaX = 0.0f;
        float deltaY = 0.0f;

        float[] x = new float[nAtoms];
        float[] y = new float[nAtoms];
        float[] z = new float[nAtoms];
        int[] iAtoms = new int[nanoBonds.length];
        int[] jAtoms = new int[nanoBonds.length];
        int[] bondProps = new int[nanoBonds.length];
        int p1 = 0;

        for (int i = 0; i < nanoBonds.length; i++) {
            int index = nanoBonds[i].index;
            int dp = nanoBonds[i].atomDelta;
            float dX = nanoBonds[i].dxValue;
            float dY = nanoBonds[i].dyValue;

            if (index >= 0) {
                p1 = index;
            }

            int p2 = p1 + dp + 1;
            x[p2] = x[p1] + dX;
            y[p2] = y[p1] + dY;
            iAtoms[i] = p1;
            jAtoms[i] = p2;
            bondProps[i] = nanoBonds[i].bondProp;
        }

        PMol pmol = new PMol();
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < nAtoms; i++) {
            double x1 = x[i] = ((x[i] - deltaX) * scale) + xOffset;
            double y1 = y[i] = ((y[i] - deltaY) * scale) + yOffset;
            minX = Math.min(x1, minX);
            minY = Math.min(y1, minY);
            maxX = Math.max(x1, maxX);
            maxY = Math.max(y1, maxY);

            if (atomElements[i] != 6) {
                pmol.addAtom(x1, y1, atomElements[i]);
            }
        }

        pmol.setMinMax(minX, minY, maxX, maxY);

        double[] coords = new double[nBonds * 4];
        int j = 0;

        for (int i = 0; i < nBonds; i++) {
            //Atom atom1 = mol.getAtom(iAtoms[i]);
            //Atom atom2 = mol.getAtom(jAtoms[i]);
            int order = bondProps[i];

            if (order == 4) {
                order = 1;
            } else if (order == 5) {
                order = 1;
            }

            int stereo = 0;
            double x1 = x[iAtoms[i]];
            double y1 = y[iAtoms[i]];
            double x2 = x[jAtoms[i]];
            double y2 = y[jAtoms[i]];
            pmol.addBond(x1, y1, x2, y2, order, stereo);
        }

        return pmol;
    }

    public void dumpAtoms() {
        if (atomElements == null) {
            atomElements = new int[nAtoms];

            for (int i = 0; i < nAtoms; i++) {
                atomElements[i] = 6;
            }

            for (int i = 0; i < nonCIndexes.length; i++) {
                atomElements[nonCIndexes[i]] = nonCElements[i];
            }
        }

        for (int i = 0; i < nAtoms; i++) {
            System.out.println(i + " " + atomElements[i]);
        }
    }

    public void dumpNanoBonds() {
        float scale = 1.0f;
        float xOffset = firstX;
        float yOffset = firstY;
        float deltaX = 0.0f;
        float deltaY = 0.0f;

        float[] x = new float[nAtoms];
        float[] y = new float[nAtoms];
        float[] z = new float[nAtoms];
        int[] iAtoms = new int[nanoBonds.length];
        int[] jAtoms = new int[nanoBonds.length];
        int[] bondProps = new int[nanoBonds.length];
        int p1 = 0;

        for (int i = 0; i < nanoBonds.length; i++) {
            int index = nanoBonds[i].index;
            int dp = nanoBonds[i].atomDelta;
            float dX = nanoBonds[i].dxValue;
            float dY = nanoBonds[i].dyValue;

            if (index >= 0) {
                p1 = index;
            }

            int p2 = p1 + dp + 1;
            x[p2] = x[p1] + dX;
            y[p2] = y[p1] + dY;
            iAtoms[i] = p1;
            jAtoms[i] = p2;
            bondProps[i] = nanoBonds[i].bondProp;
        }

        for (int i = 0; i < nAtoms; i++) {
            double valx1 = ((x[i] - deltaX) * scale) + xOffset;
            double valy1 = ((y[i] - deltaY) * scale) + yOffset;
        }

        for (int i = 0; i < nBonds; i++) {
            System.out.println("bonds " + iAtoms[i] + " " + jAtoms[i] + " "
                    + bondProps[i]);
        }
    }

    int addHeader(byte[] byteArray) {
        int iByte = 0;

        if (mode3D) {
            byteArray[iByte++] = 2; // version number
        } else {
            byteArray[iByte++] = 1; // version number
        }

        // FIXME need to handle more atoms and bonds
        byteArray[iByte++] = (byte) nAtoms;
        byteArray[iByte++] = (byte) nBonds;
        byteArray[iByte++] = (byte) (Math.round((float) (maxX / 5.0 * 255))
                & 255);
        byteArray[iByte++] = (byte) (Math.round((float) (maxY / 5.0 * 255))
                & 255);

        if (mode3D) {
            byteArray[iByte++] = (byte) (Math.round((float) (maxZ / 5.0 * 255))
                    & 255);
        }

        byteArray[iByte++] = (byte) (Math.round((float) ((centerX - firstX) / maxX / 100.0 * 255))
                & 255);
        byteArray[iByte++] = (byte) (Math.round((float) ((centerY - firstY) / maxY / 100.0 * 255))
                & 255);

        if (mode3D) {
            byteArray[iByte++] = (byte) (Math.round((float) ((centerZ - firstZ) / maxZ / 100.0 * 255))
                    & 255);
        }

        return iByte;
    }

    int readHeader(byte[] byteArray) {
        int i = 0; // version number in byte 0
        int version = byteArray[i++];
        mode3D = ((version & 2) == 2);
        coordRes = 31;

        if (mode3D) {
            coordRes = 63;
        }

        nAtoms = byteArray[i++];
        nBonds = byteArray[i++];

        int byte1 = byteArray[i++];
        int byte2 = byteArray[i++];
        int byte3 = 0;
        maxX = ((byte1 % 0x100) * 5.0) / 255.0;
        maxY = ((byte2 % 0x100) * 5.0) / 255.0;

        if (mode3D) {
            byte3 = byteArray[i++];
            maxZ = ((byte3 % 0x100) * 5.0) / 255.0;
        }

        byte1 = byteArray[i++];
        byte2 = byteArray[i++];
        deltaX = ((byte1 % 0x100) * 100.0) / 255.0 * maxX;
        deltaY = ((byte2 % 0x100) * 100.0) / 255.0 * maxY;
        deltaZ = 0.0;

        if (mode3D) {
            byte3 = byteArray[i++];
            deltaZ = ((byte3 % 0x100) * 100.0) / 255.0 * maxZ;
        }

        return i;
    }

    byte[] getBytes(byte[] inputBytes, int start) {
        int nBits = getNBits(inputBytes, start);
        int nBytes = nBits / 8;

        if ((nBits % 8) != 0) {
            nBytes++;
        }

        nBytes += getBytesForBitCount(nBits);

        byte[] outputBytes = new byte[nBytes];
        System.arraycopy(inputBytes, start, outputBytes, 0, nBytes);

        return outputBytes;
    }

    public byte[] compressNanoRep() throws Exception {
        compressBondProps();
        compressAtomDeltas();
        compressCoordDeltas();
        compressAtoms();

        int totalBytes = 7;

        if (mode3D) {
            totalBytes += 2;
        }

        int nBytes = bondBytes.length;
        totalBytes += nBytes;

        nBytes = atomTypeBytes.length;
        totalBytes += nBytes;

        nBytes = atomDeltaBytes.length;
        totalBytes += nBytes;

        nBytes = coordDeltaBytes.length;
        totalBytes += nBytes;

        byte[] outputBytes = new byte[totalBytes + 4];

        int start = 0;

        start += addHeader(outputBytes);

        System.arraycopy(bondBytes, 0, outputBytes, start, bondBytes.length);
        start += bondBytes.length;
        System.arraycopy(atomTypeBytes, 0, outputBytes, start,
                atomTypeBytes.length);
        start += atomTypeBytes.length;
        System.arraycopy(atomDeltaBytes, 0, outputBytes, start,
                atomDeltaBytes.length);
        start += atomDeltaBytes.length;
        System.arraycopy(coordDeltaBytes, 0, outputBytes, start,
                coordDeltaBytes.length);
        start += coordDeltaBytes.length;
        addCheckSum(outputBytes);

        return outputBytes;
    }

    public void addCheckSum(byte[] byteArray) {
        int n = byteArray.length - 4;
        Adler32 adler = new Adler32();
        adler.update(byteArray, 0, n);

        long aValue = adler.getValue();
        byteArray[n] = (byte) (aValue & 0xff);
        byteArray[n + 1] = (byte) ((aValue >> 8) & 0xff);
        byteArray[n + 2] = (byte) ((aValue >> 16) & 0xff);
        byteArray[n + 3] = (byte) ((aValue >> 24) & 0xff);
    }

    public boolean checkCheckSum(byte[] byteArray) {
        int n = byteArray.length - 4;
        Adler32 adler = new Adler32();
        adler.update(byteArray, 0, n);

        long aValue = adler.getValue();
        boolean ok = true;

        if (byteArray[n] != ((byte) (aValue & 0xff))) {
            ok = false;
        } else if (byteArray[n + 1] != ((byte) ((aValue >> 8) & 0xff))) {
            ok = false;
        } else if (byteArray[n + 2] != ((byte) ((aValue >> 16) & 0xff))) {
            ok = false;
        } else if (byteArray[n + 3] != ((byte) ((aValue >> 24) & 0xff))) {
            ok = false;
        }

        return ok;
    }

    public void unCompressNanoRep(byte[] inputBytes) throws Exception {
        int start = readHeader(inputBytes);
        bondBytes = getBytes(inputBytes, start);
        start += bondBytes.length;
        atomTypeBytes = getBytes(inputBytes, start);
        start += atomTypeBytes.length;
        atomDeltaBytes = getBytes(inputBytes, start);
        start += atomDeltaBytes.length;
        coordDeltaBytes = getBytes(inputBytes, start);
        start += coordDeltaBytes.length;

        nanoBonds = new NanoBond[nBonds];

        for (int i = 0; i < nBonds; i++) {
            nanoBonds[i] = new NanoBond();
        }

        unCompressBondProps();
        unCompressAtomDeltas();
        unCompressCoordDeltas();
        unCompressAtoms();

        //dumpNanoBonds();
        //dumpAtoms();
    }

    public void unCompressAtoms() {
        BitSet atomTypeBits = fromBytesToBitSet(atomTypeBytes);
        int nAtomTypeBits = getNBits(atomTypeBytes);
        atomElements = new int[nAtoms];

        for (int i = 0; i < nAtoms; i++) {
            atomElements[i] = 6;
        }

        int i = 0;
        boolean inIndex = false;
        int element = 0;

        while (i < nAtomTypeBits) {
            int eVal = 0;

            if (!inIndex) {
                if (atomTypeBits.get(i++)) {
                    eVal = getInt(atomTypeBits, i, 3);
                    i += 3;

                    switch (eVal) {
                        case 1:
                            element = 1;

                            break;

                        case 2:
                            element = 7;

                            break;

                        case 3:
                            element = 8;

                            break;

                        case 4:
                            element = 9;

                            break;

                        case 5:
                            element = 15;

                            break;

                        case 6:
                            element = 16;

                            break;

                        case 7:
                            element = 17;

                            break;

                        default:
                            element = 0;
                    }
                } else {
                    eVal = getInt(atomTypeBits, i, 9);
                    i += 9;
                }

                inIndex = true;
            } else {
                if (!atomTypeBits.get(i++)) {
                    inIndex = false;
                } else {
                    int index = 0;

                    if (!atomTypeBits.get(i++)) {
                        index = getInt(atomTypeBits, i, 7);
                        i += 7;
                    } else {
                        index = getInt(atomTypeBits, i, 15);
                        i += 15;
                    }

                    atomElements[index] = element;
                }
            }
        }
    }

    public void compressAtoms() {
        BitSet atomTypeBits = new BitSet();
        int j = 0;
        int eVal = 0;
        ArrayList[] eLists = new ArrayList[100];

        for (int i = 0; i < nonCIndexes.length; i++) {
            int element = nonCElements[i];

            if (eLists[element] == null) {
                eLists[element] = new ArrayList();
            }

            int index = (byte) nonCIndexes[i];
            Integer indexInt = new Integer(index);
            eLists[element].add(indexInt);
        }

        for (int element = 1; element < eLists.length; element++) {
            ArrayList eList = eLists[element];

            if ((eList == null) || (eList.size() == 0)) {
                continue;
            }

            switch (element) {
                case 1:
                    eVal = 1;

                    break;

                case 7:
                    eVal = 2;

                    break;

                case 8:
                    eVal = 3;

                    break;

                case 9:
                    eVal = 4;

                    break;

                case 15:
                    eVal = 5;

                    break;

                case 16:
                    eVal = 6;

                    break;

                case 17:
                    eVal = 7;

                    break;

                default:
                    eVal = 0;
            }

            if (eVal == 0) {
                atomTypeBits.clear(j++);

                for (int k = 0; k < 9; k++) {
                    atomTypeBits.set(j++, (((element >> k) & 1) == 1));
                }
            } else {
                atomTypeBits.set(j++);

                for (int k = 0; k < 3; k++) {
                    atomTypeBits.set(j++, (((eVal >> k) & 1) == 1));
                }
            }

            for (int i = 0; i < eList.size(); i++) {
                int index = ((Integer) eList.get(i)).intValue();
                int nBits = 0;

                if (index > 63) {
                    nBits = 15;
                    atomTypeBits.set(j++);
                    atomTypeBits.set(j++);
                } else {
                    nBits = 7;
                    atomTypeBits.set(j++);
                    atomTypeBits.clear(j++);
                }

                for (int k = 0; k < nBits; k++) {
                    atomTypeBits.set(j++, (((index >> k) & 1) == 1));
                }
            }

            atomTypeBits.clear(j++);
        }

        atomTypeBytes = fromBitSetToBytes(atomTypeBits, j);
    }

    public void unCompressBondProps() throws Exception {
        int j = 0;
        BitSet bondBits = fromBytesToBitSet(bondBytes);
        int nBondBits = getNBits(bondBytes);

        for (int i = 0; i < nBondBits; i++) {
            if (!bondBits.get(i)) {
                nanoBonds[j++].bondProp = 1;
            } else if (!bondBits.get(++i) && !bondBits.get(++i)) {
                nanoBonds[j++].bondProp = 2;
            } else if (!bondBits.get(++i) && bondBits.get(++i)) {
                nanoBonds[j++].bondProp = 3;
            } else if (bondBits.get(++i) && !bondBits.get(++i)) {
                nanoBonds[j++].bondProp = 4;
            } else if (bondBits.get(++i) && bondBits.get(++i)) {
                nanoBonds[j++].bondProp = 5;
            }
        }

        nBonds = j;
    }

    public void compressBondProps() throws Exception {
        BitSet bondBits = new BitSet();
        int j = 0;

        for (int i = 0; i < nanoBonds.length; i++) {
            int bondProp = nanoBonds[i].bondProp;

            if (bondProp == 1) {
                bondBits.clear(j++);
            } else {
                bondBits.set(j++);

                if (bondProp == 2) {
                    bondBits.clear(j++);
                    bondBits.clear(j++);
                } else if (bondProp == 3) {
                    bondBits.clear(j++);
                    bondBits.set(j++);
                } else if (bondProp == 4) {
                    bondBits.set(j++);
                    bondBits.clear(j++);
                } else if (bondProp == 5) {
                    bondBits.set(j++);
                    bondBits.set(j++);
                } else {
                    throw new Exception("Invalid bond bondProp");
                }
            }
        }

        bondBytes = fromBitSetToBytes(bondBits, j);

        BitSet bondBits2 = fromBytesToBitSet(bondBytes);
    }

    public int getInt(BitSet bitSet, int start, int nBits) {
        int value = 0;
        int bit = 0;

        for (int i = 0; i < nBits; i++) {
            if (bitSet.get(start + i)) {
                value += (1 << i);
            }
        }

        return value;
    }

    public void unCompressAtomDeltas() throws Exception {
        BitSet atomDeltaBits = fromBytesToBitSet(atomDeltaBytes);
        int nAtomDeltaBits = getNBits(atomDeltaBytes);
        int j = 0;
        int i = 0;

        while (i < nAtomDeltaBits) {
            if (!atomDeltaBits.get(i++)) {
                nanoBonds[j].index = getInt(atomDeltaBits, i, 5);
                i += 5;
            } else {
                nanoBonds[j].index = -1;
            }

            nanoBonds[j].atomDelta = getInt(atomDeltaBits, i, 4);
            i += 4;
            j++;
        }
    }

    public void compressAtomDeltas() throws Exception {
        BitSet atomDeltaBits = new BitSet();
        int j = 0;

        for (int i = 0; i < nanoBonds.length; i++) {
            int dp = nanoBonds[i].atomDelta;
            int index = nanoBonds[i].index;

            if (index >= 0) {
                atomDeltaBits.clear(j++);

                for (int k = 0; k < 5; k++) {
                    atomDeltaBits.set(j++, (((index >> k) & 1) == 1));
                }
            } else {
                atomDeltaBits.set(j++);
            }

            for (int k = 0; k < 4; k++) {
                atomDeltaBits.set(j++, (((dp >> k) & 1) == 1));
            }
        }

        atomDeltaBytes = fromBitSetToBytes(atomDeltaBits, j);
    }

    public void unCompressCoordDeltas() throws Exception {
        BitSet coordDeltaBits = fromBytesToBitSet(coordDeltaBytes);
        int nCoordDeltaBits = getNBits(coordDeltaBytes);
        int j = 0;
        int i = 0;

        while (i < nCoordDeltaBits) {
            float dx = getInt(coordDeltaBits, i, 5);
            i += 5;

            float dy = getInt(coordDeltaBits, i, 5);
            i += 5;
            nanoBonds[j].dxValue = unCompressCoord(dx, maxX);
            nanoBonds[j].dyValue = unCompressCoord(dy, maxY);
            j++;
        }
    }

    int compressCoord(float fValue, double max) {
        int iValue = (int) (((fValue / max / 2.0) + 0.5) * coordRes);

        return iValue;
    }

    float unCompressCoord(float value, double max) {
        float fValue = (float) (((value / coordRes) - 0.5) * 2.0 * max);

        return fValue;
    }

    public void compressCoordDeltas() throws Exception {
        BitSet coordDeltaBits = new BitSet();
        int j = 0;

        for (int i = 0; i < nanoBonds.length; i++) {
            int dx = compressCoord(nanoBonds[i].dxValue, maxX);
            int dy = compressCoord(nanoBonds[i].dyValue, maxY);

            for (int k = 0; k < 5; k++) {
                coordDeltaBits.set(j++, (((dx >> k) & 1) == 1));
            }

            for (int k = 0; k < 5; k++) {
                coordDeltaBits.set(j++, (((dy >> k) & 1) == 1));
            }
        }

        coordDeltaBytes = fromBitSetToBytes(coordDeltaBits, j);
    }

    public static String uuEncode(byte[] byteArray) {
        int nBytes = byteArray.length;
        StringBuffer sbuf = new StringBuffer();

        int i = 0;
        int[] b = new int[3];

        for (int j = 0; j < nBytes;) {
            b[0] = byteArray[j++];

            int ic1 = 0x20 + ((b[0] >> 2) & 0x3F);
            sbuf.append((char) ic1);

            if (j < nBytes) {
                b[1] = byteArray[j++];

                int ic2 = 0x20 + (((b[0] << 4) + ((b[1] >> 4) & 0xF)) & 0x3F);
                sbuf.append((char) ic2);

                if (j < nBytes) {
                    b[2] = byteArray[j++];

                    int ic3 = 0x20
                            + (((b[1] << 2) + ((b[2] >> 6) & 0x3)) & 0x3F);
                    sbuf.append((char) ic3);

                    int ic4 = 0x20 + (b[2] & 0x3F);
                    sbuf.append((char) ic4);
                } else {
                    int ic3 = 0x20 + ((b[1] << 2) & 0x3F);
                    sbuf.append((char) ic3);
                }
            } else {
                int ic2 = 0x20 + ((b[0] << 4) & 0x3F);
                sbuf.append((char) ic2);
            }
        }

        return sbuf.toString();
    }

    public static int uuDecode(String byteString, byte[] byteArray) {
        int nBytes = 0;
        char[] charArray = byteString.toCharArray();
        int i = 0;
        int[] b = new int[4];

        for (int j = 0; j < charArray.length;) {
            int k = 0;

            for (k = 0; k < 4; k++) {
                if (j >= charArray.length) {
                    break;
                }

                b[k] = charArray[j] - 32;
                j++;
            }

            byte ob1 = (byte) (((b[0] << 2) & 0xFC) + ((b[1] >> 4) & 0x03));
            byte ob2 = (byte) (((b[1] << 4) & 0xF0) + ((b[2] >> 2) & 0x0F));
            byte ob3 = (byte) (((b[2] << 6) & 0xC0) + (b[3] & 0x3F));

            if (k > 1) {
                byteArray[i++] = ob1;

                if (k > 2) {
                    byteArray[i++] = ob2;

                    if (k > 3) {
                        byteArray[i++] = ob3;
                    }
                }
            }

            nBytes = i;
        }

        return nBytes;
    }

    class NanoBond {

        int index = 0;
        int bondProp = 0;
        int atomDelta = 0;
        float dxValue = 0.0f;
        float dyValue = 0.0f;
        float dzValue = 0.0f;
        int atom1 = 0;
        int atom2 = 0;
    }
}
