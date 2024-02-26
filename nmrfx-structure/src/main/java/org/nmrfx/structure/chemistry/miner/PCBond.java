package org.nmrfx.structure.chemistry.miner;

/**
 * @author brucejohnson
 */
public class PCBond {
    final int partner;
    final int bondTypeIndex;
    static final int SINGLE = 0;
    static final int DOUBLE = 1;
    static final int TRIPLE = 2;
    static final int AROMATIC = 3;
    static final int ONE_THREE = 4;

    PCBond(int partner, int bondTypeIndex) {
        this.partner = partner;
        this.bondTypeIndex = bondTypeIndex;
    }

    public int getPartner() {
        return partner;
    }

    public int getType() {
        return bondTypeIndex;
    }

}
