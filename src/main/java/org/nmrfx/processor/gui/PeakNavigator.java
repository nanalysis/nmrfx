package org.nmrfx.processor.gui;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.util.ArrayList;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakEvent;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.PeakListener;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Bruce Johnson
 */
public class PeakNavigator implements PeakListener {

    ToolBar navigatorToolBar;
    TextField peakIdField;
    MenuButton peakListMenuButton;
    ToggleButton deleteButton;
    PeakNavigable peakNavigable;
    PeakList peakList;
    Peak currentPeak;
    static Background deleteBackground = new Background(new BackgroundFill(Color.RED, CornerRadii.EMPTY, Insets.EMPTY));
    Background defaultBackground = null;
    Background defaultCellBackground = null;

    public PeakNavigator(PeakNavigable peakNavigable) {
        this.peakNavigable = peakNavigable;
    }

    void initPeakNavigator(ToolBar toolBar) {
        this.navigatorToolBar = toolBar;
        peakListMenuButton = new MenuButton("List");
        peakIdField = new TextField();
        peakIdField.setMinWidth(50);
        peakIdField.setMaxWidth(50);

        String iconSize = "12px";
        String fontSize = "7pt";
        ArrayList<Button> buttons = new ArrayList<>();
        Button bButton;
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_BACKWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> firstPeak(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.BACKWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> previousPeak(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FORWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> nextPeak(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_FORWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> lastPeak(e));
        buttons.add(bButton);
        deleteButton = GlyphsDude.createIconToggleButton(FontAwesomeIcon.BAN, fontSize, iconSize, ContentDisplay.GRAPHIC_ONLY);
        // prevent accidental activation when inspector gets focus after hitting space bar on peak in spectrum
        // a second space bar hit would activate
        deleteButton.setOnKeyPressed(e -> e.consume());
        deleteButton.setOnAction(e -> setDeleteStatus(deleteButton));

        for (Button button : buttons) {
            // button.getStyleClass().add("toolButton");
        }
        navigatorToolBar.getItems().add(peakListMenuButton);
        toolBar.getItems().addAll(buttons);
        toolBar.getItems().add(peakIdField);
        toolBar.getItems().add(deleteButton);

        peakIdField.setOnKeyReleased(kE -> {
            if (kE.getCode() == KeyCode.ENTER) {
                gotoPeakId();
            }
        });
        updatePeakListMenu();
        MapChangeListener<String, PeakList> mapChangeListener = (MapChangeListener.Change<? extends String, ? extends PeakList> change) -> {
            updatePeakListMenu();
        };

        PeakList.peakListTable.addListener(mapChangeListener);

    }

    public void updatePeakListMenu() {
        peakListMenuButton.getItems().clear();

        for (String peakListName : PeakList.peakListTable.keySet()) {
            MenuItem menuItem = new MenuItem(peakListName);
            menuItem.setOnAction(e -> {
                setPeakList(peakListName);
            });
            peakListMenuButton.getItems().add(menuItem);
        }
    }

    public void initIfEmpty() {
        if (peakList == null) {
            PeakList testList = null;
            FXMLController controller = FXMLController.getActiveController();
            PolyChart chart = controller.getActiveChart();
            if (chart != null) {
                ObservableList<PeakListAttributes> attr = chart.getPeakListAttributes();
                if (!attr.isEmpty()) {
                    testList = attr.get(0).getPeakList();
                }
            }
            if (testList == null) {
                testList = PeakList.get(0);
            }
            setPeakList(testList);

        }
    }

    public void removePeakList() {
        if (peakList != null) {
            peakList.removeListener(this);
        }
        peakList = null;
        currentPeak = null;
    }

    public void setPeakList(String listName) {
        peakList = PeakList.get(listName);
        setPeakList(peakList);
    }

    public void setPeakList(PeakList newPeakList) {
        peakList = newPeakList;
        if (peakList != null) {
            currentPeak = peakList.getPeak(0);
            setPeakIdField();
            peakList.registerListener(this);
        } else {
        }
        peakNavigable.refreshPeakView(currentPeak);
        peakNavigable.refreshPeakListView(peakList);
    }

    public void setPeak(Peak peak) {
        currentPeak = peak;
        peakNavigable.refreshPeakView(peak);
        if (peakList != peak.getPeakList()) {
            peakList = peak.getPeakList();
            peakList.registerListener(this);
            peakNavigable.refreshPeakListView(peakList);
        }
        setPeakIdField();
        updateDeleteStatus();
    }

    void updateDeleteStatus() {
        if (defaultBackground == null) {
            defaultBackground = peakIdField.getBackground();
        }
        if (currentPeak.getStatus() < 0) {
            deleteButton.setSelected(true);
            peakIdField.setBackground(deleteBackground);
        } else {
            deleteButton.setSelected(false);
            peakIdField.setBackground(defaultBackground);
        }

    }

    private void setPeakIdField() {
        if (currentPeak == null) {
            peakIdField.setText("");
        } else {
            peakIdField.setText(String.valueOf(currentPeak.getIdNum()));
        }

    }

    public void previousPeak(ActionEvent event) {
        if (currentPeak != null) {
            int peakIndex = currentPeak.getIndex();
            peakIndex--;
            if (peakIndex < 0) {
                peakIndex = 0;
            }
            Peak peak = peakList.getPeak(peakIndex);
            setPeak(peak);
        }
    }

    public void firstPeak(ActionEvent event) {
        if (peakList != null) {
            Peak peak = peakList.getPeak(0);
            setPeak(peak);
        }
    }

    public void nextPeak(ActionEvent event) {
        if (currentPeak != null) {
            int peakIndex = currentPeak.getIndex();
            peakIndex++;
            if (peakIndex >= peakList.size()) {
                peakIndex = peakList.size() - 1;
            }
            Peak peak = peakList.getPeak(peakIndex);
            setPeak(peak);
        }
    }

    public void lastPeak(ActionEvent event) {
        if (peakList != null) {
            int peakIndex = peakList.size() - 1;
            Peak peak = peakList.getPeak(peakIndex);
            setPeak(peak);
        }
    }

    public void gotoPeakId() {
        if (peakList != null) {
            int id = -1;
            String idString = peakIdField.getText().trim();
            if (idString.length() != 0) {
                try {
                    id = Integer.parseInt(idString);
                } catch (NumberFormatException nfE) {
                    peakIdField.setText("");
                }
                if (id != -1) {
                    Peak peak = peakList.getPeakByID(id);
                    setPeak(peak);
                }
            }
        }
    }

    void setDeleteStatus(ToggleButton button) {
        if (currentPeak != null) {
            if (button.isSelected()) {
                currentPeak.setStatus(-1);
            } else {
                currentPeak.setStatus(0);
            }
            peakNavigable.refreshPeakView(currentPeak);
        }
        updateDeleteStatus();
    }

    @Override
    public void peakListChanged(PeakEvent peakEvent) {
        if (peakEvent.getSource() instanceof PeakList) {
            PeakList sourceList = (PeakList) peakEvent.getSource();
            if (sourceList == peakList) {
                peakNavigable.refreshPeakView();
            }
        }
    }
}
