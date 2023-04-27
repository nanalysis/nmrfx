package org.nmrfx.analyst.gui.datastore;

import com.nanalysis.datastore.api.enums.DataFormat;
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

public class Datastore {
    private static final Logger log = LoggerFactory.getLogger(Datastore.class);

    private static DatastoreClient createDatastoreClient() {
        return new DatastoreClient(DatastorePrefs.getUrl(), DatastorePrefs.getUsername(), DatastorePrefs.getPassword());
    }

    public static boolean isEnabled() {
        return DatastorePrefs.isEnabled();
    }

    public static ObservableList<RemoteDataset> listExperiments() {
        ObservableList<RemoteDataset> items = FXCollections.observableArrayList();

        try (DatastoreClient client = createDatastoreClient()) {
            client.getExperimentResource().list().stream()
                    .map(DatastoreExperiment::new)
                    .forEach(items::add);
        }

        return items;
    }

    public static File download(RemoteDataset data, File target) throws IOException {
        if (!(data instanceof DatastoreExperiment experiment)) {
            throw new IOException("This isn't a datastore experiment.");
        }

        if (experiment.getDatasetId() < 0) {
            throw new IOException("No compatible RAW dataset for this experiment");
        }

        if (!target.exists() && !target.mkdirs()) {
            throw new IOException("Unable to create directory: " + target.getAbsolutePath());
        }

        if (experiment.selectBestFormat() == DataFormat.SPINLAB) {
            return downloadSpinlabFiles(target, experiment);
        } else if (experiment.selectBestFormat() == DataFormat.JCAMP) {
            return downloadJcampFile(target, experiment);
        } else {
            throw new IOException("No suitable file format for this dataset, best is " + experiment.selectBestFormat());
        }
    }

    private static File downloadSpinlabFiles(File target, DatastoreExperiment experiment) throws IOException {
        log.info("Downloading SPINLAB files from datastore: " + experiment.getPath());
        try (DatastoreClient client = createDatastoreClient()) {
            try (Response response = client.getDownloadEndpoint().downloadSpinlabHeader(experiment.getExperimentId(), experiment.getDatasetId());
                 InputStream inputStream = response.readEntity(InputStream.class);
                 OutputStream out = new FileOutputStream(new File(target, RS2DData.HEADER_FILE_NAME))) {
                IOUtils.copy(inputStream, out);
            }
            try (Response response = client.getDownloadEndpoint().downloadSpinlabData(experiment.getExperimentId(), experiment.getDatasetId());
                 InputStream inputStream = response.readEntity(InputStream.class);
                 OutputStream out = new FileOutputStream(new File(target, RS2DData.DATA_FILE_NAME))) {
                IOUtils.copy(inputStream, out);
            }
        }
        return target;
    }

    private static File downloadJcampFile(File target, DatastoreExperiment experiment) throws IOException {
        log.info("Downloading JCAMP file from datastore: " + experiment.getPath());
        File targetFile = new File(target, experiment.getPath() + ".dx");
        try (DatastoreClient client = createDatastoreClient()) {
            try (Response response = client.getDownloadEndpoint().downloadJCampData(experiment.getExperimentId(), experiment.getDatasetId());
                 InputStream inputStream = response.readEntity(InputStream.class);
                 OutputStream out = new FileOutputStream(targetFile)) {
                IOUtils.copy(inputStream, out);
            }
        }
        return targetFile;
    }
}
