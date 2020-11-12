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

 /*
 * DatasetListener.java
 *
 * Created on February 9, 2006, 11:26 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.nmrfx.datasets;

import org.nmrfx.datasets.DatasetBase;

/**
 *
 * @author brucejohnson
 */
public interface DatasetListener {

    void datasetAdded(DatasetBase dataset);
    
    void datasetModified(DatasetBase dataset);

    void datasetRemoved(DatasetBase dataset);

    void datasetRenamed(DatasetBase dataset);
}
