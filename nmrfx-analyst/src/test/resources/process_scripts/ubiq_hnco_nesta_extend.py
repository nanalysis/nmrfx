from pyproc import *

FIDHOME, TMPHOME = getTestLocations()
FID(os.path.join(FIDHOME,'agilent/hnco3d.fid'))
CREATE(os.path.join(TMPHOME,'tst_ubiq_hnco_nesta_extend.nv'))

sw('sw','sw1','sw2')
sf('sfrq','dfrq','dfrq2')
ref(7.3168,'C','N')
label('HN','C','N')
printInfo()
acqOrder('12')
acqsize(0,0,0)

DIM(1)
TDCOMB(coef='echo-antiecho',dim=3)
DCFID()
SB(end=1.0, power=2.0, c=1.0, offset = 0.5)

ZF(size=512)
FT()
PHASE(-40, 0, dimag=True)
EXTRACT(0,399,mode='region')

DIM(2,3)
EXTEND(alg='nesta', factor=0, phase=[-8, 17, 0, 0])

DIM(2)
KAISER(beta=12.0, c=0.5)
ZF()
FT()
PHASE(0, 0, dimag=True)

DIM(3)
KAISER(beta=12.0, c=0.5)
ZF()
FT()
PHASE(0, 0, dimag=True)

run()
