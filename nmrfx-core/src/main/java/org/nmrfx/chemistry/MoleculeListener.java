package org.nmrfx.chemistry;

import java.util.EventListener;
@FunctionalInterface
public interface MoleculeListener extends EventListener {
    void moleculeChanged(MoleculeEvent event);
}
