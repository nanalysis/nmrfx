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

import org.nmrfx.utils.properties.CustomIntegerTextField;
import org.nmrfx.utils.properties.CustomNumberTextField;
import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.controlsfx.control.PropertySheet.Item;
import org.controlsfx.control.textfield.CustomTextField;
import org.controlsfx.property.editor.AbstractPropertyEditor;
import org.controlsfx.property.editor.DefaultPropertyEditorFactory;
import org.controlsfx.property.editor.Editors;
import org.controlsfx.property.editor.PropertyEditor;
import org.nmrfx.utils.properties.MenuTextField;
import org.nmrfx.utils.properties.MenuTextFieldEditor;
import org.nmrfx.utils.properties.PropertySliderEditor;
import org.nmrfx.utils.properties.ZoomSlider;
import org.nmrfx.utils.properties.BooleanOperationItem;
import org.nmrfx.utils.properties.ChoiceOperationItem;
import org.nmrfx.utils.properties.ComplexOperationItem;
import org.nmrfx.utils.properties.DirectoryOperationItem;
import org.nmrfx.utils.properties.DoubleOperationItem;
import org.nmrfx.utils.properties.DoubleRangeOperationItem;
import org.nmrfx.utils.properties.DoubleUnitsRangeOperationItem;
import org.nmrfx.utils.properties.EditableChoiceOperationItem;
import org.nmrfx.utils.properties.FileOperationItem;
import org.nmrfx.utils.properties.IntChoiceOperationItem;
import org.nmrfx.utils.properties.IntOperationItem;
import org.nmrfx.utils.properties.IntPropertySliderEditor;
import org.nmrfx.utils.properties.IntRangeOperationItem;
import org.nmrfx.utils.properties.IntSlider;
import org.nmrfx.utils.properties.ListOperationItem;
import org.nmrfx.utils.properties.MenuTextOperationItem;
import org.nmrfx.utils.properties.TextOperationItem;
import org.nmrfx.utils.properties.TextWaitingOperationItem;

/**
 *
 * @author brucejohnson
 */
public class NvFxPropertyEditorFactory extends DefaultPropertyEditorFactory {

    ProcessorController processorController = null;
    static ZoomSlider ph0Slider = null;

    public NvFxPropertyEditorFactory() {
        super();
    }

    public NvFxPropertyEditorFactory(ProcessorController processorController) {
        super();
        this.processorController = processorController;
    }

    public static void updatePH0Slider(double value) {
        if (ph0Slider != null) {
            ph0Slider.getSlider().setValue(value);
        }
    }

    public void sliderIconUpdated(ZoomSlider zoomSlider, DoubleUnitsRangeOperationItem item) {
        String type = zoomSlider.getIconLabel();
        switch (type) {
            case "h":
                item.setLastChar('h');
                zoomSlider.setRange(-20, 20, 0.0);
                break;
            case "f":
                item.setLastChar('f');
                zoomSlider.setRange(-0.5, 0.5, 0.0);
                break;
            case "P":
                item.setLastChar('p');
                zoomSlider.setRange(-1.0, 1.0, 0.0);
                break;
            case "p":
                item.setLastChar((char) -1);
                zoomSlider.setRange(-20, 20, 0.0);
                break;
            case "":
                item.setLastChar((char) -1);
                zoomSlider.setRange(-20, 20, 0.0);
                break;
            default:
                item.setLastChar((char) -1);
                zoomSlider.setRange(-20.0, 20.0, 0.0);
        }

    }

    @Override
    public PropertyEditor<?> call(Item item) {
        Class<?> type = item.getType();

        //TODO: add support for char and collection editors
//        System.out.println("get editor " + item.getName() + " " + item.getType().toString());
        if (type == DoubleUnitsRangeOperationItem.class) {
            //System.out.println("double item");
            Slider slider = new Slider();
            DoubleUnitsRangeOperationItem dItem = (DoubleUnitsRangeOperationItem) item;
            slider.setMin(dItem.getMin());
            slider.setMax(dItem.getMax());
            slider.setShowTickLabels(true);
            slider.setShowTickMarks(true);
            slider.setBlockIncrement((dItem.getMax() - dItem.getMin()) / 100.0);
            slider.setMajorTickUnit((dItem.getMax() - dItem.getMin()) / 4);
            ZoomSlider zoomSlider = new ZoomSlider(slider, dItem.getAmin(), dItem.getAmax());
            List<String> iconChoices = Arrays.asList("", "p", "h", "P", "f");
            zoomSlider.setIcon(iconChoices, e -> sliderIconUpdated(zoomSlider, dItem));
            zoomSlider.updateFormat();
            dItem.setZoomSlider(zoomSlider);
            return new PropertySliderEditor(dItem, zoomSlider);
        } else if (type == DoubleRangeOperationItem.class) {
            //System.out.println("double item");
            Slider slider = new Slider();
            DoubleRangeOperationItem dItem = (DoubleRangeOperationItem) item;
            slider.setMin(dItem.getMin());
            slider.setMax(dItem.getMax());
            slider.setShowTickLabels(true);
            slider.setShowTickMarks(true);
            slider.setBlockIncrement((dItem.getMax() - dItem.getMin()) / 100.0);
            slider.setMajorTickUnit((dItem.getMax() - dItem.getMin()) / 4);
            ZoomSlider zoomSlider = new ZoomSlider(slider, dItem.getAmin(), dItem.getAmax());
            zoomSlider.updateFormat();
            if (dItem.getCategory().equals("PHASE") && dItem.getName().equals("ph0")) {
                ph0Slider = zoomSlider;
            }
            return new PropertySliderEditor(dItem, zoomSlider);
        } else if (type == DoubleOperationItem.class) {
            DoubleOperationItem iItem = (DoubleOperationItem) item;
            PropertyEditor propEditor = createNumericEditor(item);
            CustomNumberTextField textField = (CustomNumberTextField) propEditor.getEditor();
            textField.setMin(iItem.getMin());
            textField.setMax(iItem.getMax());
            return propEditor;
        } else if (type == IntOperationItem.class) {
            IntOperationItem iItem = (IntOperationItem) item;
            PropertyEditor propEditor = createIntegerEditor(item);
            CustomIntegerTextField textField = (CustomIntegerTextField) propEditor.getEditor();
            textField.setMin(iItem.getMin());
            textField.setMax(iItem.getMax());
            return propEditor;
        } else if (type == FileOperationItem.class) {
            return createFileEditor(item, false);
        } else if (type == DirectoryOperationItem.class) {
            return createFileEditor(item, true);
        } else if (type == TextOperationItem.class) {
            //System.out.println("text item");
            return Editors.createTextEditor(item);
        } else if (type == TextWaitingOperationItem.class) {
            //System.out.println("textwait item");
            TextWaitingOperationItem tItem = (TextWaitingOperationItem) item;
            PropertyEditor editor = Editors.createTextEditor(item);
            TextField textField = (TextField) editor.getEditor();
            textField.setOnKeyReleased(e -> tItem.keyReleased(textField, e));
            return editor;
        } else if (type == MenuTextOperationItem.class) {
            //System.out.println("text item");
            MenuTextOperationItem tItem = (MenuTextOperationItem) item;
            MenuTextField menuTextField;
            if (tItem.getName().equalsIgnoreCase("ref")) {
                menuTextField = new ReferenceMenuTextField(processorController);
            } else {
                menuTextField = new MenuTextField();
            }
            return new MenuTextFieldEditor(tItem, menuTextField);
        } else if (type == IntRangeOperationItem.class) {
            //System.out.println("int range item");
            Slider slider = new Slider();
            IntRangeOperationItem iItem = (IntRangeOperationItem) item;
            slider.setMin(iItem.getMin());
            slider.setMax(iItem.getMax());
            slider.setShowTickLabels(true);
            slider.setShowTickMarks(false);
            slider.setBlockIncrement(1);
            int delta = iItem.getMax() - iItem.getMin();
            if (delta < 6) {
                slider.setMajorTickUnit(1);
                slider.setMinorTickCount(0);
            } else {
                slider.setMajorTickUnit(delta / 4);
                slider.setMinorTickCount(delta / 4 - 1);
            }
            slider.setSnapToTicks(true);

            IntSlider zoomSlider = new IntSlider(slider, iItem.getMin(), iItem.getMax());
            return new IntPropertySliderEditor(iItem, zoomSlider);
        } else if (type == BooleanOperationItem.class) {
            //System.out.println("bpp; range item");
            BooleanOperationItem bItem = (BooleanOperationItem) item;
            return Editors.createCheckEditor(bItem);
        } else if (type == EditableChoiceOperationItem.class) {
            EditableChoiceOperationItem cItem = (EditableChoiceOperationItem) item;
            PropertyEditor editor = Editors.createChoiceEditor(item, cItem.getChoices());
            ComboBox comboBox = (ComboBox) editor.getEditor();
            comboBox.setEditable(true);
            return editor;
        } else if (type == ChoiceOperationItem.class) {
            ChoiceOperationItem cItem = (ChoiceOperationItem) item;
            return Editors.createChoiceEditor(item, cItem.getChoices());
        } else if (type == IntChoiceOperationItem.class) {
            IntChoiceOperationItem cItem = (IntChoiceOperationItem) item;
            return Editors.createChoiceEditor(item, cItem.getChoices());
        } else if (type == ComplexOperationItem.class) {
            //System.out.println("cpx item");

            ComplexOperationItem croi = (ComplexOperationItem) item;
            return Editors.createTextEditor(croi);
        } else if (type == ListOperationItem.class) {
            //System.out.println("list item");

            ListOperationItem loi = (ListOperationItem) item;
            return Editors.createTextEditor(loi);
//            return Editors.createCustomEditor(loi).get();
        }
        return super.call(item);
//        if (/*type != null &&*/ type == String.class) {
//            return Editors.createTextEditor(item);
//        }
//
//        if (/*type != null &&*/ isNumber(type)) {
//            return Editors.createNumericEditor(item);
//        }
//
//        if (/*type != null &&*/(type == boolean.class || type == Boolean.class)) {
//            return Editors.createCheckEditor(item);
//        }
//
//        if (/*type != null &&*/type == LocalDate.class) {
//            return Editors.createDateEditor(item);
//        }
//
//        if (/*type != null &&*/type == Color.class) {
//            return Editors.createColorEditor(item);
//        }
//
//        if (type != null && type.isEnum()) {
//            return Editors.createChoiceEditor(item, Arrays.<Object>asList(type.getEnumConstants()));
//        }
//
//        if (/*type != null &&*/type == Font.class) {
//            return Editors.createFontEditor(item);
//        }
//
//        return null;
    }
    private static Class<?>[] numericTypes = new Class[]{
        byte.class, Byte.class,
        short.class, Short.class,
        int.class, Integer.class,
        long.class, Long.class,
        float.class, Float.class,
        double.class, Double.class,
        BigInteger.class, BigDecimal.class
    };

    // there should be better ways to do this
    private static boolean isNumber(Class<?> type) {
        if (type == null) {
            return false;
        }
        for (Class<?> cls : numericTypes) {
            if (type == cls) {
                return true;
            }
        }
        return false;
    }

    public static final PropertyEditor<?> createNumericEditor(Item property) {

        return new AbstractPropertyEditor<Number, CustomNumberTextField>(property, new CustomNumberTextField()) {

            private Class<? extends Number> sourceClass = (Class<? extends Number>) property.getType(); //Double.class;

            @Override
            protected ObservableValue<Number> getObservableValue() {
                return getEditor().numberProperty();
            }

            @Override
            public Number getValue() {
                try {
                    return sourceClass.getConstructor(String.class).newInstance(getEditor().getText());
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            public void setValue(Number value) {
                sourceClass = (Class<? extends Number>) value.getClass();
                getEditor().setText(value.toString());
            }

        };
    }

    public static final PropertyEditor<?> createIntegerEditor(Item property) {

        return new AbstractPropertyEditor<Number, CustomIntegerTextField>(property, new CustomIntegerTextField()) {
            @Override
            protected ObservableValue<Number> getObservableValue() {
                return getEditor().numberProperty();
            }

            @Override
            public Number getValue() {
                try {
                    return Integer.parseInt(getEditor().getText());
                } catch (NumberFormatException nfE) {
                    return null;
                }
            }

            @Override
            public void setValue(Number value) {
                getEditor().setText(value.toString());
            }
        };
    }

    public static final PropertyEditor<?> createFileEditor(Item property, final boolean directoryMode) {
        CustomTextField textField = new CustomTextField();
        Button button = GlyphsDude.createIconButton(FontAwesomeIcon.FOLDER_OPEN);
        textField.setRight(button);
        AbstractPropertyEditor<String, CustomTextField> editor = new AbstractPropertyEditor<String, CustomTextField>(property, textField) {
            @Override
            protected ObservableValue<String> getObservableValue() {
                return getEditor().textProperty();
            }

            @Override
            public String getValue() {
                return getEditor().getText();
            }

            @Override
            public void setValue(String value) {
                getEditor().setText(value);
            }
        };
        button.setOnAction(e -> {
            File file = getFile(e, directoryMode);
            if (file != null) {
                String path = file.getPath();
                editor.setValue(path);
            }

        });
        return editor;
    }

    static File getFile(ActionEvent e, boolean directoryMode) {
        File file;
        if (directoryMode) {
            DirectoryChooser dirChooser = new DirectoryChooser();
            file = dirChooser.showDialog(null);
        } else {
            FileChooser fileChooser = new FileChooser();
            file = fileChooser.showOpenDialog(null);
        }
        return file;
    }
}
