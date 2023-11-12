from pyproc import *

FIDHOME, TMPHOME = getTestLocations()
FID(os.path.join(FIDHOME,'agilent/hnco3d.fid'))
CREATE(os.path.join(TMPHOME,'tst_ubiq_hnco_lp.nv'))

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
ZF(factor=0)
FT()
PHASE(-40, 0)
EXTRACT(0,399,mode='region')
REAL()

DIM(2)
SB(end=0.95, power=2.0, c=0.5, offset=0.5)
ZF()
FT()
PHASE(-8, 17)
REAL()

DIM(3)
LP(ncoef=10,predictEnd=63)
SB(end=0.98, power=2.0, c=0.5, offset=0.5)
ZF()
FT()
PHASE(0, 0)
REAL()

UNDODIM(2)

DIM(2)
LP(ncoef=10,predictEnd=63)
SB(end=0.98, power=1.0, c=0.5, offset=0.5)
ZF()
FT()
PHASE(-8, 17)

run()
