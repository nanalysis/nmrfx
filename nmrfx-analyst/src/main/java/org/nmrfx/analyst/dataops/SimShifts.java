package org.nmrfx.analyst.dataops;

import org.ejml.data.BMatrixRMaj;
import org.ejml.data.Complex_F64;
import org.ejml.data.DMatrixRMaj;
import org.ejml.data.ZMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.CommonOps_ZDRM;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.EigenDecomposition_F64;
import org.ejml.simple.SimpleMatrix;
import org.nmrfx.processor.math.Vec;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.nmrfx.analyst.dataops.KronProduct.kronProd;

/**
 * @author brucejohnson
 */
public class SimShifts {

    DMatrixRMaj ham;
    DMatrixRMaj state;
    DMatrixRMaj obs;
    double[][] matrix;
    double field;
    List<Double> ppms = new ArrayList<>();
    List<Double> intensities = new ArrayList<>();

    public SimShifts(double[][] matrix, double field) {
        this.matrix = matrix;
        this.field = field;
    }

    public SimShifts(double[] shifts, double[] couplings, int[] pairs, double field) {
        int n = shifts.length;
        matrix = new double[n][n];
        this.field = field;
        for (int i = 0; i < n; i++) {
            matrix[i][i] = shifts[i];
        }
        for (int i = 0; i < pairs.length; i += 2) {
            int r = pairs[i];
            int c = pairs[i + 1];
            double coupling = couplings[i / 2];
            matrix[r][c] = coupling;
            matrix[c][r] = coupling;
        }
    }

    public List<Double> getPPMs() {
        return ppms;
    }

    public List<Double> getIntensiteis() {
        return intensities;
    }

    public void diag() {
        int nSpins = matrix.length;
        DMatrixRMaj shifts = new DMatrixRMaj(nSpins, nSpins);
        DMatrixRMaj couplings = new DMatrixRMaj(nSpins, nSpins);
        for (int i = 0; i < nSpins; i++) {
            shifts.set(i, i, matrix[i][i] * field);
        }
        for (int i = 0; i < nSpins - 1; i++) {
            for (int j = i + 1; j < nSpins; j++) {
                couplings.set(i, j, matrix[i][j]);
            }
        }
        getMatrices(shifts, couplings);
        EigenDecomposition_F64 eigDec = DecompositionFactory_DDRM.eig(ham.getNumCols(), true);
        eigDec.decompose(ham);
        int nEig = eigDec.getNumberOfEigenvalues();
        DMatrixRMaj eValues = new DMatrixRMaj(1, nEig);
        DMatrixRMaj eVecs = new DMatrixRMaj(nEig, nEig);
        for (int i = 0; i < nEig; i++) {
            Complex_F64 eVal = eigDec.getEigenvalue(i);
            eValues.set(i, eVal.getReal());
        }
        int[] indices = sortEig(eValues);
        for (int i = 0; i < nEig; i++) {
            int k = indices[i];
            eValues.set(i, eigDec.getEigenvalue(k).getReal());

            DMatrixRMaj vec = (DMatrixRMaj) eigDec.getEigenVector(k);
            for (int j = 0; j < eVecs.getNumRows(); j++) {
                eVecs.set(j, i, vec.get(j));
            }
        }

        double max = CommonOps_DDRM.elementMax(eVecs);
        double threshold = max * 0.01;
        absThreshold(eVecs, threshold);
        SimpleMatrix vS = SimpleMatrix.wrap(eVecs);
        SimpleMatrix obsS = SimpleMatrix.wrap(obs);
        SimpleMatrix stateS = SimpleMatrix.wrap(state);
        SimpleMatrix arS = vS.transpose().mult(obsS).mult(vS).elementMult(vS.transpose().mult(stateS).mult(vS));
        max = CommonOps_DDRM.elementMax(arS.getDDRM());
        threshold = max * 0.01;
        absThreshold(arS.getDDRM(), threshold);

        BMatrixRMaj bmat = CommonOps_DDRM.elementMoreThan(arS.getDDRM(), 1.0e-6, null);
        int nR = arS.getDDRM().getNumRows();
        int nC = arS.getDDRM().getNumCols();
        Double maxValue = Double.NEGATIVE_INFINITY;
        ppms.clear();
        intensities.clear();

        for (int i = 0; i < nR; i++) {
            for (int j = 0; j < nC; j++) {
                double v = arS.get(i, j);
                if (Math.abs(v) > 1.0e-6) {
                    double sX = eValues.get(i);
                    double sY = eValues.get(j);
                    double ppm = Math.abs(sY - sX) / field;
                    ppms.add(ppm);
                    intensities.add(v);
                    maxValue = Math.max(v, maxValue);
                }
            }
        }
        for (int i = 0; i < intensities.size(); i++) {
            intensities.set(i, intensities.get(i) / maxValue);
        }
    }

    public void makeSpec(Vec vec) {
        int n = intensities.size();
        for (int i = 0; i < n; i++) {
            double ppm = ppms.get(i);
            double intensity = intensities.get(i);
            int pt = vec.refToPt(ppm);
            if ((pt >= 0) && (pt < vec.getSize())) {
                vec.add(pt, intensity);
            }
        }
    }

    void absThreshold(DMatrixRMaj mat, double threshold) {
        int nElems = mat.getNumElements();
        for (int i = 0; i < nElems; i++) {
            if (Math.abs(mat.get(i)) < threshold) {
                mat.set(i, 0.0);
            }
        }
    }

    int[] sortEig(DMatrixRMaj eValues) {
        int[] sortedIndices = IntStream.range(0, eValues.getNumElements())
                .boxed().sorted((i, j) -> Double.compare(eValues.get(i), eValues.get(j)))
                .mapToInt(ele -> ele).toArray();
        return sortedIndices;
    }

    public void getMatrices(DMatrixRMaj shifts, DMatrixRMaj couplings) {

        DMatrixRMaj eMat = CommonOps_DDRM.identity(2, 2);
        DMatrixRMaj x = new DMatrixRMaj(2, 2);
        x.set(0, 1, 0.5);
        x.set(1, 0, 0.5);

        DMatrixRMaj z = new DMatrixRMaj(2, 2);
        z.set(0, 0, 0.5);
        z.set(1, 1, -0.5);

        ZMatrixRMaj y = new ZMatrixRMaj(2, 2);
        y.set(0, 1, 0.0, 0.5);  // y = i*(z*x-x*z)
        y.set(1, 0, 0.0, -0.5);

        ham = buildHamiltonian(x, y, z, shifts, couplings);
        state = buildState(shifts.getNumCols(), x);
        obs = buildOBservable(shifts.getNumCols(), y, state);
    }

    public DMatrixRMaj buildOBservable(int n, ZMatrixRMaj y, DMatrixRMaj state) {
        DMatrixRMaj obs = new DMatrixRMaj(state);
        int N = state.getNumCols();

        for (int i = 0; i < n; i++) {
            int m = (int) Math.pow(2, i);
            int m2 = (int) Math.pow(2, n - i - 1);
            DMatrixRMaj e1Mat = CommonOps_DDRM.identity(m, m);
            DMatrixRMaj e2Mat = CommonOps_DDRM.identity(m2, m2);
            ZMatrixRMaj cMatz = kronProd(kronProd(e1Mat, y), e2Mat);
            CommonOps_ZDRM.scale(0, 1.0, cMatz);
            DMatrixRMaj cMat = new DMatrixRMaj(N, N);
            CommonOps_ZDRM.stripReal(cMatz, cMat);
            CommonOps_DDRM.add(cMat, obs, obs);

        }
        return obs;
    }

    public DMatrixRMaj buildState(int n, DMatrixRMaj x) {
        int N = (int) Math.pow(2, n);
        DMatrixRMaj state = new DMatrixRMaj(N, N);
        for (int i = 0; i < n; i++) {
            int m = (int) Math.pow(2, i);
            int m2 = (int) Math.pow(2, n - i - 1);
            DMatrixRMaj e1Mat = CommonOps_DDRM.identity(m, m);
            DMatrixRMaj e2Mat = CommonOps_DDRM.identity(m2, m2);
            DMatrixRMaj cMat = kronProd(kronProd(e1Mat, x), e2Mat);
            CommonOps_DDRM.add(cMat, state, state);
        }
        return state;
    }

    public DMatrixRMaj buildHamiltonian(DMatrixRMaj x, ZMatrixRMaj y, DMatrixRMaj z, DMatrixRMaj shifts, DMatrixRMaj couplings) {
        int n = shifts.getNumCols();
        int N = (int) Math.pow(2, n);
        DMatrixRMaj ham = new DMatrixRMaj(N, N);
        DMatrixRMaj c1Mat = new DMatrixRMaj(z);
        for (int i = 0; i < n - 1; i++) {
            int m = (int) Math.pow(2, i);
            int m2 = (int) Math.pow(2, n - i - 1);
            DMatrixRMaj e1Mat = CommonOps_DDRM.identity(m, m);
            DMatrixRMaj e2Mat = CommonOps_DDRM.identity(m2, m2);
            DMatrixRMaj cMat = kronProd(kronProd(e1Mat, c1Mat), e2Mat);

            CommonOps_DDRM.add(ham, shifts.get(i, i), cMat, ham);
            for (int j = i + 1; j < n; j++) {
                double jValue = couplings.get(i, j);
                int sm1 = (int) Math.pow(2, i);
                int sm2 = (int) Math.pow(2, j - i - 1);
                int sm3 = (int) Math.pow(2, n - j - 1);
                DMatrixRMaj es1 = CommonOps_DDRM.identity(sm1, sm1);
                DMatrixRMaj es2 = CommonOps_DDRM.identity(sm2, sm2);
                DMatrixRMaj es3 = CommonOps_DDRM.identity(sm3, sm3);
                DMatrixRMaj cout1 = kronProd(kronProd(kronProd(kronProd(es1, x), es2), x), es3);
                ZMatrixRMaj cout2z = kronProd(kronProd(kronProd(kronProd(es1, y), es2), y), es3);
                DMatrixRMaj cout2 = new DMatrixRMaj(N, N);
                CommonOps_ZDRM.stripReal(cout2z, cout2);
                DMatrixRMaj cout3 = kronProd(kronProd(kronProd(kronProd(es1, z), es2), z), es3);
                CommonOps_DDRM.add(ham, jValue, cout1, ham);
                CommonOps_DDRM.add(ham, jValue, cout2, ham);
                CommonOps_DDRM.add(ham, jValue, cout3, ham);
            }
        }
        int sm1 = (int) Math.pow(2, n - 1);
        int sm2 = (int) Math.pow(2, 0);
        DMatrixRMaj es1 = CommonOps_DDRM.identity(sm1, sm1);
        DMatrixRMaj es2 = CommonOps_DDRM.identity(sm2, sm2);
        DMatrixRMaj cMat = kronProd(es1, z);
        CommonOps_DDRM.add(ham, shifts.get(n - 1, n - 1), cMat, ham);
        return ham;
    }

    void dump(DMatrixRMaj mat) {
        int nRows = mat.getNumRows();
        int nCols = mat.getNumCols();
        for (int j = 0; j < nCols; j++) {
            for (int i = 0; i < nRows; i++) {
                double v = mat.get(i, j);
                if (Math.abs(v) > 1.0e-8) {
                    System.out.printf("%3d %3d %7.4f\n", i + 1, j + 1, v);
                }
            }
        }
    }

    void dump(ZMatrixRMaj mat) {
        int nRows = mat.getNumRows();
        int nCols = mat.getNumCols();
        for (int j = 0; j < nCols; j++) {
            for (int i = 0; i < nRows; i++) {
                double vr = mat.getReal(i, j);
                double vi = mat.getImag(i, j);
                if ((Math.abs(vr) > 1.0e-8) || (Math.abs(vi) > 1.0e-8)) {
                    System.out.printf("%3d %3d %7.4f %7.4f\n", i + 1, j + 1, vr, vi);
                }
            }
        }
    }
}
