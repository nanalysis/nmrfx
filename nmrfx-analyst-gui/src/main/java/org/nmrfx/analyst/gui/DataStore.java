package org.nmrfx.analyst.gui;

import com.nanalysis.datastore.api.enums.DataFormat;
import com.nanalysis.datastore.api.enums.DataType;
import com.nanalysis.datastore.api.model.DatasetDTO;
import com.nanalysis.datastore.api.model.ExperimentDTO;
import com.nanalysis.datastore.client.DatastoreClient;
import jakarta.ws.rs.core.Response;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.commons.io.IOUtils;
import org.nmrfx.processor.datasets.vendor.rs2d.RS2DData;
import org.nmrfx.utilities.RemoteDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;

public class DataStore {
    private static final Logger log = LoggerFactory.getLogger(DataStore.class);

    //TODO: put in preferences, load only once
    private static DatastoreClient createDatastoreClient() {
        return new DatastoreClient("http://localhost:8080/", "admin", "password");
    }

    //TODO: put in preferences
    public static boolean isEnabled() {
        return true;
    }

    public static ObservableList<RemoteDataset> loadFromDatastore() {
        ObservableList<RemoteDataset> items = FXCollections.observableArrayList();

        try (DatastoreClient client = createDatastoreClient()) {
            client.getExperimentResource().list().stream()
                    .map(DataStore::experientToRemoteDataset)
                    .forEach(items::add);
        }

        return items;
    }

    public static void downloadFromDatastore(RemoteDataset data, File target) throws IOException {
        if (data.getDatastoreExperimentId() == null || data.getDatastoreDatasetId() == null) {
            throw new IOException("No raw spinlab dataset for this experiment");
        }

        if (!target.exists() && !target.mkdirs()) {
            throw new IOException("Unable to create directory: " + target.getAbsolutePath());
        }

        try (DatastoreClient client = createDatastoreClient()) {
            try (Response response = client.getDownloadEndpoint().downloadSpinlabHeader(data.getDatastoreExperimentId(), data.getDatastoreDatasetId());
                 InputStream inputStream = response.readEntity(InputStream.class);
                 OutputStream out = new FileOutputStream(new File(target, RS2DData.HEADER_FILE_NAME))) {
                IOUtils.copy(inputStream, out);
            }
            try (Response response = client.getDownloadEndpoint().downloadSpinlabData(data.getDatastoreExperimentId(), data.getDatastoreDatasetId());
                 InputStream inputStream = response.readEntity(InputStream.class);
                 OutputStream out = new FileOutputStream(new File(target, RS2DData.DATA_FILE_NAME))) {
                IOUtils.copy(inputStream, out);
            }
        }
    }

    private static RemoteDataset experientToRemoteDataset(ExperimentDTO experiment) {
        RemoteDataset dataset = new RemoteDataset();
        dataset.setDatastoreExperimentId(experiment.getId());

        // download only one raw data
        var rawDatasets = findSpinlabDatasets(experiment, DataType.RAW);
        if (!rawDatasets.isEmpty()) {
            dataset.setDatastoreDatasetId(rawDatasets.get(0).getId());
        }

        dataset.setPath(experiment.getId() + "-" + experiment.getName());
        dataset.setUser(experiment.getOperatorName());
        dataset.setTime(experiment.getAcquisitionDate().toString());
        dataset.setSeq(experiment.getSequenceName());
        dataset.setSample(experiment.getSampleName());
        dataset.setNd(experiment.getNumberOfDimensions());
        return dataset;
    }

    private static List<DatasetDTO> findSpinlabDatasets(ExperimentDTO experiment, DataType type) {
        return experiment.getDatasets().stream()
                .filter(ds -> ds.getType() == type)
                .filter(ds -> ds.getFiles().stream().anyMatch(df -> df.getFormat() == DataFormat.SPINLAB))
                .toList();
    }
}
