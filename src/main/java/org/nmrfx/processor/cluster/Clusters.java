/*
 * NMRFx Processor : A Program for Processing NMR Data 
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

 /*
 * Clusters.java
 *
 * Created on December 15, 1999, 2:52 PM
 */
package org.nmrfx.processor.cluster;

import static java.util.Comparator.comparing;

import java.util.*;

/**
 *
 * @author JOHNBRUC
 * @version
 */
public class Clusters extends Object {

    public List<Datum> data = new ArrayList<>();

    /**
     * Creates new Clusters
     */
    public Clusters() {
    }

    public void addDatum(Datum datum) {
        data.add(datum);
    }

//    public void clusterList(String[] argv)
//            throws IllegalArgumentException {
//        Datum iDatum;
//
//        if (argv == null) {
//            throw new IllegalArgumentException("no tclObject in clusterList");
//        }
//        if (argv.length < 3) {
//            throw new IllegalArgumentException("?-targetsize targetSize? \"tol1 tol2 ...\" data");
//        }
//
//        double[] tol = null;
//        int targetClusters = Integer.MAX_VALUE;
//        int start = 1;
//        if (argv[1].toString().equals("-targetsize")) {
//            if (argv.length < 5) {
//                throw new IllegalArgumentException("?-targetsize targetSize? \"tol1 tol2 ...\" data");
//            }
//            targetClusters = Integer.parseInt(argv[2]);
//            start = 3;
//        }
//        for (int i = start; i < argv.length; i++) {
//            TclObject[] argElems = TclList.getElements(argv[i]);
//
//            if (i == start) {
//                tol = new double[argElems.length];
//
//                for (int j = 0; j < argElems.length; j++) {
//                    tol[j] = Double.parseDouble(argElems[j]);
//                }
//            } else {
//                if (argElems.length != tol.length) {
//                    throw new TclException(interp,
//                            "cluster element dimension not equal to number of tolerances");
//                }
//
//                Datum datum = new Datum(tol.length);
//
//                for (int j = 0; j < argElems.length; j++) {
//                    datum.v[j] = Double.parseDouble(argElems[j]);
//                }
//
//                datum.act = true;
//                datum.proto[0] = i - start - 1;
//                datum.idNum = i - start - 1;
//                data.add(datum);
//            }
//        }
//
//        doCluster(tol.length, tol, targetClusters);
//
//        int npeaks = data.size();
//        TclObject linksList = TclList.newInstance();
//        TclObject protoList = TclList.newInstance();
//        TclObject jointList = TclList.newInstance();
//
//        int nClusts = -1;
//
//        for (int i = 0; i < npeaks; i++) {
//            iDatum = (Datum) data.elementAt(i);
//
//            if (iDatum.act) {
//                nClusts++;
//            }
//
//            iDatum.proto[0] = nClusts;
//        }
//
//        for (int i = 0; i < npeaks; i++) {
//            iDatum = (Datum) data.elementAt(i);
//
//            if (iDatum.act) {
//                TclList.append(protoList,
//                        TclDouble.newInstance(iDatum.v[0]));
//            }
//        }
//
//        for (int i = 0; i < npeaks; i++) {
//            iDatum = (Datum) data.elementAt(i);
//            TclList.append(linksList,
//                    TclInteger.newInstance(iDatum.proto[0]));
//            TclList.append(linksList,
//                    TclInteger.newInstance(iDatum.idNum));
//        }
//
//        TclList.append(jointList, linksList);
//        TclList.append(jointList, protoList);
//        interp.setResult(jointList);
//    }
    public void doCluster(int iPfdim, double[] tol) {
        doCluster(iPfdim, tol, Integer.MAX_VALUE);
    }

    public void doCluster(int iPfdim, double[] tol, int targetClusters)
            throws IllegalArgumentException {
        int i;
        int j;
        int k;
        int ii;
        int imin;
        int jmin;
        boolean ok;
        boolean ok1;
        double dDeltaSum;
        double min;
        double delta;
        double delta1;
        double delta2;

        Datum iDatum = null;
        Datum jDatum = null;
        int npeaks = data.size();
        int nClusters = npeaks;

        if (npeaks < 2) {
            throw new IllegalArgumentException("Can't cluster less than 2 peaks");
        }

        data.sort(comparing(Datum::getV0));

        for (i = 0; i < npeaks; i++) {
            iDatum = (Datum) data.get(i);
            iDatum.last = i;
            iDatum.proto[0] = i;
        }

        imin = 0;

        do {
            ok = false;
            min = 1e6;
            imin = -1;
            jmin = -1;

            for (i = 0; i < (npeaks - 1); i++) {
                iDatum = (Datum) data.get(i);

                if (!iDatum.act) {
                    continue;
                }

                ok1 = true;
                k = i;

                do {
                    k++;

                    for (j = k; j < npeaks; j++) {
                        jDatum = (Datum) data.get(j);

                        if ((jDatum.group >= 0)
                                && (jDatum.group == iDatum.group)) {
                            continue;
                        }

                        if (jDatum.act) {
                            break;
                        }
                    }

                    if (j >= npeaks) {
                        break;
                    }

                    ok = true;
                    dDeltaSum = 0.0;

                    for (ii = 0; ii < iPfdim; ii++) {
                        delta1 = (iDatum.n * (iDatum.v[ii] * iDatum.v[ii]))
                                + (jDatum.n * (jDatum.v[ii] * jDatum.v[ii]));
                        delta2 = (iDatum.n * iDatum.v[ii])
                                + (jDatum.n * jDatum.v[ii]);
                        delta2 = (delta2 * delta2) / (iDatum.n + jDatum.n);

                        delta = delta1 - delta2;

                        if (delta < 0.0) {
                            delta = 0.0;
                        }

                        dDeltaSum += (delta / (tol[ii] * tol[ii]));

                        if ((ii == 0) && (dDeltaSum > 2.0)) {
                            ok1 = false;
                        }
                    }

                    if (dDeltaSum < min) {
                        min = dDeltaSum;
                        imin = i;
                        jmin = j;
                    }
                } while ((k < (npeaks - 1)) && ok1);
            }

            if (ok) {
                if ((nClusters > targetClusters) || (Math.sqrt(min) < Math.sqrt(1.0 * iPfdim))) {
                    ok = true;
                } else {
                    ok = false;
                }

                if (ok && (jmin >= 0) && (imin >= 0)) {
                    iDatum = (Datum) data.get(imin);
                    jDatum = (Datum) data.get(jmin);

                    for (ii = 0; ii < iPfdim; ii++) {
                        iDatum.v[ii] = ((iDatum.v[ii] * iDatum.n)
                                + (jDatum.v[ii] * jDatum.n)) / (iDatum.n + jDatum.n);
                    }

                    iDatum.n += jDatum.n;
                    jDatum.act = false;
                    jDatum.proto[0] = iDatum.proto[0];

                    if ((jDatum.group >= 0) && (iDatum.group < 0)) {
                        iDatum.group = jDatum.group;
                    }

                    jDatum.group = iDatum.group;

                    int next = jDatum.next;

                    while (next != 0) {
                        Datum nextDatum = (Datum) data.get(next);
                        nextDatum.proto[0] = iDatum.proto[0];
                        nextDatum.group = iDatum.group;
                        next = nextDatum.next;
                    }

                    Datum lastDatum = (Datum) data.get(iDatum.last);

                    lastDatum.next = jmin;
                    iDatum.last = jDatum.last;
                    nClusters--;
                    if (nClusters == targetClusters) {
                        break;
                    }
                }
            }
        } while (ok);
    }
}
