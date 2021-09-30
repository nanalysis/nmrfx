# simple tool for getting chemical shifts out of BMRB file.
# generally better to use the Java based scanner, or bmrblib

def scanBMRB(fileName):
    assignData = []
    with open(fileName,'r') as f1:
        inSave = False
        inAssign = False
        for line in f1:
            line = line.strip()
            if inSave:
                if line == "save_":
                    inSave = False
                    inAssign = False
                elif line.startswith("_Assigned_chem_shift_list.Sf_category"):
                    inAssign = True
                    assignData.append(line)
                elif inAssign:
                    assignData.append(line)
            elif line.startswith("save_"):
                if inAssign:
                    break
                inSave = True
    return  assignData
 

def processAssign(assignData, atomNames):
    inLoop = False
    loopData = {}
    loopValues = {}
    currentLoop = ""
    for line in assignData:
        if line == "":
            continue
        if inLoop:
            if line == "stop_":
                inLoop = False
            elif line.startswith("_"):
                (tag,type) = line.split(".")
                if not tag in loopData:
                    currentLoop = tag
                    loopValues[tag] = []
                    loopData[tag] = []
                loopData[tag].append(type)
            else:
                loopValues[currentLoop].append(line)
        elif line == "loop_":
            inLoop = True
    valueIndex = loopData['_Atom_chem_shift'].index('Val')
    atomNameIndex = loopData['_Atom_chem_shift'].index('Atom_ID')
    seqIndex = loopData['_Atom_chem_shift'].index('Seq_ID')
    results = {}
    residues = {}
    for line in loopValues['_Atom_chem_shift']:
        atomName = line.split()[atomNameIndex]
        if atomName in atomNames:
            value = line.split()[valueIndex]
            seq = int(line.split()[seqIndex])
            results[seq,atomName] = value
            residues[seq] = 1
    resNums = residues.keys()
    resNums.sort()
    output = []
    for res in resNums:
        ok = True
        values = []
        for atomName in atomNames:
           if not (res,atomName) in  results:
               ok = False
               break
           shift = float(results[res,atomName])
           values.append(shift)
        if ok:
            output.append([res,values])
    return output

