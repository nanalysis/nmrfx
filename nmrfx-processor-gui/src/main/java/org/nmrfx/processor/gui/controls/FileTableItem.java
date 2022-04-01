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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javafx.beans.property.IntegerProperty;
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
    private SimpleStringProperty datasetName;
    private SimpleStringProperty seqName;
    private SimpleIntegerProperty nDim;
    private SimpleIntegerProperty row;
    private SimpleLongProperty date;
    private SimpleIntegerProperty group;
    private HashMap<String, String> extras = new HashMap<>();
    private HashMap<String, Integer> intExtras = new HashMap<>();
    private HashMap<String, Double> doubleExtras = new HashMap<>();
    private HashMap<String, Object> objectExtras = new HashMap<>();

    public FileTableItem(String fileName, String seqName, int nDim, long date, int row, String datasetName) {
        this.fileName = new SimpleStringProperty(fileName);
        this.seqName = new SimpleStringProperty(seqName);
        this.nDim = new SimpleIntegerProperty(nDim);
        this.date = new SimpleLongProperty(date);
        this.row = new SimpleIntegerProperty(row);
        this.group = new SimpleIntegerProperty(0);
        this.datasetName = new SimpleStringProperty(datasetName);
    }

    public FileTableItem(String fileName, String seqName, int nDim, long date, int row, String datasetName, HashMap<String, String> extras) {
        this(fileName, seqName, nDim, date, row, datasetName);
        this.extras.putAll(extras);
    }

    public StringProperty fileNameProperty() {
        return fileName;
    }

    public IntegerProperty groupProperty() {
        return group;
    }

    public StringProperty datasetNameProperty() {
        return datasetName;
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

    public int getGroup() {
        return group.get();
    }

    public void setGroup(int group) {
        this.group.set(group);
    }

    public void setDatasetName(String fileName) {
        this.datasetName.set(fileName);
    }

    public String getDatasetName() {
        return datasetName.get();
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

    public Double getDouble(String eName) {
        Double value = 0.0;
        if (eName.equals("group")) {
            value = (double) getGroup();
        } else if (doubleExtras.containsKey(eName)) {
            value = doubleExtras.get(eName);
        } else if (intExtras.containsKey(eName)) {
            value = intExtras.get(eName).doubleValue();
        } else {
            if (eName.equals("row")) {
                value = row.doubleValue();
            } else if (eName.equals("etime")) {
                value = getDate().doubleValue();
            }
        }
        return value;
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

    public void setObjectExtra(String name, Object value) {
        objectExtras.put(name, value);
    }

    public Optional<String> getType(String name) {
        if (intExtras.containsKey(name)) {
            return Optional.of("I");
        } else if (doubleExtras.containsKey(name)) {
            return Optional.of("D");
        } else if (extras.containsKey(name)) {
            return Optional.of("S");
        } else if (objectExtras.containsKey(name)) {
            return Optional.of("O");
        } else {
            return Optional.empty();
        }
    }

    public String getExtraAsString(String name) {
        switch (getType(name).orElse("")) {
            case "I":
                return getIntegerExtra(name).toString();
            case "D":
                return getDoubleExtra(name).toString();
            case "S":
                return extras.get(name);
            case "O":
                return String.valueOf(objectExtras.get(name));
            default:
                return "";
        }
    }

    public Object getObjectExtra(String eName) {
        Object extra = objectExtras.get(eName);
        return extra;
    }

    public void setNDim(String eName, String value) {
    }

    public void setTypes(String[] headers, boolean[] notDouble, boolean[] notInteger) {
        for (int i = 0; i < headers.length; i++) {
            String value = extras.get(headers[i]);
            if (value != null) {
                if (!notInteger[i]) {
                    intExtras.put(headers[i], Integer.parseInt(value));
                    extras.remove(headers[i]);
                } else if (!notDouble[i]) {
                    doubleExtras.put(headers[i], Double.parseDouble(value));
                    extras.remove(headers[i]);
                }
            }
        }
    }

    /*
        private SimpleStringProperty fileName;
    private SimpleStringProperty seqName;
    private SimpleIntegerProperty nDim;
    private SimpleIntegerProperty row;
    private SimpleLongProperty date;

     */
    public String toString(List<String> headers, Map<String, String> columnTypes) {
        StringBuilder sBuilder = new StringBuilder();
        char sepChar = '\t';
        for (String header : headers) {
            if (sBuilder.length() > 0) {
                sBuilder.append(sepChar);
            }
            switch (header.toLowerCase()) {
                case "path": {
                    sBuilder.append(getFileName());
                    break;
                }
                case "sequence": {
                    sBuilder.append(getSeqName());
                    break;
                }
                case "row": {
                    sBuilder.append(getRow());
                    break;
                }
                case "dataset": {
                    sBuilder.append(getDatasetName());
                    break;
                }
                case "ndim": {
                    sBuilder.append(getNDim());
                    break;
                }
                case "etime": {
                    sBuilder.append(getDate());
                    break;
                }
                default: {
                    String type = columnTypes.get(header);
                    if (type == null) {
                        type = "S";
                    }
                    switch (type) {
                        case "D": {
                            sBuilder.append(getDoubleExtra(header));
                            break;
                        }
                        case "I": {
                            sBuilder.append(getIntegerExtra(header));
                            break;
                        }
                        default: {
                            sBuilder.append(getExtra(header));
                        }

                    }

                }
            }
        }
        return sBuilder.toString();
    }

    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(fileName.get()).append(" ");
        sBuilder.append(seqName.get());
        sBuilder.append(" ");
        sBuilder.append(nDim.get());
        sBuilder.append(" ");
        sBuilder.append(row.get());
        sBuilder.append(" ");
        sBuilder.append(datasetName.get());
        sBuilder.append(" ");
        sBuilder.append(date.get());
        sBuilder.append(" ");
        sBuilder.append(extras.toString());
        sBuilder.append(" ");
        sBuilder.append(doubleExtras.toString());
        sBuilder.append(" ");
        sBuilder.append(intExtras.toString());
        return sBuilder.toString();
    }
}
