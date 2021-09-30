import os
import sys

version = sys.argv[1]
nvjp_location = '../../target/dcengine-{}-bin/dcengine-{}/nvjp'.format(version, version)

#this is a Python script that starts the NVJP jython interpreter to run the test
#modules

script = "runtests.py"

return_value = os.system('%s %s' % (nvjp_location, script))

if return_value != 0:
    #import warnings
    #warnings.warn("Test(s) failed.")
    raise Exception("Test(s) failed.")
