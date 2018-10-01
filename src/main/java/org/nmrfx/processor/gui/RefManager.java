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
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import org.nmrfx.utils.properties.MenuTextOperationItem;
import org.nmrfx.utils.properties.ChoiceOperationItem;
import org.nmrfx.utils.properties.IntOperationItem;
import org.nmrfx.utils.properties.BooleanOperationItem;
import org.nmrfx.utils.properties.EditableChoiceOperationItem;
import org.nmrfx.utils.properties.TextOperationItem;
import org.nmrfx.processor.datasets.vendor.NMRData;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.controlsfx.control.PropertySheet;
import org.apache.commons.collections4.iterators.PermutationIterator;

/**
 *
 * @author brucejohnson
 */
public class RefManager {

    ChangeListener<Number> doubleListener;
    ChangeListener<Number> intListener;
    ChangeListener<String> stringListener;
    ChangeListener<Boolean> boolListener;
    ChangeListener<String> complexListener;
    ChangeListener<String> listListener;
    PropertySheet refSheet;
    ProcessorController processorController;
    Map<String, Object> refMap = new HashMap<>();
    static public final String[] propNames = {"skip", "label", "acqarray", "acqsize", "tdsize", "sf", "sw", "ref"};
    static final String[] bSFNames = {"SFO1,1", "SFO1,2", "SFO1,3", "SFO1,4", "SFO1,5"};
    static final String[] bSWNames = {"SW_h,1", "SW_h,2", "SW_h,3", "SW_h,4", "SW_h,5"};

    RefManager(ProcessorController processorController, PropertySheet refSheet) {
        this.processorController = processorController;
        this.refSheet = refSheet;
        doubleListener = new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observableValue, Number number, Number number2) {
                updateOp((PropertySheet.Item) observableValue);
            }
        };
        intListener = new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observableValue, Number number, Number number2) {
                updateOp((PropertySheet.Item) observableValue);
            }
        };
        boolListener = new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observableValue, Boolean bool, Boolean bool2) {
                updateOp((PropertySheet.Item) observableValue);
            }
        };
        stringListener = new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String string, String string2) {
                updateOp((PropertySheet.Item) observableValue);
            }
        };
        complexListener = new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String c1, String c2) {
                updateOp((PropertySheet.Item) observableValue);
            }
        };
        listListener = new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String c1, String c2) {
                updateOp((PropertySheet.Item) observableValue);
            }
        };

        refSheet.setPropertyEditorFactory(new NvFxPropertyEditorFactory(processorController));
        refSheet.setMode(PropertySheet.Mode.NAME);
        refSheet.setModeSwitcherVisible(false);
        refSheet.setSearchBoxVisible(false);

    }

    private void updateOp(PropertySheet.Item updateItem) {
        ChartProcessor chartProcessor = processorController.chartProcessor;
        String propName = updateItem.getName();
        String dimName = updateItem.getCategory();
        String nameWithDim = propName + dimName;
        String value = updateItem.getValue().toString();
        if (propName.equals("skip")) {
            if (value.equals("true")) {
                refMap.put(nameWithDim, "1");
            } else {
                refMap.put(nameWithDim, "0");
            }
        } else {
            refMap.put(nameWithDim, value);
        }
        //System.out.println(nameWithDim + " value is " + value + " was " + updateItem.getValue().toString());
        boolean refresh = true;
        if (propName.equals("fixdsp")) {
            Map<String, Boolean> flags = new HashMap<>();
            flags.put("fixdsp", (Boolean) updateItem.getValue());
            chartProcessor.setFlags(flags);
            chartProcessor.setFixDSP((Boolean) updateItem.getValue());
            refresh = true;
        } else if (propName.equals("EchoAntiecho")) {
            boolean boolValue = (Boolean) updateItem.getValue();
            chartProcessor.setEchoAntiEcho(boolValue);
            refresh = true;
        } else if (propName.equals("dataset")) {
            String datasetName = updateItem.getValue().toString();
            chartProcessor.setDatasetName(datasetName);
        } else if (propName.equals("extension")) {
            String extension = updateItem.getValue().toString();
            chartProcessor.setExtension(extension);
        } else if (propName.equals("acqOrder")) {
            String acqOrder = updateItem.getValue().toString();
            chartProcessor.setAcqOrder(acqOrder);
        } else if (propName.equals("acqarray")) {
            String acqArray = updateItem.getValue().toString();
            int dim = 0;
            int arraySize = 0;
            try {
                dim = Integer.parseInt(dimName);
                dim--;
                arraySize = Integer.parseInt(acqArray);
                chartProcessor.setArraySize(dim, arraySize);
            } catch (NumberFormatException nFE) {
                System.out.println("set array size error " + nFE.getMessage());
            }
        }
        chartProcessor.setScriptValid(false);
        if (refresh) {
            ProcessorController pController = processorController;
            if (pController.isViewingDataset()) {
                if  (pController.autoProcess.isSelected()) {
                    processorController.processIfIdle();
                }
            } else {
                chartProcessor.execScriptList(true);
                chartProcessor.getChart().layoutPlotChildren();
            }
        }

    }

    public String getParString(int nDim, String indent) {
        ChartProcessor chartProcessor = processorController.chartProcessor;
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(indent);
        sBuilder.append("acqOrder(");
        sBuilder.append(chartProcessor.getAcqOrder(true));
        sBuilder.append(")");
        sBuilder.append(System.lineSeparator());
        sBuilder.append(indent);
        sBuilder.append("acqarray(");
        sBuilder.append(chartProcessor.getArraySizes());
        sBuilder.append(")");
        sBuilder.append(System.lineSeparator());
        for (String propName : propNames) {
            if (propName.equals("acqarray")) {
                continue;
            }
            sBuilder.append(indent);
            sBuilder.append(propName);
            sBuilder.append("(");
            for (int dim = 0; dim < nDim; dim++) {
                if (dim > 0) {
                    sBuilder.append(",");
                }
                String value = getPropValue(dim, propName, false);
                boolean useString = true;
                // Ending with F or D allows a string to be parsed as a number
                if ((value.length() > 0) && !Character.isLetter(value.charAt(value.length() - 1))) {
                    try {
                        Double.parseDouble(value);
                        useString = false;
                    } catch (NumberFormatException nFE) {
                        useString = true;

                    }
                }
                if (propName.equals("label")) {
                    useString = true;
                }
                if (useString) {
                    sBuilder.append("'");
                    sBuilder.append(value);
                    sBuilder.append("'");
                } else {
                    sBuilder.append(value);
                }
            }
            sBuilder.append(")");
            sBuilder.append(System.lineSeparator());
        }
        return sBuilder.toString();

    }

    String getPropValue(int dim, String propName, boolean getDefault) {
        String dimName = "" + (dim + 1);
        String nameWithDim = propName + dimName;
        NMRData nmrData = getNMRData();
        if (nmrData == null) {
            return "";
        }
        String value;
        if (!getDefault && refMap.containsKey(nameWithDim)) {
            value = refMap.get(nameWithDim).toString();
        } else {
            switch (propName) {
                case "sw":
                    value = nmrData.getSWNames()[dim];
                    break;
                case "skip":
                    if (nmrData.getSize(dim) > 1) {
                        value = "0";
                    } else {
                        value = "1";
                    }
                    break;
                case "tdsize":
                    if (getDefault) {
                        value = String.valueOf(nmrData.getSize(dim));
                    } else {
                        value = "0";
                    }
                    break;
                case "acqsize":
                    if (getDefault) {
                        value = String.valueOf(nmrData.getSize(dim));
                    } else {
                        value = "0";
                    }
                    break;
                case "acqarray":
                    if (getDefault) {
                        value = "0";
                    } else {
                        value = "0";
                    }
                    break;
                case "sf":
                    value = nmrData.getSFNames()[dim];
                    break;
                case "label":
                    value = nmrData.getLabelNames()[dim];
                    break;
                case "ref":
                    if (dim == 0) {
                        value = "";
                    } else {
                        String tn = nmrData.getTN(dim);
                        value = "";
                        for (int i = 0; i < tn.length(); i++) {
                            if (Character.isLetter(tn.charAt(i))) {
                                value = String.valueOf(tn.charAt(i));
                            }
                        }
                    }
                    break;
                default:
                    value = "";
            }

        }
        return value;
    }

    void resetData() {
        refMap.clear();
    }

    void setupItems(int dim) {
        ChartProcessor chartProcessor = processorController.chartProcessor;
        NMRData nmrData = getNMRData();
        ObservableList<PropertySheet.Item> newItems = FXCollections.observableArrayList();
        String dimName = "" + (dim + 1);
        if (dim == 0) {
            newItems.add(new TextOperationItem(stringListener, chartProcessor.getDatasetName(), dimName, "dataset", "Enter the name of the dataset"));
            ArrayList<String> extensionChoices = new ArrayList<>();
            extensionChoices.add(".nv");
            extensionChoices.add(".ucsf");
            newItems.add(new ChoiceOperationItem(stringListener, chartProcessor.getExtension(), extensionChoices, dimName, "extension", "Filename extension (determines dataset type)"));
            if (nmrData != null) {
                ArrayList<String> choices = new ArrayList<>();
                if (nmrData.getNDim() > 1) {
                    ArrayList<Integer> dimList = new ArrayList<>();
                    for (int i = 1; i <= (nmrData.getNDim() - 1); i++) {
                        dimList.add(i);
                    }
                    PermutationIterator permIter = new PermutationIterator(dimList);
                    StringBuilder sBuilder = new StringBuilder();
                    while (permIter.hasNext()) {
                        ArrayList<Integer> permDims = (ArrayList<Integer>) permIter.next();
                        sBuilder.setLength(0);
                        if (nmrData.getVendor().equals("bruker")) {
                            sBuilder.append(nmrData.getNDim());
                        }
                        for (Integer iVal : permDims) {
                            sBuilder.append(iVal);
                        }
                        choices.add(sBuilder.toString());
                    }
                    newItems.add(new EditableChoiceOperationItem(stringListener, chartProcessor.getAcqOrder(), choices, dimName, "acqOrder", "Enter the acquisiton order of the dataset"));
                }
            } else {
                newItems.add(new TextOperationItem(stringListener, chartProcessor.getAcqOrder(), dimName, "acqOrder", "Enter the acquisiton order of the dataset"));
            }
            if ((nmrData != null) && nmrData.getVendor().equals("bruker")) {
                newItems.add(new BooleanOperationItem(boolListener, chartProcessor.getFixDSP(), dimName, "fixdsp", "Fix DSP buildup before FT"));
            }
        }
        //newItems.add(new IntRangeOperationItem(intListener, 3, 0, 5, "op", "opname", "opDesc"));
        for (String propName : propNames) {
            if (propName.equals("skip")) {
                if (dim > 0) {
                    String value = getPropValue(dim, propName, false);
                    boolean boolValue = false;
                    if (value.equals("1")) {
                        boolValue = true;
                    }
                    newItems.add(new BooleanOperationItem(boolListener, boolValue, dimName, propName, "Skip this dimension?"));
                }
            } else if (propName.equals("ref")) {
                String value = getPropValue(dim, propName, false);
                String defaultValue = getPropValue(dim, propName, true);
                if (nmrData != null) {
                    if (dim == 0) {
                        defaultValue = nmrData.getRef(0) + "";
                    }
                }
                String comment = " (default is " + defaultValue + ")";
                newItems.add(new MenuTextOperationItem(stringListener, value, dimName, propName, "Select the " + propName + comment));
            } else if (propName.contains("size")) {
                String value = getPropValue(dim, propName, false);
                int iValue = 0;
                try {
                    iValue = Integer.parseInt(value);
                } catch (NumberFormatException nFe) {

                }
                String defaultValue = getPropValue(dim, propName, true);
                String comment = " (default is " + defaultValue + ")";
                newItems.add(new IntOperationItem(intListener, iValue, 0, 1000000, dimName, propName, "Enter the " + propName + comment));
            } else if (propName.equals("acqarray")) {
                int arraySize = 0;
                if (nmrData != null) {
                    arraySize = nmrData.getArraySize(dim);
                }
                String comment = " (default is " + 0 + ")";
                newItems.add(new IntOperationItem(intListener, arraySize, 0, 1000000, dimName, propName, "Enter the " + propName + comment));
            } else {
                String value = getPropValue(dim, propName, false);
                String defaultValue = getPropValue(dim, propName, true);
                String comment = " (default is " + defaultValue + ")";
                newItems.add(new TextOperationItem(stringListener, value, dimName, propName, "Enter the " + propName + comment));
            }
        }
        refSheet.getItems().setAll(newItems);
    }

    public void setDataFields(List<String> headerList) {
        ChartProcessor chartProcessor = processorController.chartProcessor;
        for (String s : headerList) {
            int index = s.indexOf('(');
            boolean lastIsClosePar = s.charAt(s.length() - 1) == ')';
            if ((index != -1) && lastIsClosePar) {
                String propName = s.substring(0, index);
                String args = s.substring(index + 1, s.length() - 1);
                List<String> parValues = CSVLineParse.parseLine(args);
                if (propName.equals("acqOrder")) {
                    chartProcessor.setAcqOrder(args);
                } else if (propName.equals("acqarray")) {
                    chartProcessor.setArraySize(args);
                } else {
                    int dim = 0;
                    for (String parValue : parValues) {
                        String dimName = "" + (dim + 1);
                        String nameWithDim = propName + dimName;
                        refMap.put(nameWithDim, parValue);
                        dim++;
                    }
                }
            }

        }
    }

    NMRData getNMRData() {
        return processorController.chartProcessor.getNMRData();
    }

    public boolean getSkip(String dimName) {
        int dim = 0;
        String propValue = "0";
        try {
            dim = Integer.parseInt(dimName);
            dim--;
            propValue = getPropValue(dim, "skip", false);
        } catch (NumberFormatException nFE) {
        }
        return propValue.equals("1");
    }
}
