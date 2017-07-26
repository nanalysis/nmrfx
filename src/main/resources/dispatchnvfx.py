import sys
import runpy
sys.argv.pop(0)
if len(sys.argv) > 0:
    if sys.argv[0] == "gen":
        import runall
    elif sys.argv[0] == "score":
        import runTests
    else:
        scriptName = sys.argv[0]
        runpy.run_path(scriptName)
