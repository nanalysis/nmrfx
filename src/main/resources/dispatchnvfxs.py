import sys
import os
import runpy
import argparse

def parseArgs():
    parser = argparse.ArgumentParser(description="super options")
    parser.add_argument("-r", dest="resList", default='', help="Residues to exclude from comparison. Can specify semi-colon-separated chains and comma-separated residue ranges and individual values (e.g. A: 2, 3; B: 2-5, 10).")
    parser.add_argument("-a", dest="atomList", default='', help="Atoms to exclude from comparison.")
    parser.add_argument("fileNames",nargs="*")
    args = parser.parse_args()

    resArg = args.resList
    if ";" in resArg: #multiple chains separated by ; (e.g. A: 2-5, 10; B: 1-4, 12)
        resList1 = []
        chainSplit = resArg.split(";")
        for chainArgs in chainSplit:
            chain = chainArgs.split(":")[0];
            resArgs = chainArgs.split(":")[1];
            resList1.append(makeResList(resArgs, chain))
        resList = [item for sublist in resList1 for item in sublist]
    else: #single chain
        if ":" in resArg: #chain specified (e.g. B: 5-10)
            chain = resArg.split(":")[0];
            resArgs = resArg.split(":")[1];
            resList = makeResList(resArgs, chain)
        else: #chain not specified (e.g. 2-5). Defaults to chain A
            resList = makeResList(resArg, "A")

    atomListS = args.atomList.split(",")
    atomList = [atom.strip() for atom in atomListS if atom != ""]
    fileNames = args.fileNames

    # print resList, atomList

    return resList, atomList, fileNames

def makeResList(resArg, chain):
    if resArg == "":
        resList = []
    else:
        if ("-" in resArg) and ("," in resArg): #comma-separated ranges (e.g. 2-5, 10-12) or ranges and individual residues (e.g. 2-5, 7, 10-12, 20)
            resList1 = []
            resArgSplit = resArg.split(",")
            for val in resArgSplit:
                if "-" in val: #range of residues
                    valSplit = val.split("-")
                    firstRes = int(valSplit[0].strip())
                    lastRes = int(valSplit[1].strip()) + 1
                    resList1.append([res for res in range(firstRes, lastRes)])
                else: #single residue
                    resList1.append([val.strip()])
            resList = [chain + "." + str(item) for sublist in resList1 for item in sublist]
        elif "-" in resArg: #single range of residues (e.g. 2-5)
            resArgSplit = resArg.split("-")
            firstRes = int(resArgSplit[0].strip())
            lastRes = int(resArgSplit[1].strip()) + 1
            resList = [chain + "." + str(res) for res in range(firstRes, lastRes)]
        elif "," in resArg: #comma-separated individual residues (e.g. 2, 3, 4, 5)
            resListS = resArg.split(",")
            resList = [chain + "." + str(res) for res in resListS if res != ""]

    return resList

sys.argv.pop(0)
if len(sys.argv) > 0:
    if sys.argv[0] == "batch":
        import runall
    elif sys.argv[0] == "score":
        import runTests
    elif sys.argv[0] == "summary":
        import checke
        checke.outDir = os.getcwd()
        checke.summary(sys.argv[1:])
    elif sys.argv[0] == "gen":
        import gennvfx
    elif sys.argv[0] == "predict":
        import predictor
    elif sys.argv[0] == "super":
        import super
        args = parseArgs()
        excludeRes = args[0]
        excludeAtoms = args[1]
        files = args[2]
        if len(files) > 1:
            super.runSuper(excludeRes, excludeAtoms, files)


    elif sys.argv[0] == "train":
        print sys.argv
        if (len(sys.argv) > 2) and (sys.argv[1] == "rna"):
            sys.argv.pop(0)
            import train_rna
    else:
        scriptName = sys.argv[0]
        runpy.run_path(scriptName)
