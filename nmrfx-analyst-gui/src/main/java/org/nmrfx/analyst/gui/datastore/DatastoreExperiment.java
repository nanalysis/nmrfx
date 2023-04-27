package org.nmrfx.analyst.gui.datastore;

import com.nanalysis.datastore.api.enums.DataFormat;
import com.nanalysis.datastore.api.enums.DataType;
import com.nanalysis.datastore.api.model.DataFileDTO;
import com.nanalysis.datastore.api.model.DatasetDTO;
import com.nanalysis.datastore.api.model.ExperimentDTO;
import org.nmrfx.utilities.RemoteDataset;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

public class DatastoreExperiment extends RemoteDataset {
    private static final Set<DataFormat> SUPPORTED_FORMATS = Set.of(DataFormat.SPINLAB, DataFormat.JCAMP);

    private final ExperimentDTO experiment;
    private final long datasetId;

    public DatastoreExperiment(ExperimentDTO experiment) {
        this.experiment = experiment;
        datasetId = selectRawDataset(experiment).map(DatasetDTO::getId).orElse(-1L);

        setPath(experiment.getId() + "-" + experiment.getName());
        setUser(experiment.getOperatorName());
        setTime(experiment.getAcquisitionDate().toString());
        setSeq(experiment.getSequenceName());
        setSample(experiment.getSampleName());
        setNd(experiment.getNumberOfDimensions());
    }

    public long getExperimentId() {
        return experiment.getId();
    }

    public long getDatasetId() {
        return datasetId;
    }

    /**
     * Use the original file format if supported. Otherwise, favor SPINLAB over JCAMP.
     *
     * @return the best file format to open.
     */
    public DataFormat selectBestFormat() {
        DatasetDTO dataset = experiment.getDatasets().stream().filter(ds -> ds.getId() == datasetId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No downloadable dataset for this experiment"));
        return dataset.getFiles().stream().min(Comparator.comparing(this::score))
                .map(DataFileDTO::getFormat)
                .orElseThrow(() -> new IllegalStateException("No downloadable dataset for this experiment"));
    }

    private int score(DataFileDTO dataFile) {
        if (dataFile.isOriginal() && SUPPORTED_FORMATS.contains(dataFile.getFormat()))
            return 0; // best
        if (dataFile.getFormat() == DataFormat.SPINLAB)
            return 1; // second choice
        if (dataFile.getFormat() == DataFormat.JCAMP)
            return 2; // last supported format
        return Integer.MAX_VALUE;
    }

    private static Optional<DatasetDTO> selectRawDataset(ExperimentDTO experiment) {
        return experiment.getDatasets().stream()
                .filter(ds -> ds.getType() == DataType.RAW)
                .filter(ds -> ds.getFiles().stream().anyMatch(df -> SUPPORTED_FORMATS.contains(df.getFormat())))
                .findFirst();
    }
}
