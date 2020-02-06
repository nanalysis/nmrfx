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

