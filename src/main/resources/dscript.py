from itertools import izip
import array
from shutil import copyfile
from org.nmrfx.processor.datasets import Dataset
from org.nmrfx.processor.math import Vec


class NMRFxDatasetScripting:
    def __init__(self):
        self.cmd = Dataset

    def open(self, fileName,writable=False):
        dataset = Dataset(fileName,"",writable)
        return dataset

    def combine(self, func, outName, dIn1, dIn2):
        copyfile(dIn1.getCanonicalFile(), outName)
        dOut = self.open(outName,True)
        iDim = 0
        for (vec1,vec2) in izip(dIn1.vectors(iDim), dIn2.vectors(iDim)):
            vec3 = func(vec1,vec2)
            dOut.writeVector(vec3)
        dOut.close()

    def combineN(self, func, outName, *datasets):
        nd = NMRFxDatasetScripting()
        useDatasets = []  
        for i,dataset in enumerate(datasets):
            if isinstance(dataset,str):
                dataset = nd.open(dataset,False)
            useDatasets.append(dataset)

        iDim = 0
        copyfile(useDatasets[0].getCanonicalFile(), outName)
        dOut = self.open(outName,True)
        nDim = useDatasets[0].getNDim()
        dim = array.array('i',range(0,nDim))
        vecs = []
        for i,dataset in enumerate(useDatasets):
            vec = Vec(useDatasets[0].getSize(iDim))
            vecs.append(vec)
        for vIndex in useDatasets[0].indexer(iDim):
            for i,dataset in enumerate(useDatasets):
                vecs[i].setPt(vIndex, dim)
                dataset.readVector(vecs[i])
            vec3 = func(*vecs)
            dOut.writeVector(vec3)
        dOut.close()

nd = NMRFxDatasetScripting()
