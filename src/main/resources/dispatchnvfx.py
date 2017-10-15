import sys
import runpy
sys.argv.pop(0)
if len(sys.argv) > 0:
    if sys.argv[0] == "combine":
        import dcombine
    else:
        scriptName = sys.argv[0]
        runpy.run_path(scriptName)
