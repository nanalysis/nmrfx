import collections
def getAnnealStages(dOpt, settings, mode='gen'):
    """
    # Parameters:

    * dOpt (dict); dictionary containing parameters and values for the dynamic simulation
    * settings (LinkedHashMap); java HashMap that contains force and dynamic program parameters and their values

    # Returns:

    stages (list); a list of 5 dictionarys that have values for running each stage of the dynamics program

    See also: `refine.anneal(...)` in refine.py
    """
    from refine import createStrictDict
    steps = dOpt['steps']
    cffSteps = 0
    if 'cffSteps' in dOpt:
         cffSteps = dOpt['cffSteps']
    
    stepsEnd = dOpt['stepsEnd']
    stepsHigh = int(round(steps*dOpt['highFrac']))
    stepsAnneal1 = int(round((steps-stepsEnd-stepsHigh)*dOpt['toMedFrac']))
    stepsAnneal2 = steps-stepsHigh-stepsEnd-stepsAnneal1
    medTemp = round(dOpt['highTemp'] * dOpt['medFrac'])

    stage_prep = {
        'ends' : [3,10,20,1000],
        'param'   : {
                                'dislim':4.6,
                                'updateAt': 5,
                                'swap': 20,
                                'useh':False,
                                'hardSphere':0.15,
                                'shrinkValue':0.20,
         },
        'force'   : {
                        'repel':0.5,
                        'dis':1.0,
                        'irp' : 0.05,
                        'dih':5
                     }
    }
    stage_hi = {
        'tempVal'        : dOpt['highTemp'],
        'econVal'        : dOpt['econHigh'],
        'nStepVal'       : stepsHigh,
        'gMinSteps'      : None,
        'dfreeSteps'      : None,
        'switchFracVal'  : None,
        'param'   : {
                                'end':1000,
                                'updateAt': dOpt['update'],
                                'useh':False,
                                'hardSphere':0.15,
                                'shrinkValue':0.20,
                           },
        'force'   : {
                                'repel':0.5,
                                'dis':1.0,
                                'dih':5,
                            },
        'timestep'       : dOpt['timeStep']
    }

    stage_anneal_hi = {
        'tempVal'        : [dOpt['highTemp'], medTemp, dOpt['timePowerHigh']],
        'econVal'        : dOpt['econHigh'],
        'nStepVal'       : int(round((steps-stepsEnd-stepsHigh)*dOpt['toMedFrac'])),
        'gMinSteps'      : None,
        'switchFracVal'  : None,
        'param'          : None,
        'force'          : None
    }

    stage_anneal_med = {
        'tempVal'        : [medTemp, 1.0, dOpt['timePowerMed']],
        'econVal'        : [dOpt['econHigh'],0.5],
        'nStepVal'       : steps-stepsHigh-stepsEnd-stepsAnneal1,
        'gMinSteps'      : dOpt['minSteps'],
        'switchFracVal'  : dOpt['switchFrac'],
        'param'          : {
                            'useh' : False,
                            'hardSphere' : 0.0,
                            'shrinkValue' : 0.0,
                           },
        'force'          : None
    }

    stage_anneal_low = {
        'tempVal'        : None,
        'econVal'        : None,
        'nStepVal'       : None,
        'gMinSteps'      : dOpt['minSteps'],
        'switchFracVal'  : None,
        'param'          : {
                            'useh' : True,
                            'hardSphere' : 0.0,
                            'shrinkValue' : 0.0,
                            'shrinkHValue' : 0.0,
                            },
        'force'          : {
                            'repel'  : 1.0,
                            'bondWt' : 25.0,
                            }
    }

    stage_cff_reduced = { 
        'nStepVal' : cffSteps,
        'switchFracVal': 0.2,
        'tempVal' : [100.0],
        'param' : {
            'dislim' : 6.0
        },
        'force' : {
            'cffnb' : 0.1,
            'nbmin' : 1.0,
            'repel' : -1.0,
            'tors' : -0.1,
            'dis' : 40.0,
            'dih' : 5.0,
            'irp' : 0.5,
            'stack' : 0.1
        }
    }
    stage_cff_full = {
        'nStepVal' : None,
        'switchFracVal'  : None,
        'force' : {
            'cffnb' : 0.1,
            'nbmin' : 0.50,
            'repel' : -1.0,
        },
        'param'          : None
    }

    stage_low = {
        'tempVal'        : 0.0,
        'econVal'        : dOpt['econLow'],
        'nStepVal'       : stepsEnd,
        'gMinSteps'      : dOpt['minSteps'],
        'switchFracVal'  : None,
        'param'          : None,
        'force'          : {
                            'repel'  : 2.0,
                            }
    }
    stage_refine = {
        'tempVal'        : [100, 1.0, dOpt['timePowerMed']],
        'econVal'        : None,
        'econVal'        : dOpt['econHigh'],
        'nStepVal'       : 5000,
        'gMinSteps'      : dOpt['minSteps'],
        'switchFracVal'  : None,
        'timestep'       : dOpt['timeStep'],
        'param'          : {
                            'end':1000,
                            'useh' : True,
                            'hardSphere' : 0.0,
                            'shrinkValue' : 0.0,
                            'shrinkHValue' : 0.0,
                            },
        'force'          : {
                            'repel'  : 1.0,
                             'dih':1.0,
                            'bondWt' : 25.0,
                            }
    }


    if mode == 'gen' and cffSteps != 0:
        mode = 'all'


    if mode == 'all':
        stages = [stage_prep, stage_hi, stage_anneal_hi,stage_anneal_med,stage_anneal_low, stage_cff_reduced, stage_cff_full, stage_low]
    elif mode == 'refine':
        stages = [stage_refine, stage_low]
    elif mode == 'prep':
        stages = [stage_prep]
    elif mode == 'anneal':
        stages = [stage_hi, stage_anneal_hi,stage_anneal_med,stage_anneal_low, stage_low]
    elif mode == 'cff':
        stages = [stage_cff_reduced, stage_cff_full]
    else:
        stages = [stage_prep, stage_hi, stage_anneal_hi,stage_anneal_med,stage_anneal_low, stage_low]
    stageDict = {}

    stageOrder = []
    for i,stage in enumerate(stages):
        for (k,v) in locals().items():
            if v is stage:
                if k != 'stage':
                    stageName = k

        ''' The default settings have the lowest priority '''
        stage['param'] = createStrictDict(stage['param'],'param')
        stage['force'] = createStrictDict(stage['force'],'force')

        ''' General settings made by the user have a medium priority '''
        stage['param'].strictUpdate(settings.get('param'))
        stage['force'].strictUpdate(settings.get('force'))

        ''' Specific stage settings have the highest priority '''
        userStageKey = stageName
        userStage = settings.get(userStageKey, {})
        if userStage:
            stage['param'].strictUpdate(userStage.get('param'))
            stage['force'].strictUpdate(userStage.get('force'))
        stageDict[stageName] = stage
        stageOrder.append(stageName)
    newStageNames={}
    while (True):
        newOrder = []
        foundNew = False
        for stageName in stageOrder:
            newOrder.append(stageName)
            for key in settings:
                if key.startswith('stage_'):
                    if key in stageDict:
                        continue
                    userStage = settings.get(key, {})
                    if not 'after' in userStage:
                        print 'no after key in stage ' + key
                        exit(1)
                    prevStageName = userStage['after']
                    newStageNames[key]=prevStageName
                    prevStageName = 'stage_' + prevStageName
                    if prevStageName == stageName:
                        baseStage = stageDict[prevStageName]
                        newStage = dict(baseStage)
                        newStage['param'] = createStrictDict(baseStage['param'],'param')
                        newStage['force'] = createStrictDict(baseStage['force'],'force')
                        newOrder.append(key)
                        stageDict[key] = newStage
                        for kk in userStage:
                            if not kk == 'after':
                                if kk != 'param' and kk != 'force':
                                    newStage[kk] = userStage.get(kk)
                                else:
                                    newStage[kk].strictUpdate(userStage.get(kk))
                        foundNew = True
                        break
        stageOrder = list(newOrder)
        if not foundNew:
            break
    for newStageName in newStageNames:
        if not newStageName in stageDict:        
            print 'no location for ' + newStageName
            exit(1)

    stages=[]
    stages = collections.OrderedDict()
    for stageName in stageOrder:
        stages[stageName] = stageDict[stageName]
    return stages

initialize = True

def annealPrep(refiner,  ends,  steps=100):
    ranfact=20.0
    refiner.setSeed(refiner.seed)
    refiner.prepAngles()
    energy = refiner.energy()

    for end in ends:
        refiner.setPars({'end':end})
        parString = refiner.getPars()
        print "PARS   " + parString
        refiner.gmin(nsteps=steps,tolerance=1.0e-6)
    if refiner.eFileRoot != None and refiner.reportDump:
        refiner.dump(-1.0,-1.0,refiner.eFileRoot+'_prep.txt')
    energy = refiner.energy()

def runStage(stage, refiner, rDyn):
    """
    Runs a stage of the dynamics using a stage dictionary that has
    keys as demonstrated above in the getAnnealStages. This function uses a
    global variable stored in the module to assess whether it is the first
    time the method is called. If so, it will initialize the dynamics and
    all other calls will continue the dynamics.

    # Parameters:

    * stage (dict);
    * refiner (refine); instance object of refine class
    * rDyn (RotationalDynamics); instance rotational dynamics object to run dynamics simulation

    # Returns:

    _ (None); Performs

    See also: `anneal(...)` in refine.py
    """
    refiner.setPars(stage['param'])
    refiner.setForces(stage['force'])
    forceString = refiner.getForces()
    print "FORCES " + forceString
    parString = refiner.getPars()
    print "PARS   " + parString

    prep = True
    if 'ends' in stage:
        ends = stage['ends']
        if 'gminSteps' in stage:
            gminSteps = stage['gMinSteps']
        else:
            gminSteps = 100
        annealPrep(refiner,  ends, gminSteps)
        return

    if 'dfreeSteps' in stage:
        dfreeSteps = stage['dfreeSteps']
        if dfreeSteps:
            refiner.refineNonDeriv(nsteps=dfreeSteps,radius=20, alg='cmaes');
    if 'gMinSteps' in stage:
        gminSteps = stage['gMinSteps']
        if gminSteps:
            refiner.gmin(nsteps=gminSteps, tolerance=1.0e-6)


    timeStep = rDyn.getTimeStep()/2.0
    tempFunc = stage.get('tempVal')
    econLambda = None
    if 'econVal' in stage:
        econFunc = stage['econVal']
        if callable(econFunc):
            econLambda = econFunc
        else:
            if econFunc == None:
                econLambda = 0.001
            elif isinstance(econFunc,float):
                econLambda = econFunc
            else:
                if len(econFunc) == 1:
                    upLambda = econFunc[0]
                    powBase = 0.5
                else:
                    upLambda, powBase = econFunc
                econLambda =  lambda f: upLambda*(pow(powBase,f))
    if econLambda == None:
        econLambda = 0.001

    if tempFunc is not None:
        if callable(tempFunc):
            tempLambda = tempFunc
        else:
            if isinstance(tempFunc,float):
                tempLambda = tempFunc
            else:
                if len(tempFunc) == 1:
                    upTemp = tempFunc[0]
                    downTemp = 1.0
                    powVal = 4.0
                elif len(tempFunc) == 2:
                    upTemp, downTemp = tempFunc
                    powVal = 4.0
                else:
                    upTemp, downTemp, powVal = tempFunc
                tempLambda = lambda f: (upTemp - downTemp) * pow((1.0 - f), powVal) + downTemp

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
