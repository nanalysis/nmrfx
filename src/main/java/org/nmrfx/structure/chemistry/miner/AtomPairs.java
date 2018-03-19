package org.nmrfx.structure.chemistry.miner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class AtomPairs {

    static Map globalPairMap = new HashMap();
    AtomContainer ac = null;
    Map pairMap = new TreeMap();

    public AtomPairs(AtomContainer ac) {
        this.ac = ac;
        execute();
    }

    private void execute() {
        int nAtoms = ac.getAtomCount();
        AtomTypes aTypes = new AtomTypes(ac);
        BreadthFirstIterator bI = new BreadthFirstIterator(ac);

        for (int i = 0; i < nAtoms; i++) {
            IAtom atom = ac.getAtom(i);

            if (atom.getAtomicNumber() == 1) {
                continue;
            }

            String iDesc = aTypes.get(i);
            bI.initialize(i);

            int nBonds = 0;

            while (bI.hasNext()) {
                ArrayList sphereOfAtoms = (ArrayList) bI.next();
                nBonds++;

                for (int j = 0, n = sphereOfAtoms.size(); j < n; j++) {
                    Integer sphAtom = (Integer) sphereOfAtoms.get(j);

                    if (sphAtom != null) {
                        int jAtom = sphAtom;
                        IAtom atom2 = ac.getAtom(jAtom);

                        if (atom2.getAtomicNumber() == 1) {
                            continue;
                        }

                        String jDesc = aTypes.get(jAtom);

                        if (i < jAtom) {
                            if (iDesc.compareTo(jDesc) < 0) {
                                add(iDesc + "-" + nBonds + "-" + jDesc);
                            } else {
                                add(jDesc + "-" + nBonds + "-" + iDesc);
                            }
                        }
                    }
                }
            }
        }

        //System.out.println(pairMap.size()+" "+globalPairMap.size());
    }

    void add(String aPair) {
        Integer value = (Integer) globalPairMap.get(aPair);
        int index = globalPairMap.size();

        if (value != null) {
            index = value;
        } else {
            globalPairMap.put(aPair, index);
        }

        Integer key = index;
        value = (Integer) pairMap.get(key);

        int iValue = 0;

        if (value != null) {
            iValue = value;
        }

        iValue++;
        pairMap.put(key, iValue);
    }
}
