import unittest
import pyproc
import org.apache.commons.math3.complex.Complex as ApacheComplex
from testvec import Vec

class TestOperations(unittest.TestCase):
    '''This class tests the raw Java Operations.  The pyproc operations should be tested as well and the results of each test should probably compared to the results of these tests.'''
    '''

    def testIstCL(self):
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
