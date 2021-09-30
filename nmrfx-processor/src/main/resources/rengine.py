from org.renjin.script import RenjinScriptEngineFactory
from org.nmrfx.processor.math import RVec

class REngine:
    def  __init__(self):
         renjinFactory = RenjinScriptEngineFactory()
         self.reng = renjinFactory.getScriptEngine()

    def eval(self, script):
        return self.reng.eval(script)

    def put(self, name,value):
        self.reng.put(name,value)

    def get(self, name):
        return self.reng.get(name)

    def putVec(self, vec,name):
        rVec = RVec(vec)
        self.put(name,rVec)

R = REngine()
