def getAnnealStages(dOpt):
    ''' getAnnealStages returns a list of 5 dictionaries that have values for
        running each stage of the dynamics program and requires the dynOptions
        object
    '''

    steps = dOpt.steps
    stepsEnd = dOpt.stepsEnd
    stepsHigh = int(round(steps*dOpt.highFrac))
    stepsAnneal1 = int(round((steps-stepsEnd-stepsHigh)*dOpt.toMedFrac))
    stepsAnneal2 = steps-stepsHigh-stepsEnd-stepsAnneal1
    medTemp = round(dOpt.highTemp * dOpt.medFrac)

    stage1 = {
        'tempVal'        : dOpt.highTemp,
        'econVal'        : dOpt.econHigh,
        'nStepVal'       : stepsHigh,
        'gMinSteps'      : None,
        'switchFracVal'  : None,
        'defaultParam'   : {
                                'end':1000,
                                'useh':False,
                                'hardSphere':0.15,
                                'shrinkValue':0.20,
                                'swap':dOpt.swap
                           },
        'defaultForce'   : {
                                'repel':0.5,
                                'dis':1.0,
                                'dih':5,
                                'irp':dOpt.irpWeight
                            },
        'timestep'       : dOpt.timeStep
    }

    stage2 = {
        'tempVal'        : [dOpt.highTemp, medTemp, dOpt.timePowerHigh],
        'econVal'        : dOpt.econHigh,
        'nStepVal'       : int(round((steps-stepsEnd-stepsHigh)*dOpt.toMedFrac)),
        'gMinSteps'      : None,
        'switchFracVal'  : None,
        'defaultParam'   : None,
        'defaultForce'   : None
    }

    stage3 = {
        'tempVal'        : [medTemp, 1.0, dOpt.timePowerMed],
        'econVal'        : lambda f: dOpt.econHigh*(pow(0.5,f)),
        'nStepVal'       : steps-stepsHigh-stepsEnd-stepsAnneal1,
        'gMinSteps'      : dOpt.minSteps,
        'switchFracVal'  : dOpt.switchFrac,
        'defaultParam'   : {
                            'useh' : False,
                            'hardSphere' : 0.0,
                            'shrinkValue' : 0.0,
                            'swap' :dOpt.swap
                           },
        'defaultForce'   : None
    }

    stage4 = {
        'tempVal'        : None,
        'econVal'        : None,
        'nStepVal'       : None,
        'gMinSteps'      : dOpt.minSteps,
        'switchFracVal'  : None,
        'defaultParam'   : {
                            'useh' : True,
                            'hardSphere' : 0.0,
                            'shrinkValue' : 0.0,
                            'shrinkHValue' : 0.0,
                            'swap':dOpt.swap
                            },
        'defaultForce'   : {
                            'repel'  : 1.0,
                            'bondWt' : 25.0,
                            'tors'   : 0.1
                            }
    }

    stage5 = {
        'tempVal'        : 0.0,
        'econVal'        : dOpt.econLow,
        'nStepVal'       : stepsEnd,
        'gMinSteps'      : dOpt.minSteps,
        'switchFracVal'  : None,
        'defaultParam'   : None,
        'defaultForce'   : {
                            'repel'  : 2.0,
                            }
    }
    stages = [stage1, stage2, stage3, stage4, stage5]
    return stages


initialize = True
def runStage(stage, refiner, rDyn):
    '''
        runStage runs a stage of the dynamics using a stage dictionary that has
        keys as demonstrated above in the getAnnealStages. This function uses a
        global variable stored in the module to assess whether it is the first
        time the method is called. If so, it will initialize the dynamics and
        all other calls will continue the dynamics.
    '''
    refiner.setPars(stage['defaultParam'])
    refiner.setForces(stage['defaultForce'])
    timeStep = rDyn.getTimeStep()/2.0
    gminSteps = stage['gMinSteps']
    if gminSteps:
        refiner.gmin(nsteps=gminSteps, tolerance=1.0e-6)
    tempFunc = stage.get('tempVal')
    if tempFunc is not None:
        try:
            upTemp, downTemp, time = tempFunc
            tempLambda = lambda f: (upTemp - downTemp) * pow((1.0 - f), time) + downTemp
        except TypeError:
            tempLambda = tempFunc
        econLambda = stage['econVal']
        nSteps = stage['nStepVal']
        global initialize
        if initialize:
            rDyn.initDynamics2(tempLambda, econLambda, nSteps, stage['timestep'])
        else:
            rDyn.continueDynamics2(tempLambda, econLambda, nSteps, timeStep)
        initialize=False
    else:
        rDyn.continueDynamics2(timeStep)
    switchFrac = stage.get('switchFracVal')
    if switchFrac:
        rDyn.run(switchFrac)
    else:
        rDyn.run()
