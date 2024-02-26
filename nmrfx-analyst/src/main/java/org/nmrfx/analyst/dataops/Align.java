/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.analyst.dataops;

import org.apache.commons.math3.linear.RealMatrix;
import org.nmrfx.math.VecBase.IndexValue;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.math.PositionValue;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.operations.CShift;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author Bruce Johnson
 */
public class Align {
    private static final Logger log = LoggerFactory.getLogger(Align.class);

    public void alignByMax(final Dataset dataset, final int refPt, final int pt1, final int pt2) throws IOException {
        iteratorToFiniteStream(dataset.vectors(0)).forEach(v -> {
            alignByMax(v, refPt, pt1, pt2);
            try {
                dataset.writeVector(v);
            } catch (IOException | IllegalArgumentException ex) {
                log.error("Unexpected error", ex);
                System.exit(1);
            }
        });
    }

    public int findClosest(final Dataset dataset, Vec vec1, Vec vec2, int row, final int pt1, final int pt2) throws IOException {
        int nVec = dataset.getSizeTotal(1);
        int index = 0;
        double maxCorr = Double.NEGATIVE_INFINITY;
        for (int j = row + 1; j < nVec; j++) {

            dataset.readVector(vec2, j, 0);
            double corr = compareVec(vec1, vec2, pt1, pt2);
            log.info("{} {} {}", row, j, corr);
            if (corr > maxCorr) {
                maxCorr = corr;
                index = j;
            }
        }
        log.info("{} max {} {}", row, index, maxCorr);
        return index;
    }

    public void sortByCorr(final Dataset dataset, int row, final int pt1, final int pt2) throws IOException {
        int nVec = dataset.getSizeTotal(1);
        Vec vec1 = new Vec(dataset.getSizeTotal(0), false);
        Vec vec2 = new Vec(dataset.getSizeTotal(0), false);
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
            log.info("swap {} {}", i, row);
            row = findClosest(dataset, vec1, vec2, i, pt1, pt2);
        }
    }

    public void align(final Dataset dataset, List<Double> deltas) throws IOException {
        List<int[][]> indices = dataset.getIndices(0, 0, dataset.getSizeTotal(0) - 1);
        int[] dim = new int[dataset.getNDim()];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }

        final int vecSize = dataset.getSizeTotal(0);
        indices.stream().parallel().forEach(vi -> {
            Vec vec = new Vec(vecSize);
            try {
                dataset.readVectorFromDatasetFile(vi, dim, vec);
                double delta = deltas.get(vi[1][0]);
                CShift cShift = new CShift((int) Math.round(delta), false);
                cShift.eval(vec);
                dataset.writeVecToDatasetFile(vi, dim, vec);
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
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
        List<int[][]> indices = dataset.getIndices(0, 0, dataset.getSizeTotal(0) - 1);
        int[] dim = new int[dataset.getNDim()];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        int[][] pt = indices.get(row);

        final int vecSize = dataset.getSizeTotal(0);
        Vec fixedVec = new Vec(vecSize);
        dataset.readVectorFromDatasetFile(pt, dim, fixedVec);
        IndexValue indexValue = fixedVec.maxIndex(pt1, pt2);
        double refPt = fixedVec.polyMax(indexValue.getIndex());
        Double[] deltas = new Double[indices.size()];
        indices.stream().parallel().forEach(vi -> {
            Vec vec = new Vec(vecSize);
            try {
                dataset.readVectorFromDatasetFile(vi, dim, vec);
                double delta = alignByMax(vec, refPt, p1, p2);
                dataset.writeVecToDatasetFile(vi, dim, vec);
                deltas[vi[1][0]] = delta;

            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }
        });
        return deltas;
    }

    public Double[] alignByCowStream(final Dataset dataset, final int fixStart, final int fixEnd, final int pStart, int sectionLength, final int iWarp, final int tStart) throws IOException {
        List<int[][]> indices = dataset.getIndices(0, 0, dataset.getSizeTotal(0) - 1);
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
        final int vecSize = dataset.getSizeTotal(0);
        Vec fixedVec = new Vec(fixEnd - fixStart + 1);
        dataset.readVectorFromDatasetFile(startVecPos, dim, fixedVec);
//        indices = indices.subList(0, 2);

        //[vecmat cowcorr $vec1 $vec2 -m $size -t 0 -p $pt1x -w 0
        if (sectionLength <= 0) { // -m
            sectionLength = vecSize / 128;
        }
        final int sL = fixedVec.getSize(); // fixme   XXXXXXXXXXXXXXXXXXXXXXXX
        Double[] deltas = new Double[indices.size()];

        indices.stream().parallel().forEach(vi -> {
            Vec movingVec = new Vec(vecSize);
            try {
                movingVec.setPt(vi, dim);
                dataset.readVector(movingVec);
                double delta = alignByCow(fixedVec, movingVec, pStart, sL, iWarp, tStart);
                dataset.writeVector(movingVec);
                deltas[vi[1][0]] = delta;
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }
        });
        return deltas;
    }

    public int alignByCow(Vec fixedVec, Vec movingVec, int pStart, int m, int iWarp, int tStart) {
        //     public static double cowCorr(Vec src, Vec target, int pStart, int m, int iWarp, int tStart)  {
        int range = 10;
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
            log.warn(aiE.getMessage(), aiE);
        }
        int delta = -iMax;
        CShift cShift = new CShift(delta, false);
        cShift.eval(movingVec);
        return delta;
    }

    public double alignByMax(Vec vec, double refPt, int pt1, int pt2) {
        Vec.IndexValue indexValue = vec.maxIndex(pt1, pt2);
        double delta = refPt - vec.polyMax(indexValue.getIndex());
        CShift cShift = new CShift((int) Math.round(delta), false);
        cShift.eval(vec);
        return delta;
    }

    public double alignByFFT(Vec vecR, Vec vecS, int maxShift, int pt1, int pt2) {
        PositionValue posValue = VecCorrelation.fftCorr(vecR, vecS, maxShift, pt1, pt2);
        return posValue.getPosition();
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

    public class AlignRegion {

        int min;
        int max;

        public AlignRegion(int min, int max) {
            this.min = min;
            this.max = max;
        }
    }

    class Alignment {

        int pt1;
        int pt2;
        int pt1s;
        int pt2s;
        int shift;
        int shift2;
        double corrValue;
        double ratio;
        boolean use;

        public Alignment(int pt1, int pt2, int pt1s, int pt2s, int shift, int shift2, double corrValue, double ratio, boolean use) {
            this.pt1 = pt1;
            this.pt2 = pt2;
            this.pt1s = pt1s;
            this.pt2s = pt2s;
            this.shift = shift;
            this.shift2 = shift2;
            this.corrValue = corrValue;
            this.ratio = ratio;
            this.use = use;
        }

        @Override
        public String toString() {
            return "Alignment{" + "pt1=" + pt1 + ", pt2=" + pt2 + ", pt1s=" + pt1s + ", pt2s=" + pt2s + ", shift=" + shift + ", shift2=" + shift2 + ", corrValue=" + corrValue + ", ratio=" + ratio + ", use=" + use + '}';
        }

    }

    boolean overlapsRegion(RealMatrix rM, int pt1, int pt2) {
        int nRows = rM.getRowDimension();
        boolean overlaps = false;
        for (int iRow = 0; iRow < nRows; iRow++) {
            double r1 = rM.getEntry(iRow, 0);
            double r2 = rM.getEntry(iRow, 1);
            if ((pt1 <= r1) && (pt2 > r1)) {
                overlaps = true;
                break;
            } else if ((pt1 > r1) && (pt1 < r2)) {
                overlaps = true;
                break;
            }
        }
        return overlaps;
    }

    public Vec getAverageVector(final Dataset dataset) throws IOException {
        List<int[][]> indices = dataset.getIndices(0, 0, dataset.getSizeTotal(0) - 1);
        int[] dim = new int[dataset.getNDim()];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        final int vecSize = dataset.getSizeTotal(0);
        Vec avgVec = dataset.readVector(0, 0);
        avgVec.zeros();
        indices.stream().forEach(vi -> {
            Vec movingVec = new Vec(vecSize);
            movingVec.setPt(vi, dim);
            try {
                dataset.readVector(movingVec);
                avgVec.add(movingVec);
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }

        });
        avgVec.scale(1.0 / indices.size());
        return avgVec;

    }

    public Double[] alignBySegmentsStream(final Dataset dataset, final int fixStart, final int fixEnd, int sectionLength, int maxShift, boolean useAverage) throws IOException {
        List<int[][]> indices = dataset.getIndices(0, 0, dataset.getSizeTotal(0) - 1);
        final int[][] skipIndices;
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
        final int vecSize = dataset.getSizeTotal(0);
        Vec fixedVec;
        if (useAverage) {
            fixedVec = getAverageVector(dataset);
            skipIndices = null;
        } else {
            fixedVec = dataset.readVector(0, 0);
            skipIndices = indices.get(0);
        }
//        indices = indices.subList(0, 2);

        //[vecmat cowcorr $vec1 $vec2 -m $size -t 0 -p $pt1x -w 0
        if (sectionLength <= 0) { // -m
            sectionLength = vecSize / 50;
        }
        int prePostValue = sectionLength / 4;
        final int maxShiftFinal = maxShift <= 0 ? prePostValue / 2 : maxShift;
        var regions = new ArrayList<AlignRegion>();
        for (int i = fixStart; i < fixEnd; i += sectionLength) {
            int end = i + sectionLength - 1;
            if (end >= fixEnd) {
                end = fixEnd - 1;
            }
            AlignRegion region = new AlignRegion(i, end);
            regions.add(region);
        }
        final int sL = fixedVec.getSize(); // fixme   XXXXXXXXXXXXXXXXXXXXXXXX
        Double[] deltas = new Double[indices.size()];
        deltas[0] = 0.0;
        indices.stream().filter(vi -> vi != skipIndices).forEach(vi -> {
            Vec movingVec = new Vec(vecSize);
            Vec resultVec = new Vec(vecSize);
            try {
                movingVec.setPt(vi, dim);
                resultVec.setPt(vi, dim);
                dataset.readVector(movingVec);
                segmentedAlign(fixedVec, movingVec, resultVec, prePostValue, prePostValue, maxShiftFinal, regions);
                dataset.writeVector(resultVec);
                deltas[vi[1][0]] = 0.0;
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }
        });
        return deltas;
    }

    public void segmentedAlign(Vec targetVec, Vec sampleVec, Vec resultVec, int preExtraIn, int postExtraIn, int maxShift, int regionMin, int regionMax) {
        var regions = new ArrayList<AlignRegion>();
        var region = new AlignRegion(regionMin, regionMax);
        regions.add(region);
        segmentedAlign(targetVec, sampleVec, resultVec, preExtraIn, postExtraIn, maxShift, regions);

    }

    public void segmentedAlign(Vec targetVec, Vec sampleVec, Vec resultVec, int preExtraIn, int postExtraIn, int maxShift, List<AlignRegion> regions) {
        int sDevWindow = 32;
        int vecSize = targetVec.getSize();
        double sdev = targetVec.sdev(sDevWindow);
        Vec subTarget = new Vec(32);
        Vec subSample = new Vec(32);
        var shift = 0;
        var pt1e = 0;
        var pt2e = 0;
        double corrValue = 0;
        var alignments = new ArrayList<Alignment>();
        for (AlignRegion region : regions) {
            boolean useSection = true;

            int size = region.max - region.min + 1;
            subTarget.resize(size);
            targetVec.copy(subTarget, region.min, size);
            var maxIndex = subTarget.maxIndex();
            var ratio = maxIndex.getValue() / sdev;
            int maxShiftA = maxShift + Math.max(preExtraIn, postExtraIn);
            // Note: originally this method tried three times to calculate the shift to get a shift that was greater
            // than 0 and then used a shift of 0 if the shift was still <0 but the code was commented out so removed it
            pt1e = region.min - preExtraIn;
            pt2e = region.max + postExtraIn;
            pt1e = Math.max(0, pt1e);
            pt2e = Math.min(pt2e, vecSize - 1);
            int sizeE = pt2e - pt1e + 1;
            subSample.resize(sizeE);
            sampleVec.copy(subSample, pt1e, sizeE);
            var posValue = VecCorrelation.fftCorr(subTarget, subSample, 0, maxShiftA);
            corrValue = posValue.getValue() / (size - 1.0);
            shift = posValue.getPosition();

            Alignment alignment = new Alignment(region.min, region.max, pt1e + shift, pt1e + shift + size - 1, shift, shift - preExtraIn, corrValue, ratio, useSection);
            alignments.add(alignment);
        }
        sampleVec.copy(resultVec);
        resultVec.zeros();
        int nAlign = alignments.size();
        var setPts = new ArrayList<Integer>();
        for (int iAlign = 0; iAlign < nAlign; iAlign++) {
            Alignment align = alignments.get(iAlign);
            Alignment alignPre = iAlign > 0 ? alignments.get(iAlign - 1) : null;
            Alignment alignPost = iAlign < nAlign - 1 ? alignments.get(iAlign + 1) : null;
            int segSize = align.pt2 - align.pt1 + 1;
            if (align.use) {
                if (alignPost != null) {
                    int overlapE = align.pt2s - alignPost.pt1s;
                    if ((overlapE > 0) && (align.corrValue < alignPost.corrValue)) {
                        segSize = segSize - overlapE;
                    }
                }
                if (alignPre != null) {
                    int overlapB = alignPre.pt2s - align.pt1s;
                    if ((overlapB > 0) && (align.corrValue < alignPre.corrValue)) {
                        align.pt1s += overlapB;
                        segSize -= overlapB;
                    }
                }
                if (segSize > 0) {
                    if ((align.pt1s + segSize) >= vecSize) {
                        segSize = vecSize - align.pt1s;
                    }
                    int ptA = align.pt1s;
                    int ptB = align.pt1;
                    if (ptA < 0) {
                        segSize += ptA;
                        ptB += -ptA;
                        ptA = 0;
                    }
                    sampleVec.copy(resultVec, ptA, ptB, segSize);
                    setPts.add(ptB);
                    setPts.add(ptB + segSize - 1);
                }

            } else {
                sampleVec.copy(resultVec, align.pt1, align.pt1, segSize);
                setPts.add(align.pt1 + segSize - 1);
            }
        }
        for (int i = 1; i < setPts.size() - 1; i += 2) {
            int pt1 = setPts.get(i);
            int pt2 = setPts.get(i + 1);
            int n = pt2 - pt1;
            if (n > 1) {
                double val1 = resultVec.getReal(pt1);
                double val2 = resultVec.getReal(pt2);
                for (int j = (pt1 + 1); j < pt2; j++) {
                    double f = (j - pt1) / (double) n;
                    double val = f * (val2 - val1) + val1;
                    resultVec.setReal(j, val);
                }
            }
        }
    }
}
