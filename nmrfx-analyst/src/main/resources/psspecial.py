def datasetMods(dataset, fidInfo):
    seq = fidInfo.fidObj.getSequence()
    if seq == 'hsqct2etf3gpsi3d':
        adjustBrukerT2(dataset, fidInfo)

def scriptMods(fidInfo,iDim):
    mods = ""
    seq = fidInfo.fidObj.getSequence()
    if iDim == 0:
        if seq == "hsqcnoef3gpsi":
            mods = arrayBruker2()
    return mods


def arrayBruker2():
    mods = ""
    mods += "acqOrder('a2','p1','d1')\n"
    mods += "acqarray(0,2)\n"
    return mods

def adjustBrukerT2(dataset, fidInfo):
    if dataset.getNDim() == 3:
        dpars = fidInfo.fidObj.getPar('D,1')
        if dpars != None:
            dpars = dpars.split()
            dparValues = [float(value) for value in dpars[1:]]
            d31 =  dparValues[31]
            values = dataset.getValues(2)
            newValues = [value*d31 for value in values]
            dataset.setValues(2, newValues)
