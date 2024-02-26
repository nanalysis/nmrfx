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

import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.datasets.MatrixType;
import org.nmrfx.processor.datasets.AcquisitionType;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.operations.*;
import org.nmrfx.processor.processing.ProcessingException;
import org.nmrfx.processor.processing.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * The ProcessOps class will contain a list of all Operations which will be
 * processed. Each process represents a group of Operations which need to be
 * performed sequentially on one or more Vectors. Processors will be contained
 * in another class which will execute multiple Processes at once.
 *
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class ProcessOps implements Callable<Object> {

    private static final Logger log = LoggerFactory.getLogger(ProcessOps.class);

    private ArrayList<Operation> operations = null;
    private List<Vec> vectors = null;
    private boolean hasStarted = false;
    private boolean hasFinished = false;
    private String name;
    private static int numProcessesCreated = 0;
    private int vectorsProcessed;
    private int[] dims = {0};
    private boolean isMatrix = false;
    private boolean isDataset = false;
    private boolean isUndo = false;
    private boolean firstProcess = true;

    private String completionMessage;

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

    public ProcessOps() {
        this("p" + (numProcessesCreated));
        completionMessage = "Process " + name + " has not completed";
    }

    /**
     * Create Processor.
     */
    public ProcessOps(String name) {
        numProcessesCreated++;
        this.name = name;
        operations = new ArrayList<Operation>();
        vectors = new ArrayList<Vec>();
    }

    public ProcessOps(int d) {
        this("p" + (numProcessesCreated) + "d" + (d + 1));
        completionMessage = "Process " + name + " has not completed";
        this.dims = new int[1];
        this.dims[0] = d;
    }

    public ProcessOps(int d, boolean undo) {
        this(d);
        this.isUndo = undo;
    }

    public ProcessOps(int... newDims) {
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

    public static ProcessOps createDatasetProcess() {
        ProcessOps process = new ProcessOps("dataset");
        process.setDataset();
        return process;
    }

    public String getName() {
        return name;
    }

    public void setDim(int dim) {
        dims[0] = dim;
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

    public void firstProcess(boolean state) {
        firstProcess = state;
    }

    /**
     * Add operation to the ProcessOps if the Processor has not raised an error.
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

    public void addVecList(ArrayList<Vec> addVectors) {
        vectors.addAll(addVectors);
    }

    private void addTDCombine() {
        Processor processor = Processor.getProcessor();
        if (firstProcess && (dims[0] == 0)) {
            NMRData nmrData = processor.getNMRData();
            List<Operation> tdOps = getOperation(TDCombine.class);
            if (nmrData != null) {
                for (int i = 1; i < nmrData.getNDim(); i++) {
                    boolean hasOp = false;
                    for (Operation op : tdOps) {
                        if (op instanceof TDCombine tdCombine) {
                            if (tdCombine.getDim() == i) {
                                hasOp = true;
                            }
                        }
                    }
                    if (!hasOp) {

                        AcquisitionType acquisitionType = nmrData.getUserSymbolicCoefs(i);
                        if ((acquisitionType != null) && acquisitionType == AcquisitionType.HYPER) {
                            continue;
                        }
                        String symCoef = nmrData.getSymbolicCoefs(i);
                        if ((acquisitionType == null) && ((symCoef == null) || symCoef.equalsIgnoreCase("hyper"))) {
                            continue;
                        }
                        double[] coef = acquisitionType != null ? acquisitionType.getCoefficients() : nmrData.getCoefs(i);
                        if (coef != null) {
                            int nCoef = coef.length;
                            if (nCoef > 4) {
                                int numInVec = (int) (Math.log(nCoef / 2.0) / Math.log(2.0));
                                int numOutVec = numInVec;
                                TDCombine op = new TDCombine(i, numInVec, numOutVec, coef);
                                operations.add(0, op);
                            }
                        }
                    }
                }
            }
        }
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
        addTDCombine();
        // fixme  should we have don't write flag so write op doesn't get added
        if (!hasOperation(WriteVector.class)) {
            if (isUndo) {
                operations.add(new WriteVector(false));
            } else {
                operations.add(new WriteVector(true));
            }
        }
        while (true) {
            if (processor.getProcessorError()) {
                return this;
            }
            try {
                vectors = processor.getNextVectors();
            } catch (Exception e) {
                if (!processor.setProcessorError()) {
                    processor.setProcessorErrorMessage(e.getMessage());
                    log.warn(e.getMessage(), e);
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
                    return this;
                }
                try {
                    op.eval(vectors);
                } catch (Exception e) {
                    if (!processor.setProcessorError()) {
                        processor.setProcessorErrorMessage(e.getMessage());
                        log.warn(e.getMessage(), e);
                        throw new ProcessingException(e.getMessage());
                    } else {
                        return this;
                    }
                }
            }

            vectorsProcessed += vectors.size();
            vectors.clear();

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

        MatrixType matrix = null;
        // fixme  should we have don't write flag so write op doesn't get added
        operations.add(new WriteMatrix());

        while (true) {
            if (processor.getProcessorError()) {
                return this;
            }
            try {
                matrix = processor.getNextMatrix();
            } catch (Exception e) {
                if (!processor.setProcessorError()) {
                    processor.setProcessorErrorMessage(e.getMessage());
                    log.warn(e.getMessage(), e);
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
                    return this;
                }
                try {
                    ((MatrixOperation) op).evalMatrix(matrix);
                } catch (Exception e) {
                    if (!processor.setProcessorError()) {
                        processor.setProcessorErrorMessage(e.getMessage());
                        log.warn(e.getMessage(), e);
                        throw new ProcessingException(e.getMessage());
                    } else {
                        return this;
                    }
                }
            }

            vectorsProcessed++;

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
                log.warn(e.getMessage(), e);
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
                    log.warn(oe.getMessage(), oe);
                    throw new ProcessingException(oe.getMessage());
                } else {
                    return this;
                }
            } catch (Exception e) {
                if (!processor.setProcessorError()) {
                    processor.setProcessorErrorMessage(e.getMessage());
                    log.warn(e.getMessage(), e);
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
     * Execute all of the operations in the ProcessOps.
     *
     * @return
     * @throws org.nmrfx.processor.processing.processes.IncompleteProcessException
     */
    public Object exec() throws IncompleteProcessException {
        if (vectors.isEmpty()) {
            return this;
        }
        addTDCombine();
        for (Operation op : operations) {
            try {
                op.eval(vectors);
            } catch (Exception pe) {
                throw new IncompleteProcessException(pe.getMessage(), op.getName(), operations.indexOf(op), pe.getStackTrace());
            }
        }
        vectors.clear();
        return vectors;
    }

    /**
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

    public List<Operation> getOperation(Class classType) {
        List<Operation> operationsPresent = new ArrayList<>();
        for (Operation op : operations) {
            if (op.getClass().isAssignableFrom(classType)) {
                operationsPresent.add(op);
            }
        }
        return operationsPresent;
    }

    public String getCompletionMessage() {
        return completionMessage;
    }

    public ProcessOps cloneProcess(ProcessOps proc) {
        if (isMatrix) {
            proc.setMatrix();
        }
        if (isDataset) {
            proc.setDataset();
        }

        proc.isUndo = isUndo;

        proc.firstProcess = firstProcess;

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

    public List<Vec> getVectors() {
        return vectors;
    }

    public void clearVectors() {
        vectors = new ArrayList<>();
    }

    public static void resetNumProcessesCreated() {
        numProcessesCreated = 0;
    }

    boolean useInSimVec(Operation op) {
        boolean result = false;
        if (op instanceof Apodization) {
            result = true;
        } else if (op.getClass().isAssignableFrom(Zf.class)) {
            result = true;
        } else if (op.getClass().isAssignableFrom(Ft.class)) {
            result = true;
        }
        return result;
    }

    public void applyToSimVectors(List<Vec> simVectors) {

        if (!simVectors.isEmpty()) {
            for (Operation op : operations) {
                if (useInSimVec(op)) {
                    try {
                        if (op instanceof Ft) {
                            op = new Ft(false, false);
                        }
                        op.eval(simVectors);
                    } catch (OperationException oe) {
                        throw new ProcessingException(oe.getMessage());
                    } catch (Exception e) {
                        throw new ProcessingException(e.getMessage());
                    }
                }
            }

        }
    }
}
