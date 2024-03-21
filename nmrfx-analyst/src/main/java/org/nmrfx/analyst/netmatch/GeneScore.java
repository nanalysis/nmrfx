package org.nmrfx.analyst.netmatch;

/**
 *
 * @author brucejohnson
 */
class GeneScore implements Comparable<GeneScore> {

    int index;
    double averageScore = 0.0;
    double bestScore = Double.NEGATIVE_INFINITY;
    int nScores = 0;

    GeneScore(int index, double score) {
        this.index = index;
        averageScore = score;
        bestScore = score;
        nScores = 1;
    }

    void addScore(double score) {
        averageScore = (nScores * averageScore + score) / (nScores + 1);
        if (score > bestScore) {
            bestScore = score;
        }
        nScores++;
    }

    @Override
    public String toString() {
        return String.format("%d %d %.4f %.4f", index, nScores, averageScore, bestScore);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof GeneScore) {
            return index == ((GeneScore) o).index;
        } else {
            return false;
        }

    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + this.index;
        return hash;
    }

    @Override
    public int compareTo(GeneScore o) {
        int result = 0;
        if (o == null) {
            result = 1;
        } else {
            if (!this.equals(o)) {
                if (this.bestScore > o.bestScore) {
                    result = 1;
                } else if (this.bestScore < o.bestScore) {
                    result = -1;
                }
            }
        }
        return result;
    }

}
