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
package org.nmrfx.processor.operations;

import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.math.VecBase;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.math.GRINS;
import org.nmrfx.processor.math.MatrixND;
import org.nmrfx.processor.processing.ProcessingException;
import org.nmrfx.processor.processing.SampleSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.nmrfx.processor.operations.IstMatrix.genSrcTargetMap;

/**
 * @author Bruce Johnson
 */
@PythonAPI("pyproc")
public class DGRINSOp extends DatasetOperation {

    private static final Logger log = LoggerFactory.getLogger(DGRINSOp.class);

    private final SampleSchedule schedule;
    private final double noise;
    private final double scale;
    private final double[] phase;

    private final double shapeFactor = 0.5;

    public DGRINSOp(SampleSchedule schedule, double noise, double scale) {
        this.schedule = schedule;
        this.noise = noise;
        this.scale = scale;
        this.phase = null;

    }

    @Override
    public Operation evalDataset(Dataset dataset) throws ProcessingException {
        smileDataset(dataset);
        return this;
    }

    public void smileDataset(Dataset dataset) throws ProcessingException {
        int[] dim = new int[dataset.getNDim()];
        for (int i = 0; i < dataset.getNDim() - 1; i++) {
            dim[i] = i + 1;
        }
        int size0 = dataset.getSizeTotal(0);
        for (int i = 0; i < size0; i++) {
            MatrixND matrix = getMatrixNDFromFile(dataset, dim, i);
            int[] zeroList = IstMatrix.genZeroList(schedule, matrix);
            int[] srcTargetMap = genSrcTargetMap(schedule, matrix);
            GRINS smile = new GRINS(matrix, noise, scale,128, shapeFactor, false, phase, true, false, zeroList, srcTargetMap, null);
            smile.exec();
            try {
                dataset.writeMatrixNDToDatasetFile(dim, matrix);
            } catch (IOException ioE) {
                throw new ProcessingException(ioE.getMessage());
            }
        }
    }

    public synchronized MatrixND getMatrixNDFromFile(Dataset dataset, int[] dim, int matrixCount) {
        MatrixND matrix = null;
        // zerofill matrix size for processing and writing

        int[][] writePt = calcPt(dataset, dim);
        int[][] pt = calcPt(dataset, dim);
        int[] matrixSizes = new int[pt.length - 1];
        for (int i = 0; i < pt.length - 1; i++) {
            int k = pt.length - i - 2;
            matrixSizes[k] = VecBase.checkPowerOf2(1 + pt[i][1]);
            writePt[i][1] = matrixSizes[pt.length - i - 2] - 1;
            System.out.print(writePt[i][1] + " ");
        }
        System.out.println("");

        writePt[pt.length - 1][0] = matrixCount;
        pt[pt.length - 1][0] = matrixCount;

        try {
            matrix = new MatrixND(writePt, matrixSizes);
            dataset.readMatrixND(pt, dim, matrix);
        } catch (IOException ex) {
            log.warn(ex.getMessage(), ex);
        }

        return matrix;
    }

    private int[][] calcPt(Dataset dataset, int[] dim) {        // see setDim()
        int[][] pt = new int[dataset.getNDim()][2];
        for (int i = 0; i < dataset.getNDim(); ++i) {  // read dims from dataset
            pt[i][0] = 0;
            if (dim[i] == 0) {
                pt[i][1] = dataset.getSizeTotal(dim[i]) - 1;
            } else {
                pt[i][1] = dataset.getVSize(dim[i]) - 1;
                // fixme should we use vsize this.pt[i][1] = dataset.getVSize_r(dim[i]) - 1;
            }
        }
        return pt;
    }

}
