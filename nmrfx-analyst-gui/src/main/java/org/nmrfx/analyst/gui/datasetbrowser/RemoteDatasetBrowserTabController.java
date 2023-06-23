package org.nmrfx.analyst.gui.datasetbrowser;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.AnalystPrefs;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.utilities.DatasetSummary;
import org.nmrfx.utilities.RemoteDatasetAccess;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class RemoteDatasetBrowserTabController extends DatasetBrowserTabController {
    private static final Logger log = LoggerFactory.getLogger(RemoteDatasetBrowserTabController.class);
    private static final String TAB_NAME = "Remote";
    private final FileSystem fileSystem = FileSystems.getDefault();
    private final Path pathToLocalCache = fileSystem.getPath(System.getProperty("user.home"), "NMRFx_Remote_Datasets");
    private RemoteDatasetAccess remoteDatasetAccess;

    public RemoteDatasetBrowserTabController() {
        super(TAB_NAME);
        tableView = new DatasetBrowserTableView(true);
        borderPane.setCenter(tableView);

        // Add fetch button to toolbar
        Button fetchButton = new Button("Fetch");
        fetchButton.setOnAction(e -> cacheDatasets());
        toolBar.getItems().add(fetchButton);

        directoryTextField.setText(AnalystPrefs.getRemoteDirectory());
    }

    /**
     * Create a RemoteDatasetAccess object and try to connect.
     * @return True if remoteDatasetAccess is connected, otherwise false.
     */
    private boolean initRemoteDatasetAccess() {
        if (remoteDatasetAccess == null) {
            String remoteHost = AnalystPrefs.getRemoteHostName();
            String remoteUser = AnalystPrefs.getRemoteUserName();

            remoteDatasetAccess = new RemoteDatasetAccess(remoteUser, remoteHost);
            boolean usePassword = AnalystPrefs.getUseRemotePassword();
            if (usePassword && !remoteDatasetAccess.passwordValid()) {
                String pw = GUIUtils.getPassword();
                if (pw != null) {
                    remoteDatasetAccess.setPassword(pw);
                }
            }
        }
        if (!remoteDatasetAccess.isConnected()) {
            try {
                remoteDatasetAccess.connect();
            } catch (IOException ex) {
                remoteDatasetAccess = null;
                GUIUtils.warn("Remote Access", "Can't open session " + ex.getMessage());
                log.error(ex.getMessage(), ex);
                return false;
            }
        }
        return true;
    }

    /**
     * Retrieve the index file from the remote directory and copy it to the local directory. Populate the tableview
     * with the contents of the index file.
     */
    @Override
    protected void retrieveIndex() {
        File dir = pathToLocalCache.toFile();
        if (!dir.exists()) {
            try {
                Files.createDirectories(pathToLocalCache);
            } catch (IOException ex) {
                GUIUtils.warn("Fetch", "Can't create directory");
                log.error(ex.getMessage(), ex);
                return;
            }
        }
        File localFile = getLocalIndexFile();
        String remoteFile = Path.of(directoryTextField.getText(), DatasetSummary.DATASET_SUMMARY_INDEX_FILENAME).toString();

        if (initRemoteDatasetAccess()) {
            boolean ok = remoteDatasetAccess.fetchFile(remoteFile, localFile);
            if (ok) {
                loadIndex();
            }
        }
    }

    /**
     * Update the preferences and populate the tableview with the contents of the index file.
     */
    @Override
    protected void loadIndex() {
        updatePreferences();
        File localFile = getLocalIndexFile();
        ObservableList<DatasetSummary> items = FXCollections.observableArrayList();
        if (localFile.exists()) {
            try {
                DatasetSummary.loadListFromFile(localFile);
                items.addAll(DatasetSummary.loadListFromFile(localFile));
            } catch (IOException ex) {
                log.warn(ex.getMessage(), ex);
            }
            scanDirectory(items);
        }
        tableView.setItems(items);
    }

    /**
     * Create the localIndexFile based on the fileName and the local cache directory.
     * @return The local index File
     */
    private File getLocalIndexFile() {
        return fileSystem.getPath(pathToLocalCache.toString(), DatasetSummary.DATASET_SUMMARY_INDEX_FILENAME).toFile();
    }

    /**
     * Iterate over a list of DatasetSummary and set the processed and present values for each summary based on the contents
     * of the datasets in the local cache directory.
     * @param items The DatasetSummary objects to set.
     */
    void scanDirectory(List<DatasetSummary> items) {
        String localPathString = pathToLocalCache.toString();
        for (DatasetSummary datasetSummary : items) {
            String fileName = datasetSummary.getPath();
            File localFile = fileSystem.getPath(localPathString, fileName).toFile();
            datasetSummary.setProcessed(NMRDataUtil.getProcessedDataset(localFile));
            datasetSummary.setPresent(localFile.exists());
        }
    }

    /**
     * Fetch datasets for any of the selected items in the tableview that do not have a local copy.
     */
    private void cacheDatasets() {
        var datasetSummaries = tableView.getSelectionModel().getSelectedItems();
        for (var datasetSummary : datasetSummaries) {
            if (datasetSummary != null && !datasetSummary.isPresent()) {
                try {
                    if (initRemoteDatasetAccess()) {
                       fetchDatasetFromServer(datasetSummary);
                    } else {
                        return;
                    }
                } catch (IOException ex) {
                    var title = "Retrieve Selected Data";
                    GUIUtils.warn(title, "Error: " + ex.getMessage());
                    log.error(ex.getMessage(), ex);
                    break;
                }
            }
        }
    }

    /**
     * Opens the dataset or fid file. If the file is not present locally, it is fetched from the server before opening.
     * @param useFID Whether to open the fid of the file or the dataset.
     */
    @Override
    protected void openFile(boolean useFID) {
        DatasetSummary datasetSummary = tableView.getSelectionModel().getSelectedItem();
        if (datasetSummary == null) {
            return;
        }
        String fileName = datasetSummary.getPath();
        FXMLController controller = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
        try {
            if (!useFID && !datasetSummary.getProcessed().isEmpty()) {
                File localDataset = fileSystem.getPath(pathToLocalCache.toString(), fileName, datasetSummary.getProcessed()).toFile();
                if (localDataset.exists()) {
                    controller.openDataset(localDataset, false, true);
                }
            } else {
                if (!datasetSummary.isPresent()) {
                    if (initRemoteDatasetAccess()) {
                        fetchDatasetFromServer(datasetSummary);
                    } else {
                        return;
                    }
                }
                File localFile = fileSystem.getPath(pathToLocalCache.toString(), fileName).toFile();
                controller.openFile(localFile.toString(), true, false);
            }
        } catch (IOException ex) {
            String mode = useFID ? "FID" : "Dataset";
            GUIUtils.warn("Open " + mode, "Error opening: " + ex.getMessage());
            log.error(ex.getMessage(), ex);
        }
    }

    /**
     * Fetch a dataset from the remote server and set the present value in the provided DatasetSummary;
     * @param datasetSummary The DatasetSummary of the dataset being fetched.
     * @throws IOException If unable to unzip the file.
     */
    private void fetchDatasetFromServer (DatasetSummary datasetSummary) throws IOException {
        String fileName = datasetSummary.getPath();
        String remoteFile = Path.of(directoryTextField.getText(), fileName).toString();
        File localFile = fileSystem.getPath(pathToLocalCache.toString(), fileName).toFile();
        datasetSummary.setPresent(remoteDatasetAccess.fetchFile(remoteFile, localFile));
        tableView.refresh();
    }

    /**
     * Update the remote directory in preferences with the value from the directoryTextField.
     */
    @Override
    protected void updatePreferences() {
        AnalystPrefs.setRemoteDirectory(directoryTextField.getText());
    }
    
}
