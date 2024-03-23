package org.nmrfx.analyst.netmatch;

import java.util.ArrayList;
import org.apache.commons.math3.special.Erf;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.MoleculeBase;

/**
 * Used to store a predicted shift and a set of assigned values and tolerances for a particular atom
 */
class AtomShifts {

    final Atom atom;
    double averageShift;
    boolean useList = false;
    DescriptiveStatistics stats = new DescriptiveStatistics();
    ArrayList<Double> errorLims = new ArrayList<>();
    final double SQRT2 = Math.sqrt(2.0);
    // fixme should this be 1.0, or what
    final double ERRMUL = 3.0;
    ArrayList<PeakSetAtom> peakSetAtoms = new ArrayList<>();

    AtomShifts(final String atomName) {
        this.atom = MoleculeBase.getAtomByName(atomName);
    }
    AtomShifts(final Atom atom) {
        this.atom = atom;
    }

    public AtomShifts copy() {
        return new AtomShifts(atom);
    }

    /**
     * Get the chemical shift associated with this object. Use the prediction if no data values added. Otherwise use the
     * average of the associated data values.
     *
     */
    Double getPredictedShift() {
        Double ppm = atom.getRefPPM();
        if (ppm == null) {
            System.out.println("error " + atom.getFullName());
        }
        return ppm;
    }

    /**
     * Get the chemical shift associated with this object. Use the prediction if no data values added. Otherwise use the
     * average of the associated data values.
     *
     */
    Double getPPM() {
        if (!useList) {
            return getPredictedShift();
        } else {
            return averageShift;
        }
    }

    double getSigma() {
        double scale = this.atom.getName().equalsIgnoreCase("H") ? 5.0 : 1.0;

        return atom.getSDevRefPPM() * scale;
    }

    /**
     * Calculate a quality factor given a value
     *
     * @param x The value to test
     * @return The quality factor.
     */
    double getQ(final double x) {
        double q = Math.log(1.0 - Erf.erf(Math.abs(x) / SQRT2));
                if (Double.isInfinite(q)) {
                    System.out.println("YYY " + atom.getFullName() + " " + x + " " + Erf.erf(Math.abs(x)));
                }
        return q;
    }

    /**
     * Calculate a quality factor based on how close the shift is to the predicted value
     *
     * @param ppm the chemical shift to be tested.
     */
    double getQPred(final double ppm) {
        double x = (ppm - getPredictedShift()) / getSigma();
        double q = getQ(x);
        //double x0 = 1.5;
        double x0 = 2.0;
        double q0 = getQ(x0);
        return 1.0 + (q / Math.abs(q0));
    }

    /**
     * Calculate a quality factor based on how similar a shift is to a set of shifts
     *
     * @param ppm the chemical shift to be tested.
     * @return the quality factor.
     */
    double getQMulti(final double ppm, final double errorLim) {
        double x = (ppm - averageShift) / errorLim;
        double q = getQ(x);
        double x0 = 2.0;
        double q0 = getQ(x0);
        double QMulti = 1.0 + (q / Math.abs(q0));
        if (Double.isInfinite(QMulti)) {
            System.out.println("XXX q " + q + " q0 " + q0 + " ppm " + ppm + " x " + x + " avg " + averageShift + " err " + errorLim);
        }

        return QMulti;
    }

    /**
     * Calculate a quality factor based on the similarity of a set of shifts
     *
     * @return the quality factor.
     */
    double getQMultiSum() {
        double sum = 0.0;
        long n = stats.getN();
        for (int i = 0; i < n; i++) {
            sum += getQMulti(stats.getElement(i), errorLims.get(i) * ERRMUL);
        }
        return sum;
    }

    /**
     * Add a chemical shift to a set of shifts for this AtomShifts
     *
     * @param newValue The shift to be added
     * @param errorLim The tolerance value to be used for this shift
     *
     * @return the quality factor.
     */
    void addPPM(final PeakSetAtom peakSetAtom, final double newValue, final double errorLim) {
        long nValues = stats.getN();
        // fixme 10000 is test value, should we add back peaks, in expectation that they will
        // be penalized by QMulti later?
        if (newValue < -900.0) {
            return;
        }
        if ((nValues == 0) || (Math.abs(newValue - averageShift) < errorLim * 10000.0)) {
            stats.addValue(newValue);
            errorLims.add(errorLim);
            averageShift = stats.getMean();
            useList = true;
            peakSetAtoms.add(peakSetAtom);
        }
    }

    /**
     * Clear the list of added shifts and the associated statistics object
     */
    void clearPPM() {
        useList = false;
        stats.clear();
        errorLims.clear();
        peakSetAtoms.clear();
    }

    /**
     * Get the name of the atom
     *
     * @return the name of the atom/
     */
    String getAtomName() {
        return atom.getShortName();
    }

    Atom getAtom() {
        return atom;
    }

    /*
     * Calculate, with a gaussian function, a probability given a value and error.
     *
     * @param value  The value to be tested
     * @param errorValue The error associated with the value
     *
     * @return The probability
     */
    double getProb(final double value, final double errorValue) {
        double deltaScaled;
        // fixme should we not use predictionError once we have an assignment from peak?
        //   that is should predictionError and errorValue below be swapped? (swapped in this version)
        if (useList) {
            deltaScaled = (value - getPPM()) / errorValue;
        } else {
            deltaScaled = (value - getPPM()) / getSigma();
        }
        // fixme using prediction error /  should it be errorValue for !useList
        double prob = (1.0 / (Math.sqrt(2.0 * Math.PI * getSigma()))) * Math.exp(-1.0 * deltaScaled * deltaScaled / 2.0);
        double prob2 = 1.0 - Erf.erf(Math.abs(deltaScaled) / SQRT2);
        //System.out.println(value + " " + getPPM() + " " + predictionError + " " + deltaScaled + " " + prob2 + " " + prob);
        return prob2;
    }
}
