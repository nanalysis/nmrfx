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
    private SimpleIntegerProperty row;
    private SimpleLongProperty date;
    private HashMap<String, String> extras = new HashMap<>();
    private HashMap<String, Integer> intExtras = new HashMap<>();
    private HashMap<String, Double> doubleExtras = new HashMap<>();

    public FileTableItem(String fileName, String seqName, int nDim, long date) {
        this.fileName = new SimpleStringProperty(fileName);
        this.seqName = new SimpleStringProperty(seqName);
        this.nDim = new SimpleIntegerProperty(nDim);
        this.date = new SimpleLongProperty(date);
        this.row = new SimpleIntegerProperty(0);
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

    public Integer getRow() {
        return row.get();
    }

    public void setRow(int rowNum) {
        this.row.set(rowNum);
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

    public Double getDoubleExtra(String eName) {
        Double extra = doubleExtras.get(eName);
        return extra == null ? 0.0 : extra;
    }

    public Integer getIntegerExtra(String eName) {
        Integer extra = intExtras.get(eName);
        return extra == null ? 0 : extra;
    }

    public void setExtra(String name, String value) {
        extras.put(name, value);
    }

    public void setExtra(String name, Integer value) {
        intExtras.put(name, value);
    }

    public void setExtra(String name, Double value) {
        doubleExtras.put(name, value);
    }

    public void setNDim(String eName, String value) {
    }

    public void setTypes(String[] headers, boolean[] notDouble, boolean[] notInteger) {
        for (int i = 0; i < headers.length; i++) {
            if (!notInteger[i]) {
                intExtras.put(headers[i], Integer.parseInt(headers[i]));
                extras.remove(headers[i]);
            } else if (!notDouble[i]) {
                doubleExtras.put(headers[i], Double.parseDouble(headers[i]));
                extras.remove(headers[i]);
            }
        }
    }
}
