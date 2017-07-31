from com.onemoonsci.datachord.chemistry import ProteinPredictor

def predictMol(mol):
    polymers = mol.getPolymers()
    for atomName in ('N','CA','CB','C','H','HA(2)','HA3'):
        print atomName,
    print ""
    for polymer in polymers:
        predictor = ProteinPredictor(polymer)
        for residue in polymer.iterator():
            print residue.getNumber(), residue.getName(),
            for atomName in ('N','CA','CB','C','H','HA','HA3'):
                if residue.getName() == "GLY" and atomName == 'HA':
                    atomName = 'HA2'
                atom = residue.getAtom(atomName)
                if atom == None:
                    print "  _   ",
                else:
                    value = predictor.predict(residue.getAtom(atomName), False)
                    if value != None:
                        valueStr = "%6.2f" % (value)
                        print valueStr,
                    else:
                        print "  _   ",
            print ""
