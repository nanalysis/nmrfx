import unittest
import java.lang.RuntimeException
import sys

#all test files have to be loaded into the current namespace to be able to
# execute them with unittest.main(), so we have to do: "from x import *"

from testvec import *
from testoperations import *
from testpyproc import *


if __name__ == '__main__':
    print "##############"
    testProgram = unittest.main(exit=False)
    result = testProgram.result
    print '#####result', result,'####errors',result.errors,result.failures,result.testsRun

