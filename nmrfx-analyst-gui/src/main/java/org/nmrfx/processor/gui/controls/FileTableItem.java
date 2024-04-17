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

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.paint.Color;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Bruce Johnson
 */
public class FileTableItem {

    private final SimpleStringProperty fileName;
    private final SimpleStringProperty datasetName;
    private final SimpleStringProperty seqName;
    private final SimpleIntegerProperty nDim;
    private final SimpleIntegerProperty row;
    private final SimpleLongProperty date;
    private final SimpleIntegerProperty group;
    private SimpleObjectProperty<DatasetAttributes> datasetAttr;
    private final HashMap<String, String> extras = new HashMap<>();
    private final HashMap<String, Integer> intExtras = new HashMap<>();
    private final HashMap<String, Double> doubleExtras = new HashMap<>();
    private final HashMap<String, Object> objectExtras = new HashMap<>();

    public FileTableItem(String fileName, String seqName, int nDim, long date, int row, String datasetName) {
        this.fileName = new SimpleStringProperty(fileName);
        this.seqName = new SimpleStringProperty(seqName);
        this.nDim = new SimpleIntegerProperty(nDim);
        this.date = new SimpleLongProperty(date);
        this.row = new SimpleIntegerProperty(row);
        this.group = new SimpleIntegerProperty(0);
        this.datasetName = new SimpleStringProperty(datasetName);
    }

    public FileTableItem(String fileName, String seqName, int nDim, long date, int row, String datasetName, Map<String, String> extras) {
        this(fileName, seqName, nDim, date, row, datasetName);
        this.extras.putAll(extras);
    }

    public String getFileName() {
        return fileName.get();
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

    public Integer getNDim() {
        return nDim.get();
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

    public void setNeg(boolean value) {
        if (datasetAttr != null) {
            datasetAttr.get().setNeg(value, getRow() - 1);
        }
    }

    public boolean getNeg() {
        var dataAttr = getDatasetAttributes();
        return dataAttr != null && dataAttr.getNeg(getRow() - 1);
    }

    public boolean getPos() {
        var dataAttr = getDatasetAttributes();
        return dataAttr != null && dataAttr.getPos(getRow() - 1);
    }

    public void setPos(Boolean value) {
        var dataAttr = getDatasetAttributes();
        if (dataAttr != null) {
            dataAttr.setPos(value, getRow() - 1);
        }
    }

    public Color getColor(boolean posColorMode) {
        return posColorMode ? getPosColor() : getNegColor();
    }

    public void setColor(Color color, boolean posColorMode) {
        if (posColorMode) {
            setPosColor(color);
        } else {
            setNegColor(color);
        }
    }

    public Color getPosColor() {
        var dataAttr = getDatasetAttributes();
        return dataAttr == null ? Color.BLACK : dataAttr.getPosColor(getRow() - 1);
    }

    public void setPosColor(Color color) {
        var dataAttr = getDatasetAttributes();
        if (dataAttr != null) {
            dataAttr.setPosColor(color, getRow() - 1);
        }
    }

    public Color getNegColor() {
        var dataAttr = getDatasetAttributes();
        return dataAttr == null ? Color.RED : dataAttr.getNegColor();
    }

    public void setNegColor(Color color) {
        var dataAttr = getDatasetAttributes();
        if (dataAttr != null) {
            dataAttr.setNegColor(color);
        }
    }

    public boolean getSelected() {
        var dataAttr = getDatasetAttributes();
        return dataAttr == null || dataAttr.isSelected(getRow() - 1);
    }

    public void setSelected(boolean state) {
        var dataAttr = getDatasetAttributes();
        if (dataAttr != null) {
            dataAttr.setSelectedElem(getRow() - 1, state);
        }
    }

    public void setLvl(double value) {
        var dataAttr = getDatasetAttributes();
        if (dataAttr != null) {
            dataAttr.setLvl(value);
        }
    }

    public double getLvl() {
        var dataAttr = getDatasetAttributes();
        return dataAttr != null ? dataAttr.getLvl() : 0.0;
    }

    public void setClm(double value) {
        if (datasetAttr != null) {
            datasetAttr.get().setClm(value);
        }
    }

    public double getClm() {
        var dataAttr = getDatasetAttributes();
        return dataAttr != null ? dataAttr.getClm() : 1.2;
    }

    public void setOffset(double value) {
        if (datasetAttr != null) {
            datasetAttr.get().setOffset(value);
        }
    }

    public double getOffset() {
        var dataAttr = getDatasetAttributes();
        return dataAttr != null ? dataAttr.getOffset() : 0.0;
    }

    public void setNlvls(int value) {
        if (datasetAttr != null) {
            datasetAttr.get().setNlvls(value);
        }
    }

    public int getNlvls() {
        var dataAttr = getDatasetAttributes();
        return dataAttr != null ? dataAttr.getNlvls() : 1;
    }

    public DatasetAttributes getDatasetAttributes() {
        return datasetAttr == null ? null : datasetAttr.get();
    }

    public void setDatasetAttributes(DatasetAttributes datasetAttributes) {
        this.datasetAttr = new SimpleObjectProperty<>(datasetAttributes);
    }

    public String getExtra(String eName) {
        String extra = extras.get(eName.toLowerCase());
        return extra == null ? "" : extra;
    }

    public Double getDoubleExtra(String eName) {
        Double extra = doubleExtras.get(eName.toLowerCase());
        return extra == null ? 0.0 : extra;
    }

    public Double getDouble(String eName) {
        eName = eName.toLowerCase();
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
        Integer extra = intExtras.get(eName.toLowerCase());
        return extra == null ? 0 : extra;
    }

    public void setExtra(String name, String value) {
        extras.put(name.toLowerCase(), value);
    }

    public void setExtra(String name, Integer value) {
        intExtras.put(name.toLowerCase(), value);
    }

    public void setExtra(String name, Double value) {
        doubleExtras.put(name.toLowerCase(), value);
    }

    public void setObjectExtra(String name, Object value) {
        objectExtras.put(name.toLowerCase(), value);
    }

    public Optional<String> getType(String name) {
        name = name.toLowerCase();
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
        name = name.toLowerCase();
        return switch (getType(name).orElse("")) {
            case "I" -> getIntegerExtra(name).toString();
            case "D" -> getDoubleExtra(name).toString();
            case "S" -> extras.get(name);
            case "O" -> String.valueOf(objectExtras.get(name));
            default -> "";
        };
    }

    public Optional<Double> getExtraAsDouble(String name) {
        name = name.toLowerCase();
        Double value = switch (getType(name).orElse("")) {
            case "I" -> getIntegerExtra(name).doubleValue();
            case "D" -> getDoubleExtra(name);
            default -> null;
        };
        return Optional.ofNullable(value);
    }

    public Object getObjectExtra(String eName) {
        return objectExtras.get(eName.toLowerCase());
    }

    public void setTypes(String[] headers, boolean[] notDouble, boolean[] notInteger) {
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].toLowerCase();
            String value = extras.get(header);
            if (value != null) {
                if (!notInteger[i]) {
                    intExtras.put(header, Integer.parseInt(value));
                    extras.remove(header);
                } else if (!notDouble[i]) {
                    doubleExtras.put(header, Double.parseDouble(value));
                    extras.remove(header);
                }
            }
        }
    }

    public String toString(List<String> headers, Map<String, String> columnTypes) {
        StringBuilder sBuilder = new StringBuilder();
        char sepChar = '\t';
        boolean first = true;
        for (String header : headers) {
            if (!first) {
                sBuilder.append(sepChar);
            }
            switch (header.toLowerCase()) {
                case "path" -> sBuilder.append(getFileName());
                case "sequence" -> sBuilder.append(getSeqName());
                case "row" -> sBuilder.append(getRow());
                case "dataset" -> sBuilder.append(getDatasetName());
                case "ndim" -> sBuilder.append(getNDim());
                case "etime" -> sBuilder.append(getDate());
                default -> {
                    String type = columnTypes.get(header);
                    if (type == null) {
                        type = "S";
                    }
                    switch (type) {
                        case "D" -> sBuilder.append(getDoubleExtra(header.toLowerCase()));
                        case "I" -> sBuilder.append(getIntegerExtra(header.toLowerCase()));
                        default -> sBuilder.append(getExtra(header.toLowerCase()));
                    }

                }
            }
            first = false;
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
        sBuilder.append(extras);
        sBuilder.append(" ");
        sBuilder.append(doubleExtras);
        sBuilder.append(" ");
        sBuilder.append(intExtras);
        return sBuilder.toString();
    }
}
