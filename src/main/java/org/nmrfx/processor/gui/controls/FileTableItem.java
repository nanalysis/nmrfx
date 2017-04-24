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
package org.nmrfx.processor.gui.controls;

import java.util.HashMap;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 *
 * @author Bruce Johnson
 */
public class FileTableItem {

    private SimpleStringProperty fileName;
    private SimpleStringProperty seqName;
    private SimpleIntegerProperty nDim;
    private SimpleLongProperty date;
    private HashMap<String, String> extras = new HashMap<>();

    public FileTableItem(String fileName, String seqName, int nDim, long date) {
        this.fileName = new SimpleStringProperty(fileName);
        this.seqName = new SimpleStringProperty(seqName);
        this.nDim = new SimpleIntegerProperty(nDim);
        this.date = new SimpleLongProperty(date);
    }

    public FileTableItem(String fileName, String seqName, int nDim, long date, HashMap<String, String> extras) {
        this(fileName, seqName, nDim, date);
        this.extras.putAll(extras);
    }

    public StringProperty fileNameProperty() {
        return fileName;
    }

    public StringProperty seqNameProperty() {
        return seqName;
    }

    public String getFileName() {
        return fileName.get();
    }

    public void setFileName(String fileName) {
        this.fileName.set(fileName);
    }

    public String getSeqName() {
        return seqName.get();
    }

    public void setSeqName(String seqName) {
        this.seqName.set(seqName);
    }

    public Integer getNDim() {
        return nDim.get();
    }

    public void setNDim(int nDim) {
        this.nDim.set(nDim);
    }

    public Long getDate() {
        return date.get();
    }

    public void setDate(long date) {
        this.date.set(date);
    }

    public String getExtra(String eName) {
        String extra = extras.get(eName);
        return extra == null ? "" : extra;
    }

    public void setNDim(String eName, String value) {
    }
}
