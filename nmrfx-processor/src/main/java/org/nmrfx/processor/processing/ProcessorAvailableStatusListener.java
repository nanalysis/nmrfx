package org.nmrfx.processor.processing;

@FunctionalInterface
public interface ProcessorAvailableStatusListener {

    void processorAvailableStatusUpdated(Boolean isAvailable);

}
