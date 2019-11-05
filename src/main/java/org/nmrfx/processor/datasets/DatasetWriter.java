/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.Processor;

/**
 *
 * @author brucejohnson
 */
public class DatasetWriter {

    Executor executor = Executors.newSingleThreadExecutor((Runnable r) -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });
    
    Processor processor;

    public DatasetWriter(Processor processor) {
        this.processor = processor;
        unprocessedVectorQueue = new LinkedBlockingQueue<>();
        processedVectorQueue = new LinkedBlockingQueue<>();
        executor.execute(() -> writeVectors());
    }

    /* Each LinkedList<Vec> will hold one set of arraylists for a process. The
     * outer List is synchronized but the inner List is not synchronized.
     */
    private LinkedBlockingQueue<List<Vec>> unprocessedVectorQueue;
    /**
     * Each LinkedList<Vec> will be written to a file.
     */
    private LinkedBlockingQueue<List<Vec>> processedVectorQueue;

    /* Adds vectors to the unprocessed vector queue.
     */
//    public synchronized boolean addNewVectors() {
//        ArrayList<Vec> vectors = processor.getVectorsFromFile();
//        if (!processor.endOfFile) {
//            unprocessedVectorQueue.add(vectors);
//            return true;
//        }
//        return false;
//    }
    public void addVectorsToWriteList(List<Vec> vectors) {
        processedVectorQueue.add(vectors);
    }

    public List<Vec> getVectorsFromWriteList() {
        try {
            return processedVectorQueue.take();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public void addVectorsToUnprocessedList(List<Vec> vectors) {
        unprocessedVectorQueue.add(vectors);
    }

    public List<Vec> getVectorsFromUnprocessedList() {
        try {
            return unprocessedVectorQueue.take();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Writes all of the vectors from the processedVectorQueue to file.
     */
    public final void writeVectors() {
        List<Vec> temp = null;
        System.out.println("write vectors");
        while (true) {
            try {
                temp = processedVectorQueue.take();
            } catch (InterruptedException ex) {
                Logger.getLogger(DatasetWriter.class.getName()).log(Level.SEVERE, null, ex);
            }
            if (temp != null) {
                System.out.println("temp " + temp.size());
                for (Vec vector : temp) {
                    try {
                        System.out.println("write vector");

                        processor.getDataset().writeVector(vector);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        Logger.getLogger(Processor.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                if (processedVectorQueue.isEmpty()) {
                    System.out.println("empty");
                }
            }
        }
    }

}
