package org.nmrfx.structure.chemistry.miner;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AtomQuartets {

    AtomContainer ac = null;
    static Map map = new HashMap();
    static Iterator iter = null;

    public AtomQuartets(AtomContainer ac) {
        this.ac = ac;
        execute();
    }

    public static void clear() {
        map.clear();
    }

    public static void reset() {
        iter = map.keySet().iterator();
    }

    public static boolean hasNext() {
        if (iter == null) {
            iter = map.keySet().iterator();
        }
        return iter.hasNext();
    }

    public static String next() {
        String key = (String) iter.next();
        Integer value = (Integer) map.get(key);
        return (key + " " + value);
    }

    private void execute() {
        AtomTypes aTypes = new AtomTypes(ac);
        ac.bonds().stream().forEach(bond -> {
            IAtom bAtom0 = bond.getAtom(0);
            IAtom bAtom1 = bond.getAtom(0);
            String desc1 = aTypes.get(bAtom0);
            String desc2 = aTypes.get(bAtom1);

            List<IAtom> atoms0 = ac.getConnectedAtomsList(bAtom0);
            List<IAtom> atoms1 = ac.getConnectedAtomsList(bAtom1);
            atoms0.stream().forEach(atom0 -> {
                if ((atom0 != bAtom1)
                        && (atom0.getAtomicNumber() != 1)) {
                    String desc0 = aTypes.get(atom0);
                    atoms1.stream().forEach(atom1 -> {
                        if ((atom1 != bAtom0)
                                && (atom1.getAtomicNumber() != 1)) {
                            String desc3 = aTypes.get(atom1);
                            String descA = desc0 + "-" + desc1 + "-" + desc2
                                    + "-" + desc3;
                            String descB = desc3 + "-" + desc2 + "-" + desc1
                                    + "-" + desc0;

                            if (descA.compareTo(descB) < 0) {
                                add(descA);
                            } else {
                                add(descB);
                            }
                        }
                    });
                }
            });
        });

    }

    void add(String aQuartet
    ) {
        Integer value = (Integer) map.get(aQuartet);
        int iValue = 0;

        if (value != null) {
            iValue = value;
        }

        iValue++;
        map.put(aQuartet, iValue);
    }
}
