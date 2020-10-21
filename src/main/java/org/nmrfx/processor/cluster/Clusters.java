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

    public static class ClusterItem {

        double[] v;
        int group;
        List<Object> objects = new ArrayList<>();
        boolean active = true;

        public double getV0() {
            return v[0];
        }

        public List<Object> getObjects() {
            return objects;
        }

        public int getN() {
            return objects.size();
        }

        public boolean isActive() {
            return active;
        }

        public ClusterItem(Object obj, double[] v, int group) {
            this.objects.add(obj);
            this.v = v.clone();
            this.group = group;
        }

        public ClusterItem(List<Object> objs, double[] v, int group) {
            this.objects.addAll(objs);
            this.v = v.clone();
            this.group = group;
        }

        public void merge(ClusterItem item2) {
            int iPfdim = v.length;
            for (int ii = 0; ii < iPfdim; ii++) {
                v[ii] = ((v[ii] * getN()) + (item2.v[ii] * item2.getN()))
                        / (getN() + item2.getN());
            }
            objects.addAll(item2.objects);
            item2.objects.clear();
            item2.active = false;
        }

        public String toString() {
            StringBuilder sBuilder = new StringBuilder();
            for (int i = 0; i < objects.size(); i++) {
                sBuilder.append(objects.get(i).toString());
                sBuilder.append(" ");
            }
            for (int i = 0; i < v.length; i++) {
                sBuilder.append(v[i]).append(" ");
            }
            sBuilder.append(group);
            return sBuilder.toString();
        }
    }

    public List<ClusterItem> data = new ArrayList<>();

    /**
     * Creates new Clusters
     */
    public Clusters() {
    }

    public void dump() {
        for (ClusterItem item : data) {
            if (item.isActive()) {
                System.out.println(item.toString());
            }
        }
    }

    public void testDuplicates() {
        Set<Object> set = new HashSet<>();
        for (ClusterItem item : data) {
            if (item.isActive()) {
                for (Object obj : item.objects) {
                    if (set.contains(obj)) {
                        System.out.println("duplicate " + obj.toString());
                        System.out.println(item.toString());
                    } else {
                        set.add(obj);
                    }
                }
            }
        }

    }

    public void addDatum(ClusterItem item) {
        data.add(item);
    }

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

        int nClusterItems = data.size();
        int nClusters = nClusterItems;

        if (nClusterItems < 2) {
            throw new IllegalArgumentException("Can't cluster less than 2 peaks");
        }

        data.sort(comparing(ClusterItem::getV0));

        imin = 0;

        do {
            ok = false;
            min = 1e6;
            imin = -1;
            jmin = -1;

            for (i = 0; i < (nClusterItems - 1); i++) {
                ClusterItem iDatum = data.get(i);

                if (!iDatum.active) {
                    continue;
                }

                ok1 = true;
                k = i;

                do {
                    k++;
                    ClusterItem jDatum = null;

                    for (j = k; j < nClusterItems; j++) {
                        ClusterItem testDatum = data.get(j);

                        if ((testDatum.group == 0) && (iDatum.group == 0)) {
                            continue;
                        }

                        if (testDatum.active) {
                            jDatum = testDatum;
                            break;
                        }
                    }

                    if (j >= nClusterItems) {
                        break;
                    }

                    if (jDatum != null) {
                        ok = true;
                        dDeltaSum = 0.0;

                        for (ii = 0; ii < iPfdim; ii++) {
                            delta1 = (iDatum.getN() * (iDatum.v[ii] * iDatum.v[ii]))
                                    + (jDatum.getN() * (jDatum.v[ii] * jDatum.v[ii]));
                            delta2 = (iDatum.getN() * iDatum.v[ii])
                                    + (jDatum.getN() * jDatum.v[ii]);
                            delta2 = (delta2 * delta2) / (iDatum.getN() + jDatum.getN());

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
                    }
                } while ((k < (nClusterItems - 1)) && ok1);
            }

            if (ok) {
                if ((nClusters > targetClusters) || (Math.sqrt(min) < Math.sqrt(1.0 * iPfdim))) {
                    ok = true;
                } else {
                    ok = false;
                }

                if (ok && (jmin >= 0) && (imin >= 0)) {
                    ClusterItem iDatum = data.get(imin);
                    ClusterItem jDatum = data.get(jmin);
                    if (iDatum.group == 0) {
                        iDatum.merge(jDatum);
                    } else {
                        jDatum.merge(iDatum);
                    }

                    nClusters--;
                    if (nClusters == targetClusters) {
                        break;
                    }
                }
            }
        } while (ok);
    }
}
