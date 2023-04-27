package org.nmrfx.analyst.gui.datastore;

import com.nanalysis.datastore.api.enums.DataFormat;
import com.nanalysis.datastore.api.enums.DataType;
import com.nanalysis.datastore.api.model.DatasetDTO;
import com.nanalysis.datastore.api.model.ExperimentDTO;
import org.nmrfx.utilities.RemoteDataset;

import java.util.Optional;
import java.util.Set;

public class DatastoreExperiment extends RemoteDataset {
    private static final Set<DataFormat> SUPPORTED_FORMATS = Set.of(DataFormat.SPINLAB, DataFormat.JCAMP);

    private final long experimentId;
    private final long datasetId;

    public DatastoreExperiment(ExperimentDTO experiment) {
        experimentId = experiment.getId();
        datasetId = selectRawDataset(experiment).map(DatasetDTO::getId).orElse(-1L);

        setPath(experiment.getId() + "-" + experiment.getName());
        setUser(experiment.getOperatorName());
        setTime(experiment.getAcquisitionDate().toString());
        setSeq(experiment.getSequenceName());
        setSample(experiment.getSampleName());
        setNd(experiment.getNumberOfDimensions());
    }

    public long getExperimentId() {
        return experimentId;
    }

    public long getDatasetId() {
        return datasetId;
    }

    private static Optional<DatasetDTO> selectRawDataset(ExperimentDTO experiment) {
        return experiment.getDatasets().stream()
                .filter(ds -> ds.getType() == DataType.RAW)
                .filter(ds -> ds.getFiles().stream().anyMatch(df -> SUPPORTED_FORMATS.contains(df.getFormat())))
                .findFirst();
    }
}
