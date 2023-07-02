'''
Sample test file for Vec.  Also contains a wrapper for the Vec class which can 
be included by other tests.

import unittest at the beginning of the test file.

create a class that inherits from unittest.TestCase
all methods of that class that start with test will be called by unittest.main()

at the end include the lines:
if __name__ == '__main__':
    unittest.main()

'''
import unittest
import pyproc
import org.apache.commons.math3.complex.Complex as ApacheComplex
    
class Vec(pyproc.Vec):
    '''Convenience wrapper for a Vec.'''  
    def __getitem__(self, index):
        if (type(index)==slice):
            return self.getList()[index]
        '''Overloading brackets so we can index a Vec like vector[35]'''
        if self.isComplex():
            c = self.getComplex(index)
            return complex(c.getReal(), c.getImaginary())
        else:
            return self.get(index)

    def __len__(self):
        return self.getSize()

    def __repr__(self):
        return str(self.getList())

class VecTestCase(unittest.TestCase):
    '''Test Case for checking equality of Vecs.'''
    def assertVecEqual(self, v1, v2):
        v1_list = v1.getList()
        v2_list = v2.getList()

        for (v1_val, v2_val) in zip(v1_list, v2_list):
            self.assertAlmostEqual(v1_val.real, v2_val.real)
            self.assertAlmostEqual(v1_val.imag, v2_val.imag)

class TestVec(VecTestCase):
    def testAddOp(self):
        v = Vec(32, True)
        v.rand()

        y1 = v[0]
        pyproc.ADD(value=1.0+1.0j, vector=v)
        y2 = v[0]
        self.assertAlmostEqual(y2.real, (y1.real + 1.0))
        self.assertAlmostEqual(y2.imag, (y1.imag + 1.0))

    def testAdd1(self):
        '''test add(int i, double v)'''
        vec = Vec(3, False)
        vec.ones()
        vec.add(1, 3.0)
        #self.assertEqual(vec[0], 1)
        #self.assertEqual(vec[1], 4.0)
        #self.assertEqual(vec[2], 1)

    def testAdd2(self):
        '''test add(Vec v2)'''
        vec1 = Vec(1024, True)
        vec2 = Vec(1024, True)
        vec1.ones()
        vec2.rand()

        #self.assertVecEqual(vec1, vec2)


    def testadjustRef(self):
        pass

    def testApache_fft1(self):
        '''test apache_fft(boolean negate)'''
        pass

    def testApache_fft2(self):
        '''test apache_fft(Complex[] ftvec)'''
        pass

    def testAutoPhase(self):
        pass

    def testAutoPhaseByMax(self):
        pass

    def testCexpand(self):
        pass

    def testCheckPowerOf2(self):
        pass

    def testConjugate(self):
        pass

    def testConvertFloat(self):
        pass

    def testCopy1(self):
        pass

    def testCopy2(self):
        pass

    def testCopy3(self):
        pass

    def testCopy4(self):
        pass

    def testCopyRef(self):
        pass

    def testCorrectVec(self):
        pass

    def testCorrectVecSine(self):
        pass

    def testEaCombine(self):
        pass

    def testEsmooth(self):
        pass

    def testExpandIvec(self):
        pass

    def testExpandRvec(self):
        pass

    def testExtendWithPrediction(self):
        pass

    def testFindBrukerInitialPoint(self):
        pass

    def testFitSine(self):
        pass

    def testFixGroupDelay(self):
        pass

    def testFixWithBrukerFilter1(self):
        '''test fixWithBrukerFilter(double amp, double phase)'''
        pass

    def testFixWithBrukerFilter2(self):
        '''test fixWithBrukerFilter()'''
        pass

    def testFixWithPhasedHFT1(self):
        '''test fixWithPhasedHFT(double phase)'''
        pass

    def testFixWithPhasedHFT2(self):
        '''test fixWithPhasedHFT()'''
        pass

    def testFixWithShifted(self):
        pass

    def testFreqDomain(self):
        pass

    def testFT(self):
        pass

    def testGapSmooth(self):
        pass

    def testGenNoise(self):
        pass

    def testGenSignal(self):
        pass

    def testGet1(self):
        '''test get(int i)'''
        pass

    def testGet2(self):
        '''test get(int i, boolean imag)'''
        pass

    def testGetBytes(self):
        pass

    def testGetCoefsByTLS(self):
        pass

    def testGetComplex(self):
        pass

    def testGetCvec(self):
        pass

    def testGetDoublePosition1(self):
        '''test getDoublePosition(Frequency freq)'''
        pass

    def testGetDoublePosition2(self):
        '''test getDoublePosition(PPM ppm)'''
        pass

    def testGetDoublePosition3(self):
        '''test getDoublePosition(Time time)'''
        pass

    def testGetDoublePosition4(self):
        '''test getDoublePosition(Index index)'''
        pass

    def testGetDoublePosition5(self):
        '''test getDoublePosition(Point point)'''
        pass

    def testGetDoublePosition6(self):
        '''test getDoublePosition(Fraction frac)'''
        pass

    def testGetFreqDomain(self):
        pass

    def testGroupDelay(self):
        pass

    def testGetImag(self):
        pass

    def testGetIvec(self):
        pass

    def testGetLPPseudoInverse(self):
        pass

    def testGetNorm(self):
        pass

    def testGetPH0(self):
        pass

    def testGetPH1(self):
        pass

    def testGetPseudoInverse1(self):
        '''getPseudoInverse(FieldMatrix<Complex> A)'''
        pass

    def testGetPseudoInverse2(self):
        '''getPseudoInverse(Zmat A)'''
        pass

    def testGetPt(self):
        pass

    def testGetReal(self):
        pass

    def testGetRvec(self):
        pass

    def testGetSize(self):
        pass

    def testGetStart(self):
        pass

    def testGetString(self):
        pass

    def testHcCombine(self):
        pass

    def testHft(self):
        pass

    def testIft(self):
        pass

    def testImag(self):
        pass

    def testImagMultiply(self):
        pass

    def testInsertWithPrediction(self):
        pass

    def testIsComplex(self):
        pass

    def testIsReal(self):
        pass

    def testMakeApache(self):
        pass

    def testMakeComplex(self):
        pass

    def testMakeNotApache(self):
        pass

    def testMakeReal(self):
        pass

    def testMaxIndex1(self):
        '''test maxIndex(int first, int last)'''
        pass

    def testMaxIndex2(self):
        '''test maxIndex(first, int last)'''
        pass
    
    def testMult(self):
        v = Vec(32, False)
        v.ones()

        y1 = v.getList()
        multiplicand = 10.0
        pyproc.MULT(value=multiplicand, vector=v)
        y2 = v.getList()

        for pair in zip(y1, y2):
            self.assertEqual(multiplicand*pair[0], pair[1])

        v2 = Vec(32, True)
        v2.ones()

        multiplicand = -3.0 + 3.5j
        complex_multiplicand = \
            ApacheComplex(multiplicand.real, multiplicand.imag)

        v2.rand()

        v2_before = v2.getApacheComplexList()
        pyproc.MULT(value=multiplicand, vector=v2)
        v2_after = v2.getApacheComplexList()

        #using Apache Complex multiplication, assert multiplication of Complex
        # objects is correct
        for pair in zip(v2_before, v2_after):
            self.assert_(ApacheComplex.equals(
                pair[0].multiply(complex_multiplicand),
                pair[1]))

    def testOnes(self):
        v = Vec(256, True)
        v.ones()

        v_list = v.getApacheComplexList()
        for c in v_list:
            self.assert_(ApacheComplex.equals(c, ApacheComplex.ONE))

    def testPower(self):
        vec = Vec(99, True)
        for i in range(1, 100):
            vec.set(i-1, i, 1.0/i)

        vec.power()

        for i, v in enumerate(vec.getList()):
            self.assertAlmostEqual(v, (i+1)**2 + 1.0/((i+1)**2))

    def testRand(self):
        '''Assert that a Random Vec has elements between 0 and 1, inclusive'''
        v = Vec(1024, True)
        v.rand()
        v_list = v.getList()

        for c in v_list:
            self.assert_((lambda x: x.real >=0 and x.imag >= 0 and
                                   x.real <= 1 and x.imag <= 1)(c))

    def testResize(self):
        #FIXME test vector.pt on resize of real / complex Vecs
        import warnings
        import org.nmrfx.math.IllegalVecState as IllegalVecState

        v1 = Vec(1, False)
        self.assertEqual(v1.getSize(), 1) #size should be 1
        self.assert_(not v1.isComplex()) #should be real
        self.assert_(v1.useApache())
        self.assertEqual(len(v1.getRvec()), 1)

        v1.resize(1, True)
        self.assertEqual(v1.getSize(), 1)
        self.assert_(v1.isComplex())
        self.assert_(v1.useApache())
        self.assertRaises(IllegalVecState, v1.getRvec)
        self.assertRaises(IllegalVecState, v1.getIvec)

        v1.resize(100, False)
        self.assertEqual(v1.getSize(), 100)
        self.assert_(not v1.isComplex())
        self.assert_(v1.useApache())
        self.assertEqual(len(v1.getRvec()), 100)

        v1.resize(100, True)
        self.assert_(v1.useApache())
        self.assertEqual(len(v1.getCvec()), 100)
        v1.makeNotApache();
        self.assert_(not v1.useApache())
        self.assertEqual(len(v1.getRvec()), 100)
        self.assertEqual(len(v1.getIvec()), 100)
        v1.resize(150)
        self.assert_(not v1.useApache())
        self.assertEqual(v1.getSize(), 150)
        self.assert_(v1.isComplex())
        self.assertEqual(len(v1.getRvec()), 150)
        self.assertEqual(len(v1.getIvec()), 150)

        size = 1024
        newsize = 512
        v2 = Vec(size, True)
        self.assertEqual(len(v2.getCvec()), 1024)
        v2.makeNotApache()
        self.assertEqual(len(v2.getRvec()), 1024)
        self.assertEqual(len(v2.getIvec()), 1024)
        v2.resize(newsize)
        self.assertEqual(v2.getSize(), 512)
        self.assertEqual(len(v2.getRvec()), 1024)
        self.assertEqual(len(v2.getIvec()), 1024)
        self.assertRaises(IllegalVecState, v2.getCvec)

        v3 = Vec(size, True)
        v3.makeNotApache()
        for i in range(size):
            v3.set(i, float(i), float(i+1))
        v3.resize(newsize)

        for index, value in enumerate(v3.getList()):
            self.assertEqual(value.real, float(index))
            self.assertEqual(value.imag, float(index+1))

        v4 = Vec(size, True)
        v4.makeNotApache()
        for i in range(size):
            v4.set(i, float(i), float(i+1))
        v4.resize(newsize)

        for index, value in enumerate(v4.getList()):
            self.assertEqual(value.real, float(index))
            self.assertEqual(value.imag, float(index+1))

if __name__ == '__main__':
    testProgram = unittest.main(exit=False)
    result = testProgram.result

