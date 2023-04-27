package org.nmrfx.analyst.gui.datastore;

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

import java.io.*;
import java.util.Optional;
import java.util.Set;

public class Datastore {
    private static final Set<DataFormat> SUPPORTED_FORMATS = Set.of(DataFormat.SPINLAB, DataFormat.JCAMP);

    private static DatastoreClient createDatastoreClient() {
        return new DatastoreClient(DatastorePrefs.getUrl(), DatastorePrefs.getUsername(), DatastorePrefs.getPassword());
    }

    public static boolean isEnabled() {
        return DatastorePrefs.isEnabled();
    }

    public static ObservableList<RemoteDataset> loadFromDatastore() {
        ObservableList<RemoteDataset> items = FXCollections.observableArrayList();

        try (DatastoreClient client = createDatastoreClient()) {
            client.getExperimentResource().list().stream()
                    .map(Datastore::experientToRemoteDataset)
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
        selectRawDataset(experiment).ifPresent(ds -> dataset.setDatastoreDatasetId(ds.getId()));

        dataset.setPath(experiment.getId() + "-" + experiment.getName());
        dataset.setUser(experiment.getOperatorName());
        dataset.setTime(experiment.getAcquisitionDate().toString());
        dataset.setSeq(experiment.getSequenceName());
        dataset.setSample(experiment.getSampleName());
        dataset.setNd(experiment.getNumberOfDimensions());
        return dataset;
    }

    private static Optional<DatasetDTO> selectRawDataset(ExperimentDTO experiment) {
        return experiment.getDatasets().stream()
                .filter(ds -> ds.getType() == DataType.RAW)
                .filter(ds -> ds.getFiles().stream().anyMatch(df -> SUPPORTED_FORMATS.contains(df.getFormat())))
                .findFirst();
    }
}
