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
package org.nmrfx.structure.chemistry;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;

import java.io.*;
import java.util.HashMap;
import java.util.StringTokenizer;

public class SVMPredict {

    HashMap<String, svm_model> models = new HashMap<String, svm_model>();

    public svm_model getModel(String atomName) {
        if (!models.containsKey(atomName)) {
            InputStream modelStream = this.getClass().getClassLoader().getSystemResourceAsStream("data/rnasvm/svr_in_" + atomName + ".txt.trim.model");
            BufferedReader modelReader = new BufferedReader(new InputStreamReader(modelStream));
            svm_model model;
            try {
                model = svm.svm_load_model(modelReader);
            } catch (IOException ioE) {
                return null;
            }
            models.put(atomName, model);
        }
        return models.get(atomName);
    }

    private static double atof(String s) {
        return Double.valueOf(s).doubleValue();
    }

    private static int atoi(String s) {
        return Integer.parseInt(s);
    }

    public double predict(String atomName, double[] attributes) throws IllegalArgumentException {
        int m = attributes.length;
        svm_node[] x = new svm_node[m];
        for (int j = 0; j < m; j++) {
            x[j] = new svm_node();
            x[j].index = j + 1;
            x[j].value = attributes[j];
        }
        svm_model model = getModel(atomName);
        if (model == null) {
            throw new IllegalArgumentException("No model for " + atomName);
        }
        double v = svm.svm_predict(model, x);
        return v;

    }

    private static void predict(BufferedReader input, DataOutputStream output, svm_model model) throws IOException {
        int total = 0;
        double error = 0;
        double sumv = 0, sumy = 0, sumvv = 0, sumyy = 0, sumvy = 0;

        while (true) {
            String line = input.readLine();
            if (line == null) {
                break;
            }

            StringTokenizer st = new StringTokenizer(line, " \t\n\r\f:");

            double target = atof(st.nextToken());
            int m = st.countTokens() / 2;
            svm_node[] x = new svm_node[m];
            for (int j = 0; j < m; j++) {
                x[j] = new svm_node();
                x[j].index = atoi(st.nextToken());
                x[j].value = atof(st.nextToken());
            }

            double v = svm.svm_predict(model, x);
            output.writeBytes(v + "\n");

            error += (v - target) * (v - target);
            sumv += v;
            sumy += target;
            sumvv += v * v;
            sumyy += target * target;
            sumvy += v * target;
            ++total;
        }
        System.out.println("Mean squared error = " + error / total + " (regression)\n");
        System.out.println("Squared correlation coefficient = "
                + ((total * sumvy - sumv * sumy) * (total * sumvy - sumv * sumy))
                / ((total * sumvv - sumv * sumv) * (total * sumyy - sumy * sumy))
                + " (regression)\n");
    }

    public static void main(String argv[]) throws IOException {
        InputStream modelStream = ClassLoader.getSystemResourceAsStream("svr_in_AH8.txt.trim.model");
        if (modelStream != null) {
            try (BufferedReader input = new BufferedReader(new FileReader("filein.txt"));
                 BufferedReader modelReader = new BufferedReader(new InputStreamReader(modelStream));
                 DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream("fileout.txt")))) {
                svm_model model = svm.svm_load_model(modelReader);
                predict(input, output, model);
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }
}
