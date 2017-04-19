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
package org.nmrfx.processor.datasets;

import java.io.IOException;

/**
 * Interface for memory-mapped matrix files.
 *
 * @author brucejohnson
 */
public interface MappedMatrixInterface {

    /**
     * Return whether matrix can be written. Will depend on underlying file.
     *
     * @return true if matrix can be written
     */
    public boolean isWritable();

    /**
     * Return the index in file that corresponds to offsets specified for the various dimensions
     *
     * @param offsets the offsets for each dimension
     * @return the position in file
     */
    public long position(int... offsets);

    /**
     * Return the size of the dimension
     *
     * @param dim dimension index
     * @return the size
     */
    public int getSize(final int dim);

    /**
     * Return the number of elements in file (not including header)
     *
     * @return the size
     */
    public long getTotalSize();

    /**
     * Get a value as a float at specified position in matrix
     *
     * @param offsets the position
     * @return the value
     * @throws IOException if an I/O error occurs
     */
    public float getFloat(int... offsets) throws IOException;

    /**
     * Set the value at a specified position in matrix
     *
     * @param value the value to set
     * @param offsets the position
     * @throws IOException if an I/O error occurs
     */
    public void setFloat(float value, int... offsets) throws IOException;

    /**
     * Close the underlying file
     *
     * @throws IOException if an I/O error occurs
     */
    public void close() throws IOException;

    /**
     * Return the sum of data values in file. Used for testing.
     *
     * @return the sum of data values
     * @throws IOException if an I/O error occurs
     */
    public double sum() throws IOException;

    /**
     * Return the sum of data values in file. Used for testing. May use a faster method than sum, but skip error
     * checking.
     *
     * @return sum of data values
     * @throws IOException if an I/O error occurs
     */
    public double sumFast() throws IOException;

    /**
     * Set all values in file to zero.
     *
     * @throws IOException if an I/O error occurs
     */
    public void zero() throws IOException;

    /**
     * Call force on mapping buffer
     */
    public void force();
}
