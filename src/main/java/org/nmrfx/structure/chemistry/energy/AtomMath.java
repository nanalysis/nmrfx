/*
 * NMRFx Structure : A Program for Calculating Structures 
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

package org.nmrfx.structure.chemistry.energy;

import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Point3;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.util.FastMath;

/**
 * This program performs various calculations with an atom
 *
 * @author brucejohnson
 */
public class AtomMath {

    static final double sumAvgN = 6.0;

    // atm_dis Vector3D
    // atm_sqdis Vector3D
    private static class IrpParameter {

        final int irpClass;
        final double v;
        final double s;
        final int n;

        IrpParameter(int irpClass, double v, double s, int n) {
            this.irpClass = irpClass;
            this.v = v;
            this.s = s;
            this.n = n;
        }
    }
    static IrpParameter[] IrpParameters = new IrpParameter[10];

    static {
        IrpParameters[1] = new IrpParameter(1, 0.00, 0.0, 0);
        IrpParameters[2] = new IrpParameter(2, 3.70, 1.0, 3);
        IrpParameters[3] = new IrpParameter(3, 0.00, 0.0, 0);
        IrpParameters[4] = new IrpParameter(4, 6.55, -1.0, 2);
        IrpParameters[5] = new IrpParameter(5, 6.70, -1.0, 1);
        IrpParameters[6] = new IrpParameter(6, 0.60, 1.0, 3);
        IrpParameters[7] = new IrpParameter(7, 3.40, -1.0, 2);
        IrpParameters[8] = new IrpParameter(8, 1.90, 1.0, 3);
        IrpParameters[9] = new IrpParameter(9, 1.80, 1.0, 3);
    }
    static final double RADJ = 0.02;

    /**
     *
     */
    public static boolean atomLimit(final Point3 a, final Point3 b, final double cutOff, final double cutOffSq) {
        double delX = FastMath.abs(a.getX() - b.getX());
        boolean result = false;
        if (delX < cutOff) {
            double delY = FastMath.abs(a.getY() - b.getY());
            if (delY < cutOff) {
                double delZ = FastMath.abs(a.getZ() - b.getZ());
                if (delZ < cutOff) {
                    double sqDis = delX * delX + delY * delY + delZ * delZ;
                    if (sqDis < cutOffSq) {
                        result = true;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Calculates the angle between two vectors
     *
     * @param pt1 first point
     * @param pt2 second point
     * @param pt3 third point
     */
    public static double calcAngle(final Vector3D pt1, final Vector3D pt2, final Vector3D pt3) {
        Vector3D v12 = pt1.subtract(pt2);
        Vector3D v32 = pt3.subtract(pt2);
        return Vector3D.angle(v12, v32);
    }

    /**
     * Calculates the triple using three points
     *
     * @param pt1 first point
     * @param pt2 second point
     * @param pt3 third point
     * @return triple scalar float
     */
    public static double calcTriple(final Vector3D pt1, final Vector3D pt2, final Vector3D pt3) {
        return Vector3D.dotProduct(pt1, Vector3D.crossProduct(pt2, pt3));
    }

    /**
     * Calculates the volume or space occupied between 4 points
     *
     * @param pt1 first point
     * @param pt2 second point
     * @param pt3 third point
     * @param pt4 fourth point
     * @return volume double
     */
    public static double calcVolume(final Point3 a, final Point3 b, final Point3 c, final Point3 d) {
        return calcTriple(a.subtract(d), b.subtract(d), c.subtract(d));
    }

    /**
     * Calculates the dihedral angle
     *
     * @param pt1 first point
     * @param pt2 second point
     * @param pt3 third point
     * @param pt4 fourth point
     * @return angle
     */
    public static double calcDihedral(final Point3 a, final Point3 b, final Point3 c, final Point3 d) {

        final double d12 = Vector3D.distance(a, b);
        final double sd13 = Vector3D.distanceSq(a, c);
        final double sd14 = Vector3D.distanceSq(a, d);
        final double sd23 = Vector3D.distanceSq(b, c);
        final double sd24 = Vector3D.distanceSq(b, d);
        final double d34 = Vector3D.distance(c, d);
        final double ang123 = Vector3D.angle(a.subtract(b), c.subtract(b));
        final double ang234 = Vector3D.angle(b.subtract(c), d.subtract(c));
        final double cosine = (sd13 - sd14 + sd24 - sd23 + 2.0 * d12 * d34 * FastMath.cos(ang123) * FastMath.cos(ang234))
                / (2.0 * d12 * d34 * FastMath.sin(ang123) * FastMath.sin(ang234));

        final double volume = calcVolume(a, b, c, d);

        final double sgn = (volume < 0.0) ? 1.0 : -1.0;
        double angle = 0.0;
        if (cosine > 1.0) {
            angle = 0.0;
        } else if (cosine < -1.0) {
            angle = FastMath.PI;
        } else {
            angle = sgn * FastMath.acos(cosine);
        }
        return (angle);

    }

    /**
     * Calculates the distance between 2 points
     *
     * @param pt1 first point
     * @param pt2 second point
     * @return distance
     */
    public static double calcDistance(Point3 pt1, Point3 pt2) {
        return Vector3D.distance(pt1, pt2);
    }

    public static double calcDistanceSumAvg(DistancePair distancePair, double avgN) {
// fixme
        final double distance;
        if ((distancePair.atomPairs.length == 1)) {
            distance = distancePair.atomPairs[0].getDistance();
        } else {
            int nMono = 1;
            double sum = 0.0;
            for (AtomDistancePair atomDistancePair : distancePair.atomPairs) {
                double distance1 = atomDistancePair.getDistance();
                sum += FastMath.pow(distance1, -avgN);
            }
            sum /= nMono;
            distance = FastMath.pow(sum, -1.0 / avgN);
        }
        return distance;
    }

    public static AtomEnergy calcRobson(final Point3 pt1, final Point3 pt2, final AtomPair atmPair, final ForceWeight forceWeight, final boolean calcDeriv) {
        final double a = atmPair.ePair.a1;
        final double b = atmPair.ePair.b1;
        final double c;
        if (forceWeight.getElectrostatic() > 0.0) {
            c = atmPair.ePair.c1 * 332.0;
        } else {
            c = 0.0;
        }
        final double p = Vector3D.distanceSq(pt1, pt2);
        final AtomEnergy result;
        if (p > forceWeight.cutoffSq) {
            result = AtomEnergy.ZERO;
        } else {
            final double cutoffScale;
            if (p > forceWeight.cutoffSwitchSq) {
                cutoffScale = (forceWeight.cutoffSq - p) / forceWeight.cutoffDem;
            } else {
                cutoffScale = -1.0;
            }
            if (!calcDeriv) {
                final double q = 1.0 + 0.25 * p;
                final double s = 2.0 * q / (q * q + p);
                final double s3 = s * s * s;
                final double s6 = s3 * s3;
                double e = forceWeight.getRobson() * ((a * s3 - b) * s6 + c * s);
                if (cutoffScale >= 0.0) {
                    e *= cutoffScale;
                }
                result = new AtomEnergy(e);
            } else {
                final double u = 2.0 + 0.5 * p;
                final double v = 1.0 + (0.0625 * p + 1.5) * p;
                final double s = u / v;
                final double s2 = s * s;
                final double s3 = s2 * s;
                final double s5 = s2 * s3;
                final double s6 = s3 * s3;
                final double deds = (9.0 * a * s3 - 6.0 * b) * s5 + c;
                final double dsdp = (0.5 - (u / v) * (1.5 + 0.125 * p)) / v;
                double e = forceWeight.getRobson() * ((a * s3 - b) * s6 + c * s);
                /*
                 * what is needed is actually the derivitive/r, therefore the r that
                 * would be in following drops out
                 */
                double deriv = deds * dsdp * 2.0 * forceWeight.getRobson();
                if (cutoffScale >= 0.0) {
                    e *= cutoffScale;
                    deriv *= cutoffScale;
                }
                result = new AtomEnergy(e, deriv);
            }
        }
        return result;
    }

    public static AtomEnergy calcRepel(final Point3 pt1, final Point3 pt2, final AtomPair atmPair, final ForceWeight forceWeight, final boolean calcDeriv) {
        double r = Vector3D.distance(pt1, pt2);
        final AtomEnergy result;
        final double r0 = atmPair.ePair.rh;
        if (r > r0) {
            result = AtomEnergy.ZERO;
        } else {
            double weight = forceWeight.getRepel();
            double dif = r0 - r;
            double e = weight * dif * dif;
            if (!calcDeriv) {
                result = new AtomEnergy(e);
            } else {
                //  what is needed is actually the derivitive/r, therefore 
                // we divide by r
                // fixme problems if r near 0.0 so we add small adjustment.  Is there a better way???
                double deriv = -2.0 * weight * dif / (r + RADJ);
                if (Math.abs(deriv) > 100000.0) {
                    System.out.printf("bad repel deriv %9.4g %9.4f %9.4f %9.4f\n", deriv, dif, r, e);
                }
                result = new AtomEnergy(e, deriv);
            }
        }
        return result;
    }

    public static AtomEnergy calcRepelOLD(final Point3 pt1, final Point3 pt2, final AtomPair atmPair, final ForceWeight forceWeight, final boolean calcDeriv) {
        double r2 = Vector3D.distanceSq(pt1, pt2);
        final AtomEnergy result;
        final double r20 = atmPair.ePair.r2;
        if (r2 > r20) {
            result = AtomEnergy.ZERO;
        } else {
            double weight = forceWeight.getRepel();
            double dif = r20 - r2;
            double e = weight * dif * dif;
            if (!calcDeriv) {
                result = new AtomEnergy(e);
            } else {
                //  what is needed is actually the derivitive/r, therefore the r that
                // would be in following drops out
                double deriv = -4.0 * weight * dif;
                result = new AtomEnergy(e, deriv);
            }
        }
        return result;
    }

    public static AtomEnergy calcBond(final Point3 pt1, final Point3 pt2, final BondPair bondPair, final ForceWeight forceWeight, final boolean calcDeriv) {
        double r = Vector3D.distance(pt1, pt2);
        final AtomEnergy result;
        final double r0 = bondPair.r0;
        double viol = r - r0;
        double viol2 = viol * viol;
        if (calcDeriv) {
            double f = forceWeight.getBond() * viol2 * viol;
            // fixme problems if r near 0.0 so we add small adjustment.  Is there a better way???

            double deriv = 4.0 * f / (r + RADJ);
            double e = f * viol;
            result = new AtomEnergy(e, deriv);
        } else {
            double e = forceWeight.getBond() * viol2 * viol2;
            result = new AtomEnergy(e);
        }
        return result;
    }

    //Uses Distance Contraints to Calculate Energy
    public static AtomEnergy calcDistanceEnergy(final DistancePair distancePair, final ForceWeight forceWeight, final boolean calcDeriv) {
        //distance between atoms
        double r = calcDistanceSumAvg(distancePair, sumAvgN);

        //distance between atoms squared
        //double r2 = r * r;
        /**
         * the energy result returned
         */
        final AtomEnergy result;

        /**
         * upper bounds for distance between two atoms atom cannot exceed this distance - provided by NMR data
         */
        double upper = distancePair.rUp;

        /**
         * lower bounds for distance between two atoms atom cannot be lower than this distance - provided by NMR data
         */
        double lower = distancePair.rLow;
        double noeWeight = forceWeight.getNOE();
        double bSwitch = 1.0;
        double aSwitch = 1.0;
        double rSwitch = 1.0;
        int noeClass = 1;

        /**
         * viol initially set to distance between r (current distance) and upper bounds
         */
        double viol = upper - r;
        switch (noeClass) {
            case 0:
                if ((r < upper) && (r > lower)) {
                    result = AtomEnergy.ZERO;
                } else {
                    if (r < upper) {
                        viol = lower - r;
                    }
                    if (viol > rSwitch) {
                        double energy = noeWeight * ((bSwitch / viol) + aSwitch);
                        if (calcDeriv) {
                            double viol2 = viol * viol;
                            double deriv = -noeWeight * (bSwitch / viol2) / (r + RADJ);
                            result = new AtomEnergy(energy, deriv);
                        } else {
                            result = new AtomEnergy(energy);
                        }
                    } else {
                        double viol2 = viol * viol;
                        if (calcDeriv) {
                            double energy = noeWeight * viol2 * viol;
                            // fixme problems if r near 0.0 so we add small adjustment.  Is there a better way???

                            double deriv = 4.0 * energy / (r + RADJ);
                            energy = energy * viol;
                            result = new AtomEnergy(energy, deriv);
                        } else {
                            double energy = noeWeight * viol2 * viol2;
                            result = new AtomEnergy(energy);
                        }
                    }
                }
                break;

            case 1:
                //*if the energy is between the constraints set the result to 0*/
                if ((r < upper) && (r > lower)) {
                    result = AtomEnergy.ZERO;
                } else {
                    /*if the energy is below upper (meaning also below lower)
                     * the viol is calculated to be lower - r.
                     * 
                     */
                    if (r < upper) {
                        viol = lower - r;
                    }
                    /* squared the viol value
                     */
                    double viol2 = viol * viol;
                    /*used to calculate derivative of target function*/
                    if (calcDeriv) {
                        double energy = noeWeight * viol * viol;
                        // fixme problems if r near 0.0 so we add small adjustment.  Is there a better way???
                        double deriv = -2.0 * noeWeight * viol / (r + RADJ);
                        if (Math.abs(deriv) > 10000.0) {
                            System.out.printf("bad noe deriv %9.4g %9.4f %9.4f %9.4f\n", deriv, viol, r, energy);
                            System.out.println(distancePair.toString());
                            System.exit(1);
                        }

                        result = new AtomEnergy(energy, deriv);
                    } else {
                        double energy = noeWeight * viol2;
                        result = new AtomEnergy(energy);
                    }
                }
                break;

            default:
                if ((r < upper) && (r > lower)) {
                    result = AtomEnergy.ZERO;
                } else {
                    if (r < upper) {
                        viol = lower - r;
                    }
                    double viol2 = viol * viol;
                    if (calcDeriv) {
                        double energy = noeWeight * viol2 * viol;
                        // fixme problems if r near 0.0 so we add small adjustment.  Is there a better way???                    
                        double deriv = 2.0 * energy / (r + RADJ);
                        energy = energy * viol;
                        result = new AtomEnergy(energy, deriv);
                    } else {
                        double energy = noeWeight * viol2 * viol2;
                        result = new AtomEnergy(energy);
                    }
                }
        }
        return (result);
    }

    public static AtomEnergy calcDihedralEnergy(AngleBoundary boundary, final ForceWeight forceWeight, final boolean calcDeriv) {
        double dihedral = boundary.atom.dihedralAngle;
        dihedral = Dihedral.reduceAngle(dihedral);

        double upper = boundary.upper;
        double lower = boundary.lower;
        final AtomEnergy result;
        if (upper > Math.PI) {
            if (dihedral < 0.0) {
                dihedral += 2.0 * Math.PI;
            }
        }
        if ((dihedral < upper) && (dihedral > lower)) {
            result = AtomEnergy.ZERO;
        } else {
            double range = upper - lower;
            double halfRange = Math.PI - (range / 2.0);
            double halfRange2 = halfRange * halfRange;
            double delta = 0.0;
            if (dihedral > upper) {
                delta = upper - dihedral;
                double deltaR = 2.0 * Math.PI - (range - delta);
                if (Math.abs(delta) > deltaR) {
                    delta = deltaR;
                }
            } else {
                delta = lower - dihedral;
                double deltaR = 2.0 * Math.PI - (range + delta);
                if (delta > deltaR) {
                    delta = -deltaR;
                }
            }
            double delta2 = delta * delta;
            double energy = forceWeight.getDihedral() * (1.0 - 0.5 * delta2 / halfRange2) * delta2;
            //System.out.printf("%.3f %.3f %.3f %.3f %.3f %.3f %.3f\n",lower,dihedral,upper,delta, halfRange,(delta/halfRange),energy);
            //double energy = forceWeight.getDihedral()*delta2;
            double deriv = 0.0;
            if (calcDeriv) {
                deriv = forceWeight.getDihedral() * 2.0 * delta * (1.0 - delta2 / halfRange2);
                //deriv = forceWeight.getDihedral()*2.0*delta;
            }
            result = new AtomEnergy(energy, deriv);
        }
        return result;
    }

    public static AtomEnergy calcIrpEnergy(Atom atom, final ForceWeight forceWeight, final boolean calcDeriv) {
        int irpIndex = atom.parent.irpIndex;
        final AtomEnergy result;
        if ((irpIndex < 2) || !atom.parent.rotActive) {
            result = AtomEnergy.ZERO;
        } else {
            double angle = atom.dihedralAngle;
            angle = Dihedral.reduceAngle(angle);
            double v = IrpParameters[irpIndex].v;
            double s = IrpParameters[irpIndex].s;
            double n = IrpParameters[irpIndex].n;
            double energy = forceWeight.getIrp() * v * (1.0 + s * Math.cos(n * angle));
            double deriv = 0.0;
            if (calcDeriv) {
                deriv = -forceWeight.getIrp() * v * s * n * Math.sin(n * angle);
            }
            result = new AtomEnergy(energy, deriv);
        }
        return result;
    }

    public static AtomEnergy calcTorsionAngleEnergy(AngleBoundary boundary, final ForceWeight forceWeight) {
        final AtomEnergy result;
        double energy = Double.MAX_VALUE;
        if (boundary != null) {
            if (boundary.angleProp != null) {
                AngleProp temp = boundary.angleProp;
                int indexValue = 0;
                double dis = FastMath.abs(boundary.atom.dihedralAngle - temp.target[0]);
                for (int i = 1; i < temp.target.length; i++) {
                    double newdis = FastMath.abs(boundary.atom.dihedralAngle - temp.target[i]);
                    if (newdis < dis) {
                        dis = newdis;
                        indexValue = i;
                    }
                }
                energy = 1 - temp.height[indexValue] * FastMath.exp((-dis * dis) / (2 * temp.sigma[indexValue] * temp.sigma[indexValue]));
            } else {
                energy = 0;
            }
        } else {
            energy = 0;
        }
        result = new AtomEnergy(energy);
        return result;

    }
}
/*
 double
 atm_irp(irp_index, angle, deriv)
 int             irp_index;
 double          angle;
 double         *deriv;
 {
 double          v, s, e;
 int             n;
 if (irp_index < 2) {
 e = 0.0;
 if (deriv != NULL)
 *deriv = 0.0;
 return (e);
 }
 v = irpdef[irp_index].v;
 s = irpdef[irp_index].s;
 n = irpdef[irp_index].n;
 e = v * (1.0 + s * cos(n * angle));
 if (deriv != NULL)
 *deriv = -v * s * n * sin(n * angle);
 return (e);
 }

 double
 atm_dih_delta(angle, a_exp, a_tol)
 double          a_exp, a_tol, angle;
 {
 double          delta;
 angle = reduce_angle(angle);
 delta = angle - a_exp;
 if (delta > M_PI)
 delta -= 2.0 * M_PI;
 else if (delta < -M_PI)
 delta += 2.0 * M_PI;
 delta = fabs(delta);
 if (delta > a_tol)
 return (delta - a_tol);
 else
 return (0.0);
 }

 double
 atm_dih_energy(angle, a_exp, a_tol, deriv)
 double          a_exp, a_tol, angle;
 double         *deriv;
 {
 double          e = 0.0;
 double          delta;
 if (deriv != NULL)
 *deriv = 0.0;
 angle = reduce_angle(angle);
 delta = angle - a_exp;
 if (delta > M_PI)
 delta -= 2.0 * M_PI;
 else if (delta < -M_PI)
 delta += 2.0 * M_PI;
 if (fabs(delta) > a_tol) {
 if (delta < 0.0)
 delta += a_tol;
 else
 delta -= a_tol;
 e = w_dih * delta * delta;
 if (deriv != NULL)
 *deriv = 2.0 * w_dih * delta;
 }
 return (e);
 }
 double
 atm_jval(angle, type)
 double          angle;
 int             type;
 {
 double          A, B, C, del, j, cosa;
 switch (type) {
 case 0:
 A = 6.4;
 B = 1.4;
 C = 1.9;
 del = 60.0 * M_PI / 180.0;
 break;
 case 1:
 A = 9.5;
 B = 1.6;
 C = 1.8;
 del = 0.0;
 break;
 }
 angle = angle - del;
 angle = reduce_angle(angle);
 cosa = cos(angle);
 j = (A * cosa - B) * cosa + C;
 return (j);
 }
 double
 atm_jphi(j, j0, angle, deriv)
 double          j, angle;
 double         *j0;
 double         *deriv;
 {
 double          a60 = 1.04719755;
 double          A = 6.4;
 double          B = 1.4;
 double          C = 1.9;
 double          cosa, sina, e, ang;
 ang = fabs(angle - a60);
 // Don't try to pull the angle above phi = -30
 if

 (ang < M_PI / 2.0)
 ang = M_PI / 2.0;
 cosa  = cos(ang);
 *
 j0  = (A * cosa - B) * cosa + C;
 if

 (fabs
 (j -  {
 *j0) < 1.0) {
 if (deriv != NULL)
 * deriv = 0.0;
 }
 e = 0.0;
 return (e);
 }
 if


 (j > *j0)
 j += -1.0;
 else
 j += 1.0 ;
 e  = w_jphi * (j - *  j0);
 if




 (deriv
 != NULL) {
 sina = sin(ang);
 * deriv = -2.0 * e * (B * sina - A * 2.0 * cosa * sina);
 if (angle < 0.0) {
 *  deriv *= -1.0;
 }
 }
 e  = e * (j - *  j0);
 return



 (e);
 }




 double
 atm_repel(atma, atmb, i, j, deriv)
 POINT3          atma, atmb;


 int             i, j;


 double         *deriv;


 {
 double          r2, r20;


 double          dif;


 double          e = 0.0;
 r2

 = atm_sqdis(atma, atmb);
 r20

 = zeromin[i][j].r2;


 if (r2 < r20) {
 dif = r20 - r2;
 e

 = w_repel * dif * dif;


 if (deriv != NULL)

 // what is needed is actually the derivitive/r, therefore the r that
 // would be in following drops out

 *deriv = -4.0 * w_repel * dif;


 } else if (deriv != NULL)
 *deriv = 0.0;


 return (e);


 }
 double
 reduce_angle(x)
 double          x;


 {
 while (x > 2.0 * M_PI)
 x = x - 2.0 * M_PI;


 while (x < -2.0 * M_PI)
 x = x + 2.0 * M_PI;


 if (x > M_PI)
 x = x - 2.0 * M_PI;


 if (x < -M_PI)
 x = x + 2.0 * M_PI;


 return (x);


 }
 double
 reduce_angle2(x)
 double          x;


 {
 while (x > 360.0)
 x = x - 360.0;


 while (x < -360.0)
 x = x + 360.0;


 if (x > 180.0)
 x = x - 360;


 if (x < -180.0)
 x = x + 360.0;


 return (x);

 }
 */
