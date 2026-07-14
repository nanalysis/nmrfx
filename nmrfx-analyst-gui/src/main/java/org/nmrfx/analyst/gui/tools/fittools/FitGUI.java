package org.nmrfx.analyst.gui.tools.fittools;

import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import org.controlsfx.control.CheckComboBox;
import org.nmrfx.analyst.gui.TablePlotGUI;

import java.util.List;

public abstract class FitGUI {
    public abstract void setupGridPane(VBox extraBox);
    public abstract  List<TablePlotGUI.ParItem> addDerivedPars(List<TablePlotGUI.ParItem> parItems);

    public abstract void setXYChoices(TableView tableView, ChoiceBox<String> xArrayChoice, CheckComboBox<String> yArrayChoice);

    }
