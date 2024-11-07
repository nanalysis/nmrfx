package org.nmrfx.analyst.netmatch;

import org.apache.commons.math3.genetics.AbstractListChromosome;
import org.apache.commons.math3.genetics.InvalidRepresentationException;

import java.util.List;

/**
 *
 * @author brucejohnson
 */
public class PeakMatchChromosome extends AbstractListChromosome<Integer> {

    PeakMatcher peakMatcher;

    public PeakMatchChromosome(List<Integer> representation, PeakMatcher peakMatcher) {
        super(representation);
        this.peakMatcher = peakMatcher;
    }

    @Override
    public AbstractListChromosome newFixedLengthChromosome(List list) {
        return new PeakMatchChromosome(list, peakMatcher);
    }

    public double fitness() {
        return peakMatcher.getValue(getRepresentation());
    }

    @Override
    protected void checkValidity(List<Integer> list) throws InvalidRepresentationException {
    }

    public List<Integer> getRep() {
        return getRepresentation();
    }
}
