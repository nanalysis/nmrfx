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
package org.nmrfx.processor.processing;

import org.nmrfx.processor.operations.Hft;
import org.nmrfx.processor.operations.Phase;
import org.nmrfx.processor.operations.Real;
import org.nmrfx.processor.operations.WriteVector;
import org.nmrfx.processor.processing.processes.ProcessOps;

/**
 * @author johnsonb
 */
public class Test {

    public static void main(String[] args) {
        double p0 = 25.0, p1 = 10.0;

        Processor processor = Processor.getProcessor();
        ProcessOps defaultProcess = processor.getDefaultProcess();

        processor.opendata("/home/johnsonb/Development/NMRView/9.1/dcengine/target/dcengine-9.1.0-b1-bin/dcengine-9.1.0-b1/cnnoe-py.nv", true);

        defaultProcess.addOperation(new Hft());
        defaultProcess.addOperation(new Phase(p0, p1, false));
        defaultProcess.addOperation(new Real());
        defaultProcess.addOperation(new WriteVector());

        long start = System.currentTimeMillis();
        processor.run();

        long stop = System.currentTimeMillis();

        System.out.println("Runtime: " + (stop - start) + " milliseconds");
        ;
    }
}
