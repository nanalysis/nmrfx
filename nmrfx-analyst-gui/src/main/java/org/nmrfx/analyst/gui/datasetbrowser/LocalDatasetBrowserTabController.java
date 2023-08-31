package org.nmrfx.analyst.gui.datasetbrowser;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.stage.DirectoryChooser;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.AnalystPrefs;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.utilities.DatasetSummary;
import org.nmrfx.utils.GUIUtils;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class LocalDatasetBrowserTabController extends DatasetBrowserTabController {
    private static final String TAB_NAME = "Local";
    private final Consumer<String> taskStatusUpdater;
    private final FileSystem fileSystem = FileSystems.getDefault();


    public LocalDatasetBrowserTabController(Consumer<String> taskStatusUpdater) {
        super(TAB_NAME);
        setTableView(new DatasetBrowserTableView(false));
        this.taskStatusUpdater = taskStatusUpdater;
        directoryTextField.setText(AnalystPrefs.getLocalDirectory());

        // Add extra button to open file browser to select directory
        Button button = GlyphsDude.createIconButton(FontAwesomeIcon.FOLDER_OPEN);
        button.setOnAction(e -> browseDirectory());
        hBox.getChildren().add(button);
    }

    @Override
    protected void retrieveIndex() {
        scanTask();
    }

    @Override
    protected void loadIndex() {
        updatePreferences();
        scanTask();
    }

    /**
     * Scans the directory provided in directoryTextField for datasets and creates a list of DatasetSummary objects
     * which is used to update the index file and the tableview.
     */
    private void scanTask() {
        final String scanDir = directoryTextField.getText();
        final Path outPath = Paths.get(scanDir, DatasetSummary.DATASET_SUMMARY_INDEX_FILENAME);
        
        Task<List<DatasetSummary>> task = new Task<>() {
            @Override
            protected List<DatasetSummary> call() {
                Fx.runOnFxThread(() -> taskStatusUpdater.accept("Dataset Browser: Scanning"));

                List<DatasetSummary> results = DatasetBrowserUtil.scanDirectory(scanDir, outPath);
                Fx.runOnFxThread(() -> tableView.setDatasetSummaries(results));
                Fx.runOnFxThread(() -> taskStatusUpdater.accept("Dataset Browser"));
                return results;
            }
        };
        Thread th = new Thread(task);
        th.setDaemon(true);
        th.start();
    }

    @Override
    protected void openFile(boolean useFID) {
        DatasetSummary datasetSummary = tableView.getSelectionModel().getSelectedItem();
        if (datasetSummary != null) {
            String fileName = datasetSummary.getPath();
            File localFile = fileSystem.getPath(directoryTextField.getText(), fileName).toFile();
            if (!localFile.exists()) {
                GUIUtils.warn("Fetch", "File doesn't exist: " + localFile.toString());
                return;
            }
            FXMLController controller = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
            Optional<String> selectedProcessedDataset = datasetSummary.getSelectedProcessedData();
            if (!useFID && selectedProcessedDataset.isPresent()) {
                File baseFile = fileSystem.getPath(directoryTextField.getText(), fileName).toFile();
                if (baseFile.isFile()) {
                    baseFile = baseFile.getParentFile();
                }
                File localDataset = fileSystem.getPath(baseFile.toString(), selectedProcessedDataset.get()).toFile();
                 if (localDataset.exists()) {
                    controller.openDataset(localDataset, false, true);
                }
            } else {
                controller.openFile(localFile.toString(), true, false);
            }
        }
    }

    @Override
    protected void updatePreferences() {
        AnalystPrefs.setLocalDirectory(directoryTextField.getText());
    }

    private void browseDirectory() {
        DirectoryChooser fileChooser = new DirectoryChooser();
        String curDir = directoryTextField.getText();
        String userDir = System.getProperty("user.home");
        File initialDir;
        if (curDir.isEmpty()) {
            initialDir = new File(userDir);
        } else {
            initialDir = new File(curDir);
            if (!initialDir.exists()) {
                initialDir = new File(userDir);
            }
        }
        fileChooser.setInitialDirectory(initialDir);
        File file = fileChooser.showDialog(null);
        if (file != null) {
            directoryTextField.setText(file.toString());
        }
        loadIndex();
    }
}
