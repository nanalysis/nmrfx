package org.nmrfx.peaks;


import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PeakPath implements Comparable<PeakPath> {

    private final PeakPaths peakPaths;

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.firstPeak);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PeakPath other = (PeakPath) obj;
        return Objects.equals(this.firstPeak, other.firstPeak);
    }

    Peak firstPeak;
    List<PeakDistance> peakDists = new ArrayList<>();
    double radius;
    boolean confirmed = false;
    boolean active = false;
    double[] pars = null;
    double[] parErrs = null;

    public PeakPath(PeakPaths peakPaths, List<PeakDistance> path, double dis) {
        this.peakPaths = peakPaths;
        peakDists.addAll(path);
        firstPeak = path.get(0).getPeak();
        radius = dis;
    }

    public PeakPath(PeakPaths peakPaths, Peak peak) {
        this.peakPaths = peakPaths;
        firstPeak = peak;
        double[] deltas = new double[peakPaths.tols.length];
        PeakDistance peakDist = new PeakDistance(peak, 0.0, deltas);

        peakDists.add(peakDist);
        for (int i = 1; i < peakPaths.peakLists.size(); i++) {
            peakDists.add(null);
        }
        radius = 0.0;
    }

    public PeakPath(PeakPaths peakPaths, List<PeakDistance> path) {
        this.peakPaths = peakPaths;
        peakDists.addAll(path);
        firstPeak = path.get(0).getPeak();
        double maxDis = 0.0;
        for (PeakDistance peakDis : path) {
            if ((peakDis != null) && (peakDis.distance > maxDis)) {
                maxDis = peakDis.distance;
            }
        }
        radius = maxDis;
    }

    public PeakPaths getPeakPaths() {
        return peakPaths;
    }

    public void refresh() {
        for (int i = 0; i < peakDists.size(); i++) {
            PeakDistance peakDis = peakDists.get(i);
            if (peakDis != null) {
                double[] deltas = peakPaths.calcDeltas(firstPeak, peakDis.peak);
                double distance = peakPaths.calcDistance(firstPeak, peakDis.peak);
                PeakDistance newPeakDis = new PeakDistance(peakDis.peak, distance, deltas);
                peakDists.set(i, newPeakDis);
            }
        }

    }

    public List<PeakDistance> getPeakDistances() {
        return peakDists;
    }

    public void confirm() {
        confirmed = true;
    }

    public boolean confirmed() {
        return confirmed;
    }

    public void setActive(boolean state) {
        active = state;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public int compareTo(PeakPath o) {
        if (o == null) {
            return 1;
        } else {
            return Double.compare(radius, o.radius);
        }
    }

    public boolean isComplete() {
        boolean complete = true;
        for (PeakDistance peakDis : peakDists) {
            if (peakDis == null) {
                complete = false;
                break;
            }
        }
        return complete;
    }

    public boolean isFree() {
        boolean free = true;
        for (PeakDistance peakDis : peakDists) {
            if ((peakDis != null) && (peakDis.peak.getStatus() != 0)) {
                free = false;
                break;
            }
        }
        return free;
    }

    public int getNValid() {
        int nValid = 0;
        for (PeakDistance peakDis : peakDists) {
            if (peakDis != null) {
                nValid++;
            }
        }
        return nValid;
    }

    public int getId() {
        return getFirstPeak().getIdNum();
    }

    public Peak getFirstPeak() {
        return firstPeak;
    }

    public void setFitPars(double[] pars) {
        this.pars = pars != null ? pars.clone() : null;
    }

    public void setFitErrs(double[] parErrs) {
        this.parErrs = parErrs != null ? parErrs.clone() : null;
    }

    public void setFitParErrors(Double[] parsIn, Double[] errsIn) {
        int nOK = 0;
        for (var par:parsIn) {
            if (par != null) {
                nOK++;
            }
        }
        pars = new double[nOK];
        parErrs = new double[nOK];
        int j =0;
        for (int i=0;i<parsIn.length;i++) {
            if (parsIn[i] != null) {
                pars[j] = parsIn[i];
                parErrs[j] = errsIn[i];
                j++;
            }
        }

    }
    public int getPeak() {
        return firstPeak.getIdNum();
    }

    public String getAtom() {
        return firstPeak.getPeakDim(0).getLabel();
    }

    public double getPar(int i) {
        if (i < pars.length) {
            return pars[i];
        } else {
            return 0.0;
        }
    }

    public double getErr(int i) {
        if (i < parErrs.length) {
            return parErrs[i];
        } else {
            return 0.0;
        }
    }

    public boolean hasPars() {
        return pars != null;
    }

    public double[] getFitPars() {
        return pars;
    }

    public double[] getFitErrs() {
        return parErrs;
    }

    public double getRadius() {
        return radius;
    }

    public Double getA() {
        if (pars == null) {
            return null;
        } else {
            if (pars.length == 3) {
                return pars[0];
            } else {
                return 0.0;
            }
        }
    }

    public Double getADev() {
        if (parErrs == null) {
            return null;
        } else {
            if (parErrs.length == 3) {
                return parErrs[0];
            } else {
                return 0.0;
            }
        }
    }

    public Double getK(int iState) {
        if (pars == null) {
            return null;
        } else {
            if (pars.length == 3) {
                return pars[1];
            } else {
                return pars[0];
            }
        }
    }

    public Double getKDev(int iState) {
        if (parErrs == null) {
            return null;
        } else {
            if (parErrs.length == 3) {
                return parErrs[1];
            } else {
                return parErrs[0];
            }
        }
    }

    public Double getD(int iState) {
        if (pars == null) {
            return null;
        } else {
            if (pars.length == 3) {
                return pars[2];
            } else {
                return pars[1];
            }
        }
    }

    public Double getDDev(int iState) {
        if (parErrs == null) {
            return null;
        } else {
            if (parErrs.length == 3) {
                return parErrs[2];
            } else {
                return parErrs[1];
            }
        }
    }

    public String toSTAR3ParString(int id, int pathID, int dim) {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(String.format("%4d %4d %d %3s %3s", id, pathID, dim + 1,
                (confirmed ? "yes" : "no"), (active ? "yes" : "no")));
        int nPars = peakPaths.pathMode == PeakPaths.PATHMODE.PRESSURE ? 3 : 4;
        Double[] outPars = new Double[nPars];
        Double[] outErrs = new Double[nPars];

        if ((pars != null) && (pars.length > 0)) {
            if ((pars.length != nPars) && (peakPaths.getPathMode() == PeakPaths.PATHMODE.TITRATION)) {
                for (int i = 0; i < 2; i++) {
                    outPars[i * 2] = pars[i];
                    outErrs[i * 2] = parErrs[i];
                }
            } else {
                for (int i = 0; i < pars.length; i++) {
                    outPars[i] = pars[i];
                    outErrs[i] = parErrs[i];
                }
            }
        }

        int start = dim * nPars;
        for (int i = 0; i < nPars; i++) {
            sBuilder.append(" ");
            if (pars == null) {
                sBuilder.append(String.format("%10s %10s", "?", "?"));
            } else {
                if (outPars[i + start] == null) {
                    sBuilder.append(". .");
                } else {
                    sBuilder.append(String.format("%10.4f %10.4f", outPars[i + start], outErrs[i + start]));
                }
            }
        }
        return sBuilder.toString();
    }

    public String toSTAR3String(int i) {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(i);
        for (PeakDistance peakDis : peakDists) {
            sBuilder.append(" ");
            if (peakDis == null) {
                sBuilder.append("?");
            } else {
                sBuilder.append(peakDis.peak.getIdNum());
            }
        }
        return sBuilder.toString();
    }

    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        for (PeakDistance peakDis : peakDists) {
            if (sBuilder.length() != 0) {
                sBuilder.append(" ");
            }
            if (peakDis == null) {
                sBuilder.append("empty");
            } else {
                sBuilder.append(peakDis.peak.getName());
                sBuilder.append(" ");
                sBuilder.append(String.format("%.3f", peakDis.distance));
            }
        }
        sBuilder.append(" ");
        sBuilder.append(String.format("%.3f %b %b %b", radius, confirmed(), isActive(), hasPars()));
        return sBuilder.toString();
    }
}
