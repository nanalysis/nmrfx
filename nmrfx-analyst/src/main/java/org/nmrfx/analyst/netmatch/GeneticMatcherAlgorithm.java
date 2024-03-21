package org.nmrfx.analyst.netmatch;

import org.apache.commons.math3.genetics.*;
import org.apache.commons.math3.exception.OutOfRangeException;

/**
 *
 * @author brucejohnson
 */
public class GeneticMatcherAlgorithm extends GeneticAlgorithm {

    public double mutationRate;

    public GeneticMatcherAlgorithm(final CrossoverPolicy crossoverPolicy,
            final double crossoverRate,
            final MutationPolicy mutationPolicy,
            final double mutationRate,
            final SelectionPolicy selectionPolicy) throws OutOfRangeException {
        super(crossoverPolicy, crossoverRate, mutationPolicy, mutationRate, selectionPolicy);
    }

    public void setMutationRate(double newRate) {
        mutationRate = newRate;
    }

}
