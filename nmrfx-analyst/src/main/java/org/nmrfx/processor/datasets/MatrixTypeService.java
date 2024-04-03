/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.processor.datasets;

import org.nmrfx.datasets.MatrixType;
import org.nmrfx.processor.math.MatrixND;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author brucejohnson
 */
public class MatrixTypeService {
    private static final Logger log = LoggerFactory.getLogger(MatrixTypeService.class);

    /* Each LinkedList<MatrixType> will hold one set of arraylists for a process. The
     * outer List is synchronized but the inner List is not synchronized.
     */
    private final LinkedBlockingQueue<List<MatrixType>> unprocessedItemQueue;
    /**
     * Each LinkedList<MatrixType> will be written to a file.
     */
    private final LinkedBlockingQueue<List<MatrixType>> processedItemQueue;
    AtomicInteger nWritten = new AtomicInteger(0);
    AtomicInteger nRead = new AtomicInteger(0);
    AtomicInteger processedQueueLimit;
    AtomicBoolean errorWhileReadWrite = new AtomicBoolean(false);
    int itemsToWrite;
    int itemsToRead;

    FutureTask<Boolean> futureTask;

    ExecutorService executor = Executors.newSingleThreadExecutor((Runnable r) -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    Processor processor;

    public MatrixTypeService(Processor processor, int processedQueueLimit, int itemsToRead, int itemsToWrite) {
        this.processor = processor;
        this.itemsToWrite = itemsToWrite;
        this.itemsToRead = itemsToRead;
        this.processedQueueLimit = new AtomicInteger(processedQueueLimit);
        unprocessedItemQueue = new LinkedBlockingQueue<>();
        processedItemQueue = new LinkedBlockingQueue<>();
        futureTask = new FutureTask(() -> readWriteItems());
        executor.execute(futureTask);
    }

    public void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(4, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            log.warn(ex.getMessage(), ex);
        }
    }

    public boolean hasError() {
        return errorWhileReadWrite.get();
    }

    public boolean finished() {
        if ((nRead.get() >= itemsToRead) && !unprocessedItemQueue.isEmpty()) {
            return false;
        } else {
            return (nRead.get() >= itemsToRead) && unprocessedItemQueue.isEmpty();
        }
    }

    public boolean isDone(int timeOut) {
        try {
            return futureTask.get(timeOut, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            log.warn(ex.getMessage(), ex);
            return false;
        } catch (ExecutionException ex) {
            log.warn(ex.getMessage(), ex);
            return false;
        } catch (TimeoutException ex) {
            log.warn("time out {} {} {} {}", nWritten.get(), itemsToWrite, nRead.get(), itemsToRead);
            log.warn(ex.getMessage(), ex);
            return false;
        }
    }

    /* Adds items to the unprocessed item queue.
     */
    public boolean addNewItems() {
        if (!processor.getEndOfFile()) {
            List<MatrixType> vectors = processor.getMatrixTypesFromFile();
            unprocessedItemQueue.add(vectors);
            if (vectors != null) {
                int nVec = vectors.size();
                if ((nVec == 1) && vectors.get(0) == null) {
                    nVec = 0;
                }
                nRead.addAndGet(nVec);
            }
            return true;
        }
        return false;
    }

    public void addItemsToWriteList(List<MatrixType> vectors) {
        processedItemQueue.add(vectors);
    }

    public List<MatrixType> getItemsFromUnprocessedList(int timeOut) {
        List<MatrixType> vecs;
        try {
            if (finished()) {
                vecs = null;
            } else {
                vecs = unprocessedItemQueue.poll(timeOut, TimeUnit.MILLISECONDS);
            }
            return vecs;
        } catch (InterruptedException ex) {
            log.warn(ex.getMessage(), ex);
            return new ArrayList<>();
        }
    }

    private boolean writeItems(List<MatrixType> temp) throws DatasetException, IOException {
        for (MatrixType vector : temp) {
            Dataset dataset = processor.getDataset();
            if (dataset == null) {
                throw new DatasetException("Dataset is null.");
            }
            checkDataset(dataset, vector);
            dataset.writeMatrixType(vector);
            nWritten.incrementAndGet();
        }
        return true;
    }

    private void checkDataset(Dataset dataset, MatrixType matrixType) throws DatasetException {
        if (matrixType instanceof Vec vec) {
            checkVector(dataset, vec);
        } else {
            MatrixND matrix = (MatrixND) matrixType;
            checkMatrix(dataset, matrix);
        }
    }

    private void checkVector(Dataset dataset, Vec vec) throws DatasetException {
        int[][] pt = vec.getPt();
        int[] dim = vec.getDim();
        int nDim = dataset.getNDim();
        if (!dataset.hasLayout()) {
            int[] idNVectors = processor.getIndirectSizes();
            int size = vec.getSize();
            dataset.resize(size, idNVectors);
        }
        for (int i = 0; i < nDim; i++) {
            if (pt[i][0] == pt[i][1]) {
                int testSize = pt[i][0] + 1;
                if (testSize > dataset.getFileDimSize(dim[i])) {
                    if (i > 0) {
                        int[] idNVectors = processor.getIndirectSizes();
                        testSize = testSize < idNVectors[i - 1] ? idNVectors[i - 1] : (int) Math.ceil(testSize / 16.0) * 16;
                        int doubleSize = dataset.getFileDimSize(dim[i]) * 2;
                        testSize = Math.max(doubleSize, testSize);
                    } else {
                        int doubleSize = dataset.getFileDimSize(dim[i]) * 2;
                        testSize = (int) Math.ceil(testSize / 16.0) * 16;
                        testSize = Math.max(doubleSize, testSize);
                    }
                    dataset.resizeDim(dim[i], testSize);
                }
            } else {
                if ((pt[i][1] + 1) > dataset.getFileDimSize(dim[i])) {
                    dataset.resizeDim(dim[i], pt[i][1] + 1);
                }
            }
        }
    }

    private void checkMatrix(Dataset dataset, MatrixND matrix) throws DatasetException {
        int[][] pt = matrix.getPt();
        int[] dim = matrix.getDim();
        int nDim = dataset.getNDim();
        boolean resize = false;
        int[] dimSizes = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            dimSizes[dim[i]] = dataset.getFileDimSize(dim[i]);
            if (pt[i][0] == pt[i][1]) {
                if ((pt[i][0] + 1) > dataset.getFileDimSize(dim[i])) {
                    dimSizes[dim[i]] = pt[i][1] + 1;
                    resize = true;
                }
            } else {
                if ((pt[i][1] + 1) > dataset.getFileDimSize(dim[i])) {
                    dimSizes[dim[i]] = pt[i][1] + 1;
                    resize = true;
                }
            }
        }
        if (resize) {
            dataset.resizeDims(dimSizes);
        }
    }

    /**
     * Writes all of the items from the processedItemQueue to file.
     */
    public final boolean readWriteItems() throws InterruptedException {
        List<MatrixType> temp = null;
        while (true) {
            try {
                if (nRead.get() < itemsToRead) {
                    if ((unprocessedItemQueue.size() < processedQueueLimit.get()) && (processedItemQueue.size() < processedQueueLimit.get())) {
                        for (int i = 0; i < 4; i++) {
                            if (!addNewItems()) {
                                break;
                            }
                        }
                    }
                }

                temp = processedItemQueue.poll(100, TimeUnit.MILLISECONDS);
                if (temp != null) {
                    writeItems(temp);
                } else {
                    if (nWritten.get() >= itemsToWrite) {
                        return true;
                    }
                }
            } catch (InterruptedException ex) {
                log.error(ex.getMessage(), ex);
                throw (ex);
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex);
                errorWhileReadWrite.set(true);
                return false;
            }
        }
    }
}
