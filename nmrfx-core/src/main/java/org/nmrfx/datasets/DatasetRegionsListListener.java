package org.nmrfx.datasets;

import java.util.List;

@FunctionalInterface
public interface DatasetRegionsListListener {

    void datasetRegionsListUpdated(List<DatasetRegion> newList);

}
