/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.dataops;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.stat.descriptive.rank.Max;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.commons.math3.stat.descriptive.summary.Sum;
import org.apache.commons.math3.util.MathArrays;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.math.Vec;

/**
 *
 * @author Bruce Johnson
 */
public class Normalize {

    public enum NORMALIZE {
        SUM() {
            MathArrays.Function getInstance() {
                return new Sum();
            }
        },
        MEDIAN() {
            MathArrays.Function getInstance() {
                return new Median();
            }
        },
        MAX() {
            MathArrays.Function getInstance() {
                return new Max();
            }
        };

        abstract MathArrays.Function getInstance();
    }

    public synchronized MathArrays.Function getFunction() {
        MathArrays.Function function = new Percentile(50);
        return function;
    }

    public Double[] normalizeByStream(final Dataset dataset, final int iRow, final int regionStart, final int regionEnd, String methodName) throws IOException {
        NORMALIZE nMethod = NORMALIZE.valueOf(methodName.toUpperCase());
        int iDim = 0;
        final int p1;
        final int p2;
        if (regionStart > regionEnd) {
            p1 = regionEnd;
            p2 = regionStart;
        } else {
            p1 = regionStart;
            p2 = regionEnd;
        }
        List<int[][]> indices = dataset.getIndices(iDim, 0, dataset.getSize(iDim) - 1);
        int[] dim = new int[dataset.getNDim()];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        int[][] pt = indices.get(iRow);
        final int vecSize = dataset.getSize(iDim);
        Vec fixedVec = new Vec(vecSize);
        dataset.readVecFromDatasetFile(pt, dim, fixedVec);
        int nPoints = p2 - p1 + 1;
        double[] values = new double[nPoints];
        fixedVec.getReal(values, p1);
        final double fixValue = nMethod.getInstance().evaluate(values);
        Double[] scales = new Double[indices.size()];
        indices.stream().parallel().forEach(vi -> {
            Vec movingVec = new Vec(vecSize);
            try {
                movingVec.setPt(vi, dim);
                dataset.readVector(movingVec);
                double[] mvalues = new double[nPoints];
                movingVec.getReal(mvalues, p1);
                double moveValue = nMethod.getInstance().evaluate(mvalues);
                double scale = fixValue / moveValue;
                movingVec.scale(scale);
                scales[vi[1][0]] = scale;

                dataset.writeVector(movingVec);
            } catch (IOException ex) {
                Logger.getLogger(Align.class.getName()).log(Level.SEVERE, null, ex);
                System.out.println(ex.getMessage());
            }
        });
        return scales;
    }

    public void normalizeByStream(final Dataset dataset, final int iRow, final int regionStart, final int regionEnd, List<Double> scales) throws IOException {
        int iDim = 0;
        final int p1;
        final int p2;
        if (regionStart > regionEnd) {
            p1 = regionEnd;
            p2 = regionStart;
        } else {
            p1 = regionStart;
            p2 = regionEnd;
        }
        List<int[][]> indices = dataset.getIndices(iDim, 0, dataset.getSize(iDim) - 1);
        int[] dim = new int[dataset.getNDim()];
        for (int i = 0; i < dim.length; i++) {
            dim[i] = i;
        }
        int[][] pt = indices.get(iRow);
        final int vecSize = dataset.getSize(iDim);
        indices.stream().parallel().forEach(vi -> {
            Vec movingVec = new Vec(vecSize);
            try {
                movingVec.setPt(vi, dim);
                dataset.readVector(movingVec);
                movingVec.scale(scales.get(vi[1][0]));
                dataset.writeVector(movingVec);
            } catch (IOException ex) {
                Logger.getLogger(Align.class.getName()).log(Level.SEVERE, null, ex);
                System.out.println(ex.getMessage());
            }
        });
    }

    void normalize(Vec movingVec) {

    }
}
