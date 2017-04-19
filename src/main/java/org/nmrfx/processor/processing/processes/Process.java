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
package org.nmrfx.processor.processing.processes;

//import org.nmrfx.processor.math.Matrix;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.math.MatrixType;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.math.VecException;
import org.nmrfx.processor.operations.DatasetOperation;
import org.nmrfx.processor.operations.MatrixOperation;
import org.nmrfx.processor.operations.Operation;
import org.nmrfx.processor.operations.OperationException;
import org.nmrfx.processor.operations.WriteMatrix;
import org.nmrfx.processor.operations.WriteVector;
import org.nmrfx.processor.processing.ProcessingException;
import org.nmrfx.processor.processing.Processor;
import java.util.ArrayList;
import java.util.concurrent.Callable;

/**
 * The Process class will contain a list of all Operations which will be processed. Each process represents a group of
 * Operations which need to be performed sequentially on one or more Vectors. Processors will be contained in another
 * class which will execute multiple Processes at once.
 *
 * @author johnsonb
 */
public class Process implements Callable<Object> {

    private ArrayList<Operation> operations = null;
    private ArrayList<Vec> vectors = null;
    private boolean hasStarted = false;
    private boolean hasFinished = false;
    private String name;
    private static int numProcessesCreated = 0;
    private int vectorsProcessed;
    private int[] dims = {0};
    private boolean isMatrix = false;
    private boolean isDataset = false;
    private boolean isUndo = false;
//    private Matrix matrix = null;

    private String completionMessage;

    //private HashMap<String, Vec> vectorMatMap = null; = new HashMap<String, Vec>();
    public synchronized boolean getHasFinished() {
        return hasFinished;
    }

    public synchronized void setHasFinished() {
        hasFinished = true;
    }

    public synchronized boolean getHasStarted() {
        return hasStarted;
    }

    public synchronized void setHasStarted() {
        hasStarted = true;
    }

    public Process() {
        this("p" + (numProcessesCreated));
        completionMessage = "Process " + name + " has not completed";
    }

    /**
     * Create Processor.
     */
    public Process(String name) {
        numProcessesCreated++;
        this.name = name;
        operations = new ArrayList<Operation>();
        vectors = new ArrayList<Vec>();
    }

    public Process(int d) {
        this("p" + (numProcessesCreated) + "d" + (d + 1));
        completionMessage = "Process " + name + " has not completed";
        this.dims = new int[1];
        this.dims[0] = d;
    }

    public Process(int d, boolean undo) {
        this(d);
        this.isUndo = undo;
    }

    public Process(int... newDims) {
        this("");
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append("p");
        sBuilder.append(numProcessesCreated);
        sBuilder.append("d");

        completionMessage = "Process " + name + " has not completed";
        this.dims = new int[newDims.length];
        for (int i = 0; i < newDims.length; i++) {
            this.dims[i] = newDims[i];
            sBuilder.append(newDims[i] + 1);
        }
        name = sBuilder.toString();
        isMatrix = newDims.length > 1;
    }

    public static Process createDatasetProcess() {
        Process process = new Process("dataset");
        process.setDataset();
        return process;
    }

    public String getName() {
        return name;
    }

    public int getDim() {
        return dims[0];
    }

    public int[] getDims() {
        return dims.clone();
    }

    public void setMatrix() {
        isMatrix = true;
    }

    public boolean isMatrix() {
        return isMatrix;
    }

    public void setDataset() {
        isDataset = true;
    }

    public boolean isDataset() {
        return isDataset;
    }

    public boolean isUndo() {
        return isUndo;
    }

    /**
     * Add operation to the Process if the Processor has not raised an error.
     *
     * @param op An Operation to add to the pool.
     */
    public void addOperation(Operation op) throws IllegalStateException {
        if (Processor.getProcessor().getProcessorError()) {
            throw new IllegalStateException("Can't add operation to processor with error, clear error state first");
        }
        operations.add(op);
    }

    public void addOp(Operation op) {
        operations.add(op);
    }

    public void add(Operation op) {
        operations.add(op);
    }

    public void clearOps() {
        operations.clear();
    }

    public void addVec(Vec vector) {
        vectors.add(vector);
    }

    public void addVecList(ArrayList<Vec> vectors) {
        vectors.addAll(vectors);
    }

    /**
     * Execute all of the operations in the pool.
     */
    @Override
    public Object call() {
        if (getHasStarted()) {
            return false;
        }
        setHasStarted();

        if (isDataset) {
            return callDataset();
        } else if (isMatrix) {
            return callMatrix();
        }

        Processor processor = Processor.getProcessor();

        vectors = new ArrayList<>();
        boolean error = false;
        // fixme  should we have don't write flag so write op doesn't get added
        if (!hasOperation(WriteVector.class)) {
            if (isUndo) {
                operations.add(new WriteVector(false));
            } else {
                operations.add(new WriteVector(true));
            }
        }
        while (!error) {
            if (processor.getProcessorError()) {
                return this;
            }
            try {
                vectors = processor.getVectorsFromFile();
            } catch (Exception e) {
                if (!processor.setProcessorError()) {
                    processor.setProcessorErrorMessage(e.getMessage());
                    e.printStackTrace();
                    throw new ProcessingException(e.getMessage());
                } else {
                    return this;
                }
            }
            if (vectors.isEmpty()) {
                break;
            }

            for (Operation op : operations) {
                if (processor.getProcessorError()) {
                    error = true;
                    return this;
                }
                try {
                    op.eval(vectors);
                } catch (OperationException oe) {
                    if (!processor.setProcessorError()) {
                        processor.setProcessorErrorMessage(oe.getMessage());
                        oe.printStackTrace();
                        throw new ProcessingException(oe.getMessage());
                    } else {
                        return this;
                    }
                } catch (Exception e) {
                    if (!processor.setProcessorError()) {
                        processor.setProcessorErrorMessage(e.getMessage());
                        e.printStackTrace();
                        throw new ProcessingException(e.getMessage());
                    } else {
                        return this;
                    }
                }
            }

            if (error) {
                break;
            }

            vectorsProcessed += vectors.size();
            vectors.clear();

            if (processor.getEndOfFile()) {
                break;
            }
        }

        completionMessage = "Process " + name + " has processed " + vectorsProcessed + " vectors.";

        setHasFinished();
        return vectors;
    }

    /**
     * Execute all of the matrix operations in the pool.
     */
    public Object callMatrix() {
        Processor processor = Processor.getProcessor();

        boolean error = false;
        MatrixType matrix = null;
        // fixme  should we have don't write flag so write op doesn't get added
        operations.add(new WriteMatrix());

        while (!error) {
            if (processor.getProcessorError()) {
                return this;
            }
            try {
                matrix = processor.getMatrixFromFile();
            } catch (Exception e) {
                if (!processor.setProcessorError()) {
                    processor.setProcessorErrorMessage(e.getMessage());
                    e.printStackTrace();
                    throw new ProcessingException(e.getMessage());
                } else {
                    return this;
                }
            }
            if (matrix == null) {
                break;
            }

            for (Operation op : operations) {
                if (processor.getProcessorError()) {
                    error = true;
                    return this;
                }
                try {
                    if (matrix != null) {
                        ((MatrixOperation) op).evalMatrix(matrix);
                    }
                } catch (OperationException oe) {
                    if (!processor.setProcessorError()) {
                        processor.setProcessorErrorMessage(oe.getMessage());
                        oe.printStackTrace();
                        throw new ProcessingException(oe.getMessage());
                    } else {
                        return this;
                    }
                } catch (Exception e) {
                    if (!processor.setProcessorError()) {
                        processor.setProcessorErrorMessage(e.getMessage());
                        e.printStackTrace();
                        throw new ProcessingException(e.getMessage());
                    } else {
                        return this;
                    }
                }
            }

            if (error) {
                break;
            }

            if (matrix != null) {
                vectorsProcessed++;
            }

            if (processor.getEndOfFile()) {
                break;
            }
        }

        completionMessage = "Process " + name + " has processed " + vectorsProcessed + " matrices.";

        setHasFinished();
        return matrix;
    }

    /**
     * Execute all of the dataset operations in the pool.
     */
    public Object callDataset() {
        Processor processor = Processor.getProcessor();

        boolean error = false;
        Dataset dataset = null;
        if (processor.getProcessorError()) {
            return this;
        }
        try {
            dataset = processor.getDataset();
        } catch (Exception e) {
            if (!processor.setProcessorError()) {
                processor.setProcessorErrorMessage(e.getMessage());
                e.printStackTrace();
                throw new ProcessingException(e.getMessage());
            } else {
                return this;
            }
        }
        if (dataset == null) {
            throw new ProcessingException("No dataset");
        }

        for (Operation op : operations) {
            if (processor.getProcessorError()) {
                error = true;
                return this;
            }
            try {
                if (dataset != null) {
                    ((DatasetOperation) op).evalDataset(dataset);
                }
            } catch (OperationException oe) {
                if (!processor.setProcessorError()) {
                    processor.setProcessorErrorMessage(oe.getMessage());
                    oe.printStackTrace();
                    throw new ProcessingException(oe.getMessage());
                } else {
                    return this;
                }
            } catch (Exception e) {
                if (!processor.setProcessorError()) {
                    processor.setProcessorErrorMessage(e.getMessage());
                    e.printStackTrace();
                    throw new ProcessingException(e.getMessage());
                } else {
                    return this;
                }
            }
        }

        if (dataset != null) {
            vectorsProcessed++;
        }

        completionMessage = "Process " + name + " has processed " + vectorsProcessed + " datasets.";

        setHasFinished();
        return dataset;
    }

    /**
     * Execute all of the operations in the Process.
     *
     * @return
     * @throws org.nmrfx.processor.processing.processes.IncompleteProcessException
     */
    public Object exec() throws IncompleteProcessException {
        if (vectors.isEmpty()) {
            return this;
        }
        for (Operation op : operations) {
            try {
                op.eval(vectors);
            } catch (ProcessingException pe) {
//                pe.printStackTrace();
                throw new IncompleteProcessException(pe.getMessage(), op.getName(), operations.indexOf(op), pe.getStackTrace());
            } catch (OperationException oe) {
//                oe.printStackTrace();
                throw new IncompleteProcessException(oe.getMessage(), op.getName(), operations.indexOf(op), oe.getStackTrace());
            } catch (VecException ve) {
//                ve.printStackTrace();
                throw new IncompleteProcessException(ve.getMessage(), op.getName(), operations.indexOf(op), ve.getStackTrace());
            } catch (Exception e) {
//                e.printStackTrace();
                throw new IncompleteProcessException(e.getMessage(), op.getName(), operations.indexOf(op), e.getStackTrace());
            }
        }
        vectors.clear();
        return vectors;
    }

    /**
     *
     * @return
     */
    public ArrayList<Operation> getOperations() {
        return operations;
    }

    public boolean hasOperations() {
        return (operations.size() > 0);
    }

    public boolean hasOperation(Class classType) {
        boolean hasOp = false;
        for (Operation op : operations) {
            if (op.getClass().isAssignableFrom(classType)) {
                hasOp = true;
                break;
            }
        }
        return hasOp;
    }

    public String getCompletionMessage() {
        return completionMessage;
    }

    public Process cloneProcess(Process proc) {
        if (isMatrix) {
            proc.setMatrix();
        }
        if (isDataset) {
            proc.setDataset();
        }

        proc.isUndo = isUndo;

        proc.operations = new ArrayList<>();

        proc.dims = dims.clone();

        for (Operation op : operations) {
            proc.addOperation(op.clone());
        }

        return proc;
    }

    public String getOperationString() {
        String opString = "";
        for (Operation op : operations) {
            opString += " " + op.getName();
        }
        if (opString.equals("")) {
            opString += " no operations in process";
        }
        return opString.substring(1, opString.length());
    }

    public synchronized String getStatus() {
        String temp = "has ";
        if (!getHasStarted()) {
            temp += "not ";
        }
        temp += "started, has ";
        if (!getHasFinished()) {
            temp += "not ";
        }
        temp += "finished";
        return temp;
    }

    public int getVectorsSize() {
        return vectors.size();
    }

    public ArrayList<Vec> getVectors() {
        return vectors;
    }

    public void clearVectors() {
        vectors = new ArrayList<>();
    }

    public static void resetNumProcessesCreated() {
        numProcessesCreated = 0;
    }
}
