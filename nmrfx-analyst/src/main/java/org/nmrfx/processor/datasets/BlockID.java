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

public final class BlockID {

    private final Dataset theFile;
    private final int fileBlock;
    private final int hashCode;
    private final long accessTime;

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (!(that instanceof BlockID)) {
            return false;
        }
        BlockID thatID = (BlockID) that;
        if (this.fileBlock != thatID.fileBlock) {
            return false;
        }
        return this.theFile.getFileName().equals(thatID.theFile.getFileName());
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public int calculateHashCode() {
        int result = 17;
        result = 37 * result + theFile.getFileName().hashCode();
        result = 37 * result + fileBlock;
        return result;
    }

    public BlockID(Dataset theFile, int fileBlock) {
        this.theFile = theFile;
        this.fileBlock = fileBlock;
        hashCode = calculateHashCode();
        accessTime = System.currentTimeMillis();
    }

    public Dataset getFile() {
        return theFile;
    }

    public int getBlock() {
        return fileBlock;
    }

    @Override
    public String toString() {
        return theFile.getFileName() + ":" + fileBlock;
    }

    public void close() {
    }
}
