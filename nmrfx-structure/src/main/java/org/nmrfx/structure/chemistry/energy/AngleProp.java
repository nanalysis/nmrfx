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

import java.util.HashMap;

/**
 * @author gluthr1
 */
public class AngleProp {

    public static HashMap<String, AngleProp> map = new HashMap<String, AngleProp>();

    public static AngleProp alpha = new AngleProp("alpha", new double[]{-64.7029644001, 65.193527207, 162.224200013},
            new double[]{6.63879317909, 13.8651143009, 14.1769070028},
            new double[]{1, (108.241472644 / 873.847844017), (130.195437888 / 873.847844017)});
    public static AngleProp beta = new AngleProp("beta", new double[]{-179.9, 173.865335385},
            new double[]{26.7348405551, 7.69459762215},
            new double[]{(640.693804717 / 684.413675721), 1});
    public static AngleProp gamma = new AngleProp("gamma", new double[]{53.137095038, -65.2843455597, 174.489622504,
            (181.71744189 - 360.0)}, new double[]{4.57064056781, 7.1589722129,
            12.5724013257, 3.77517654037}, new double[]{1, (38.1204100833 / 1117.11283152),
            (90.7219250741 / 1117.07402188), (39.3595545863 / 1117.07402188)});
    public static AngleProp epsilon = new AngleProp("epsilon", new double[]{-150.650967765, -130.616065934, 173.545305696},
            new double[]{8.49439153974, 30.6456988969, 5.80037395083},
            new double[]{1, 576.536308101 / 813.033535904, 11.4736511248 / 813.033535904});
    public static AngleProp delta = new AngleProp("delta", new double[]{80.986642655, 148.02528646},
            new double[]{2.65075237294, 4.19156080606},
            new double[]{1, 175.122237292 / 1102.78457069});
    public static AngleProp zeta = new AngleProp("zeta", new double[]{-70.0}, new double[]{6.7109}, new double[]{1.0});

    double[] sigma = null;
    double[] target = null;
    double[] height = null;
    String angleName = null;

    final static double toRad = Math.PI / 180.0;

    public AngleProp(String name, double[] target, double[] sigma, double[] height) {
        this.sigma = sigma;
        this.target = target;
        this.height = height;

        for (int i = 0; i < target.length; i++) {
            target[i] = target[i] * toRad;
            sigma[i] = sigma[i] * toRad;
        }
        angleName = name;
        map.put(name, this);

    }

    public double[] getTarget() {
        return target;
    }

}
