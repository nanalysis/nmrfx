package org.nmrfx.analyst.netmatch;

/**
 * Used to store an item number (for a peak or atom) and the probability of that item matching
 */
class ItemMatch implements Comparable<ItemMatch> {

    final int itemNum;
    final double probability;
    double groupProbability;
    double localScore;

    ItemMatch(int itemNum, double probability) {
        this.itemNum = itemNum;
        this.probability = probability;
    }

    void setGroupProbability(double value) {
        groupProbability = value;
    }

    public double getGroupProbability() {
        return groupProbability;
    }
    
    void setLocalScore(double value) {
        localScore = value;
    }
    double getLocalScore() {
        return localScore;
    }

    @Override
    public int compareTo(ItemMatch o) {
        int result = 0;
        if (o == null) {
            result = 1;
        } else {
            if (!this.equals(o)) {
                if (this.probability > o.probability) {
                    result = 1;
                } else if (this.probability < o.probability) {
                    result = -1;
                }
            }
        }
        return result;
    }
    
    @Override
    public String toString() {
        return itemNum + " " + probability + " " + groupProbability;
    }
}
