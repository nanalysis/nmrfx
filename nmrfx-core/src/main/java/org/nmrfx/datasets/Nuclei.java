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

import org.nmrfx.annotations.PluginAPI;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Representation of different NMR active nuclei.
 *
 * @author brucejohnson
 */
@PluginAPI("ring")
public class Nuclei {
    private static final Map<String, Nuclei> nameNumberMap = new HashMap<>();
    private static final Map<String, Nuclei> numberNameMap = new HashMap<>();
    public static final Nuclei H1;
    public static final Nuclei C13;
    public static final Nuclei N15;

    static {
        loadNuclei();
        H1 = numberNameMap.get("1H");
        C13 = numberNameMap.get("13C");
        N15 = numberNameMap.get("15N");
    }

    final String name;
    final int num;
    final String spin;
    final double abundance;
    final double freqRatio;

    final double ratio;

    final boolean standard;

    final double ratioAcq;

    private Nuclei(final String name, final int num, final String spin, final double abundance,
                   final double freqRatio, double ratio, double ratioAcq) {
        this.name = name;
        this.num = num;
        this.spin = spin;
        this.abundance = abundance;
        this.freqRatio = freqRatio;
        this.ratio = ratio;
        this.ratioAcq = ratioAcq;
        boolean isStandard = true;
        if ((name.equals("N") && (num != 15)) || (name.equals("H") && (num != 1))) {
            isStandard = false;
        }
        standard = isStandard;
    }

    public static void loadNuclei() {
        ClassLoader cl = ClassLoader.getSystemClassLoader();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(cl.getResourceAsStream("nuclei_table.txt")))) {
            String line;
            boolean gotHeader = false;
            while ((line = reader.readLine()) != null) {
                if (!gotHeader) {
                    gotHeader = true;
                    continue;
                }
                String[] fields = line.trim().split("\t");
                int number = Integer.parseInt(fields[0]);
                String name = fields[1];
                String spin = fields[2];
                double abundance = Double.parseDouble(fields[3]);
                double f = Double.parseDouble(fields[4]);
                double ratio = Double.parseDouble(fields[5]);
                double ratioAcq = Double.parseDouble(fields[6]);
                Nuclei nuclei = new Nuclei(name, number, spin, abundance, f, ratio, ratioAcq);
                String numberName = number + name;
                String nameNumber = name + number;
                numberNameMap.put(numberName, nuclei);
                nameNumberMap.put(nameNumber, nuclei);
            }
        } catch (IOException ioE) {

        }

    }

    /**
     * Return if the nuclei is a spin 1/2 nuclei
     *
     * @return true if spin 1/2
     */
    public boolean isSpinOneHalf() {
        return Objects.equals(spin, "1/2");
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
     * Return the chemical shift reference ratio with TMS in CDCl3 = 0.0. Scale with H=100.0;
     *
     * @return the ratio
     */
    public double getRatio() {
        return ratio;
    }

    /**
     * Return the chemical shift reference ratio with DSS in H2O = 0.0. Scale with H=100.0;
     *
     * @return the ratio
     */
    public double getRatioAcqueous() {
        return ratioAcq;
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
                case '0' -> nucNumber.append('\u2070');
                case '1' -> nucNumber.append('\u00b9');
                case '2' -> nucNumber.append('\u00b2');
                case '3' -> nucNumber.append('\u00b3');
                case '4' -> nucNumber.append('\u2074');
                case '5' -> nucNumber.append('\u2075');
                case '6' -> nucNumber.append('\u2076');
                case '7' -> nucNumber.append('\u2077');
                case '8' -> nucNumber.append('\u2078');
                case '9' -> nucNumber.append('\u2079');
                default -> {
                }
            }
        }

        return nucNumber.toString();
    }

    @Override
    public String toString() {
        return getSuper() + getName();
    }

    public String toLatexString() {
        return "^{" + getNumber() + "}" + getName();
    }

    /**
     * Return nuclei object that matches the test string. Will find matches for
     * formats like 13C, C13 and C.
     *
     * @param test name of nucleus to test
     * @return Nuclei object that matches the test string.
     */
    public static Nuclei findNuclei(final String test) {
        Nuclei result = nameNumberMap.get(test);
        if (result == null) {
            result = numberNameMap.get(test);
            if (result == null) {
                var foundNuc = nameNumberMap.values().stream()
                        .filter(n -> test.equals(n.getName()))
                        .filter(n -> n.standard)
                        .findFirst();
                result = foundNuc.orElse(H1);
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
            for (Nuclei nucleus : nameNumberMap.values()) {
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

    public static Optional<Nuclei> findNucleusInLabel(String labelTest) {
        var foundNuc = nameNumberMap.values().stream().filter(n -> labelTest.contains(n.getNameNumber())).findFirst();
        if (foundNuc.isPresent()) {
            return foundNuc;
        }
        foundNuc = numberNameMap.values().stream().filter(n -> labelTest.contains(n.getNumberName())).findFirst();
        if (foundNuc.isPresent()) {
            return foundNuc;
        }
        foundNuc = nameNumberMap.values().stream()
                .filter(n -> labelTest.contains(n.getName()))
                .filter(n -> n.standard)
                .findFirst();

        return foundNuc;
    }
}
