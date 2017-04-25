import os
import sys

version = sys.argv[1]
dcchem_location = '../../target/dcchem-{}-bin/dcchem-{}/dcchem'.format(version, version)

#this is a Python script that starts the NVJP jython interpreter to run the test
#modules

#script = "runtests"

#return_value = os.system('%s %s' % (dcchem_location, script))

#if return_value != 0:
    #import warnings
    #warnings.warn("Test(s) failed.")
#    raise Exception("Test(s) failed.")
