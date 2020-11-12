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
package org.nmrfx.datasets;

/**
 * Representation of different NMR active nuclei.
 *
 * @author brucejohnson
 */
public enum Nuclei {

    H1("H", 1, "1/2", 99.98, 1.00000) {
    },
    H3("H", 3, "1/2", 0, 1.06663) {
    },
    F19("F", 19, "1/2", 100, 0.94077) {
    },
    He3("He", 3, "1/2", 1.3e-4, 0.76178) {
    },
    Tl205("Tl", 205, "1/2", 70.5, 0.57708) {
    },
    Tl203("Tl", 203, "1/2", 29.5, 0.57149) {
    },
    P31("P", 31, "1/2", 100, 0.40481) {
    },
    Sn119("Sn", 119, "1/2", 8.58, 0.37272) {
    },
    Sn117("Sn", 117, "1/2", 7.61, 0.35625) {
    },
    Sn115("Sn", 115, "1/2", 0.35, 0.32700) {
    },
    Te125("Te", 125, "1/2", 6.99, 0.31597) {
    },
    Xe129("Xe", 129, "1/2", 26.44, 0.27660) {
    },
    Al27("Al", 27, "5/2", 100.0, 0.26077) {
    },
    Te123("Te", 123, "1/2", 0.87, 0.26208) {
    },
    C13("C", 13, "1/2", 1.108, 0.25144) {
    },
    Cd113("Cd", 113, "1/2", 12.26, 0.22183) {
    },
    Pt195("Pt", 195, "1/2", 33.8, 0.21499) {
    },
    Cd111("Cd", 111, "1/2", 12.75, 0.21205) {
    },
    Pb207("Pb", 207, "1/2", 22.6, 0.20922) {
    },
    Si29("Si", 29, "1/2", 4.7, 0.19865) {
    },
    Se77("Se", 77, "1/2", 7.58, 0.19068) {
    },
    Hg199("Hg", 199, "1/2", 16.84, 0.17827) {
    },
    Yb171("Yb", 171, "1/2", 14.31, 0.17613) {
    },
    N15("N", 15, "1/2", 0.37, 0.10133) {
    },
    Tm169("Tm", 169, "1/2", 100, 0.08271) {
    },
    Y89("Y", 89, "1/2", 100, 0.04899) {
    },
    Ag109("Ag", 109, "1/2", 48.18, 0.04652) {
    },
    W183("W", 183, "1/2", 14.4, 0.04161) {
    },
    Ag107("Ag", 107, "1/2", 51.82, 0.04046) {
    },
    Fe57("Fe", 57, "1/2", 2.19, 0.03231) {
    },
    Rh103("Rh", 103, "1/2", 100, 0.03147) {
    },
    Os187("Os", 187, "1/2", 1.64, 0.02303) {
    },
    Li7("Li", 7, "3/2", 92.58, 0.38863) {
    },
    Rb87("Rb", 87, "3/2", 27.85, 0.32721) {
    },
    B11("B", 11, "3/2", 80.42, 0.32084) {
    },
    Ga71("Ga", 71, "3/2", 39.6, 0.30495) {
    },
    Cu65("Cu", 65, "3/2", 30.91, 0.28394) {
    },
    Br81("Br", 81, "3/2", 49.46, 0.27006) {
    },
    Cu63("Cu", 63, "3/2", 69.09, 0.26505) {
    },
    Na23("Na", 23, "3/2", 100, 0.26451) {
    },
    Br79("Br", 79, "3/2", 50.54, 0.25053) {
    },
    Ga69("Ga", 69, "3/2", 60.4, 0.24003) {
    },
    Tb159("Tb", 159, "3/2", 100, 0.22678) {
    },
    As75("As", 75, "3/2", 100, 0.17127) {
    },
    Be9("Be", 9, "3/2", 100, 0.14053) {
    },
    Ba137("Ba", 137, "3/2", 11.32, 0.11113) {
    },
    Ba135("Ba", 135, "3/2", 6.59, 0.09934) {
    },
    Cl35("Cl", 35, "3/2", 75.53, 0.09798) {
    },
    Ni61("Ni", 61, "3/2", 1.19, 0.08936) {
    },
    Xe131("Xe", 131, "3/2", 21.18, 0.08199) {
    },
    Cl37("Cl", 37, "3/2", 24.47, 0.08156) {
    },
    Ne21("Ne", 21, "3/2", 0.257, 0.07894) {
    },
    Os189("Os", 189, "3/2", 16.1, 0.07759) {
    },
    S33("S", 33, "3/2", 0.76, 0.07670) {
    },
    Hg201("Hg", 201, "3/2", 13.22, 0.06600) {
    },
    Cr53("Cr", 53, "3/2", 9.55, 0.05652) {
    },
    Gd157("Gd", 157, "3/2", 15.68, 0.04774) {
    },
    K39("K", 39, "3/2", 93.1, 0.04666) {
    },
    Gd155("Gd", 155, "3/2", 14.74, 0.03819) {
    },
    Ru99("Ru", 99, "3/2", 12.72, 0.03390) {
    },
    K41("K", 41, "3/2", 6.88, 0.02561) {
    },
    Ir193("Ir", 193, "3/2", 62.7, 0.01871) {
    },
    Ir191("Ir", 191, "3/2", 37.3, 0.01719) {
    },
    Au197("Au", 197, "3/2", 100, 0.01713) {
    };

    String name;
    int num;
    int spin;
    double abundance;
    double freqRatio;

    Nuclei(final String name, final int num, final String spin, final double abundance, final double freqRatio) {
        this.name = name;
        this.num = num;
        this.spin = Integer.parseInt(spin.substring(0, 1));
        this.abundance = abundance;
        this.freqRatio = freqRatio;
    }

    /**
     * Return if the nuclei is a spin 1/2 nuclei
     *
     * @return true if spin 1/2
     */
    public boolean isSpinOneHalf() {
        return spin == 1;
    }

    /**
     * Get the nucleus name and isotope number (like C13)
     *
     * @return a name-number string
     */
    public String getNameNumber() {
        return name + num;
    }

    /**
     * Get the nucleus number and name (like 13C)
     *
     * @return a number - name string
     */
    public String getNumberName() {
        return num + name;
    }

    /**
     * Return the nucleus name
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Return the nucleus number
     *
     * @return the number
     */
    public String getNumber() {
        return String.valueOf(num);
    }

    /**
     * Return the frequency ratio. Scale with H=1.0;
     *
     * @return the ratio
     */
    public double getFreqRatio() {
        return freqRatio;
    }

    /**
     * Return Unicode string for the isotope number in superscript format
     *
     * @return Unicode superscript string
     */
    public String getSuper() {
        StringBuilder nucNumber = new StringBuilder();

        String numberString = getNumber();
        int sLen = numberString.length();
        for (int i = 0; i < sLen; i++) {
            switch (numberString.charAt(i)) {
                case '0':
                    nucNumber.append('\u2070');
                    break;
                case '1':
                    nucNumber.append('\u00b9');
                    break;
                case '2':
                    nucNumber.append('\u00b2');
                    break;
                case '3':
                    nucNumber.append('\u00b3');
                    break;
                case '4':
                    nucNumber.append('\u2074');
                    break;
                case '5':
                    nucNumber.append('\u2075');
                    break;
                case '6':
                    nucNumber.append('\u2076');
                    break;
                case '7':
                    nucNumber.append('\u2077');
                    break;
                case '8':
                    nucNumber.append('\u2078');
                    break;
                case '9':
                    nucNumber.append('\u2079');
                    break;
                default:
            }
        }

        return nucNumber.toString();
    }

    @Override
    public String toString() {
        return getSuper() + getName();
    }

    /**
     * Return nuclei object that matches the test string. Will find matches for
     * formats like 13C, C13 and C.
     *
     * @param test name of nucleus to test
     * @return Nuclei object that matches the test string.
     */
    public static Nuclei findNuclei(final String test) {
        Nuclei result = H1;
        for (Nuclei nucleus : values()) {
            if (nucleus.getNameNumber().equals(test)) {
                result = nucleus;
                break;
            } else if (nucleus.getNumberName().equals(test)) {
                result = nucleus;
                break;
            } else if (nucleus.getName().equals(test)) {
                result = nucleus;
                break;
            }
        }

        return result;
    }

    /**
     * Return an array of nuclei that matches the array of frequencies, assuming
     * that the highest frequency is 1H
     *
     * @param frequencies array of frequencies to test
     * @return array of Nuclei that match frequencies.
     */
    public static Nuclei[] findNuclei(final double[] frequencies) {
        Nuclei[] result = new Nuclei[frequencies.length];
        double max = Double.NEGATIVE_INFINITY;
        for (double freq : frequencies) {
            if (freq > max) {
                max = freq;
            }
        }
        int iFreq = 0;
        for (double freq : frequencies) {
            double ratio = freq / max;
            double min = Double.MAX_VALUE;
            Nuclei matchNucleus = null;
            for (Nuclei nucleus : values()) {
                double delta = Math.abs(ratio - nucleus.freqRatio);
                if (delta < min) {
                    min = delta;
                    matchNucleus = nucleus;
                }
            }
            result[iFreq++] = matchNucleus;
        }
        return result;
    }
}
