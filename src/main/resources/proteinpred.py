from org.nmrfx.structure.chemistry.predict import ProteinPredictor

def predict(mol):
    pred=ProteinPredictor()
    pred.init(mol)
    pred.predict(0)

