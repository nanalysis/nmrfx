from dscript import *
import sys
if len(sys.argv) < 4:
    exit('usage: formula targetFile sourceFiles...') 
fBody = 'lambda ' + sys.argv[1]
targetFile = sys.argv[2]
sourceFiles = sys.argv[3:]

f = eval(fBody)
nd.combineN(f, targetFile, *sourceFiles)
