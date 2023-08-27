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

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TextField;
import org.apache.commons.math3.complex.Complex;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.PropertySheet;
import org.nmrfx.processor.processing.ProcessingOperation;
import org.nmrfx.processor.processing.ProcessingOperationInterface;
import org.nmrfx.utils.properties.*;
import org.python.core.PyComplex;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author brucejohnson
 */
public class PropertyManager {
    private static final String PATTERN_STRING = "(\\w+)=((\\[[^\\[]*\\])|(\"[^\"]*\")|('[^']*')|([^,]+))";

    ChangeListener<Number> doubleListener;
    ChangeListener<Number> doubleSliderListener;
    ChangeListener<Number> intListener;
    ChangeListener<String> stringListener;
    ChangeListener<Boolean> boolListener;
    ChangeListener<String> complexListener;
    ChangeListener<String> listListener;
    ProcessorController processorController;
    ObservableList<ProcessingOperationInterface> listItems;
    private int currentIndex = -1;
    private String currentOp = "";
    private TextField opTextField;
    PopOver popOver;
    static ObservableList<PropertySheet.Item> propItems = FXCollections.observableArrayList();
    ChangeListener<Number> scriptOpListener = null;
    Map<String, ParInfo> parInfoMap = new HashMap<>();

    PropertyManager(ProcessorController processorController, TextField opTextField, PopOver popOver) {
        this.processorController = processorController;
        this.opTextField = opTextField;
        this.popOver = popOver;
        doubleSliderListener = new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observableValue, Number number, Number number2) {
                updateOp((OperationItem) observableValue);
            }
        };
        doubleListener = new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observableValue, Number number, Number number2) {
                PropertySheet.Item item = (PropertySheet.Item) observableValue;
                if (item.getCategory().equals("PHASE")) {
                    if (item.getName().equals("ph0") || item.getName().equals("ph1")) {
                        updatePhases(item, number, number2);
                    }
                }
                updateOp((OperationItem) observableValue);
            }
        };
        intListener = new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observableValue, Number number, Number number2) {
                updateOp((OperationItem) observableValue);
            }
        };
        boolListener = new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observableValue, Boolean bool, Boolean bool2) {
                updateOp((OperationItem) observableValue);
            }
        };
        stringListener = new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String string, String string2) {
                updateOp((OperationItem) observableValue);
            }
        };
        complexListener = new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String c1, String c2) {
                updateOp((OperationItem) observableValue);
            }
        };
        listListener = new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String c1, String c2) {
                updateOp((OperationItem) observableValue);
            }
        };

        scriptOpListener = new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observableValue, Number number, Number number2) {
                int scriptIndex = (Integer) number2;
                try {
                    if ((listItems.size() == 1) && (scriptIndex == -1)) {
                        //there is a single item in the Cells -- Don't unselect
                        scriptIndex = 0;
                    } else if (scriptIndex != -1) {
//                        ProcessingOperation selOp = scriptView.getItems().get(scriptIndex);
//                        setPropSheet(scriptIndex, selOp.toString());
                    } else {
                        setPropSheet(scriptIndex, "");
                    }
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                } finally {
                    currentIndex = scriptIndex;
                }
            }
        };
        // scriptView.getSelectionModel().selectedIndexProperty().addListener(scriptOpListener);

    }

    public ObservableList<PropertySheet.Item> getItems() {
        return propItems;
    }

    public void removeScriptListener() {
        // scriptView.getSelectionModel().selectedIndexProperty().removeListener(scriptOpListener);
    }

    public void addScriptListener() {
        // scriptView.getSelectionModel().selectedIndexProperty().addListener(scriptOpListener);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public int getCurrentPosition(List<ProcessingOperationInterface> items, String op) {
        op = op.trim();
        op = OperationInfo.fixOp(op);
        int currentPos = OperationInfo.getCurrentPosition(items, op);
        return currentPos;
    }

    public void updateOp(ProcessingOperation processingOperation, String op) {
        if (processingOperation != null) {
            processingOperation.update(op);
            processorController.chartProcessor.updateOpList();
        }
    }

    public void addOp(ProcessingOperationInterface processingOperation, List<ProcessingOperationInterface> ops, int index) {
            if (processingOperation != null) {
                int opIndex = -1;
                String opName = processingOperation.getName();
                opIndex = index;
                if (opIndex == -1) {
                    opIndex = OperationInfo.getPosition(ops, opName);
                }
                if (opIndex < 0) {
                    System.out.println("bad op " + opName);
                } else if (opIndex >= ops.size()) {
                    ops.add(processingOperation);
                } else {
                    ops.add(opIndex, processingOperation);
                }
                popOver.hide();
            }
    }

    void updatePhases(PropertySheet.Item item, Number oldValue, Number newValue) {
        PolyChart chart = PolyChartManager.getInstance().getActiveChart();
        if (chart != null) {
            if (item.getName().equals("ph1")) {
                //   adjustPhasePivot(propertySheet.getItems(), oldValue, newValue);
            } else if (item.getName().equals("ph0")) {
                if (chart.getFXMLController().isPhaseSliderVisible()) {
                    chart.getFXMLController().getPhaser().handlePh0Reset(newValue.doubleValue(), false);
                }
            }
        }
    }

    void adjustPhasePivot(List<PropertySheet.Item> items, Number oldValue, Number newValue) {
        DoubleOperationItem ph0Item = (DoubleOperationItem) items.stream().
                filter(e -> e.getName().equals("ph0")).findFirst().get();
        double deltaPH1 = newValue.doubleValue() - oldValue.doubleValue();
        double oldPH0 = ph0Item.doubleValue();
        PolyChart chart = PolyChartManager.getInstance().getActiveChart();
        if (chart != null) {
            double pivotFraction = chart.getPivotFraction();
            if (pivotFraction != 0.0) {
                Double newPH0 = oldPH0 - deltaPH1 * pivotFraction;
                ph0Item.setValue(newPH0);
                if (chart.getFXMLController().isPhaseSliderVisible()) {
                    chart.getFXMLController().getPhaser().handlePh0Reset(newPH0, false);
                    chart.getFXMLController().getPhaser().handlePh1Reset(newValue.doubleValue(), false);
                }
            }
        }

    }

    void setOp(String op) {
       // fixme  setOp(null, op, false, -1);
    }

    private void updateOp(OperationItem updateItem) {
        PropertySheet propertySheet = updateItem.getPropertySheet();
        String opName = updateItem.getCategory();
        updatePropertySheet(propertySheet, opName);
    }

    public void updatePropertySheet(PropertySheet propertySheet, String opName) {
        List<PropertySheet.Item> items = propertySheet.getItems();
        if (items.size() == 0) {
            return;
        }
        StringBuilder opString = new StringBuilder();
        opString.append(opName);
        opString.append("(");
        boolean first = true;
        for (PropertySheet.Item item : items) {
            OperationItem opItem = (OperationItem) item;
            if (!opItem.isDefault()) {
                if (!first) {
                    opString.append(',');
                }
                opString.append(opItem.getName());
                opString.append('=');
                char lastChar = (char) -1;
                if (opItem instanceof DoubleRangeOperationItem) {
                    lastChar = ((DoubleRangeOperationItem) opItem).getLastChar();
                }
                opString.append(opItem.getStringRep());
                first = false;
            }

        }
        opString.append(')');
        ProcessingOperation processingOperation = (ProcessingOperation) propertySheet.getProperties().get("Op");
        updateOp(processingOperation, opString.toString());
    }

    void addExtractRegion(double min, double max, double f1, double f2) {
        int imin = (int) (min + 0.5);
        int imax = (int) (max + 0.5);
        String opString = "EXTRACT(start=" + imin + ",end=" + imax + ",mode='region')";
          setOp(opString);
        //setPropSheet(opIndex, opString);
    }

    public void setPropSheet(PropertySheet propertySheet, String op) {
        currentIndex = -1;
        String trimOp = OperationInfo.trimOp(op);
        Pattern pattern = null;
        String opPars = "";
        if (!op.equals("")) {
            opPars = op.substring(op.indexOf('(') + 1, op.length() - 1);
            pattern = Pattern.compile(PATTERN_STRING);
        }
        List<PropertySheet.Item> items = propertySheet.getItems();
        ObservableList<PropertySheet.Item> newItems = FXCollections.observableArrayList();
        for (PropertySheet.Item item : items) {
            if (item == null) {
                System.out.println("item null");
            } else if (item.getCategory().equals(trimOp) && (pattern != null)) {

                boolean foundIt = false;
                Matcher matcher = pattern.matcher(opPars);
                while (matcher.find()) {
                    if (matcher.groupCount() > 1) {
                        String parName = matcher.group(1);
                        if (item.getName().equals(parName)) {
                            String parValue = matcher.group(2);
                            foundIt = true;
                            ((OperationItem) item).setFromString(parValue);
                        }
                    }
                }
                if (!foundIt) {
                    ((OperationItem) item).setToDefault();
                }
            }
            newItems.add(item);
        }
        propertySheet.getItems().setAll(newItems);
    }



    public void setPropSheet(int scriptIndex, String op) {
        currentIndex = -1;
        String trimOp = OperationInfo.trimOp(op);
        Pattern pattern = null;
        String opPars = "";
        if (!op.equals("")) {
            opPars = op.substring(op.indexOf('(') + 1, op.length() - 1);
            pattern = Pattern.compile(PATTERN_STRING);
        }
        ObservableList<PropertySheet.Item> newItems = FXCollections.observableArrayList();
        for (PropertySheet.Item item : propItems) {
            if (item == null) {
                System.out.println("item null");
            } else if (item.getCategory().equals(trimOp) && (pattern != null)) {

                boolean foundIt = false;
                Matcher matcher = pattern.matcher(opPars);
                while (matcher.find()) {
                    if (matcher.groupCount() > 1) {
                        String parName = matcher.group(1);
                        if (item.getName().equals(parName)) {
                            String parValue = matcher.group(2);
                            foundIt = true;
                            ((OperationItem) item).setFromString(parValue);
                        }
                    }
                }
                if (!foundIt) {
                    ((OperationItem) item).setToDefault();
                }
                newItems.add(item);
            }
        }
        currentIndex = scriptIndex;
        currentOp = trimOp;
        // propertySheet.getItems().setAll(newItems);

    }

    @Nonnull
    public static Map<String, String> parseOpString(String op) {
        Map<String, String> values = new HashMap<>();
        Pattern pattern = null;
        String opPars = "";
        if (!op.equals("")) {
            opPars = op.substring(op.indexOf('(') + 1, op.length() - 1);
            pattern = Pattern.compile(PATTERN_STRING);
            Matcher matcher = pattern.matcher(opPars);
            while (matcher.find()) {
                if (matcher.groupCount() > 1) {
                    String parName = matcher.group(1);
                    String parValue = matcher.group(2);
                    parValue = parValue.replace("'", "");
                    parValue = parValue.replace("\"", "");
                    values.put(parName, parValue);
                }
            }
        }
        return values;
    }

    record ParInfo(ArrayList parList, String description) {}

    void setupItems() {
        List<?> pyDocs = processorController.chartProcessor.getDocs();
        for (int i = 0; i < pyDocs.size(); i += 4) {
            String op = (String) pyDocs.get(i);
            ArrayList parList = (ArrayList) pyDocs.get(i + 2);
            String description = (String) pyDocs.get(i + 3);
            ParInfo parInfo = new ParInfo(parList, description);
            parInfoMap.put(op, parInfo);
        }
    }

    void setupItem(PropertySheet propertySheet, String op) {
        if (parInfoMap.isEmpty()) {
            setupItems();
        }
        ParInfo parInfo = parInfoMap.get(op);
        propItems.clear();
        for (Object parObj : parInfo.parList) {
            HashMap parMap = (HashMap) parObj;
            String name = (String) parMap.get("name");
            String parDesc = (String) parMap.get("desc");
            Boolean optional = (Boolean) parMap.get("optional");
            ArrayList types = (ArrayList) parMap.get("type");
            if (types.size() == 1) {
                String type = (String) types.get(0);
                switch (type) {
                    case "string": {
                        String defaultString;
                        Object defObj = parMap.get("default");
                        if (defObj instanceof String) {
                            defaultString = (String) defObj;
                        } else if (defObj == null) {
                            defaultString = ""; // fixme  what to do when no default
                        } else {
                            defaultString = defObj.toString();
                        }
                        propItems.add(new TextOperationItem(propertySheet, stringListener, defaultString, op, name, parDesc));

                        break;
                    }
                    case "wstring": {
                        String defaultString;
                        Object defObj = parMap.get("default");
                        if (defObj instanceof String) {
                            defaultString = (String) defObj;
                        } else if (defObj == null) {
                            defaultString = ""; // fixme  what to do when no default
                        } else {
                            defaultString = defObj.toString();
                        }
                        propItems.add(new TextWaitingOperationItem(propertySheet, null, this::updateOp, defaultString, op, name, parDesc));
                        break;
                    }
                    case "file":
                        String defaultString;
                        Object defObj = parMap.get("default");
                        if (defObj instanceof String) {
                            defaultString = (String) defObj;
                        } else if (defObj == null) {
                            defaultString = ""; // fixme  what to do when no default
                        } else {
                            defaultString = defObj.toString();
                        }
                        propItems.add(new FileOperationItem(propertySheet, stringListener, defaultString, op, name, parDesc));
                        break;
                    case "bool":
                        boolean defaultBool;
                        defObj = parMap.get("default");
                        if (defObj instanceof Boolean) {
                            defaultBool = ((Boolean) defObj);
                        } else if (defObj == null) {
                            defaultBool = false; // fixme  what to do when no default
                        } else {
                            String defStr = (String) defObj;
                            defaultBool = Boolean.parseBoolean(defStr);
                        }
                        propItems.add(new BooleanOperationItem(propertySheet, boolListener, defaultBool, op, name, parDesc));
                        break;
                    case "int":
                        int minInt = -16384;
                        int maxInt = 16384;
                        if (parMap.containsKey("min")) {
                            Object minObj = parMap.get("min");
                            if (minObj instanceof Integer) {
                                minInt = ((Integer) minObj).intValue();
                            } else {
                                String minStr = (String) minObj;
                                if (minStr.startsWith("size")) {
                                    minInt = 0;
                                } else {
                                    minInt = Integer.parseInt(minStr);
                                }
                            }
                        }
                        if (parMap.containsKey("max")) {
                            Object maxObj = parMap.get("max");
                            if (maxObj instanceof Integer) {
                                maxInt = ((Integer) maxObj).intValue();
                            } else {

                                String maxStr = (String) maxObj;
                                if (maxStr.startsWith("size")) {
                                    maxInt = 16384;
                                } else {
                                    maxInt = Integer.parseInt(maxStr);
                                }
                            }
                        }
                        int defaultInt;
                        defObj = parMap.get("default");
                        if (defObj instanceof Integer) {
                            defaultInt = ((Integer) defObj).intValue();
                        } else if (defObj == null) {
                            defaultInt = 0; // fixme  what to do when no default
                        } else {
                            String defStr = (String) defObj;
                            defaultInt = Integer.parseInt(defStr);
                        }
                        if ((maxInt - minInt) < 2048) {
                            propItems.add(new IntRangeOperationItem(propertySheet, intListener, defaultInt, minInt, maxInt, op, name, parDesc));
                        } else {
                            propItems.add(new IntOperationItem(propertySheet, intListener, defaultInt, minInt, maxInt, op, name, parDesc));

                        }

                        break;
                    case "complex":
                        Object defComplexObj = parMap.get("default");
                        if (defComplexObj instanceof PyComplex) {
                            PyComplex defPyComplex = (PyComplex) defComplexObj;
                            Complex defComplex = new Complex(
                                    defPyComplex.real, defPyComplex.imag);
                            ComplexOperationItem crItem = new ComplexOperationItem(propertySheet, complexListener, defComplex, op, name, parDesc);
                            propItems.add(crItem);
                        } else if (defComplexObj instanceof Number) {
                            PyComplex defPyComplex = (PyComplex) defComplexObj;
                            Complex defComplex = new Complex(
                                    defPyComplex.real, defPyComplex.imag);
                            ComplexOperationItem crItem = new ComplexOperationItem(propertySheet, complexListener, defComplex, op, name, parDesc);
                            propItems.add(crItem);
                        }
                        break;

                    case "real":
                    case "double":
                    case "position":
                        boolean useSlider = true;
                        if (parMap.containsKey("slider")) {
                            Object sliderObj = parMap.get("slider");
                            if (sliderObj instanceof Boolean) {
                                useSlider = ((Boolean) sliderObj).booleanValue();
                            } else {
                                useSlider = sliderObj.toString().equals("1");
                            }
                        }
                        double minDouble = -100000.0;
                        double maxDouble = 100000.0;
                        if (parMap.containsKey("min")) {
                            Object minObj = parMap.get("min");
                            if (minObj instanceof Double) {
                                minDouble = ((Double) minObj).doubleValue();
                            } else {
                                String minStr = (String) minObj;
                                if (minStr.startsWith("size")) {
                                    minDouble = 0;
                                } else if (minStr.startsWith("Double.MIN")) {
                                    minDouble = Double.MIN_VALUE;
                                } else {
                                    minDouble = Double.parseDouble(minStr);
                                }
                            }
                        }
                        if (parMap.containsKey("max")) {
                            Object maxObj = parMap.get("max");
                            if (maxObj instanceof Double) {
                                maxDouble = ((Double) maxObj).doubleValue();
                            } else {

                                String maxStr = (String) maxObj;
                                if (maxStr.startsWith("size")) {
                                    maxDouble = 1;
                                } else if (maxStr.startsWith("Double.MAX")) {
                                    maxDouble = 100000.0;
                                } else {
                                    maxDouble = Double.parseDouble(maxStr);
                                }
                            }
                        }
                        double aminDouble = Double.NEGATIVE_INFINITY;
                        double amaxDouble = Double.MAX_VALUE;
                        if (parMap.containsKey("amin")) {
                            Object minObj = parMap.get("amin");
                            if (minObj instanceof Double) {
                                aminDouble = ((Double) minObj).doubleValue();
                            } else {
                                String minStr = (String) minObj;
                                if (minStr.startsWith("size")) {
                                    aminDouble = 0;
                                } else if (minStr.startsWith("Double.MIN")) {
                                    aminDouble = Double.MIN_VALUE;
                                } else {
                                    aminDouble = Double.parseDouble(minStr);
                                }
                            }

                        }
                        if (parMap.containsKey("amax")) {
                            Object maxObj = parMap.get("amax");
                            if (maxObj instanceof Double) {
                                amaxDouble = ((Double) maxObj).doubleValue();
                            } else {

                                String maxStr = (String) maxObj;
                                if (maxStr.startsWith("size")) {
                                    amaxDouble = 1;
                                } else if (maxStr.startsWith("Double.MAX")) {
                                    amaxDouble = 100000.0;
                                } else {
                                    amaxDouble = Double.parseDouble(maxStr);
                                }
                            }

                        }
                        double defaultDouble;
                        char lastChar = (char) -1;
                        Object defDoubleObj = parMap.get("default");
                        if (defDoubleObj instanceof Double) {
                            defaultDouble = ((Double) defDoubleObj).doubleValue();
                        } else if (defDoubleObj instanceof Integer) {
                            defaultDouble = ((Integer) defDoubleObj).doubleValue();
                        } else if (defDoubleObj == null) {
                            defaultDouble = 0.0; // fixme  what to do when no default
                        } else {
                            String defStr = (String) defDoubleObj;
                            if (type.equals("position")) {
                                lastChar = defStr.charAt(defStr.length() - 1);
                                if (!Character.isLetter(lastChar)) {
                                    lastChar = (char) -1;
                                }
                            }
                            defaultDouble = Double.parseDouble(defStr);
                        }
                        if (type.equals("position")) {
                            DoubleUnitsRangeOperationItem drItem = new DoubleUnitsRangeOperationItem(propertySheet, doubleSliderListener, defaultDouble, minDouble, maxDouble,
                                    aminDouble, amaxDouble, op, name, parDesc);
                            drItem.setLastChar(lastChar);
                            propItems.add(drItem);

                        } else {
                            if (useSlider) {
                                DoubleRangeOperationItem drItem = new DoubleRangeOperationItem(propertySheet, doubleSliderListener, defaultDouble, minDouble, maxDouble,
                                        aminDouble, amaxDouble, op, name, parDesc);
                                drItem.setLastChar(lastChar);
                                propItems.add(drItem);
                            } else {
                                DoubleOperationItem dItem = new DoubleOperationItem(propertySheet, doubleListener, defaultDouble, minDouble, maxDouble,
                                        op, name, parDesc);
                                dItem.setLastChar(lastChar);
                                propItems.add(dItem);
                            }
                        }

                        break;
                    case "list":
                        ListOperationItem lstItem;
                        ArrayList defaultList = (ArrayList) parMap.get("default");
                        ListOperationItemTypeSelector typeSelector = null;
                        lstItem = new ListOperationItem(propertySheet, listListener, defaultList, op, name, parDesc, typeSelector);
                        propItems.add(lstItem);
                        break;
                    default:
                        break;
                }
            } else {
                boolean isInt = true;
                for (Object type : types) {
                    if (!(type instanceof Integer)) {
                        isInt = false;
                        break;

                    }
                }
                if (isInt) {
                    propItems.add(new IntChoiceOperationItem(propertySheet, intListener, (Integer) types.get(0), types, op, name, parDesc));
                } else {
                    propItems.add(new ChoiceOperationItem(propertySheet, stringListener, (String) types.get(0), types, op, name, parDesc));

                }
            }

        }
        propItems.add(new BooleanOperationItem(propertySheet, boolListener, false, op, "disabled", "Disable this operation"));
    }

}

