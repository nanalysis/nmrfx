import unittest
import pyproc
import math
import org.apache.commons.math3.complex.Complex as ApacheComplex
from testvec import Vec
from pyproc import getOperationList

def importOp(opName):
    exec '''from org.nmrfx.processor.operations import %s''' % opName
    exec '''globals()[\'%s\'] = %s''' % (opName, opName)


class TestOperations(unittest.TestCase):
    '''This class tests the raw Java Operations.  The pyproc operations should be tested as well and the results of each test should probably compared to the results of these tests.'''
    '''
    def testAllOperationsHaveTests(self):
        ops = getOperationList()
        ops_implemented = filter(lambda x: x in TestOperations.__dict__, ops)
        self.assert_(len(ops)
        pass'''

    def isAlmostEqualTo(self, values, testValue, nDigits=7):
        for val in values:
            self.assertAlmostEqual(testValue, val, nDigits)

    def dumpSpectrum(self, v):
        for i,v in enumerate(v.getList()):
            print i,v
        
    def genSpectrum(self,size=1024):
        importOp("Gen")
        importOp("Ft")
        v = Vec(size, True)
        sw = 1000.0
        dwell = 1.0/sw
        v.setDwellTime(dwell)
        freq = sw/4.0
        lw = 10.0
        amp = 100
        ph = 0.0
        op = Gen(freq, lw, amp, ph)
        op.eval(v)
        op = Ft(False,False)
        op.eval(v)
        return v

    
    def testAdd(self):
        importOp('Add')
        v = Vec(256, True)
        temp = v.getList()
        real, imag = 1, 3
        op = Add(real, imag, 0, -1)

        op.eval(v)
        for i,v in enumerate(v.getList()):
            self.assertAlmostEqual(v.real, temp[i].real + real)
            self.assertAlmostEqual(v.imag, temp[i].imag + imag)

    def testApodization(self):
        importOp('Apodization')

        class MultByLineApod(Apodization):
            def __init__(self, size):
                self.resize(size)
                self.setApodVec([float(i)/256 for i in range(256)])

            def eval(self, vector):
                self.applyApod(vector)
                return self

        v = Vec(256, True)
        v.ones()

        op = MultByLineApod(256)

        op.eval(v)
        temp = v.getList()
        for i, v in enumerate(temp):
            self.assertEqual(i/256.0, v)


    def testAsmooth(self):
        pass

    def testAutoPhase(self):
        pass

    def testBcMed(self):
        pass

    def testBcPoly(self):
        pass

    def testBcSine(self):
        pass

    def testBcwhit(self):
        pass

    def testBracketMinimizer(self):
        pass

    def testBucket(self):
        pass

    def testBz(self):
        pass
    
    def testCShift(self):
        pass

    def testCombine(self):
        pass

    def testCwtd(self):
        pass

    def testDc(self):
        pass

    def testDcfid(self):
        pass

    def testDx(self):
        v = Vec(1000, False)
        v = v.rand();

    def testEACombine(self):
        pass

    def testESmooth(self):
        pass

    def testExpd(self):
        importOp("Expd")
        from math import pi, exp
        v = Vec(1025, False)
        v.ones()
        dwell = 1.0/1000.0
        v.setDwellTime(dwell)

        lb = 2.0
        fPoint = 1.0
        op = Expd(lb, fPoint,False)
        op.eval(v)
        decay = pi * lb

        for index, val in enumerate(v.getList()):
            testVal = exp(-1.0*index * decay * dwell)
            self.assertAlmostEqual(val, testVal)

    def testExpdInverse(self):
        importOp("Expd")
        from math import pi, exp
        v = Vec(1025, False)
        v.ones()
        dwell = 1.0/1000.0
        v.setDwellTime(dwell)

        lb = 2.0
        fPoint = 1.0

        op = Expd(lb, fPoint,False)
        op.eval(v)
        iop = op.getInverseOp()
        iop.eval(v)
        self.isAlmostEqualTo(v.getList(), 1.0)

    def testExtend(self):
        pass

    def testExtendNew(self):
        pass

    def testExtract(self):
        pass

    def testFDSolventMinimizer(self):
        pass

    def testFdss(self):
        pass

    def testFt(self):
        pass

    def testFtInverse(self):
        importOp("Ft")
        importOp("Gen")
        size = 1024
        v = self.genSpectrum(size)
        vSave = Vec(size, True)
        v.copy(vSave)

        op = Ft(False,False)
        op.eval(v)
        iop = op.getInverseOp()
        iop.eval(v)
        v.subtract(vSave)
        self.isAlmostEqualTo(v.getList(), 0.0)

        vSave.copy(v)
        op = Ft(True,False)
        op.eval(v)
        iop = op.getInverseOp()
        iop.eval(v)
        v.subtract(vSave)
        self.isAlmostEqualTo(v.getList(), 0.0)

        vSave.copy(v)
        op = Ft(False,True)
        op.eval(v)
        iop = op.getInverseOp()
        iop.eval(v)
        v.subtract(vSave)
        self.isAlmostEqualTo(v.getList(), 0.0)

    def testGapSmooth(self):
        pass

    def testGen(self):
        importOp("Gen")
        size = 1024
        v = Vec(size, True)
        sw = 1000.0
        dwell = 1.0/sw
        v.setDwellTime(dwell)
        freq = sw/4.0
        lw = 10.0
        amp = 100
        ph = 0.0
        op = Gen(freq, lw, amp, ph)
        op.eval(v)
        values = v.getList()
        # check amplitude is amp
        self.assertAlmostEqual(values[0],amp)

        v.zeros()
        op = Gen(freq, lw, amp, 90.0)
        op.eval(v)
        values = v.getList()
        # check imaginary  amplitude is amp when phase is 90
        self.assertAlmostEqual(values[0].imag,amp)

        v.zeros()
        op = Gen(freq, lw, amp, 0.0)
        op.eval(v)
        v.ft()
        peakCenter = size*3/4
        values = v.getList()
        # check if expected peak position is max
        self.assert_(v[peakCenter-1].real < v[peakCenter].real)
        self.assert_(v[peakCenter+1].real < v[peakCenter].real)
        # check if peak is symmetrical
        self.assertAlmostEqual(values[peakCenter-2].real,values[peakCenter+2].real)
        
        v.zeros()
        op = Gen(freq, lw, amp, 0.0)
        op.eval(v)
        op.eval(v)
        values = v.getList()
        # check if additive (successive calls don't zero first)
        self.assertAlmostEqual(values[0].real,2.0*amp)
 

    def testGf(self):
        importOp("Gf")
        from math import pi, exp
        v = Vec(1025, False)
        v.ones()
        dwell = 1.0/1000.0
        v.setDwellTime(dwell)

        gf = 0.5
        gfs = 0.1
        fPoint = 1.0

        op = Gf(gf, gfs, fPoint)
        op.eval(v)

    def testGfInverse(self):
        importOp("Gf")
        from math import pi, exp
        v = Vec(1025, False)
        v.ones()
        dwell = 1.0/1000.0
        v.setDwellTime(dwell)

        gf = 0.5
        gfs = 0.1
        fPoint = 1.0

        op = Gf(gf, gfs, fPoint)
        op.eval(v)
        iop = op.getInverseOp()
        iop.eval(v)

        self.isAlmostEqualTo(v.getList(), 1.0)

    def testGmb(self):
        pass

    def testGmbInverse(self):
        importOp("Gmb")
        from math import pi, exp
        v = Vec(1025, False)
        v.ones()
        dwell = 1.0/1000.0
        v.setDwellTime(dwell)

        gb = 0.1
        lb = 2.0
        fPoint = 1.0

        op = Gmb(gb, lb, fPoint)
        op.eval(v)
        iop = op.getInverseOp()
        iop.eval(v)

        self.isAlmostEqualTo(v.getList(), 1.0)

    def testHft(self):
        importOp("Hft")
        size = 1024
        v = self.genSpectrum(size)
        vSave = Vec(size, True)
        v.copy(vSave)
        v.makeReal()

        op = Hft()
        op.eval(v)
        v.subtract(vSave)
        # fixme  Hft generates some wiggle in baseline which requires relatively large tolerance here
        self.isAlmostEqualTo(v.getList(), 0.0, 3)


    def testIDBaseline2(self):
        pass

    def testIO(self):
        pass

    def testIft(self):
        importOp("Ft")
        importOp("Ift")
        importOp("Gen")
        size = 1024
        v = Vec(size, True)
        vSave = Vec(size, True)
        sw = 1000.0
        dwell = 1.0/sw
        v.setDwellTime(dwell)
        op = Gen(1000.0, 10.0, 100.0, 0.0)
        op.eval(v)
        v.copy(vSave)
        op = Ft(False,False)
        op.eval(v)
        op = Ift()
        op.eval(v)
        v.subtract(vSave)
        self.isAlmostEqualTo(v.getList(), 0.0)

    def testImag(self):
        importOp("Imag")
        size = 8
        v = Vec(size, True)
        v.set(0,1.0,2.0)
        v.set(1,3.0,4.0)
        values = v.getList()
        op = Imag()
        op.eval(v)
        values = v.getList()
        self.assert_(not v.isComplex())
        self.assert_(values[0].real == 2.0)
        self.assert_(values[1].real == 4.0)

    def testIntegrate(self):
        pass

    def testIst(self):
        pass

    def offtestIstCL(self):
        importOp("IstCL")
        importOp("Ft")
        importOp("Add")
        import pyproc
        from java.util import ArrayList

        #testing single Vector FFT with JavaCL and comparing the result to
        #the vector.ft method
        vec = Vec(1024, True)
        self.assert_(vec.useApache())
        pyproc.GEN(vector=vec)
        vectors = ArrayList()
        vectors.add(vec)

        op = IstCL(1)
        op.initializeVectors(vectors)
        op.initializeBuffers()
        #op.onePass(vectors)
        op.parallelfft(False)
        op.copyBack(vectors, False)
        op.finish()

        vec2 = Vec(1024, True)
        pyproc.GEN(vector=vec2)

        op = Ft(False, False)
        op.eval(vec2)

        #vec2.ft()

        #testing fft
        for (v1, v2) in zip(vec.getList(), vec2.getList()):
            self.assertAlmostEqual(v1.real, v2.real, 12)
            self.assertAlmostEqual(v1.imag, v2.imag, 12)

        '''
        test norm
        '''
        vec1 = Vec(32, True)
        vec2 = Vec(32, True)

        add = Add(100.0, 100.0)
        add.eval(vec1)
        add.eval(vec2)

        set_max_index = 31

        vec1.set(set_max_index, 101.0, 101.0)
        vec2.set(set_max_index, 101.0, 101.0)

        op = IstCL(1)
        vectors = ArrayList()
        vectors.add(vec1)

        op.initializeVectors(vectors)
        op.initializeBuffers()
        op.declareComplexNorm()
        op.declareReduceMax()
        op.norm()
        norms = op.copyBackNormBuffer()
        op.reduceMax()
        m = op.copyBackMaxBuffer()
        index = op.copyBackMaxIndexBuffer()
        print index
        op.finish()

        vec2norm = [i.real**2+i.imag**2 for i in vec2.getList()]
        for v1, v2 in zip(norms, vec2norm):
            self.assertEqual(v1, v2)

        self.assertEqual(m, 101*101 + 101*101)
        self.assertAlmostEqual(index, set_max_index)

        #for (v1, v2) in zip(vec1.getList(), vec2.getList()):
        #    self.assertAlmostEqual(v1.real, v2.real, 12)
        #    self.assertAlmostEqual(v1.imag, v2.imag, 12)

        '''Test thresholding without fft'''
        vec1 = Vec(256, True)
        vec2 = Vec(256, True)

        vec1.ones()
        vec2.ones()

        vec1.multiply(100.0, 0)
        vec2.multiply(3.04, 0)
        #vec2.ft();
        #vec2.ift();
        #if we have a threshold of .96, then all values will be thresholded by
        #96.96 and will be reduced to 3.04
        vec1.setComplex(2, 101.0, 0.0)
        vec2.setComplex(2, 3.04, 0.0)

        op = IstCL(1)
        vectors = ArrayList()
        vectors.add(vec1)
        op.initializeVectors(vectors)
        op.initializeBuffers()
        op.norm()
        op.reduceMax()
        op.threshold()
        op.copyBack(vectors, True)

        op.finish()

        for (i, (v1, v2)) in enumerate(zip(vec1.getList(), vec2.getList())):
            print i
            self.assertAlmostEqual(v1.real, v2.real, 12)
            self.assertAlmostEqual(v1.imag, v2.imag, 12)

        '''
        import pyproc
        import os
        
        homeDir = os.getcwd()
        dataDir = os.environ['BRUKERTESTFIDDIR']

        serDir = dataDir + 'HMQC/4/'

        f = pyproc.FID(serDir)

        tdSizes = pyproc.getTdSizes()

        f.label = ['H1', 'C13']

        f.printInfo()

        nusSchedule = serDir + 'sample_schedule.txt'
        pyproc.SAMPLE_SCHEDULE(nusSchedule, demo=True, mode='create')

        pyproc.CREATE(serDir+'hmqc_ist_cl.nv')

        pyproc.DIM(1)
        pyproc.SB(end = 1.0, power = 2.0, offset = 0.3, apodSize = tdSizes[0])
        pyproc.ZF(factor = 1)
        pyproc.FT()
        pyproc.PHASE(202.2, 202.2)
        pyproc.REAL()
        pyproc.BCPOLY()

        pyproc.DIM(2)

        pyproc.SB(end = 1.0, power = 2.0, offset = 0.3)
        pyproc.ZF(factor=1)
        #pyproc.PRINT()
        pyproc.ISTCL(threshold=0.95, iterations=1, alg='phased', ph0=0, ph1=0, timeDomain=False, schedule=None)
        #pyproc.PRINT()
        pyproc.IFT()
        pyproc.FT(auto=True)
# PHASE(0.0, 0.0)
        pyproc.REVERSE()
        pyproc.REAL()
        pyproc.BCPOLY()

        pyproc.run()
        '''

    def testLoadVector(self):
        pass

    def testMag(self):
        importOp("Mag")
        size=1024
        v = self.genSpectrum(size=size)
        peakCenter = 3*size/4
        # offset so has some real and imaginary
        offset = 4
        rValue = v.getReal(peakCenter-offset)
        iValue = v.getImag(peakCenter-offset)
        mag = math.sqrt(rValue*rValue+iValue*iValue)

        op = Mag()
        op.eval(v)
        mValue = v.getReal(peakCenter-offset)
        self.assertAlmostEqual(mag, mValue, 7)

    def testPower(self):
        importOp("Power")
        size=1024
        v = self.genSpectrum(size=size)
        peakCenter = 3*size/4
        # offset so has some real and imaginary
        offset = 4
        rValue = v.getReal(peakCenter-offset)
        iValue = v.getImag(peakCenter-offset)
        mag = rValue*rValue+iValue*iValue

        op = Power()
        op.eval(v)
        mValue = v.getReal(peakCenter-offset)
        self.assertAlmostEqual(mag, mValue, 7)

    def testExp(self):
        importOp("Exp")
        size=1024
        v = self.genSpectrum(size=size)
        peakCenter = 3*size/4
        # offset so has some real and imaginary
        offset = 4
        value = v.getComplex(peakCenter-offset).exp()

        op = Exp()
        op.eval(v)
        eValue = v.getComplex(peakCenter-offset)
        self.assertAlmostEqual(value, eValue, 7)

    def testSqrt(self):
        importOp("Sqrt")
        size=1024
        v = self.genSpectrum(size=size)
        peakCenter = 3*size/4
        # offset so has some real and imaginary
        offset = 4
        value = v.getComplex(peakCenter-offset).sqrt()

        op = Sqrt()
        op.eval(v)
        eValue = v.getComplex(peakCenter-offset)
        self.assertAlmostEqual(value, eValue, 7)



    def testMult(self):
        pass

    def testOnes(self):
        importOp("Ones")
        v = Vec(32, False)
        op = Ones()
        op.eval(v)
        self.isAlmostEqualTo(v.getList(), 1.0, 9)
        v = Vec(32, True)
        op.eval(v)
        self.isAlmostEqualTo(v.getList(), 1.0, 9)

    def testOperation(self):
        importOp("Operation")
        class TestOperation(Operation):
            __slots__ = ['a', 'b', 'c']
            def __init__(self, a, b, c):
                self.a = a
                self.b = b
                self.c = c

            def eval(self, vector):
                if len(vector) > 3:
                    vector.set(0, self.a, 0)
                    vector.set(1, self.b, 0)
                    vector.set(2, self.c, 0)
                    vector.set(3, self.a*self.b, self.c)

        a, b, c = 2, 3, 4
        op = TestOperation(a, b, c)
        v = Vec(32, True)
        op.eval(v)

        self.assertEqual(v[0].real, a)
        self.assertEqual(v[1].real, b)
        self.assertEqual(v[2].real, c)
        self.assertEqual(v[3].real, a*b)
        self.assertEqual(v[3].imag, c)
                

    def testOperationException(self):
        pass

    def testPascalRow(self):
        pass

    def testPhase(self):
        importOp("Phase")
        size = 256
        v = self.genSpectrum(size)
        peakCenter = 3*size/4
        v1 = v.getReal(peakCenter)
        op = Phase(180.0,0.0)
        op.eval(v)
        v2 = v.getReal(peakCenter)
        self.assertAlmostEqual(v1, -v2, 7)

    def testPhaseInverse(self):
        importOp("Phase")
        size = 256
        v = self.genSpectrum(size)
        peakCenter = 3*size/4
        v1 = v.getComplex(peakCenter)
        op = Phase(73.0,42.0)
        op.eval(v)
        iop = op.getInverseOp()
        iop.eval(v)
        v2 = v.getComplex(peakCenter)
        self.assertAlmostEqual(v1.getReal(), v2.getReal(), 7)
        self.assertAlmostEqual(v1.getImaginary(), v2.getImaginary(), 7)

    def testPythonScript(self):
        pass

    def testRand(self):
        pass

    def testRandN(self):
        pass

    def testRange(self):
        pass

    def testReal(self):
        importOp("Real")
        size = 128
        v = self.genSpectrum(size)
        cValues = v.getList()

        op = Real()
        op.eval(v)
        rValues = v.getList()

        peakCenter = size*3/4
        self.assert_(not v.isComplex())
        self.assert_(cValues[peakCenter-2].real == rValues[peakCenter-2])
        self.assert_(cValues[peakCenter].real == rValues[peakCenter])

    def testRealInverse(self):
        importOp("Real")
        size = 1024
        v = self.genSpectrum(size)
        vSave = Vec(size, True)
        v.copy(vSave)

        op = Real()
        op.eval(v)
        iop = op.getInverseOp()
        iop.eval(v)
        v.subtract(vSave)
        # fixme  Hft generates some wiggle in baseline which requires relatively large tolerance here
        self.isAlmostEqualTo(v.getList(), 0.0, 3)


    def testReverse(self):
        importOp("Reverse")
        size = 64
        v = self.genSpectrum(size)
        op = Reverse()

    def testRft(self):
        pass

    def testShift(self):
        importOp("Shift")

        def test_case(size=10, shift=1, complex=False, useApache=False):
            v = Vec(10, complex)
            if (useApache):
                v.makeApache();

            v.ones()
            v.rand()

            temp = v.getList()

            shift = 3
            op = Shift(shift)
            op.eval(v)

            for index, val in enumerate(v.getList()):
                if index < shift:
                    self.assertEqual(val.real, 0)
                    if complex:
                        self.assertEqual(val.imag, 0)
                else:
                    self.assertEqual(val.real, temp[index-shift].real)
                    if complex:
                        self.assertEqual(val.imag, temp[index-shift].imag)

    def testSinebellApod(self): 
        importOp("SinebellApod")
        from math import pi, sin
        #from 0 to Pi inclusive
        v = Vec(1025, False)
        v.ones()

        #offset = 0.5
        offset = 0.0
        end = 1.0
        power = 1.0
        c = 1.0
        apodSize = 0

        temp = v.getList()
        op = SinebellApod(offset, end, power, c, apodSize, False)

        op.eval(v)

        for val, index in zip(
            [sin(i) for i in [pi*j/4 for j in range(5)]], 
            [0, 256, 512, 768, 1024]):
            self.assertAlmostEqual(val, v[index])

        offset = 0.5
        end = 1.5

        v = Vec(1025, False)
        v.ones()

        op = SinebellApod(offset, end, power, c, apodSize, False)

        op.eval(v)
        for val, index in zip(
            [sin(i) for i in [.5*pi + pi*j/4 for j in range(5)]], 
            [0, 256, 512, 768, 1024]):
            self.assertAlmostEqual(val, v[index])


    def testSinebellApodInverse(self): 
        v = Vec(1024, False)
        v.ones()
        offset = 0.5
        end = 0.98
        power = 1.0
        c = 1.0
        apodSize = 0
        op = SinebellApod(offset, end, power, c, apodSize, False)
        op.eval(v)
        iop = op.getInverseOp()
        iop.eval(v)
        self.isAlmostEqualTo(v.getList(), 1.0)
        
    def testSinebellApodInverseComplex(self): 
        v = Vec(1024, True)
        v.ones()
        offset = 0.5
        end = 0.98
        power = 1.0
        c = 1.0
        apodSize = 0
        op = SinebellApod(offset, end, power, c, apodSize, False)
        op.eval(v)
        iop = op.getInverseOp()
        iop.eval(v)
        self.isAlmostEqualTo(v.getList(), 1.0)

    def testTDPoly(self):
        pass

    def testTdss(self):
        pass

    def testTestBasePoints(self):
        pass

    def testTM(self):
        from org.nmrfx.processor.operations import Tm
        '''Test Trapezoid Multiply Operation'''
        def test_case(size, pt1, pt2):
            '''Pass in pt1, pt2, and vector size and verify that it is correct'''
            v = Vec(size, False)
            v.ones()
            op = Tm(pt1, pt2)
            op.eval(v)

            for i in range(0, pt1):
                self.assert_(v[i] < v[i+1])

            for i in range(pt1, pt2):
                self.assert_(v[i] == v[i+1])

            for i in range(pt2, size-1):
                self.assert_(v[i] > v[i+1])
            self.assertAlmostEqual(v[pt1],1.0)
            self.assertAlmostEqual(v[pt1/2],0.5,5)
            self.assertAlmostEqual(v[pt2],1.0)
            self.assertAlmostEqual(v[pt2+(size-pt2)/2],0.5,5)

        test_case(100, 2, 5)
        test_case(101, 24, 50)
        test_case(100, 50, 75)
        test_case(256, 64, 193)

    def testTri(self):
        from org.nmrfx.processor.operations import Tri
        op = Tri(0, 1.0, 1.0, False)

        v = Vec(1000, False)
        v.ones()
        op.eval(v)
        for i in range(v.getSize()):
            self.assertAlmostEqual(v[i], 1.0)
        self.assertEqual(v.getReal(0), 1.0)
        self.assertEqual(v.getReal(999), 1.0)

        op = Tri(500, -10.0, 10.0, False)
        v1 = Vec(1000, False)
        v1.ones()
        op.eval(v1)
        self.assertAlmostEqual(v1.getReal(0), -10.0)
        self.assertAlmostEqual(v1.getReal(999), 10.0)

        for i in range(v.getSize()-1):
            self.assert_(v[i] <= v[i+1])

        v2 = Vec(100, False)
        v2.ones()
        op = Tri(0, 0.0, 2.0, False)
        op.eval(v2)
        self.assertAlmostEqual(v2.getReal(0), 1.0)
        self.assertAlmostEqual(v2.getReal(49), 1.5, 1)
        self.assertAlmostEqual(v2.getReal(99), 2.0)

        for i in range(v.getSize() - 1):
            self.assert_(v[i] <= v[i+1])

    def testTriInverse(self):
        from org.nmrfx.processor.operations import Tri
        op = Tri(0, 1.0, 1.0, False)
        v = Vec(1000, False)
        v.ones()
        op.eval(v)
        iop = op.getInverseOp()
        iop.eval(v)
        self.isAlmostEqualTo(v.getList(), 1.0)

    def testUtil(self):
        pass

    def testWriteVector(self):
        pass

    def testZeros(self):
        importOp("Zeros")
        v = Vec(32, False)
        op = Zeros()

        op.eval(v)
        self.isAlmostEqualTo(v.getList(), 0.0, 9)

    def testZF(self):
        from org.nmrfx.processor.operations import Zf
        import math
        log2 = lambda x: int(math.log(x) / math.log(2))

        size = 32
        newSize = 63
        factor = None
        pad = None
        v = Vec(size, False)

        op = Zf(factor, newSize, pad)
        op.eval(v)

        self.assert_( len(v) == newSize)
        self.assert_(v.getSize() == newSize)

        size = 64
        v = Vec(size, False)
        newSize = None
        factor = 2
        pad = None

        op = Zf(factor, newSize, pad)
        op.eval(v)
        self.assert_(len(v) == 2**(2 + log2(size)))
        self.assert_(v.getSize() == 2**(2+log2(size)))

        size = 100
        v = Vec(size, False)
        newSize = None
        factor = None
        pad = 15

        op = Zf(factor, newSize, pad)
        op.eval(v)
        self.assert_(len(v) == size + pad)
        self.assert_(v.getSize() == size + pad)

    def testZFInverse(self):
        from org.nmrfx.processor.operations import Zf

        size = 31
        newSize = 64
        factor = None
        pad = None
        v = Vec(size, False)

        op = Zf(factor, newSize, pad)
        op.eval(v)
        size2 = v.getSize()
        self.assert_(newSize == size2)

        iop = op.getInverseOp()
        iop.eval(v)
        size3 = v.getSize()
        self.assert_(size == size3)


if __name__ == '__main__':
    testProgram = unittest.main(exit=False)
    result = testProgram.result

