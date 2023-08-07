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

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.apache.commons.collections4.iterators.PermutationIterator;
import org.controlsfx.control.textfield.CustomTextField;
import org.nmrfx.processor.datasets.DatasetType;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author brucejohnson
 */
public class RefManager {

    private static final Logger log = LoggerFactory.getLogger(RefManager.class);
    TitledPane referencePane;
    ProcessorController processorController;
    Map<String, Object> controlsMap = new HashMap<>();
    Map<DataProps, ToggleButton> toggleButtons = new HashMap<>();

    enum DataProps {
        LABEL("Label", false) {
            String getValue(NMRData nmrData, int iDim) {
                return nmrData.getLabelNames()[iDim];
            }
        },
        TDSIZE("TDSize", true) {
            String getValue(NMRData nmrData, int iDim) {
                return String.valueOf(nmrData.getSize(iDim));
            }
        },
        ACQSIZE("AcqSize", true) {
            String getValue(NMRData nmrData, int iDim) {
                return String.valueOf(nmrData.getSize(iDim));
            }
        },
        SF("Frequency (MHz)", true) {
            String getValue(NMRData nmrData, int iDim) {
                return String.valueOf(nmrData.getSF(iDim));
            }
        },
        SW("Sweep Width", true) {
            String getValue(NMRData nmrData, int iDim) {
                return String.valueOf(nmrData.getSW(iDim));
            }
        },
        REF("Reference", false) {
            String getValue(NMRData nmrData, int iDim) {
                String value;
                if (iDim == 0) {
                    value = "";
                } else {
                    String tn = nmrData.getTN(iDim);
                    value = "";
                    for (int i = 0; i < tn.length(); i++) {
                        if (Character.isLetter(tn.charAt(i))) {
                            value = String.valueOf(tn.charAt(i));
                        }
                    }
                }
                return value;
            }
        },
        SKIP("Skip", true) {
            String getValue(NMRData nmrData, int iDim) {
                return "0";
            }
        };
        final String title;
        final boolean locked;

        DataProps(String title, boolean locked) {
            this.title = title;
            this.locked = locked;
        }

        abstract String getValue(NMRData nmrData, int iDim);

        String getString(Map<String, Object> textFieldMap, int iDim) {
            Object field = textFieldMap.get(name() + iDim);
            if (field instanceof TextField textField) {
                return textField.getText().trim();
            } else if (field instanceof ReferenceMenuTextField referenceMenuTextField) {
                return referenceMenuTextField.getTextField().getText();
            } else if (field instanceof CheckBox checkBox) {
                return checkBox.isSelected() ? "True" : "False";
            } else {
                return "";
            }
        }
    }

    static public final String[] propNames = {"skip", "label", "acqarray", "acqsize", "tdsize", "sf", "sw", "ref"};
    static final String[] bSFNames = {"SFO1,1", "SFO1,2", "SFO1,3", "SFO1,4", "SFO1,5"};
    static final String[] bSWNames = {"SW_h,1", "SW_h,2", "SW_h,3", "SW_h,4", "SW_h,5"};

    RefManager(ProcessorController processorController, TitledPane referencePane) {
        this.processorController = processorController;
        this.referencePane = referencePane;
    }

    public String getCurrentValues(DataProps dataProps, int nDim, String indent) {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(indent);
        sBuilder.append(dataProps.name().toLowerCase(Locale.ROOT));
        sBuilder.append("(");

        for (int dim = 0; dim < nDim; dim++) {
            if (dim > 0) {
                sBuilder.append(",");
            }
            String value = getCurrentValue(dataProps, dim);
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
            if ((value.equals("False") || value.equals("True"))) {
                useString = false;
            }
            if (dataProps == DataProps.LABEL) {
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
        return sBuilder.toString();
    }

    public String getCurrentValue(DataProps dataProps, int iDim) {
        return dataProps.getString(controlsMap, iDim);
    }

    private void updateEditable(ToggleButton toggleButton, DataProps dataProp, NMRData nmrData, int nDim) {
        if (toggleButton.isSelected()) {
            for (int iDim = 0; iDim < nDim; iDim++) {
                Object field = controlsMap.get(dataProp.name() + iDim);
                if (field instanceof TextField textField) {
                    textField.setText(dataProp.getValue(nmrData, iDim));
                } else if (field instanceof ReferenceMenuTextField referenceMenuTextField) {
                    referenceMenuTextField.setText(dataProp.getValue(nmrData, iDim));
                } else if (field instanceof CheckBox checkBox) {
                } else {
                }
            }
        }
    }

    private void setControlValue(Object control, String value) {
        if (control instanceof TextField textField) {
            textField.setText(value);
        } else if (control instanceof ReferenceMenuTextField referenceMenuTextField) {
            referenceMenuTextField.setText(value);
        } else if (control instanceof CheckBox checkBox) {
            checkBox.setSelected(Boolean.parseBoolean(value.toLowerCase()));
        } else {
        }
    }

    private void invalidateScript() {
        processorController.chartProcessor.setScriptValid(false);
        refresh();
    }

    private ComboBox<String> setupAcqOrder(NMRData nmrData) {
        ComboBox<String> choiceBox = new ComboBox();
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
                    if (nmrData.getVendor().equals("bruker") || nmrData.getVendor().equals("rs2d")) {
                        sBuilder.append(nmrData.getNDim());
                    }
                    for (Integer iVal : permDims) {
                        sBuilder.append(iVal);
                    }
                    choices.add(sBuilder.toString());
                }
                choiceBox.getItems().addAll(choices);
                choiceBox.setEditable(true);
                choiceBox.setValue(processorController.chartProcessor.getAcqOrder());
                choiceBox.valueProperty().addListener(e -> invalidateScript());
            }
        }
        return choiceBox;
    }


    private void refresh() {
        ChartProcessor chartProcessor = processorController.chartProcessor;
        if (processorController.isViewingDataset()) {
            if (processorController.autoProcess.isSelected()) {
                processorController.processIfIdle();
            }
        } else {
            chartProcessor.execScriptList(true);
            chartProcessor.getChart().layoutPlotChildren();
        }
    }

    private void updateDatasetChoice(DatasetType dataType) {
        ChartProcessor chartProcessor = processorController.chartProcessor;
        if (dataType != chartProcessor.getDatasetType()) {
            processorController.unsetDatasetName();
        }
        chartProcessor.setDatasetType(dataType);
        processorController.updateFileButton();
    }

    public void updateReferencePane(NMRData nmrData, int nDim) {
        VBox vBox = new VBox();
        vBox.setSpacing(10);
        Label dataTypeLabel = new Label("Output Type");
        dataTypeLabel.setPrefWidth(100);
        dataTypeLabel.setAlignment(Pos.CENTER_LEFT);
        HBox datatypeBox = new HBox();
        datatypeBox.setAlignment(Pos.CENTER_LEFT);
        ChoiceBox<DatasetType> dataChoice = new ChoiceBox<>();
        dataChoice.getItems().addAll(DatasetType.values());
        datatypeBox.getChildren().addAll(dataTypeLabel, dataChoice);
        dataChoice.setValue(processorController.chartProcessor.getDatasetType());
        dataChoice.setOnAction(e -> updateDatasetChoice(dataChoice.getValue()));

        Label acqOrderLabel = new Label("Acq. Order");
        acqOrderLabel.setPrefWidth(100);
        acqOrderLabel.setAlignment(Pos.CENTER_LEFT);
        HBox acqOrderBox = new HBox();
        acqOrderBox.setAlignment(Pos.CENTER_LEFT);
        acqOrderBox.getChildren().addAll(acqOrderLabel, setupAcqOrder(nmrData));


        vBox.getChildren().addAll(datatypeBox, acqOrderBox);

        if ((nmrData != null) && nmrData.getVendor().equals("bruker")) {
            CheckBox checkBox = new CheckBox("Fix DSP");
            checkBox.setSelected(processorController.chartProcessor.getFixDSP());
            vBox.getChildren().add(checkBox);
            checkBox.setOnAction(e -> {
                processorController.chartProcessor.setFixDSP(checkBox.isSelected());
                invalidateScript();
            });
        }

        ScrollPane scrollPane = new ScrollPane();
        GridPane gridPane = new GridPane();
        scrollPane.setContent(gridPane);
        vBox.getChildren().add(scrollPane);
        referencePane.setContent(vBox);
        int start = 2;
        for (int i = 0; i < nDim; i++) {
            Label label = new Label(String.valueOf(i + 1));
            gridPane.add(label, i + start, 0);
        }
        int row = 1;
        for (DataProps dataProp : DataProps.values()) {
            Label label = new Label(dataProp.title);
            Insets insets = new Insets(5, 5, 5, 10);
            label.setPadding(insets);
            gridPane.add(label, 1, row);
            ToggleButton toggleButton = GlyphsDude.createIconToggleButton(FontAwesomeIcon.LOCK, "", "12", ContentDisplay.GRAPHIC_ONLY);
            gridPane.add(toggleButton, 0, row);
            toggleButton.setSelected(dataProp.locked);
            toggleButton.setOnAction(e -> updateEditable(toggleButton, dataProp, nmrData, nDim));
            toggleButtons.put(dataProp, toggleButton);
            for (int i = 0; i < nDim; i++) {
                if (dataProp == DataProps.SKIP) {
                    if (i > 0) {
                        CheckBox checkBox = new CheckBox();
                        gridPane.add(checkBox, i + start, row);
                        controlsMap.put(dataProp.name() + i, checkBox);
                        checkBox.disableProperty().bind(toggleButton.selectedProperty());
                        checkBox.setOnAction(e -> invalidateScript());
                    }
                } else {
                    if (dataProp == DataProps.REF) {
                        ReferenceMenuTextField referenceMenuTextField = new ReferenceMenuTextField(processorController);
                        referenceMenuTextField.setPrefWidth(100);
                        referenceMenuTextField.setText(dataProp.getValue(nmrData, i));
                        referenceMenuTextField.getTextField().textProperty().addListener(e -> invalidateScript());
                        gridPane.add(referenceMenuTextField, i + start, row);
                        controlsMap.put(dataProp.name() + i, referenceMenuTextField);
                    } else {
                        CustomTextField textField = new CustomTextField();
                        textField.setPrefWidth(100);
                        textField.setText(dataProp.getValue(nmrData, i));
                        textField.editableProperty().bind(toggleButton.selectedProperty().not());
                        textField.setOnKeyPressed(e -> invalidateScript());
                        gridPane.add(textField, i + start, row);
                        controlsMap.put(dataProp.name() + i, textField);
                    }
                }
            }
            row++;
        }
    }


    public boolean getSkip(String iDim) {
        CheckBox checkBox = (CheckBox) controlsMap.get(DataProps.SKIP.name()+iDim);
        return checkBox != null && checkBox.isSelected();
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
        sBuilder.append(indent);
        sBuilder.append("fixdsp(");
        sBuilder.append(chartProcessor.getFixDSP() ? "True" : "False");
        sBuilder.append(")");
        sBuilder.append(System.lineSeparator());
        for (DataProps dataProps : DataProps.values()) {
            if (!toggleButtons.get(dataProps).isSelected()) {
                sBuilder.append(getCurrentValues(dataProps, nDim, indent));
                sBuilder.append(System.lineSeparator());
            }
        }
        return sBuilder.toString();
    }

    Optional<String> getSkipString() {
        Optional<String> result = Optional.empty();
        NMRData nmrData = getNMRData();
        if (nmrData != null) {
            Optional<String> skipString = processorController.getSkipString();
            if (skipString.isPresent()) {
                String s = "markrows(" + skipString.get() + ")";
                result = Optional.of(s);
            }
        }
        return result;

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
                switch (propName) {
                    case "acqOrder":
                        chartProcessor.setAcqOrder(args);
                        break;
                    case "acqarray":
                        chartProcessor.setArraySize(args);
                        break;
                    case "fixdsp":
                        chartProcessor.setFixDSP(args.equals("True"));
                        break;
                    default:
                        int dim = 0;
                        for (String parValue : parValues) {
                            String dimName = "" + (dim + 1);
                            String nameWithDim = propName + dimName;
                            Object control = controlsMap.get(nameWithDim);
                            setControlValue(control, parValue);
                            dim++;
                        }
                        break;
                }
            }
        }
    }

    NMRData getNMRData() {
        return processorController.chartProcessor.getNMRData();
    }
}
