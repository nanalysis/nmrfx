package org.nmrfx.analyst.gui.molecule;

import javafx.scene.control.TextInputDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.structure.chemistry.Molecule;

import java.util.Optional;

public class MoleculeUtils {

    private MoleculeUtils() {
    }

    /**
     * Adds the active molecule to the active chart and refreshes the chart.
     */
    public static void addActiveMoleculeToCanvas() {
        Molecule activeMol = Molecule.getActive();
        if (activeMol != null) {
            PolyChart activeChart = AnalystApp.getFXMLControllerManager().getOrCreateActiveController().getActiveChart();
            var cMols = activeChart.findAnnoTypes(CanvasMolecule.class);
            CanvasMolecule cMol = null;
            if (cMols.isEmpty()) {
                cMol = new CanvasMolecule(activeChart);
                cMol.setPosition(0.1, 0.1, 0.3, 0.3, "FRACTION", "FRACTION");
            } else {
                cMol = (CanvasMolecule) cMols.get(0);
            }

            cMol.setMolName(activeMol.getName());
            activeMol.label = MoleculeBase.LABEL_NONHC;
            activeMol.clearSelected();

            activeChart.clearAnnoType(CanvasMolecule.class);
            activeChart.addAnnotation(cMol);
            activeChart.refresh();
        }
    }

    /**
     * Clears any molecules from the active chart and refreshes it.
     */
    public static void removeMoleculeFromCanvas() {
        PolyChart chart = AnalystApp.getFXMLControllerManager().getOrCreateActiveController().getActiveChart();
        chart.clearAnnoType(CanvasMolecule.class);
        chart.refresh();
    }

    /**
     * Displays a text input dialog to the user to get a name for a molecule.
     *
     * @return The provided name or an empty string.
     */
    public static String moleculeNamePrompt() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Set Molecule Name");
        dialog.setHeaderText("Molecule name missing.");
        dialog.setContentText("Set a name for the molecule");
        Optional<String> result = dialog.showAndWait();
        return result.orElse("");
    }
}
