package org.nmrfx.processor.processing;

public interface ProcessingOperationInterface {
    public String getName();
    public String toString();
    public void disabled(boolean state);
    public boolean isDisabled();
    public String getTitle(boolean state);
}
