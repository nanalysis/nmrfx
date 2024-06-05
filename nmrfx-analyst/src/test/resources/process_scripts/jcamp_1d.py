import os
from pyproc import *
FIDHOME, TMPHOME = getTestLocations()
FID(os.path.join(FIDHOME,'jcamp/TESTFID.DX'))
CREATE(os.path.join(TMPHOME,'tst_jcamp_1d.nv'))

acqOrder()
skip(0)
label('1H')
sf('')
sw('')
printInfo()
ref('h2o')
DIM(1)
SB()
ZF()
FT()
PHASE(ph0=50,ph1=0.0)
run()
