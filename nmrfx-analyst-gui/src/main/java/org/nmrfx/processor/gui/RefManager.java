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
import javafx.beans.property.Property;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;
import org.apache.commons.collections4.iterators.PermutationIterator;
import org.controlsfx.control.textfield.CustomTextField;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.processor.datasets.AcquisitionType;
import org.nmrfx.processor.datasets.DatasetType;
import org.nmrfx.processor.datasets.ReferenceCalculator;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.UnaryOperator;

/**
 * @author brucejohnson
 */
public class RefManager {

    private static final Logger log = LoggerFactory.getLogger(RefManager.class);
    TitledPane referencePane;
    ProcessorController processorController;
    Map<DataProps, ToggleButton> toggleButtons = new HashMap<>();
    Map<String, SimpleObjectProperty> objectPropertyMap = new HashMap<>();
    Map<String, TextField> parameterMap = new HashMap<>();
    VendorParsGUI vendorParsGUI = new VendorParsGUI();
    ComboBox<String> acqOrderCombo;
    ChoiceBox<Integer> acqArrayChoice;

    SimpleDoubleProperty zeroFieldProp = new SimpleDoubleProperty(1.0);


    public static class PositiveIntegerFilter implements UnaryOperator<TextFormatter.Change> {

        @Override
        public TextFormatter.Change apply(TextFormatter.Change change) {
            if (change.getControlNewText().matches("[0-9]*")) {
                return change;
            }
            return null;
        }
    }

    public static class PositiveIntegerStringConverter extends IntegerStringConverter {

        @Override
        public Integer fromString(String value) {
            int result = super.fromString(value);
            if (result < 0) {
                throw new RuntimeException("Negative number");
            }
            return result;
        }

        @Override
        public String toString(Integer value) {
            if (value < 0) {
                return "0";
            }
            return super.toString(value);
        }

    }

    public static class FixedDecimalFilter implements UnaryOperator<TextFormatter.Change> {

        @Override
        public TextFormatter.Change apply(TextFormatter.Change change) {
            if (change.getControlNewText().matches("-?([0-9]+)?(\\.[0-9]*)?")) {
                return change;
            }
            return null;
        }
    }

    public static class FixedDecimalConverter extends DoubleStringConverter {

        private final int decimalPlaces;

        public FixedDecimalConverter(int decimalPlaces) {
            this.decimalPlaces = decimalPlaces;
        }

        @Override
        public String toString(Double value) {
            return String.format("%." + decimalPlaces + "f", value);
        }

        @Override
        public Double fromString(String valueString) {
            if (valueString.isEmpty()) {
                return 0d;
            }
            return super.fromString(valueString);
        }
    }

    enum DataProps {
        LABEL("Label", false) {
            String getDataValue(NMRData nmrData, int iDim) {
                return nmrData.getLabelNames()[iDim];
            }

            void setObjectValue(Map<String, SimpleObjectProperty> objectPropertyMap, NMRData nmrData, int iDim) {
                ((SimpleObjectProperty<String>) objectPropertyMap.get(name() + iDim)).set(nmrData.getLabelNames()[iDim]);
            }

            void setObjectValue(Map<String, SimpleObjectProperty> objectPropertyMap, NMRData nmrData, String value, int iDim) {
                ((SimpleObjectProperty<String>) objectPropertyMap.get(name() + iDim)).set(value);
            }

            SimpleObjectProperty<String> getObjectProperty() {
                return new SimpleObjectProperty<>("");
            }
        },
        TDSIZE("TDSize", true) {
            String getDataValue(NMRData nmrData, int iDim) {
                return String.valueOf(nmrData.getSize(iDim));
            }

            void setObjectValue(Map<String, SimpleObjectProperty> objectPropertyMap, NMRData nmrData, int iDim) {
                ((SimpleObjectProperty<Integer>) objectPropertyMap.get(name() + iDim)).set(nmrData.getSize(iDim));
            }

            void setObjectValue(Map<String, SimpleObjectProperty> objectPropertyMap, NMRData nmrData, String value, int iDim) {
                ((SimpleObjectProperty<Integer>) objectPropertyMap.get(name() + iDim)).set(Integer.parseInt(value));
            }

            SimpleObjectProperty<Integer> getObjectProperty() {
                return new SimpleObjectProperty<>(0);
            }
        },
        ACQSIZE("AcqSize", true) {
            String getDataValue(NMRData nmrData, int iDim) {
                return String.valueOf(nmrData.getSize(iDim));
            }

            void setObjectValue(Map<String, SimpleObjectProperty> objectPropertyMap, NMRData nmrData, int iDim) {
                ((SimpleObjectProperty<Integer>) objectPropertyMap.get(name() + iDim)).set(nmrData.getSize(iDim));
            }

            void setObjectValue(Map<String, SimpleObjectProperty> objectPropertyMap, NMRData nmrData, String value, int iDim) {
                ((SimpleObjectProperty<Integer>) objectPropertyMap.get(name() + iDim)).set(Integer.parseInt(value));
            }

            SimpleObjectProperty<Integer> getObjectProperty() {
                return new SimpleObjectProperty<>(0);
            }
        },
        SF("Frequency (MHz)", true) {
            String getDataValue(NMRData nmrData, int iDim) {
                return String.valueOf(nmrData.getSF(iDim));
            }

            void setObjectValue(Map<String, SimpleObjectProperty> objectPropertyMap, NMRData nmrData, int iDim) {
                ((SimpleObjectProperty<Double>) objectPropertyMap.get(name() + iDim)).set(nmrData.getSF(iDim));
            }

            void setObjectValue(Map<String, SimpleObjectProperty> objectPropertyMap, NMRData nmrData, String value, int iDim) {
                Double dValue = NMRDataUtil.parsePar(nmrData, iDim, value);
                if (dValue == null) {
                    dValue = nmrData.getSF(iDim);
                }
                ((SimpleObjectProperty<Double>) objectPropertyMap.get(name() + iDim)).set(dValue);
            }

            SimpleObjectProperty<Double> getObjectProperty() {
                return new SimpleObjectProperty<>(0.0);
            }
        },
        SW("Sweep Width", true) {
            String getDataValue(NMRData nmrData, int iDim) {
                return String.valueOf(nmrData.getSW(iDim));
            }

            void setObjectValue(Map<String, SimpleObjectProperty> objectPropertyMap, NMRData nmrData, int iDim) {
                ((SimpleObjectProperty<Double>) objectPropertyMap.get(name() + iDim)).set(nmrData.getSW(iDim));
            }

            void setObjectValue(Map<String, SimpleObjectProperty> objectPropertyMap, NMRData nmrData, String value, int iDim) {
                Double dValue = NMRDataUtil.parsePar(nmrData, iDim, value);
                if (dValue == null) {
                    dValue = nmrData.getSW(iDim);
                }
                ((SimpleObjectProperty<Double>) objectPropertyMap.get(name() + iDim)).set(dValue);
            }

            SimpleObjectProperty<Double> getObjectProperty() {
                return new SimpleObjectProperty<>(0.0);
            }
        },
        REF("Reference", false) {
            String getDataValue(NMRData nmrData, int iDim) {
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

            void setObjectValue(Map<String, SimpleObjectProperty> objectPropertyMap, NMRData nmrData, int iDim) {
                ((SimpleObjectProperty<String>) objectPropertyMap.get(name() + iDim)).set(getDataValue(nmrData, iDim));
            }

            void setObjectValue(Map<String, SimpleObjectProperty> objectPropertyMap, NMRData nmrData, String value, int iDim) {
                ((SimpleObjectProperty<String>) objectPropertyMap.get(name() + iDim)).set(value);
            }

            SimpleObjectProperty<String> getObjectProperty() {
                return new SimpleObjectProperty<>("");
            }
        },
        SKIP("Skip", true) {
            String getDataValue(NMRData nmrData, int iDim) {
                return "0";
            }

            void setObjectValue(Map<String, SimpleObjectProperty> objectPropertyMap, NMRData nmrData, int iDim) {
                ((SimpleObjectProperty<Boolean>) objectPropertyMap.get(name() + iDim)).set(false);
            }

            void setObjectValue(Map<String, SimpleObjectProperty> objectPropertyMap, NMRData nmrData, String value, int iDim) {
                ((SimpleObjectProperty<Boolean>) objectPropertyMap.get(name() + iDim)).set(Boolean.parseBoolean(value.toLowerCase()));
            }

            SimpleObjectProperty<Boolean> getObjectProperty() {
                return new SimpleObjectProperty<>(false);
            }
        },

        ACQMODE("AcqMode", false) {
            String getDataValue(NMRData nmrData, int iDim) {
                return nmrData.getSymbolicCoefs(iDim);
            }

            void setObjectValue(Map<String, SimpleObjectProperty> objectPropertyMap, NMRData nmrData, int iDim) {
                AcquisitionType modeType = getMode(nmrData, iDim);
                ((SimpleObjectProperty<AcquisitionType>) objectPropertyMap.get(name() + iDim)).set(modeType);
            }

            void setObjectValue(Map<String, SimpleObjectProperty> objectPropertyMap, NMRData nmrData, String value, int iDim) {
                AcquisitionType modeType = getMode(value);
                ((SimpleObjectProperty<AcquisitionType>) objectPropertyMap.get(name() + iDim)).set(modeType);
            }

            SimpleObjectProperty<AcquisitionType> getObjectProperty() {
                return new SimpleObjectProperty<>(AcquisitionType.HYPER);
            }
        },
        ;
        final String title;
        final boolean locked;

        DataProps(String title, boolean locked) {
            this.title = title;
            this.locked = locked;
        }

        abstract String getDataValue(NMRData nmrData, int iDim);

        abstract void setObjectValue(Map<String, SimpleObjectProperty> objectPropertyMap, NMRData nmrData, int iDim);

        abstract void setObjectValue(Map<String, SimpleObjectProperty> objectPropertyMap, NMRData nmrData, String value, int iDim);

        abstract SimpleObjectProperty getObjectProperty();

        String getString(Map<String, SimpleObjectProperty> objectPropertyMap, int iDim) {
            SimpleObjectProperty field = objectPropertyMap.get(name() + iDim);
            Object value = field == null ? null : field.getValue();
            return value == null ? "" : value.toString();
        }

        boolean isDefault(Map<String, SimpleObjectProperty> objectPropertyMap, NMRData nmrData, int iDim) {
            String dataString = getDataValue(nmrData, iDim);
            return dataString == null || dataString.equals(getString(objectPropertyMap, iDim));
        }
    }

    static public final String[] propNames = {"skip", "label", "acqarray", "acqsize", "tdsize", "sf", "sw", "ref"};
    static final String[] bSFNames = {"SFO1,1", "SFO1,2", "SFO1,3", "SFO1,4", "SFO1,5"};
    static final String[] bSWNames = {"SW_h,1", "SW_h,2", "SW_h,3", "SW_h,4", "SW_h,5"};

    RefManager(ProcessorController processorController, TitledPane referencePane) {
        this.processorController = processorController;
        this.referencePane = referencePane;
    }

    public static AcquisitionType getMode(NMRData nmrData, int iDim) {
        AcquisitionType modeType;
        if (iDim == 0) {
            modeType = AcquisitionType.COMPLEX;
        } else {
            modeType = nmrData.getUserSymbolicCoefs(iDim);
            if (modeType == null) {
                String modeString = nmrData.getSymbolicCoefs(iDim);
                modeType = getMode(modeString);
            }
        }
        return modeType;
    }

    public static AcquisitionType getMode(String modeString) {
        AcquisitionType modeType;
        try {
            modeType = AcquisitionType.fromLabel(modeString);
        } catch (IllegalArgumentException iAE) {
            modeType = AcquisitionType.HYPER;
        }
        return modeType;
    }

    public String getPythonString(DataProps dataProps, int nDim, String indent) {
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
            if (value.equals("false")) {
                value = "False";
                useString = false;
            } else if (value.equals("true")) {
                value = "True";
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
        return dataProps.getString(objectPropertyMap, iDim);
    }

    private void updateEditable(ToggleButton toggleButton, DataProps dataProp, NMRData nmrData, int nDim) {
        if (toggleButton.isSelected()) {
            for (int iDim = 0; iDim < nDim; iDim++) {
                dataProp.setObjectValue(objectPropertyMap, nmrData, iDim);
            }
        }
        invalidateScript();
    }

    private void invalidateScript() {
        processorController.chartProcessor.setScriptValid(false);
        refresh();
    }

    private void acqOrderChanged() {
        String acqOrderValue = acqOrderCombo.getValue();
        boolean ok = processorController.chartProcessor.setAcqOrder(acqOrderValue);
        if (ok) {
            invalidateScript();
        }
    }

    private void acqArrayChanged() {
        int arraySize = acqArrayChoice.getValue();
        String acqOrder = processorController.chartProcessor.getAcqOrder(false);
        int index = acqOrder.indexOf("a");
        if (index != -1) {
            int arrayDim = Integer.parseInt(acqOrder.substring(index + 1, index + 2)) - 1;
            processorController.chartProcessor.setArraySize(arrayDim, arraySize);
            invalidateScript();
        }

    }

    private ComboBox<String> setupAcqOrder(NMRData nmrData) {
        acqOrderCombo = new ComboBox();
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
                acqOrderCombo.getItems().addAll(choices);
                acqOrderCombo.setEditable(true);
                acqOrderCombo.setValue(processorController.chartProcessor.getAcqOrder());
                acqOrderCombo.valueProperty().addListener(e -> acqOrderChanged());
            }
        }
        return acqOrderCombo;
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
        NMRData nmrData = getNMRData();
        refreshParameters(nmrData);
    }

    private void updateDatasetChoice(DatasetType dataType) {
        ChartProcessor chartProcessor = processorController.chartProcessor;
        if (dataType != chartProcessor.getDatasetType()) {
            processorController.unsetDatasetName();
        }
        chartProcessor.setDatasetType(dataType);
        processorController.updateFileButton();
    }

    HBox getParDisplay(NMRData nmrData, String field) {
        HBox hBox = new HBox();
        Label label = new Label(field);
        label.setPrefWidth(100);
        label.setAlignment(Pos.CENTER_LEFT);
        hBox.setSpacing(10);
        TextField textField = new TextField();
        textField.setPrefWidth(200);
        textField.setEditable(false);
        parameterMap.put(field, textField);
        hBox.getChildren().addAll(label, textField);
        return hBox;
    }

    public void refreshParameters(NMRData nmrData) {
        for (var entry : parameterMap.entrySet()) {
            String field = entry.getKey();
            TextField textField = entry.getValue();
            switch (field) {
                case "Solvent" -> textField.setText(nmrData.getSolvent());
                case "Sequence" -> textField.setText(nmrData.getSequence());
                case "Temperature" -> textField.setText(String.valueOf(nmrData.getTempK()));
                case "Date" -> textField.setText(nmrData.getZonedDate().toString());
            }
        }
    }

    public void clearObjectPropertyMap() {
        objectPropertyMap.clear();
    }

    public void updateReferencePane(NMRData nmrData, int nDim) {
        VBox vBox = new VBox();
        vBox.setSpacing(4);
        String[] infoFields = {"Sequence", "Solvent", "Temperature", "Date"};
        for (String infoField : infoFields) {
            vBox.getChildren().add(getParDisplay(nmrData, infoField));
        }
        refreshParameters(nmrData);

        Label dataTypeLabel = new Label("Output Type");
        dataTypeLabel.setPrefWidth(100);
        dataTypeLabel.setAlignment(Pos.CENTER_LEFT);
        HBox datatypeBox = new HBox();
        datatypeBox.setSpacing(10);

        datatypeBox.setAlignment(Pos.CENTER_LEFT);
        ChoiceBox<DatasetType> dataChoice = new ChoiceBox<>();
        dataChoice.getItems().addAll(DatasetType.values());
        datatypeBox.getChildren().addAll(dataTypeLabel, dataChoice, processorController.getDatasetFileButton());
        dataChoice.setValue(processorController.chartProcessor.getDatasetType());
        dataChoice.setOnAction(e -> updateDatasetChoice(dataChoice.getValue()));

        Label acqOrderLabel = new Label("Acq. Order");
        acqOrderLabel.setPrefWidth(100);
        acqOrderLabel.setAlignment(Pos.CENTER_LEFT);
        HBox acqOrderBox = new HBox();
        acqOrderBox.setSpacing(10);
        acqOrderBox.setAlignment(Pos.CENTER_LEFT);
        acqArrayChoice = new ChoiceBox<>();
        for (int i = 0; i < 32; i++) {
            acqArrayChoice.getItems().add(i);
        }
        acqArrayChoice.setValue(0);
        acqOrderBox.getChildren().addAll(acqOrderLabel, setupAcqOrder(nmrData), acqArrayChoice);
        acqArrayChoice.valueProperty().addListener(e -> acqArrayChanged());


        vBox.getChildren().addAll(datatypeBox, acqOrderBox);

        CustomTextField zeroFreqTextField = new CustomTextField();
        zeroFreqTextField.setPrefWidth(150);
        TextFormatter<Double> zFtextFormatter = new TextFormatter<>(new FixedDecimalConverter(7), 0.0, new FixedDecimalFilter());
        zFtextFormatter.valueProperty().bindBidirectional((Property) zeroFieldProp);
        nmrData.setZeroFreq(null);
        zeroFieldProp.set(nmrData.getZeroFreq());
        zeroFieldProp.addListener(e -> {
            processorController.chartProcessor.setZeroFreq(zeroFieldProp.doubleValue());
            invalidateScript();
        });
        zeroFreqTextField.setTextFormatter(zFtextFormatter);
        Label zfLabel = new Label("ZeroFreq");
        zfLabel.setPrefWidth(100);
        HBox zfBox = new HBox();
        zfBox.setSpacing(10);
        zfBox.setAlignment(Pos.CENTER_LEFT);
        MenuButton zfMenu = new MenuButton("Set");
        zfMenu.setPrefWidth(50);
        zfBox.getChildren().addAll(zfLabel, zeroFreqTextField, zfMenu);
        zeroFreqTextField.setEditable(false);
        MenuItem dssMenuItem = new MenuItem("Set from lock");
        MenuItem inputMenuItem = new MenuItem("Input");
        zfMenu.getItems().addAll(dssMenuItem, inputMenuItem);
        dssMenuItem.setOnAction(e -> setZeroFreqFromLock(zeroFreqTextField));
        inputMenuItem.setOnAction(e -> zeroFreqTextField.setEditable(true));

        vBox.getChildren().add(zfBox);

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
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        GridPane gridPane = new GridPane();
        scrollPane.setContent(gridPane);
        vBox.getChildren().add(scrollPane);
        referencePane.setContent(vBox);
        int start = 2;
        for (int i = 0; i < nDim; i++) {
            Label label = new Label("Dim: " + String.valueOf(i + 1));
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
                var currentProp = objectPropertyMap.get(dataProp.name() + i);
                SimpleObjectProperty prop;
                if (currentProp != null) {
                    prop = currentProp;
                } else {
                    prop = dataProp.getObjectProperty();
                }
                if (!dataProp.isDefault(objectPropertyMap, nmrData, i)) {
                    toggleButton.setSelected(false);
                }
                objectPropertyMap.put(dataProp.name() + i, prop);
                if (dataProp == DataProps.SKIP) {
                    if (i > 0) {
                        CheckBox checkBox = new CheckBox();
                        checkBox.disableProperty().bind(toggleButton.selectedProperty());
                        checkBox.selectedProperty().bindBidirectional(prop);
                        checkBox.setOnAction(e -> invalidateScript());
                        gridPane.add(checkBox, i + start, row);
                    }
                } else {
                    if (dataProp == DataProps.ACQMODE) {
                        ChoiceBox<AcquisitionType> modeBox = new ChoiceBox<>();
                        if (i == 0) {
                            modeBox.getItems().add(AcquisitionType.COMPLEX);
                        } else {
                            modeBox.getItems().addAll(AcquisitionType.values());
                        }
                        modeBox.setPrefWidth(100);
                        if (currentProp == null) {
                            AcquisitionType modeType = getMode(nmrData, i);
                            prop.set(modeType);
                            modeBox.setValue(modeType);
                        }

                        gridPane.add(modeBox, i + start, row);
                        modeBox.valueProperty().bindBidirectional(prop);
                        modeBox.valueProperty().addListener(e -> invalidateScript());
                        modeBox.disableProperty().bind(toggleButton.selectedProperty());

                    } else if (dataProp == DataProps.REF) {
                        ReferenceMenuTextField referenceMenuTextField = new ReferenceMenuTextField(processorController);
                        referenceMenuTextField.setPrefWidth(100);
                        if (currentProp == null) {
                            referenceMenuTextField.setText(dataProp.getDataValue(nmrData, i));
                        }
                        referenceMenuTextField.getTextField().textProperty().bindBidirectional(prop);
                        int iDim = i;
                        referenceMenuTextField.getTextField().textProperty().addListener(e -> updateReference(prop, iDim));

                        gridPane.add(referenceMenuTextField, i + start, row);
                    } else {
                        CustomTextField textField = new CustomTextField();
                        textField.setPrefWidth(100);
                        textField.editableProperty().bind(toggleButton.selectedProperty().not());
                        textField.setOnKeyPressed(e -> invalidateScript());
                        gridPane.add(textField, i + start, row);
                        if (prop.get() instanceof Double dValue) {
                            int decimalPlaces = dataProp == DataProps.SF ? 7 : 2;
                            if (currentProp == null) {
                                prop.set(Double.parseDouble(dataProp.getDataValue(nmrData, i)));
                            }
                            TextFormatter<Double> textFormatter = new TextFormatter<>(new FixedDecimalConverter(decimalPlaces),
                                    0.0, new FixedDecimalFilter());
                            textFormatter.valueProperty().bindBidirectional(prop);
                            textField.setTextFormatter(textFormatter);
                        } else if (prop.get() instanceof Integer dValue) {
                            if (currentProp == null) {
                                prop.set(Integer.parseInt(dataProp.getDataValue(nmrData, i)));
                            }
                            TextFormatter<Integer> textFormatter = new TextFormatter<>(new PositiveIntegerStringConverter(), 0, new PositiveIntegerFilter());
                            textFormatter.valueProperty().bindBidirectional(prop);
                            textField.setTextFormatter(textFormatter);
                        } else {
                            if (currentProp == null) {
                                prop.set(dataProp.getDataValue(nmrData, i));
                            }
                            TextFormatter<String> textFormatter = new TextFormatter<>(TextFormatter.IDENTITY_STRING_CONVERTER);
                            textFormatter.valueProperty().bindBidirectional(prop);
                            textField.setTextFormatter(textFormatter);
                        }
                    }
                }
            }
            row++;
        }
        Button button = new Button("Vendor Pars...");
        vBox.getChildren().add(button);
        button.setOnAction(e -> showVendorPars(nmrData));
    }

    private void showVendorPars(NMRData nmrData) {
        vendorParsGUI.showStage();
        vendorParsGUI.updateParTable(nmrData);
    }

    private void setZeroFreqFromLock(CustomTextField textField) {
        NMRData nmrData = getNMRData();
        nmrData.setZeroFreq(null);
        double z = nmrData.getZeroFreq();
        zeroFieldProp.set(z);
        processorController.chartProcessor.setZeroFreq(z);
        textField.setEditable(false);
        String labelText = ReferenceCalculator.isAcqueous(nmrData.getSolvent()) ? "DSS" : "TMS";
        Label label = new Label(labelText);
        textField.setRight(label);
        invalidateScript();
    }

    private void updateReference(SimpleObjectProperty property, int iDim) {
        if (iDim == 0) {
            String refString = property.getValue().toString();
            NMRData nmrData = getNMRData();
            double sf = nmrData.getSF(0);
            String tn = nmrData.getTN(0);
            Nuclei nuclei = Nuclei.findNuclei(tn);
            double z;
            if (refString.isEmpty()) {
                nmrData.setZeroFreq(null);
                z = nmrData.getZeroFreq();
            } else if (refString.equals("H2O")) {
                double ref = ReferenceCalculator.getH2ORefPPM(nmrData.getTempK());
                z = sf / (1.0 + ref * 1.0e-6);
            } else {
                try {
                    double ref = Double.parseDouble(refString);
                    z = sf / (1.0 + ref * 1.0e-6);
                    z /= nuclei.getRatio() / 100.0;
                } catch (NumberFormatException nfE) {
                    z = nmrData.getZeroFreq();
                }
            }
            zeroFieldProp.set(z);
            processorController.chartProcessor.setZeroFreq(z);
        }
        invalidateScript();
    }

    public boolean getSkip(String iDim) {
        SimpleObjectProperty objectProp = objectPropertyMap.get(DataProps.SKIP.name() + iDim);
        return (objectProp == null) || (objectProp.get() == null) ? false : ((Boolean) objectProp.get()).booleanValue();
    }

    public String getScriptReferenceLines(int nDim, String indent) {
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
        Double zeroFreq = chartProcessor.getZeroFreq();
        if (zeroFreq != null) {
            sBuilder.append("zerofreq(");
            sBuilder.append(zeroFreq);
            sBuilder.append(")");
        }
        sBuilder.append(System.lineSeparator());
        for (DataProps dataProps : DataProps.values()) {
            if (!toggleButtons.get(dataProps).isSelected()) {
                sBuilder.append(getPythonString(dataProps, nDim, indent));
                sBuilder.append(System.lineSeparator());
            }
        }
        getSkipString().ifPresent(s -> sBuilder.append(s).append(System.lineSeparator()));
        return sBuilder.toString();
    }

    Optional<String> getSkipString() {
        Optional<String> result = Optional.empty();
        NMRData nmrData = getNMRData();
        if (nmrData != null) {
            Optional<String> skipString = processorController.navigatorGUI.getSkipString();
            if (skipString.isPresent()) {
                String s = "markrows(" + skipString.get() + ")";
                result = Optional.of(s);
            }
        }
        return result;

    }


    public void setDataFields(List<String> headerList) {
        objectPropertyMap.clear();
        NMRData nmrData = getNMRData();
        if (nmrData != null) {
            updateReferencePane(nmrData, nmrData.getNDim());
        }
        ChartProcessor chartProcessor = processorController.chartProcessor;
        for (String s : headerList) {
            int index = s.indexOf('(');
            boolean lastIsClosePar = s.charAt(s.length() - 1) == ')';
            if ((index != -1) && lastIsClosePar) {
                String propName = s.substring(0, index).toUpperCase();
                String args = s.substring(index + 1, s.length() - 1);
                switch (propName) {
                    case "ACQORDER" -> chartProcessor.setAcqOrder(args);
                    case "ACQARRAY" -> chartProcessor.setArraySize(args);
                    case "FIXDSP" -> chartProcessor.setFixDSP(args.equals("True"));
                    case "ZEROFREQ" -> chartProcessor.setZeroFreq(Double.parseDouble(args));
                    default -> {
                        DataProps dataProps = DataProps.valueOf(propName);
                        List<String> parValues = CSVLineParse.parseLine(args);
                        int dim = 0;
                        for (String parValue : parValues) {
                            dataProps.setObjectValue(objectPropertyMap, nmrData, parValue, dim);
                            if (dataProps == DataProps.ACQMODE) {
                                chartProcessor.setAcqMode(dim, parValue.toUpperCase());
                            }
                            dim++;
                        }
                    }
                }
            }
        }
        if (nmrData != null) {
            updateReferencePane(nmrData, nmrData.getNDim());
        }
    }

    NMRData getNMRData() {
        return processorController.chartProcessor.getNMRData();
    }
}
