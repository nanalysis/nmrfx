import sys
import os
import runpy

sys.argv.pop(0)
if len(sys.argv) > 0:
    if sys.argv[0] == "-h":
        print "usage: nmrfxs batch|gen|predict|score|summary|super|train|script.py"
        exit(0)
    if sys.argv[0] == "batch":
        import runall
    elif sys.argv[0] == "score":
        import runTests
    elif sys.argv[0] == "measure":
        import measure
        measure.parseArgs()
    elif sys.argv[0] == "summary":
        import checke
        checke.outDir = os.getcwd()
        checke.parseArgs()
    elif sys.argv[0] == "gen":
        import gennvfx
    elif sys.argv[0] == "predict":
        import predictor
        predictor.parseArgs()
    elif sys.argv[0] == "rdc":
        import rdc
        rdc.parseArgs()
    elif sys.argv[0] == "super":
        import super
        super.parseArgs()
    elif sys.argv[0] == "train":
        print sys.argv
        if (len(sys.argv) > 2) and (sys.argv[1] == "rna"):
            sys.argv.pop(0)
            import train_rna
        elif (len(sys.argv) > 2) and (sys.argv[1] == "protein"):
            sys.argv.pop(0)
            import train_protein
    else:
        scriptName = sys.argv[0]
        runpy.run_path(scriptName)
