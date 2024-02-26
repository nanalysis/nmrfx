import sys
import runpy
import argparse
from dscript import *

def combineVecs(args):
    fBody = 'lambda ' + args.formula
    targetFile = args.target
    sourceFiles = args.fileNames
    f = eval(fBody)
    nd.combineN(f, targetFile, *sourceFiles)

def autoProcess(args):
    import autoscript
    script = autoscript.makeScript(args)
    autoscript.saveScript(script)
    if args.execScript:
        autoscript.execScript(script)

sys.argv.pop(0)

if len(sys.argv) > 0 and sys.argv[0].endswith(".py"):
    scriptName = sys.argv[0]
    runpy.run_path(scriptName)
else:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers()

    parser_combine = subparsers.add_parser('combine')
    parser_combine.add_argument('formula')
    parser_combine.add_argument('target')
    parser_combine.add_argument("fileNames",nargs="+")
    parser_combine.set_defaults(func=combineVecs)

    coefs = {'hy','hy-r','ea','ea-r','ge','sep','real'}


    parser_auto = subparsers.add_parser('auto',help="Auto-generate processing script and optionally execute it")

    parser_auto.add_argument("-e",dest='execScript',action='store_true',help="Execute the generated script")
    parser_auto.add_argument("-a",dest='autoPhase',action='store_true',help="Autophase dataset")
    parser_auto.add_argument("--extract",dest='extractArgs',default='',nargs=2,help="Extract this region in the first dimension")
    parser_auto.add_argument("--tdcomb2",dest='tdcombArgs2',choices=coefs,default='',help="Mode for combining phase data in dimension 2")
    parser_auto.add_argument("--tdcomb3",dest='tdcombArgs3',choices=coefs,default='',help="Mode for combining phase data in dimension 3")
    parser_auto.add_argument("--ref",dest='refArg',default='',help="Reference for dimension 1")
    parser_auto.add_argument('fidfile', nargs='?', default="ser")
    parser_auto.add_argument('dataset', nargs='?', default="dataset.nv")
    parser_auto.add_argument('vars', nargs='*', default="")

    parser_auto.set_defaults(func=autoProcess)

    args = parser.parse_args(sys.argv)
    args.func(args)
