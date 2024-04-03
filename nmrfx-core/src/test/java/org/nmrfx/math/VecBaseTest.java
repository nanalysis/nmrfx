package org.nmrfx.math;

import org.junit.Assert;
import org.junit.Test;

public class VecBaseTest {

    private VecBase getVec() {
        VecBase vec = new VecBase(32);
        vec.setSW(2000.0);
        vec.setSF(500.0);
        vec.setRefValue(5.0);
        return vec;
    }

    @Test
    public void pointToPPM() {
        VecBase vec = getVec();
        double refValue = vec.getRefValue();
        int midPt = vec.getSize() / 2;
        int size = vec.getSize();
        double widthPPM = vec.getSW() / vec.getSF();
        double calDelta = vec.pointToPPM(0) - vec.pointToPPM(vec.getSize());
        double leftValue = refValue + widthPPM / 2.0;
        double rightValue = refValue - widthPPM * (size / 2 - 1) / size;
        Assert.assertEquals(refValue, vec.pointToPPM(midPt), 1.0e-6);
        Assert.assertEquals(leftValue, vec.pointToPPM(0), 1.0e-6);
        Assert.assertEquals(rightValue, vec.pointToPPM(vec.getSize() - 1), 1.0e-6);
    }

    @Test
    public void refToPt() {
        VecBase vec = getVec();
        double refValue = vec.getRefValue();
        double widthPPM = vec.getSW() / vec.getSF();
        int size = vec.getSize();
        double leftValue = refValue + widthPPM / 2.0;
        double rightValue = refValue - widthPPM * (size / 2 - 1) / size;
        Assert.assertEquals(size / 2, vec.refToPt(refValue));
        Assert.assertEquals(0, vec.refToPt(leftValue));
        Assert.assertEquals(size - 1, vec.refToPt(rightValue));
    }

    @Test
    public void refToPtD() {
        VecBase vec = getVec();
        double refValue = vec.getRefValue();
        double widthPPM = vec.getSW() / vec.getSF();
        int size = vec.getSize();
        double leftValue = refValue + widthPPM / 2.0;
        double rightValue = refValue - widthPPM * (size / 2 - 1) / size;
        Assert.assertEquals(size / 2, vec.refToPtD(refValue), 1.0e-6);
        Assert.assertEquals(0, vec.refToPtD(leftValue), 1.0e-6);
        Assert.assertEquals(size - 1, vec.refToPtD(rightValue), 1.0e-6);
    }
}