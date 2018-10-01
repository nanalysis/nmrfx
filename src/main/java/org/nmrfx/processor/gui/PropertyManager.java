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

import org.nmrfx.processor.gui.properties.ListOperationItem;
import org.nmrfx.processor.gui.properties.ChoiceOperationItem;
import org.nmrfx.processor.gui.properties.IntRangeOperationItem;
import org.nmrfx.processor.gui.properties.IntOperationItem;
import org.nmrfx.processor.gui.properties.BooleanOperationItem;
import org.nmrfx.processor.gui.properties.FileOperationItem;
import org.nmrfx.processor.gui.properties.OperationItem;
import org.nmrfx.processor.gui.properties.DoubleRangeOperationItem;
import org.nmrfx.processor.gui.properties.TextWaitingOperationItem;
import org.nmrfx.processor.gui.properties.ListOperationItemTypeSelector;
import org.nmrfx.processor.gui.properties.ComplexOperationItem;
import org.nmrfx.processor.gui.properties.IntChoiceOperationItem;
import org.nmrfx.processor.gui.properties.TextOperationItem;
import org.nmrfx.processor.gui.spectra.SpecRegion;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import org.apache.commons.math3.complex.Complex;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.PropertySheet;
import org.python.core.PyComplex;
import java.util.regex.*;

/**
 *
 * @author brucejohnson
 */
public class PropertyManager {

    private static String patternString = "(\\w+)=((\\[[^\\[]*\\])|(\"[^\"]*\")|('[^']*')|([^,]+))";

    ChangeListener<Number> doubleListener;
    ChangeListener<Number> intListener;
    ChangeListener<String> stringListener;
    ChangeListener<Boolean> boolListener;
    ChangeListener<String> complexListener;
    ChangeListener<String> listListener;
    PropertySheet propertySheet;
    ListView scriptView;
    ProcessorController processorController;
    ObservableList<String> listItems;
    private int currentIndex = -1;
    private String currentOp = "";
    private TextField opTextField;
    PopOver popOver;
    ObservableList<PropertySheet.Item> propItems = FXCollections.observableArrayList();

    PropertyManager(ProcessorController processorController, final ListView scriptView, PropertySheet propertySheet, ObservableList<String> listItems, TextField opTextField, PopOver popOver) {
        this.processorController = processorController;
        this.scriptView = scriptView;
        this.listItems = listItems;
        this.opTextField = opTextField;
        this.propertySheet = propertySheet;
        this.popOver = popOver;
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

        ChangeListener<Number> scriptOpListener = new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observableValue, Number number, Number number2) {
                int scriptIndex = (Integer) number2;
                try {
                    if ((listItems.size() == 1) && (scriptIndex == -1)) {
                        //there is a single item in the Cells -- Don't unselect
                        scriptIndex = 0;
                    } else if (scriptIndex != -1) {
                        String selOp = (String) scriptView.getItems().get(scriptIndex);
                        setPropSheet(scriptIndex, selOp);
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
        scriptView.getSelectionModel().selectedIndexProperty().addListener(scriptOpListener);

        propertySheet.setPropertyEditorFactory(new NvFxPropertyEditorFactory(processorController));
        propertySheet.setMode(PropertySheet.Mode.NAME);
        propertySheet.setModeSwitcherVisible(false);
        propertySheet.setSearchBoxVisible(false);

    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    protected String getScript() {
        StringBuilder script = new StringBuilder();
        for (Object obj : listItems) {
            script.append(obj.toString());
            script.append("\n");
        }
        return script.toString();
    }

    public int getCurrentPosition(String op) {
        op = op.trim();
        op = OperationInfo.fixOp(op);
        int currentPos = OperationInfo.getCurrentPosition(listItems, op);
        return currentPos;
    }

    public void setOps(String[] ops) {
        ArrayList<String> opList = new ArrayList<>();
        for (String op : ops) {
            op = op.trim();
            op = OperationInfo.fixOp(op);
            opList.add(op);
        }
        listItems.setAll(opList);
    }

    public int setOp(String op, boolean appendOp, int index) {
        op = op.trim();
        op = OperationInfo.fixOp(op);
        int opIndex = -1;
        if (op.length() != 0) {
            int currentPos = OperationInfo.getCurrentPosition(listItems, op);
            if (appendOp && (currentPos == -1)) {
                appendOp = false;
            }
            opIndex = index;
            if (opIndex == -1) {
                opIndex = OperationInfo.getPosition(listItems, op);
            }
            if (opIndex < 0) {
                System.out.println("bad op");
            } else if (opIndex >= listItems.size()) {
                listItems.add(op);
                scriptView.getSelectionModel().select(opIndex);
                opIndex = listItems.size() - 1;
            } else {
                String curOp = OperationInfo.trimOp(listItems.get(opIndex));
                String trimOp = OperationInfo.trimOp(op);
                if (!appendOp && trimOp.equals(curOp)) {
                    listItems.set(opIndex, op);
                    /**
                     * If the selected index isn't equal to the op index, or if we are not at the case where there's a
                     * single op, then change the selected cell.
                     */
                    if (!(scriptView.getSelectionModel().getSelectedIndex() == opIndex)
                            && !(scriptView.getSelectionModel().getSelectedIndex() == -1 && listItems.size() == 1)) {
                        scriptView.getSelectionModel().select(opIndex);
                    }
                } else {
                    listItems.add(opIndex, op);
                    scriptView.getSelectionModel().select(opIndex);
                }
            }
            opTextField.setText("");
            popOver.hide();
        }
        return opIndex;
    }

    int setOp(String op, int index) {
        return setOp(op, false, index);
    }

    int setOp(String op) {
        return setOp(op, false, -1);
    }

    private void updateOp(PropertySheet.Item updateItem) {
        if (currentIndex == -1) {
            return;
        }
        int index = currentIndex;
        String opName = updateItem.getCategory();
        if (!opName.equals(currentOp)) {
            return;
        }
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
            } else {
            }

        }
        opString.append(')');
        setOp(opString.toString(), false, index);

    }

    void addRegionRangeOld(double min, double max, double f1, double f2) {
        System.out.println("add region range " + f1 + " " + f2);
        List<PropertySheet.Item> items = propertySheet.getItems();
        for (PropertySheet.Item item : items) {
            if ((item != null) && item.getCategory().equals("REGIONS") && item.getName().equals("regions")) {
                System.out.println("really add region range " + f1 + " " + f2);
                ListOperationItem listOpItem = (ListOperationItem) item;
                ArrayList newValue = new ArrayList(listOpItem.getValueList());
                f1 = Math.round(f1 * 1.0e5) / 1.0e5;
                f2 = Math.round(f2 * 1.0e5) / 1.0e5;
                newValue.add(new Double(f1));
                newValue.add(new Double(f2));
                ((OperationItem) item).setValue(newValue);
                //setPropSheet("REGIONS");
                break;
            } else if ((item != null) && item.getCategory().equals("EXTRACT") && item.getName().equals("start")) {
                //int imin = (((int) min) / 32) * 32;
                int imin = (int) (min + 0.5);
                //System.out.println("really add start" + imin);
                ((OperationItem) item).setValue(imin);
            } else if ((item != null) && item.getCategory().equals("EXTRACT") && item.getName().equals("end")) {
                //int imax = ((((int) max) / 32) + 1) * 32;
                int imax = (int) (max + 0.5);
                //System.out.println("really add end" + imax);
                ((OperationItem) item).setValue(imax);
            } else if ((item != null) && item.getCategory().equals("EXTRACT") && item.getName().equals("mode")) {
                ((OperationItem) item).setValue("region");
            }
        }
    }

    void addExtractRegion(double min, double max, double f1, double f2) {
        int imin = (int) (min + 0.5);
        int imax = (int) (max + 0.5);
        String opString = "EXTRACT(start=" + imin + ",end=" + imax + ",mode='region')";
        int opIndex = setOp(opString);
        setPropSheet(opIndex, opString);
    }

    void addBaselineRegion(ArrayList<Double> values, double f1, double f2, boolean clear) {
        TreeSet<SpecRegion> regions = new TreeSet(new SpecRegion());
        for (int i = 0; i < values.size(); i += 2) {
            SpecRegion region = new SpecRegion(values.get(i), values.get(i + 1));
            regions.add(region);
        }

        f1 = Math.round(f1 * 1.0e5) / 1.0e5;
        f2 = Math.round(f2 * 1.0e5) / 1.0e5;
        SpecRegion region = new SpecRegion(f1, f2);
        region.removeOverlapping(regions);
        if (!clear) {
            regions.add(region);
        }

        StringBuilder sBuilder = new StringBuilder();
        boolean first = true;
        for (SpecRegion specRegion : regions) {
            if (!first) {
                sBuilder.append(",");
            } else {
                first = false;
            }
            sBuilder.append(specRegion.getSpecRegionStart(0));
            sBuilder.append(",");
            sBuilder.append(specRegion.getSpecRegionEnd(0));

        }
        String opString = "REGIONS(regions=[" + sBuilder.toString() + "])";
        int opIndex = -1;
        if ((currentIndex != -1) && currentOp.equals("REGIONS")) {
            opIndex = setOp(opString, false, currentIndex);
        } else {
            opIndex = setOp(opString);
        }
        setPropSheet(opIndex, opString);
    }

    void clearBaselineRegions() {
        String opString = "REGIONS(regions=[])";
        int opIndex = -1;
        if ((currentIndex != -1) && currentOp.equals("REGIONS")) {
            opIndex = setOp(opString, false, currentIndex);
        } else {
            opIndex = setOp(opString);
        }
        setPropSheet(opIndex, opString);

    }

    void setPropSheet(int scriptIndex, String op) {
        currentIndex = -1;
        String trimOp = OperationInfo.trimOp(op);
        Pattern pattern = null;
        String opPars = "";
        if (!op.equals("")) {
            opPars = op.substring(op.indexOf('(') + 1, op.length() - 1);
            pattern = Pattern.compile(patternString);
        }
        //System.out.println("set prop sheet " + scriptIndex + " " + op);
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
        propertySheet.getItems().setAll(newItems);

    }

    public void updatePropSheet() {
        ArrayList<PropertySheet.Item> copyItems = new ArrayList<>(propertySheet.getItems());
        propertySheet.getItems().setAll(copyItems);
    }

    public static Map<String, String> parseOpString(String op) {
        Map<String, String> values = new HashMap<>();
        Pattern pattern = null;
        String opPars = "";
        if (!op.equals("")) {
            opPars = op.substring(op.indexOf('(') + 1, op.length() - 1);
            pattern = Pattern.compile(patternString);
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

    void setupItems() {
        ArrayList pyDocs = processorController.chartProcessor.getDocs();
        for (int i = 0; i < pyDocs.size(); i += 3) {
            String op = (String) pyDocs.get(i);
            ArrayList parList = (ArrayList) pyDocs.get(i + 1);
            String description = (String) pyDocs.get(i + 2);

            for (Object parObj : parList) {
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
                            propItems.add(new TextOperationItem(stringListener, defaultString, op, name, parDesc));

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
                            propItems.add(new TextWaitingOperationItem(null, this::updateOp, defaultString, op, name, parDesc));
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
                            propItems.add(new FileOperationItem(stringListener, defaultString, op, name, parDesc));
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
                            propItems.add(new BooleanOperationItem(boolListener, defaultBool, op, name, parDesc));
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
                                propItems.add(new IntRangeOperationItem(intListener, defaultInt, minInt, maxInt, op, name, parDesc));
                            } else {
                                propItems.add(new IntOperationItem(intListener, defaultInt, minInt, maxInt, op, name, parDesc));

                            }

                            break;
                        case "complex":
                            Object defComplexObj = parMap.get("default");
                            if (defComplexObj instanceof PyComplex) {
                                PyComplex defPyComplex = (PyComplex) defComplexObj;
                                Complex defComplex = new Complex(
                                        defPyComplex.real, defPyComplex.imag);
                                ComplexOperationItem crItem = new ComplexOperationItem(complexListener, defComplex, op, name, parDesc);
                                propItems.add(crItem);
                            } else if (defComplexObj instanceof Number) {
                                PyComplex defPyComplex = (PyComplex) defComplexObj;
                                Complex defComplex = new Complex(
                                        defPyComplex.real, defPyComplex.imag);
                                ComplexOperationItem crItem = new ComplexOperationItem(complexListener, defComplex, op, name, parDesc);
                                propItems.add(crItem);
                            }
                            break;

                        case "real":
                        case "double":
                        case "position":
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

                            DoubleRangeOperationItem drItem = new DoubleRangeOperationItem(doubleListener, defaultDouble, minDouble, maxDouble,
                                    aminDouble, amaxDouble, op, name, parDesc);
                            drItem.setLastChar(lastChar);
                            propItems.add(drItem);

                            break;
                        case "list":
                            ArrayList listTypes = (ArrayList) parMap.get("listTypes");
                            ListOperationItem lstItem;
                            ArrayList defaultList = (ArrayList) parMap.get("default");
                            //ListOperationItemTypeSelector typeSelector = new ListOperationItemTypeSelector(stringListener, (String) listTypes.get(0), listTypes, op, "listType", parDesc);
                            ListOperationItemTypeSelector typeSelector = null;

                            if (listTypes == null || listTypes.isEmpty()) { // just a default list, so treat them as real values.
                                lstItem = new ListOperationItem(listListener, defaultList, listTypes, op, name, parDesc, typeSelector);
                            } else {
                                lstItem = new ListOperationItem(listListener, defaultList, listTypes, op, name, parDesc, typeSelector);
                            }

                            propItems.add(lstItem);
                            //propItems.add(typeSelector);
                            //propItems.add(listItemTypeSelector(listType));

//                            ListOperationItem lstItem = new ListOperationItem(listListener, defaultList, listType, op, name, parDesc)
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
                        propItems.add(new IntChoiceOperationItem(intListener, (Integer) types.get(0), types, op, name, parDesc));
                    } else {
                        propItems.add(new ChoiceOperationItem(stringListener, (String) types.get(0), types, op, name, parDesc));

                    }
                }
                // propItems.add(new DoubleRangeOperationItem(doubleListener, 1.0, 0.5, 1.0, "SB", "c", "First point multiplier"));

            }
            propItems.add(new BooleanOperationItem(boolListener, false, op, "disabled", "Disable this operation"));
        }

        //propItems.add(new DoubleRangeOperationItem(doubleListener, 1.0, 0.5, 1.0, "SB", "c", "First point multiplier"));
    }

}
