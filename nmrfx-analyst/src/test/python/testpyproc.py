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

class TestPyproc(unittest.TestCase):
    def testPyprocImport(self):
        import pyproc

if __name__ == '__main__':
    testProgram = unittest.main(exit=False)
    result = testProgram.result

