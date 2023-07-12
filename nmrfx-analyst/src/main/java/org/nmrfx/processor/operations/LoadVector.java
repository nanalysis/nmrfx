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

import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;
import org.nmrfx.processor.processing.processes.ProcessOps;

/**
 * @author johnsonb
 */
public class LoadVector extends Operation { // not currently used

    private final ProcessOps process;

    /**
     * @param fileName
     * @param dimension Dimension will be decremented by one.
     */
    public LoadVector(ProcessOps process) {
        this.process = process;
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        return this;
    }

    public void setupScanner(String newDataset, int dim) {
        int sizes;
        int nDim;
    }
}
