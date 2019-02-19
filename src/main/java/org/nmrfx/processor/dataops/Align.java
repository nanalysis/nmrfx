/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.dataops;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.math.Vec.IndexValue;
import org.nmrfx.processor.operations.CShift;

/**
 *
 * @author Bruce Johnson
 */
public class Align {

    public void alignByMax(final Dataset dataset, final int refPt, final int pt1, final int pt2) throws IOException {
        iteratorToFiniteStream(dataset.vectors(0)).forEach(v -> {
            alignByMax(v, refPt, pt1, pt2);
            try {
                dataset.writeVector(v);
            } catch (IOException | IllegalArgumentException ex) {
                Logger.getLogger(Align.class.getName()).log(Level.SEVERE, null, ex);
                System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
                System.exit(1);
            }
        });
    }

    public int findClosest(final Dataset dataset, Vec vec1, Vec vec2, int row, final int pt1, final int pt2) throws IOException {
        int nVec = dataset.getSize(1);
        int index = 0;
        double maxCorr = Double.NEGATIVE_INFINITY;
        for (int j = row + 1; j < nVec; j++) {

            dataset.readVector(vec2, j, 0);
            double corr = compareVec(vec1, vec2, pt1, pt2);
            System.out.println(row + " " + j + " " + corr);
            if (corr > maxCorr) {
                maxCorr = corr;
                index = j;
            }
        }
        System.out.println(row + " max " + index + " " + maxCorr);
        return index;
    }

    public void sortByCorr(final Dataset dataset, int row, final int pt1, final int pt2) throws IOException {
        int nVec = dataset.getSize(1);
        Vec vec1 = new Vec(dataset.getSize(0), false);
        Vec vec2 = new Vec(dataset.getSize(0), false);
        double maxCenter = Double.NEGATIVE_INFINITY;
        if (row < 0) {
            int index = 0;
            for (int i = 0; i < nVec; i++) {
                dataset.readVector(vec1, i, 0);
                double cOfMass = centerOfMass(vec1, pt1, pt2);
                if (cOfMass > maxCenter) {
                    maxCenter = cOfMass;
                    index = i;
                }
            }
            row = index;
        }
        for (int i = 0; i < (nVec - 1); i++) {
            dataset.readVector(vec1, row, 0);
            dataset.readVector(vec2, i, 0);
            dataset.writeVector(vec1, i, 0);
            dataset.writeVector(vec2, row, 0);
            System.out.println("swap " + i + " " + row);
            row = findClosest(dataset, vec1, vec2, i, pt1, pt2);
        }
    }

    public void align(final Dataset dataset, List<Double> deltas) throws IOException {
        List<int[][]> indices = dataset.getIndices(0, 0, dataset.getSize(0) - 1);
        int[] dim = new int[dataset.getNDim()];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }

        final int vecSize = dataset.getSize(0);
        indices.stream().parallel().forEach(vi -> {
            Vec vec = new Vec(vecSize);
            try {
                dataset.readVecFromDatasetFile(vi, dim, vec);
                double delta = deltas.get(vi[1][0]);
                CShift cShift = new CShift((int) Math.round(delta));
                cShift.eval(vec);
                dataset.writeVecToDatasetFile(vi, dim, vec);
            } catch (IOException ex) {
                Logger.getLogger(Align.class
                        .getName()).log(Level.SEVERE, null, ex);
                System.out.println(ex.getMessage());
            }
        });

    }

    public Double[] alignByMaxStream(final Dataset dataset, final int row, final int pt1, final int pt2) throws IOException {
        final int p1;
        final int p2;
        if (pt1 > pt2) {
            p1 = pt2;
            p2 = pt1;
        } else {
            p1 = pt1;
            p2 = pt2;
        }
        List<int[][]> indices = dataset.getIndices(0, 0, dataset.getSize(0) - 1);
        int[] dim = new int[dataset.getNDim()];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        int[][] pt = indices.get(row);

        final int vecSize = dataset.getSize(0);
        Vec fixedVec = new Vec(vecSize);
        dataset.readVecFromDatasetFile(pt, dim, fixedVec);
        IndexValue indexValue = fixedVec.maxIndex(pt1, pt2);
        int refPt = indexValue.getIndex();
        Double[] deltas = new Double[indices.size()];
        indices.stream().parallel().forEach(vi -> {
            Vec vec = new Vec(vecSize);
            try {
                dataset.readVecFromDatasetFile(vi, dim, vec);
                double delta = alignByMax(vec, refPt, p1, p2);
                dataset.writeVecToDatasetFile(vi, dim, vec);
                deltas[vi[1][0]] = delta;

            } catch (IOException ex) {
                Logger.getLogger(Align.class
                        .getName()).log(Level.SEVERE, null, ex);
                System.out.println(ex.getMessage());
            }
        });
        return deltas;
    }

    public Double[] alignByCovStream(final Dataset dataset, final int fixStart, final int fixEnd, final int pStart, int sectionLength, final int iWarp, final int tStart) throws IOException {
        List<int[][]> indices = dataset.getIndices(0, 0, dataset.getSize(0) - 1);
        int[] dim = new int[dataset.getNDim()];
        int[][] pt = indices.get(0);
        int[][] startVecPos = new int[dim.length][2];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
            startVecPos[i] = new int[2];
            startVecPos[i][0] = pt[i][0];
            startVecPos[i][1] = pt[i][1];
        }
        startVecPos[0][0] = fixStart;
        startVecPos[0][1] = fixEnd;
        final int vecSize = dataset.getSize(0);
        Vec fixedVec = new Vec(fixEnd - fixStart + 1);
        dataset.readVecFromDatasetFile(startVecPos, dim, fixedVec);
//        indices = indices.subList(0, 2);

        //[vecmat cowcorr $vec1 $vec2 -m $size -t 0 -p $pt1x -w 0
        if (sectionLength <= 0) { // -m
            sectionLength = vecSize / 128;
        }
        final int sL = fixedVec.getSize(); // fixme   XXXXXXXXXXXXXXXXXXXXXXXX
//        System.out.println("indices " + indices.size() + " " + vecSize + " " + startVecPos[0][0] + " " + startVecPos[0][1] + " " + sectionLength);
        Double[] deltas = new Double[indices.size()];

        indices.stream().parallel().forEach(vi -> {
            Vec movingVec = new Vec(vecSize);
            try {
                movingVec.setPt(vi, dim);
                dataset.readVector(movingVec);
                double delta = alignByCov(fixedVec, movingVec, pStart, sL, iWarp, tStart);
                dataset.writeVector(movingVec);
                deltas[vi[1][0]] = delta;
            } catch (IOException ex) {
                Logger.getLogger(Align.class
                        .getName()).log(Level.SEVERE, null, ex);
                System.out.println(ex.getMessage());
            }
        });
        return deltas;
    }

    public int alignByCov(Vec fixedVec, Vec movingVec, int pStart, int m, int iWarp, int tStart) {
        //     public static double cowCorr(Vec src, Vec target, int pStart, int m, int iWarp, int tStart)  {
        int range = 50;
        double max = Double.NEGATIVE_INFINITY;
        int iMax = 0;
        try {
            for (int i = -range; i < range; i++) {
                double delta = VecCorrelation.cowCorr(fixedVec, movingVec, pStart + i, m, iWarp, tStart);
                if (delta > max) {
                    max = delta;
                    iMax = i;
                }
            }
        } catch (ArrayIndexOutOfBoundsException aiE) {
            aiE.printStackTrace();
        }
        int delta = -iMax;
        CShift cShift = new CShift(delta);
        cShift.eval(movingVec);
        return delta;
    }

    public int alignByMax(Vec vec, int refPt, int pt1, int pt2) {
        Vec.IndexValue indexValue = vec.maxIndex(pt1, pt2);
        int delta = refPt - indexValue.getIndex();
        CShift cShift = new CShift(delta);
        cShift.eval(vec);
        return delta;
    }

    public double centerOfMass(Vec vec1, int pt1, int pt2) {
        int nPoints = pt2 - pt1 + 1;

        double sum = 0.0;
        double mass = 0.0;
        for (int i = pt1; i <= pt2; i++) {
            double value = Math.abs(vec1.getReal(i));
            double delta = i - pt1;
            mass += value;
            sum += value * delta;
        }
        double cOfMass = sum / mass;
        return cOfMass;
    }

    public double compareVec(Vec vec1, Vec vec2, int pt1, int pt2) {
        int nPoints = pt2 - pt1 + 1;
        double[] rvec1 = new double[nPoints];
        double[] rvec2 = new double[nPoints];
        double[] wvec = new double[nPoints];

        for (int i = pt1, j = 0; i <= pt2; i++) {
            rvec1[j] = vec1.getReal(i);
            rvec2[j] = vec2.getReal(i);
            wvec[j] = 1.0;
            j++;
        }
        return VecCorrelation.correlation(rvec1, rvec2, wvec);
    }

    static <T> Stream<T> iteratorToFiniteStream(final Iterator<T> iterator) {
        final Iterable<T> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    static <T> Stream<T> iteratorToInfiniteStream(final Iterator<T> iterator) {
        return Stream.generate(iterator::next);
    }
}
