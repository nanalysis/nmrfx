package org.nmrfx.processor.processing;

import java.util.ArrayList;
import java.util.List;

public class ProcessingOperationGroup implements ProcessingOperationInterface {
    String name;
    boolean disabled = false;
    List<ProcessingOperation> processingOperationList = new ArrayList<>();

    public ProcessingOperationGroup(String name) {
        this.name = name;
    }

    void addOps(List<String> opNames) {
        for (String opName: opNames) {
            add(new ProcessingOperation(opName + "(disabled=True)"));
        }
    }

    public List<ProcessingOperation> getProcessingOperationList() {
        return processingOperationList;
    }

    public void add(ProcessingOperation processingOperation) {
        processingOperationList.add(processingOperation);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void disabled(boolean state) {
        disabled = state;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public String getTitle(boolean state) {
        return name;
    }

    public void update(String opName, String line) {
        if (!line.endsWith(")")) {
            line = line + "()";
        }
        for (ProcessingOperation processingOperation : processingOperationList) {
            if (processingOperation.getName().equals(opName)) {
                processingOperation.update(line);
                break;
            }
        }
    }

    public void adjust(int sourceIndex, int targetIndex) {
        targetIndex = Math.min(targetIndex, processingOperationList.size() - 1);

        ProcessingOperation swap = processingOperationList.remove(sourceIndex);
        processingOperationList.add(targetIndex, swap);
    }
}
