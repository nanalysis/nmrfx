from org.nmrfx.structure.chemistry.predict import ProteinPredictor

def predict(mol, ppmSet=-1):
    pred=ProteinPredictor()
    pred.init(mol)
    pred.predict(ppmSet)

