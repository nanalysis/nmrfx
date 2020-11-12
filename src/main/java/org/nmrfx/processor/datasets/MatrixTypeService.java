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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmrfx.processor.math.MatrixType;
import org.nmrfx.processor.processing.Processor;

/**
 *
 * @author brucejohnson
 */
public class MatrixTypeService {

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
            ex.printStackTrace();
        }
    }

    public boolean finished() {
        if ((nRead.get() >= itemsToRead) && !unprocessedItemQueue.isEmpty()) {
//            System.out.println("still " + unprocessedItemQueue.size());
            return false;
        } else {
            return (nRead.get() >= itemsToRead) && unprocessedItemQueue.isEmpty();
        }
    }

    public boolean isDone(int timeOut) {
        try {
            return futureTask.get(timeOut, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            return false;
        } catch (ExecutionException ex) {
            ex.printStackTrace();
            return false;
        } catch (TimeoutException ex) {
            System.out.println("time out " + nWritten.get() + " " + itemsToWrite + " " + nRead.get() + " " + itemsToRead);
            ex.printStackTrace();
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
//            System.out.println("n read " + nRead.get() + " of " + itemsToRead);
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
            ex.printStackTrace();
            return new ArrayList<>();
        }
    }

    private void writeItems(List<MatrixType> temp) {
        for (MatrixType vector : temp) {
            try {
                //vector.printLocation();
                processor.getDataset().writeMatrixType(vector);
                nWritten.incrementAndGet();
//                System.out.println("n written " + nWritten.get() + " of " + itemsToWrite);
            } catch (IOException ex) {
                ex.printStackTrace();
                Logger.getLogger(Processor.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    /**
     * Writes all of the items from the processedItemQueue to file.
     */
    public final boolean readWriteItems() {
        List<MatrixType> temp = null;
        while (true) {
            try {
//                System.out.println(unprocessedItemQueue.size() + " " + processedItemQueue.size() + " " + nWritten.get() + " " +itemsToWrite + " " + this);
                if (nRead.get() < itemsToRead) {
                    if ((unprocessedItemQueue.size() < processedQueueLimit.get()) && (processedItemQueue.size() < processedQueueLimit.get())) {
                        for (int i = 0; i < 4; i++) {
                            if (!addNewItems()) {
                                break;
                            }
                        }
                    }
                } else {
//                    System.out.println("finished reading");
                }

                temp = processedItemQueue.poll(100, TimeUnit.MILLISECONDS);
                if (temp != null) {
                    writeItems(temp);
                } else {
                    if (nWritten.get() >= itemsToWrite) {
                        System.out.println("finished writing");
                        return true;
                    }
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(MatrixTypeService.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }
        }
    }
}
