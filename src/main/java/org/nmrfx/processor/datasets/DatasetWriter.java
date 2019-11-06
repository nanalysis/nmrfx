/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.Processor;

/**
 *
 * @author brucejohnson
 */
public class DatasetWriter {

    AtomicBoolean endOfFile = new AtomicBoolean(false);
    AtomicInteger nWritten = new AtomicInteger(0);

    FutureTask<Boolean> futureTask;

    ExecutorService executor = Executors.newSingleThreadExecutor((Runnable r) -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    Processor processor;

    public DatasetWriter(Processor processor) {
        this.processor = processor;
        unprocessedVectorQueue = new LinkedBlockingQueue<>();
        processedVectorQueue = new LinkedBlockingQueue<>();
        futureTask = new FutureTask(() -> readWriteVectors());
        executor.execute(futureTask);

    }

    /* Each LinkedList<Vec> will hold one set of arraylists for a process. The
     * outer List is synchronized but the inner List is not synchronized.
     */
    private LinkedBlockingQueue<List<Vec>> unprocessedVectorQueue;
    /**
     * Each LinkedList<Vec> will be written to a file.
     */
    private LinkedBlockingQueue<List<Vec>> processedVectorQueue;

    public void shutdown() {
        executor.shutdown();
    }

    public boolean finished() {
        return endOfFile.get() && unprocessedVectorQueue.isEmpty();
    }

    public boolean isDone(int timeOut) {
        try {
            return futureTask.get(timeOut, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            return false;
        } catch (ExecutionException ex) {
            return false;
        } catch (TimeoutException ex) {
            return false;
        }
    }

    /* Adds vectors to the unprocessed vector queue.
     */
    public boolean addNewVectors() {
        if (!processor.getEndOfFile()) {
            List<Vec> vectors = processor.getVectorsFromFile();
            unprocessedVectorQueue.add(vectors);
            if (processor.getEndOfFile()) {
                endOfFile.set(true);
            }
            return true;
        } else {
            endOfFile.set(true);
        }
        return false;
    }

    public void addVectorsToWriteList(List<Vec> vectors) {
        processedVectorQueue.add(vectors);
    }

    public List<Vec> getVectorsFromUnprocessedList(int timeOut) {
        List<Vec> vecs;
        try {
            if (finished()) {
                vecs = null;
            } else {
                vecs = unprocessedVectorQueue.poll(timeOut, TimeUnit.MILLISECONDS);
            }
            return vecs;
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            return new ArrayList<>();
        }
    }

    private void writeVectors(List<Vec> temp) {
        for (Vec vector : temp) {
            try {
                //vector.printLocation();
                processor.getDataset().writeVector(vector);
                nWritten.incrementAndGet();
            } catch (IOException ex) {
                ex.printStackTrace();
                Logger.getLogger(Processor.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Writes all of the vectors from the processedVectorQueue to file.
     */
    public final boolean readWriteVectors() {
        List<Vec> temp = null;
        while (true) {
            try {
                if (!processor.getEndOfFile() && (unprocessedVectorQueue.size() < 4) && (processedVectorQueue.size() < 128)) {
                    for (int i = 0; i < 4; i++) {
                        if (!addNewVectors()) {
                            break;
                        }
                    }
                }

                temp = processedVectorQueue.poll(100, TimeUnit.MILLISECONDS);
                if (temp != null) {
                    writeVectors(temp);
                } else {
                    if (processor.doneWriting()) {
                        return true;
                    }
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(DatasetWriter.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }
        }
    }

}
