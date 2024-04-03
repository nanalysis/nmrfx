/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.optimization;

import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.util.FastMath;
import org.nmrfx.processor.datasets.peaks.SyncPar;
import org.nmrfx.processor.processing.LineShapeCatalog;

import java.util.List;

/**
 * @author brucejohnson
 */
public class LorentzGaussNDWithCatalog extends LorentzGaussND {

    LineShapeCatalog lsCatalog;
    double[][][] catValues;
    int[][] offsets;

    public LorentzGaussNDWithCatalog(final int[][] positions, LineShapeCatalog lsCatalog) {
        super(positions);
        this.lsCatalog = lsCatalog;
    }

    @Override
    public double valueWithUnScaled(final double[] pars) {
        double sum = 0.0;
        for (int iSig = 0; iSig < nSignals; iSig++) {
            int iPar = sigStarts[iSig];
            iPar++; // amplitude
            if (intensities.length > 1) {
                iPar += 2;
            }
            for (int iDim = 0; iDim < nDim; iDim++) {
                double lw = pars[iPar++];
                double freq = pars[iPar++];
                catValues[iSig][iDim] = lsCatalog.interpolate(iDim, freq, lw);
                offsets[iSig][iDim] = (int) Math.round(freq);
            }
        }
        for (int iDelay = 0; iDelay < nDelays; iDelay++) {
            for (int i = 0; i < positions.length; i++) {
                double y = pars[0];
                for (int iSig = 0; iSig < nSignals; iSig++) {
                    int iPar = sigStarts[iSig];
                    double amplitude = pars[iPar++];

                    double base = 0.0;
                    if (intensities.length > 1) {
                        amplitude *= FastMath.exp(-1.0 * delays[iDelay] / pars[iPar++]);
                        base = pars[iPar++];
                    }
                    double val = amplitude;
                    for (int iDim = 0; iDim < nDim; iDim++) {
                        int pos = positions[i][iDim];
                        int offset = offsets[iSig][iDim];
                        int maxPos = catValues[iSig][iDim].length / 2;
                        int index = pos - offset + maxPos;
                        if ((index >= 0) && index < catValues[iSig][iDim].length) {
                            val *= catValues[iSig][iDim][index];
                        } else {
                            val = 0.0;
                        }
                    }
                    y += val;
                    y += base;
                }
                double delta = intensities[iDelay][i] - y;
                sum += FastMath.abs(delta);
            }

        }

        double result = sum / (positions.length * nDelays);
        if ((best == null) || (best.getValue() > result)) {
            best = new PointValuePair(pars, result);
        }
        return result;
    }

    public final void setOffsets(final double[] start, final double[] lower,
                                 final double[] upper, boolean[] floating, List<SyncPar> syncPars) {
        super.setOffsets(start, lower, upper, floating, syncPars);
        catValues = new double[nSignals][nDim][];
        offsets = new int[nSignals][nDim];
    }
}
