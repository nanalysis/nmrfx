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
package org.nmrfx.processor.processing.processes;

import org.nmrfx.processor.processing.Processor;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author johnsonb
 */
public class DebugThread implements Callable<Object> {

    /**
     * Execute all of the operations in the pool.
     */
    @Override
    public Object call() {
        Processor processor = Processor.getProcessor();

        while (!processor.getEndOfFile()) {
            try {
                System.out.println("Unprocessed | Processed");
//                System.out.println(processor.getUnprocessedVectorQueueSize() + " | "
//                        + processor.getProcessedVectorQueueSize());
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                Logger.getLogger(DebugThread.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        return this;
    }
}
