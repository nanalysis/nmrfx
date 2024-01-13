import os
from pyproc import *
procOpts(nprocess=7)

FIDHOME, TMPHOME = getTestLocations()
FID(os.path.join(FIDHOME,'rs2d/1Dproton/680/data.dat'))
CREATE(os.path.join(TMPHOME,'tst_rs2d_1dproton/Proc/1/data.dat'))

acqOrder('1')
acqarray(0)
fixdsp(True)
skip(0)
label('1H')
acqsize(0)
tdsize(0)
sf('BASE_FREQ_1')
sw('SPECTRAL_WIDTH')
ref('')
DIM(1)
EXPD(lb=0.5)
ZF()
FT()
PHASE(ph0=43.6,ph1=158.0)
run()
