package org.nmrfx.analyst.netmatch;

/**
 *
 * @author brucejohnson
 */
class PeakValue extends Value {

    PeakValue(int index, Double[] values, Double[] tvalues) {
        super(index, values, tvalues);
    }

    /**
     * Get the probability that the shifts of an AtomValue match the shifts of this PeakValue
     *
     * @param atomValue The AtomValue to be tested
     *
     * @return the probability.
     */
    double getProbability(AtomValue atomValue) {
        double cumProb = 1.0;
        if (atomValue.getEmpty()) {
            cumProb = 0.0;
        }
        StringBuilder sbuild = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            // fixme  should penalize missing peaks when no missing peak expected
            if (values[i] != null) {
                AtomShifts atomShifts = atomValue.getAtomShifts(i);
                if (atomShifts != null) {
                    double prob = atomShifts.getProb(values[i], tvalues[i]);
                    sbuild.append(String.format("%3d %5.3f %5.3f %10s %5.3f", i, values[i], atomShifts.getPPM(), atomShifts.getAtomName(), prob));
                    cumProb *= prob;
                } else {
                    // fixme  what to do about peaks where none should be
                    cumProb *= 0.5;
                }
            }
        }
        sbuild.append(String.format(" %10.6g", cumProb));
        if (cumProb < 1.0e-6) {
            cumProb = 0.0;
        }
        //System.out.println(sbuild.toString());

        return cumProb;
    }

    double overlap(PeakValue other, int[][] map) {
        int n = map[0].length;
        double sum = 0.0;
        boolean ok = true;
        for (int i = 0; i < n; i++) {
            double v1 = values[map[0][i]];
            double v2 = other.values[map[1][i]];
            double delta = 0.0;
            if ((v1 > -990.0) && (v2 > -990.0)) {
                double t = tvalues[map[0][i]];
                delta = Math.abs(v1 - v2) / t;
                if (delta > 1.0) {
                    ok = false;
                    break;
                }
            }
            sum += delta;
        }
        if (!ok) {
            sum = -1.0;
        }
        return sum;
    }

    @Override
    public String toString() {
        StringBuilder sBuild = new StringBuilder();
        for (double value : values) {
            sBuild.append(value);
            sBuild.append(' ');
        }
        return sBuild.toString();
    }
}
