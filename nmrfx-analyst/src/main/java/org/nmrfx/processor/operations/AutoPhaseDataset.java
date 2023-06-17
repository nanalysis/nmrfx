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
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DatasetPhaser;
import org.nmrfx.processor.processing.ProcessingException;

import java.io.IOException;

/**
 * @author Bruce Johnson
 */
@PythonAPI("pyproc")
public class AutoPhaseDataset extends DatasetOperation {

    private final int iDim;
    private final boolean firstOrder;
    private final int winSize;
    private final double ratio;
    private final double ph1Limit;

    public AutoPhaseDataset(int iDim, boolean firstOrder, int winSize, double ratio, double ph1Limit) {
        this.iDim = iDim;
        this.firstOrder = firstOrder;
        this.winSize = winSize;
        this.ratio = ratio;
        this.ph1Limit = ph1Limit;
    }

    @Override
    public Operation evalDataset(Dataset dataset) throws ProcessingException {
        phaseDataset(dataset);
        return this;
    }

    public void phaseDataset(Dataset dataset) throws ProcessingException {
        if (iDim == -1) {
            int nDims = dataset.getNDim();
            for (int phaseDim = 0; phaseDim < nDims; phaseDim++) {
                if (dataset.getFreqDomain(phaseDim)) {
                    phaseDim(dataset, phaseDim);
                }
            }
        } else {
            phaseDim(dataset, iDim);
        }
    }

    void phaseDim(Dataset dataset, int phaseDim) throws ProcessingException {
        DatasetPhaser phaser = new DatasetPhaser(dataset);
        try {
            phaser.setup(phaseDim, winSize, ratio, IDBaseline2.ThreshMode.SDEV);
            if (firstOrder) {
                double[] phases = phaser.getPhase(ph1Limit);
                phaser.applyPhases2(phaseDim, phases[0], phases[1]);
            } else {
                double phase = phaser.getPhaseZero();
                phaser.applyPhases2(phaseDim, phase, 0.0);
            }
        } catch (IOException ioE) {
            throw new ProcessingException(ioE.getMessage());
        }
    }

}
