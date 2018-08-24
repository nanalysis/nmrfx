import re
def getMode(s):
    s = s.lower()
    pat = re.compile('(assign|or|set) ')
    m = pat.match(s)
    if m:
        mode = m.group(1)
        return (mode,m.start(0),m.end(0))
    else:
        return None

def getSelection(s):
    pat = re.compile(' *\(([^\(\)]+)\)')
    m = pat.match(s)
    if m:
        mode = m.group(1)
        return (mode,m.start(0),m.end(0))
    else:
        return None

def getBounds(s):
    pat = re.compile('\s+([0-9\.]+)\s+([0-9\.]+)\s+([0-9\.]+).*')
    m = pat.match(s)
    if m:
        b1 = m.group(1)
        b2 = m.group(2)
        b3 = m.group(3)
        return (b1,b2,b3)
    else:
        return None

def getComment(s):
    pat = re.compile('[^!]*!(.*)')
    m = pat.match(s)
    if m:
         comment = m.group(1)
    else:
         comment = ""
    
    return comment

def parseSelection(s):
    s = s.upper()
    pat = re.compile('("[^"]*"|[^ "]+)')
    fields = pat.findall(s)
    nFields = len(fields)
    res = "*"
    aname = "*"
    segid = ""
    for i in range(nFields):
        type = fields[i]
        if type.startswith("RESID"):
            i += 1
            res = fields[i]
        elif type == "NAME":
            i += 1
            aname = fields[i]
        elif type == "SEGID":
            i += 1
            segid = fields[i].strip('"').strip()
        elif type == "AND":
            pass
        elif type == "OR":
            pass
    if segid != "":
        fullAtom = segid+':'+res+'.'+aname 
    else:
        fullAtom = res+'.'+aname 
    return fullAtom


def getNextString(f, s, i1, i2):
    gotString = True
    if len(s) == 0:
        gotString = False
        while (True):
            s = f.readline()
            if s == None:
                break
            if len(s) == 0:
                break
            s = s.strip()
            if len(s) == 0:
                continue
            if s[0] == '!':
                continue
            gotString = True
            break
    if gotString:
        s = s[i1:]
        return s
    else:
        return None

def processDistanceConstraints(energyLists, atomSels, bounds):
    atoms1 = atomSels[0::2]
    atoms2 = atomSels[1::2]
    energyLists.addDistanceConstraint(atoms1, atoms2,bounds[0], bounds[1])

def readXPLORDistanceConstraints(fileName, energyLists): 
    f1 = open(fileName,'r')
    s=""
    atomSels=[]
    bounds = []
    while(True):
        s = getNextString(f1, s,0,-1)
        if s == None:
            break
        (mode, start, end) = getMode(s)
        if mode == "set":
            s=""
            continue
        elif mode == "assign":
            if len(atomSels) > 0:
                processDistanceConstraints(energyLists, atomSels, bounds)
            atomSels=[]
            bounds = []
        s = getNextString(f1, s,end,-1)
        nSel = 2
        for iSel in range(nSel):
            (selValue,start,end) = getSelection(s)
            atomSel = parseSelection(selValue)
            atomSels.append(atomSel)
            s = getNextString(f1, s,end,-1)
        if mode == "assign":
            (b1,b2,b3) = getBounds(s)
            lower = float(b1)-float(b2)
            upper = float(b1)+float(b3)
            bounds = [lower,upper]
            comment = getComment(s)
            s = ""
            s = getNextString(f1, s,0,-1)
            if s == None:
                break
        elif mode == "or":
            comment = getComment(s)
            s = ""
            s = getNextString(f1, s,0,-1)
            if s == None:
                break
    if len(atomSels) > 0:
        processDistanceConstraints(energyLists, atomSels, bounds)
    f1.close()
