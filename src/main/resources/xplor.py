import re
#from org.nmrfx.structure.chemistry.energy import EnergyLists
from java.util import ArrayList
from org.nmrfx.structure.chemistry.energy import AngleBoundary


class XPLOR:
    def __init__(self, f):
        self.f = open(f, 'r')
        self.s = ""
        self.invalidAtomSelections = []

    def getMode(self):
        s = self.getNextString()
        if s is None:
            return None
        pat = re.compile(r'(assi[a-z]*|or|set)\s*', re.IGNORECASE)
        #print "\t\t\tVALUE OF S in getMode() => ", s
        m = pat.match(s)
        # m is None if no match is found
        #print m
        if m:
            mode = m.group(1)
            # mode is ASSIGN
            self.s = s[m.end(0):].strip()
            return mode
        else:
            return None


    def getSelection(self):
        s = self.getNextString()
        if s is not None:
            s = s.lower()
            pat = re.compile(r'\s*\(?\(([^\(\)]+)\)?\)')
            m = pat.match(s)
            if m:
                selection = m.group(1)
                self.s = s[m.end(0):].strip()
                return selection
        else:
            return None


    def getBounds(self):
        s = self.getNextString()
        pat = re.compile(r'(\-??[0-9\.]+)\s+(\-??[0-9\.]+)\s+(\-??[0-9\.]+)\s*([0-9])?')
        m = pat.match(s)
        if m:
            b = [m.group(i+1) for i in range(m.lastindex)]
            self.s = s[m.end(m.lastindex):].strip()

            if len(b) == 3:
                return b[0], b[1], b[2]
            else:
                return b[0], b[1], b[2], b[3]
        else:
            return None


    def getComment(self, s):
        pat = re.compile(r'[^!]*!(.*)')
        m = pat.match(s)
        if m:
             comment = m.group(1)
        else:
             comment = ""
        return comment


    def parseSelection(self, s):
        #print "s value in parse selection :", s
        s = s.upper()
        pat = re.compile(r'("[^"]*"|[^ "]+)')
        fields = pat.findall(s)
        nFields = len(fields)
        res = "*"
        aname = "*"
        segid = ""
        for i in range(nFields):
            type = fields[i]
            if type.startswith("RESI"):
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


    def getNextString(self):
        gotString = True
        self.s = self.s.strip()
        if len(self.s) == 0:
            gotString = False
            while (True):
                self.s = self.f.readline()
                #print "\tLine being read  ==== ", self.s
                if self.s == None:
                    break
                if len(self.s) == 0:
                    break
                self.s = self.s.strip()
                if len(self.s) == 0:
                    continue
                if self.s[0] == '!':
                    continue
                gotString = True
                break
        if gotString:
            return self.s
        else:
            return None


    def processDistanceConstraints(self, energyLists, atomSels, bounds):
        atoms1 = atomSels[0::2]
        atoms2 = atomSels[1::2]
        # if energyLists is None:
        #     pass
        # else:
            #print atoms1,'atoms2',atoms2
        #atomNames1 = ArrayList()
        #atomNames2 = ArrayList()
        atomPairs = []
        for (fullAtom1,fullAtom2) in zip(atoms1,atoms2):
            atomPair = ' '.join([fullAtom1,fullAtom2]) if fullAtom1 < fullAtom2 else ' '.join([fullAtom2, fullAtom1])
            atomPairs.append(atomPair);
            #atomNames1.add(aname1)
            #atomNames2.add(aname2)
        (lower, upper) = bounds;
        return {'atomPairs' : atomPairs, 'lower' : lower, 'upper':upper}
        #energyLists.addDistanceConstraint(atomNames1, atomNames2, bounds[0], bounds[1])

    def processAngleConstraints(self, dihedral, atomsSels, bounds, scale=1):
        # EX: fullAtoms = ["2koc:1.C5'","2koc:1.C4'","2koc:1.C3'","2koc:1.O3'"]
        #print atomsSels
        validAtomSelections = AngleBoundary.allowRotation(atomsSels)
        #print validAtomSelections
        if validAtomSelections:
            lower, upper = bounds
            if lower == upper:
                lower = lower - 20
                upper = upper + 20
            if (lower < -180) and (upper < 0.0):
                lower += 360
                upper += 360
            # if dihedral is None:
            #     pass
            #     print "atomSels : ", atomsSels
            #     print "bounds : ", bounds
            # else:
            dihedral.addBoundary(atomsSels, lower, upper, scale)
        else:
            self.invalidAtomSelections.append(atomsSels)
            #raise ValueError("Rotation about atom selections not permissible.")



    def readXPLORDistanceConstraints(self, energyLists=None):
        constraints = []
        f1 = self.f
        self.distances = True
        atomSels = []
        bounds = []
        while True:
            mode = self.getMode()
            if self.s is None or mode is None:
                break
            if mode is not None:
                mode = mode.lower()
                if mode == "set":
                    continue
                elif mode.startswith("assi"):
                    if len(atomSels) > 0:
                        constraints.append(self.processDistanceConstraints(energyLists, atomSels, bounds))
                    atomSels = []
                    bounds = []
                nSel = 2
                for iSel in range(nSel):
                    selValue = self.getSelection()
                    atomSel = self.parseSelection(selValue)
                    atomSels.append(atomSel)
                if mode.startswith("assi"):
                    (b1, b2, b3) = self.getBounds()
                    lower = float(b1)-float(b2)
                    upper = float(b1)+float(b3)
                    bounds = [lower,upper]
                    if self.s is None:
                        break
                    comment = self.getComment(self.s)
                elif mode == "or":
                    comment = self.getComment(self.s)
                    if self.s is None:
                        break

        if len(atomSels) > 0:
            constraints.append(self.processDistanceConstraints(energyLists, atomSels, bounds))
        f1.close()
        return constraints


    def readXPLORAngleConstraints(self, dihedral):
        f1 = self.f
        atomSels = []
        bounds = []
        done = False
        # count = 0
        while True:
            # count += 1
            if done:
                break
            mode = self.getMode()
            if self.s is None:
                break
            if mode is not None:
                mode = mode.lower()
                if mode == "assi" or mode == "assign":
                    if len(atomSels) > 0:
                        self.processAngleConstraints(dihedral, atomSels, bounds)
                    atomSels = []
                    bounds = []
                nSel = 4
                for iSel in range(nSel):
                    selValue = self.getSelection()
                    atomSel = self.parseSelection(selValue)
                    atomSels.append(atomSel)
                #print atomSels
                if mode == "assi" or mode == "assign":
                    b = self.getBounds()
                    b1 = b[1]
                    b2 = b[2]
                    lower = float(b1) - float(b2)
                    upper = float(b1) + float(b2)
                    bounds = [lower, upper]
                    #print bounds
                    if self.s is None:
                        break
            # if len(self.s) > 0:
            #     self.s = ""
            # THE FOLLOWING IF-STATEMENT IS FOR TESTING USE-ONLY
            # if count == 2:
            #      break
            if self.getNextString() is None:
                done = True

        if len(atomSels) > 0:
            self.processAngleConstraints(dihedral, atomSels, bounds)
        f1.close()

# f1 = open('XplorGenDis.tbl','r')
#xp = XPLOR('XplorNOEDis.mr')
#xp.readXPLORDistanceConstraints()
