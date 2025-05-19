package org.nmrfx.structure.chemistry.predict;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class ProteinPredictorGen {
    enum PredProps {
        FUNC0("cos(chiC)*sin(chi2C)") {
            double calcValue(Map<String, Double> v) {
                Double chiC = v.get("chiC");
                Double chi2C = v.get("chi2C");
                if (ProteinPredictor.checkVars(chiC, chi2C)) {
                    return Math.cos(chiC) * Math.sin(chi2C);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC1("sin(2*chi2C)") {
            double calcValue(Map<String, Double> v) {
                Double chi2C = v.get("chi2C");
                if (ProteinPredictor.checkVars(chi2C)) {
                    return Math.sin(2 * chi2C);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC2("cos(psiC)*cos(psiP)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double psiP = v.get("psiP");
                if (ProteinPredictor.checkVars(psiC, psiP)) {
                    return Math.cos(psiC) * Math.cos(psiP);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC3("sin(3*psiP)") {
            double calcValue(Map<String, Double> v) {
                Double psiP = v.get("psiP");
                if (ProteinPredictor.checkVars(psiP)) {
                    return Math.sin(3 * psiP);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC4("cos(2*psiS)") {
            double calcValue(Map<String, Double> v) {
                Double psiS = v.get("psiS");
                if (ProteinPredictor.checkVars(psiS)) {
                    return Math.cos(2 * psiS);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC5("sin(phiC)*PRO_P") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                Double PRO_P = v.get("PRO_P");
                if (ProteinPredictor.checkVars(phiC, PRO_P)) {
                    return Math.sin(phiC) * PRO_P;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC6("cos(2*chiC)") {
            double calcValue(Map<String, Double> v) {
                Double chiC = v.get("chiC");
                if (ProteinPredictor.checkVars(chiC)) {
                    return Math.cos(2 * chiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC7("sin(phiP)") {
            double calcValue(Map<String, Double> v) {
                Double phiP = v.get("phiP");
                if (ProteinPredictor.checkVars(phiP)) {
                    return Math.sin(phiP);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC8("sin(3*psiC)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                if (ProteinPredictor.checkVars(psiC)) {
                    return Math.sin(3 * psiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC9("cos(psiP)") {
            double calcValue(Map<String, Double> v) {
                Double psiP = v.get("psiP");
                if (ProteinPredictor.checkVars(psiP)) {
                    return Math.cos(psiP);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC10("cos(2*phiP)") {
            double calcValue(Map<String, Double> v) {
                Double phiP = v.get("phiP");
                if (ProteinPredictor.checkVars(phiP)) {
                    return Math.cos(2 * phiP);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC11("cos(phiC)*cos(chiC)") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                Double chiC = v.get("chiC");
                if (ProteinPredictor.checkVars(phiC, chiC)) {
                    return Math.cos(phiC) * Math.cos(chiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC12("sin(psiC)*ARO_S") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double ARO_S = v.get("ARO_S");
                if (ProteinPredictor.checkVars(psiC, ARO_S)) {
                    return Math.sin(psiC) * ARO_S;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC13("sin(3*phiP)") {
            double calcValue(Map<String, Double> v) {
                Double phiP = v.get("phiP");
                if (ProteinPredictor.checkVars(phiP)) {
                    return Math.sin(3 * phiP);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC14("h3") {
            double calcValue(Map<String, Double> v) {
                Double h3 = v.get("h3");
                if (ProteinPredictor.checkVars(h3)) {
                    return h3;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC15("sin(psiC)*cos(chi2C)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double chi2C = v.get("chi2C");
                if (ProteinPredictor.checkVars(psiC, chi2C)) {
                    return Math.sin(psiC) * Math.cos(chi2C);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC16("sin(chi2C)") {
            double calcValue(Map<String, Double> v) {
                Double chi2C = v.get("chi2C");
                if (ProteinPredictor.checkVars(chi2C)) {
                    return Math.sin(chi2C);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC17("cos(phiP)") {
            double calcValue(Map<String, Double> v) {
                Double phiP = v.get("phiP");
                if (ProteinPredictor.checkVars(phiP)) {
                    return Math.cos(phiP);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC18("sin(3*psiS)") {
            double calcValue(Map<String, Double> v) {
                Double psiS = v.get("psiS");
                if (ProteinPredictor.checkVars(psiS)) {
                    return Math.sin(3 * psiS);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC19("cos(3*psiS)") {
            double calcValue(Map<String, Double> v) {
                Double psiS = v.get("psiS");
                if (ProteinPredictor.checkVars(psiS)) {
                    return Math.cos(3 * psiS);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC20("cos(psiC)*cos(chiC)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double chiC = v.get("chiC");
                if (ProteinPredictor.checkVars(psiC, chiC)) {
                    return Math.cos(psiC) * Math.cos(chiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC21("cos(phiC)*BULK_P") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                Double BULK_P = v.get("BULK_P");
                if (ProteinPredictor.checkVars(phiC, BULK_P)) {
                    return Math.cos(phiC) * BULK_P;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC22("ring") {
            double calcValue(Map<String, Double> v) {
                Double ring = v.get("ring");
                if (ProteinPredictor.checkVars(ring)) {
                    return ring;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC23("cos(2*psiC)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                if (ProteinPredictor.checkVars(psiC)) {
                    return Math.cos(2 * psiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC24("cos(phiC)*ARO_P") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                Double ARO_P = v.get("ARO_P");
                if (ProteinPredictor.checkVars(phiC, ARO_P)) {
                    return Math.cos(phiC) * ARO_P;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC25("cos(phiC)*cos(chi2C)") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                Double chi2C = v.get("chi2C");
                if (ProteinPredictor.checkVars(phiC, chi2C)) {
                    return Math.cos(phiC) * Math.cos(chi2C);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC26("cos(psiC)*cos(phiC)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double phiC = v.get("phiC");
                if (ProteinPredictor.checkVars(psiC, phiC)) {
                    return Math.cos(psiC) * Math.cos(phiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC27("sin(2*psiC)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                if (ProteinPredictor.checkVars(psiC)) {
                    return Math.sin(2 * psiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC28("sin(phiC)*ARO_P") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                Double ARO_P = v.get("ARO_P");
                if (ProteinPredictor.checkVars(phiC, ARO_P)) {
                    return Math.sin(phiC) * ARO_P;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC29("cos(psiC)*ARO_S") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double ARO_S = v.get("ARO_S");
                if (ProteinPredictor.checkVars(psiC, ARO_S)) {
                    return Math.cos(psiC) * ARO_S;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC30("cos(psiC)*CHRG_S") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double CHRG_S = v.get("CHRG_S");
                if (ProteinPredictor.checkVars(psiC, CHRG_S)) {
                    return Math.cos(psiC) * CHRG_S;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC31("cos(psiC)*HPHB_S") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double HPHB_S = v.get("HPHB_S");
                if (ProteinPredictor.checkVars(psiC, HPHB_S)) {
                    return Math.cos(psiC) * HPHB_S;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC32("sin(psiC)*cos(psiP)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double psiP = v.get("psiP");
                if (ProteinPredictor.checkVars(psiC, psiP)) {
                    return Math.sin(psiC) * Math.cos(psiP);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC33("cos(psiS)") {
            double calcValue(Map<String, Double> v) {
                Double psiS = v.get("psiS");
                if (ProteinPredictor.checkVars(psiS)) {
                    return Math.cos(psiS);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC34("cos(chi2C)") {
            double calcValue(Map<String, Double> v) {
                Double chi2C = v.get("chi2C");
                if (ProteinPredictor.checkVars(chi2C)) {
                    return Math.cos(chi2C);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC35("hshift3") {
            double calcValue(Map<String, Double> v) {
                Double hshift3 = v.get("hshift3");
                if (ProteinPredictor.checkVars(hshift3)) {
                    return hshift3;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC36("hshift2") {
            double calcValue(Map<String, Double> v) {
                Double hshift2 = v.get("hshift2");
                if (ProteinPredictor.checkVars(hshift2)) {
                    return hshift2;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC37("hshift1") {
            double calcValue(Map<String, Double> v) {
                Double hshift1 = v.get("hshift1");
                if (ProteinPredictor.checkVars(hshift1)) {
                    return hshift1;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC38("sin(phiC)*sin(chiC)") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                Double chiC = v.get("chiC");
                if (ProteinPredictor.checkVars(phiC, chiC)) {
                    return Math.sin(phiC) * Math.sin(chiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC39("sin(psiC)*PRO_S") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double PRO_S = v.get("PRO_S");
                if (ProteinPredictor.checkVars(psiC, PRO_S)) {
                    return Math.sin(psiC) * PRO_S;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC40("sin(phiC)*sin(chi2C)") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                Double chi2C = v.get("chi2C");
                if (ProteinPredictor.checkVars(phiC, chi2C)) {
                    return Math.sin(phiC) * Math.sin(chi2C);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC41("cos(psiC)*BULK_S") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double BULK_S = v.get("BULK_S");
                if (ProteinPredictor.checkVars(psiC, BULK_S)) {
                    return Math.cos(psiC) * BULK_S;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC42("cos(chiC)*cos(chi2C)") {
            double calcValue(Map<String, Double> v) {
                Double chiC = v.get("chiC");
                Double chi2C = v.get("chi2C");
                if (ProteinPredictor.checkVars(chiC, chi2C)) {
                    return Math.cos(chiC) * Math.cos(chi2C);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC43("cos(3*psiC)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                if (ProteinPredictor.checkVars(psiC)) {
                    return Math.cos(3 * psiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC44("sin(psiS)") {
            double calcValue(Map<String, Double> v) {
                Double psiS = v.get("psiS");
                if (ProteinPredictor.checkVars(psiS)) {
                    return Math.sin(psiS);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC45("cos(2*psiP)") {
            double calcValue(Map<String, Double> v) {
                Double psiP = v.get("psiP");
                if (ProteinPredictor.checkVars(psiP)) {
                    return Math.cos(2 * psiP);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC46("intercept") {
            double calcValue(Map<String, Double> v) {
                Double intercept = v.get("intercept");
                if (ProteinPredictor.checkVars(intercept)) {
                    return intercept;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC47("cos(2*phiC)") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                if (ProteinPredictor.checkVars(phiC)) {
                    return Math.cos(2 * phiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC48("sin(psiC)*sin(chiC)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double chiC = v.get("chiC");
                if (ProteinPredictor.checkVars(psiC, chiC)) {
                    return Math.sin(psiC) * Math.sin(chiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC49("sin(psiC)*HPHB_S") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double HPHB_S = v.get("HPHB_S");
                if (ProteinPredictor.checkVars(psiC, HPHB_S)) {
                    return Math.sin(psiC) * HPHB_S;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC50("cos(2*phiS)") {
            double calcValue(Map<String, Double> v) {
                Double phiS = v.get("phiS");
                if (ProteinPredictor.checkVars(phiS)) {
                    return Math.cos(2 * phiS);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC51("sin(2*psiS)") {
            double calcValue(Map<String, Double> v) {
                Double psiS = v.get("psiS");
                if (ProteinPredictor.checkVars(psiS)) {
                    return Math.sin(2 * psiS);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC52("sin(phiS)") {
            double calcValue(Map<String, Double> v) {
                Double phiS = v.get("phiS");
                if (ProteinPredictor.checkVars(phiS)) {
                    return Math.sin(phiS);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC53("cos(phiC)*sin(chi2C)") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                Double chi2C = v.get("chi2C");
                if (ProteinPredictor.checkVars(phiC, chi2C)) {
                    return Math.cos(phiC) * Math.sin(chi2C);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC54("sin(2*phiP)") {
            double calcValue(Map<String, Double> v) {
                Double phiP = v.get("phiP");
                if (ProteinPredictor.checkVars(phiP)) {
                    return Math.sin(2 * phiP);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC55("sin(psiP)") {
            double calcValue(Map<String, Double> v) {
                Double psiP = v.get("psiP");
                if (ProteinPredictor.checkVars(psiP)) {
                    return Math.sin(psiP);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC56("cos(phiS)") {
            double calcValue(Map<String, Double> v) {
                Double phiS = v.get("phiS");
                if (ProteinPredictor.checkVars(phiS)) {
                    return Math.cos(phiS);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC57("cos(3*phiS)") {
            double calcValue(Map<String, Double> v) {
                Double phiS = v.get("phiS");
                if (ProteinPredictor.checkVars(phiS)) {
                    return Math.cos(3 * phiS);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC58("cos(psiC)*sin(chi2C)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double chi2C = v.get("chi2C");
                if (ProteinPredictor.checkVars(psiC, chi2C)) {
                    return Math.cos(psiC) * Math.sin(chi2C);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC59("sin(phiC)*cos(chi2C)") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                Double chi2C = v.get("chi2C");
                if (ProteinPredictor.checkVars(phiC, chi2C)) {
                    return Math.sin(phiC) * Math.cos(chi2C);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC60("sin(psiC)*CHRG_S") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double CHRG_S = v.get("CHRG_S");
                if (ProteinPredictor.checkVars(psiC, CHRG_S)) {
                    return Math.sin(psiC) * CHRG_S;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC61("sin(2*phiC)") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                if (ProteinPredictor.checkVars(phiC)) {
                    return Math.sin(2 * phiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC62("cos(psiC)*sin(chiC)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double chiC = v.get("chiC");
                if (ProteinPredictor.checkVars(psiC, chiC)) {
                    return Math.cos(psiC) * Math.sin(chiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC63("cos(phiC)") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                if (ProteinPredictor.checkVars(phiC)) {
                    return Math.cos(phiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC64("sin(phiC)") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                if (ProteinPredictor.checkVars(phiC)) {
                    return Math.sin(phiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC65("cos(3*phiC)") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                if (ProteinPredictor.checkVars(phiC)) {
                    return Math.cos(3 * phiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC66("sin(chiC)*sin(chi2C)") {
            double calcValue(Map<String, Double> v) {
                Double chiC = v.get("chiC");
                Double chi2C = v.get("chi2C");
                if (ProteinPredictor.checkVars(chiC, chi2C)) {
                    return Math.sin(chiC) * Math.sin(chi2C);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC67("cos(psiC)*PRO_S") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double PRO_S = v.get("PRO_S");
                if (ProteinPredictor.checkVars(psiC, PRO_S)) {
                    return Math.cos(psiC) * PRO_S;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC68("sin(psiC)*cos(chiC)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double chiC = v.get("chiC");
                if (ProteinPredictor.checkVars(psiC, chiC)) {
                    return Math.sin(psiC) * Math.cos(chiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC69("sin(psiC)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                if (ProteinPredictor.checkVars(psiC)) {
                    return Math.sin(psiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC70("sin(chiC)*cos(chi2C)") {
            double calcValue(Map<String, Double> v) {
                Double chiC = v.get("chiC");
                Double chi2C = v.get("chi2C");
                if (ProteinPredictor.checkVars(chiC, chi2C)) {
                    return Math.sin(chiC) * Math.cos(chi2C);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC71("cos(phiC)*HPHB_P") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                Double HPHB_P = v.get("HPHB_P");
                if (ProteinPredictor.checkVars(phiC, HPHB_P)) {
                    return Math.cos(phiC) * HPHB_P;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC72("cos(phiC)*CHRG_P") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                Double CHRG_P = v.get("CHRG_P");
                if (ProteinPredictor.checkVars(phiC, CHRG_P)) {
                    return Math.cos(phiC) * CHRG_P;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC73("cos(psiC)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                if (ProteinPredictor.checkVars(psiC)) {
                    return Math.cos(psiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC74("sin(2*phiS)") {
            double calcValue(Map<String, Double> v) {
                Double phiS = v.get("phiS");
                if (ProteinPredictor.checkVars(phiS)) {
                    return Math.sin(2 * phiS);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC75("cos(chiC)") {
            double calcValue(Map<String, Double> v) {
                Double chiC = v.get("chiC");
                if (ProteinPredictor.checkVars(chiC)) {
                    return Math.cos(chiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC76("cos(phiC)*PRO_P") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                Double PRO_P = v.get("PRO_P");
                if (ProteinPredictor.checkVars(phiC, PRO_P)) {
                    return Math.cos(phiC) * PRO_P;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC77("sin(phiC)*CHRG_P") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                Double CHRG_P = v.get("CHRG_P");
                if (ProteinPredictor.checkVars(phiC, CHRG_P)) {
                    return Math.sin(phiC) * CHRG_P;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC78("cos(psiC)*sin(phiC)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double phiC = v.get("phiC");
                if (ProteinPredictor.checkVars(psiC, phiC)) {
                    return Math.cos(psiC) * Math.sin(phiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC79("sin(3*phiC)") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                if (ProteinPredictor.checkVars(phiC)) {
                    return Math.sin(3 * phiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC80("cos(3*psiP)") {
            double calcValue(Map<String, Double> v) {
                Double psiP = v.get("psiP");
                if (ProteinPredictor.checkVars(psiP)) {
                    return Math.cos(3 * psiP);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC81("cos(psiC)*sin(psiP)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double psiP = v.get("psiP");
                if (ProteinPredictor.checkVars(psiC, psiP)) {
                    return Math.cos(psiC) * Math.sin(psiP);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC82("DIS") {
            double calcValue(Map<String, Double> v) {
                Double DIS = v.get("DIS");
                if (ProteinPredictor.checkVars(DIS)) {
                    return DIS;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC83("sin(psiC)*sin(psiP)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double psiP = v.get("psiP");
                if (ProteinPredictor.checkVars(psiC, psiP)) {
                    return Math.sin(psiC) * Math.sin(psiP);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC84("eshift") {
            double calcValue(Map<String, Double> v) {
                Double eshift = v.get("eshift");
                if (ProteinPredictor.checkVars(eshift)) {
                    return eshift;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC85("sin(2*psiP)") {
            double calcValue(Map<String, Double> v) {
                Double psiP = v.get("psiP");
                if (ProteinPredictor.checkVars(psiP)) {
                    return Math.sin(2 * psiP);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC86("sin(phiC)*cos(chiC)") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                Double chiC = v.get("chiC");
                if (ProteinPredictor.checkVars(phiC, chiC)) {
                    return Math.sin(phiC) * Math.cos(chiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC87("sin(psiC)*sin(phiC)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double phiC = v.get("phiC");
                if (ProteinPredictor.checkVars(psiC, phiC)) {
                    return Math.sin(psiC) * Math.sin(phiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC88("sin(chiC)") {
            double calcValue(Map<String, Double> v) {
                Double chiC = v.get("chiC");
                if (ProteinPredictor.checkVars(chiC)) {
                    return Math.sin(chiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC89("sin(phiC)*BULK_P") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                Double BULK_P = v.get("BULK_P");
                if (ProteinPredictor.checkVars(phiC, BULK_P)) {
                    return Math.sin(phiC) * BULK_P;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC90("sin(psiC)*cos(phiC)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double phiC = v.get("phiC");
                if (ProteinPredictor.checkVars(psiC, phiC)) {
                    return Math.sin(psiC) * Math.cos(phiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC91("sin(psiC)*sin(chi2C)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double chi2C = v.get("chi2C");
                if (ProteinPredictor.checkVars(psiC, chi2C)) {
                    return Math.sin(psiC) * Math.sin(chi2C);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC92("cos(psiC)*cos(chi2C)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double chi2C = v.get("chi2C");
                if (ProteinPredictor.checkVars(psiC, chi2C)) {
                    return Math.cos(psiC) * Math.cos(chi2C);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC93("cos(2*chi2C)") {
            double calcValue(Map<String, Double> v) {
                Double chi2C = v.get("chi2C");
                if (ProteinPredictor.checkVars(chi2C)) {
                    return Math.cos(2 * chi2C);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC94("sin(phiC)*HPHB_P") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                Double HPHB_P = v.get("HPHB_P");
                if (ProteinPredictor.checkVars(phiC, HPHB_P)) {
                    return Math.sin(phiC) * HPHB_P;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC95("cos(3*phiP)") {
            double calcValue(Map<String, Double> v) {
                Double phiP = v.get("phiP");
                if (ProteinPredictor.checkVars(phiP)) {
                    return Math.cos(3 * phiP);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC96("cos(phiC)*sin(chiC)") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                Double chiC = v.get("chiC");
                if (ProteinPredictor.checkVars(phiC, chiC)) {
                    return Math.cos(phiC) * Math.sin(chiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC97("sin(2*chiC)") {
            double calcValue(Map<String, Double> v) {
                Double chiC = v.get("chiC");
                if (ProteinPredictor.checkVars(chiC)) {
                    return Math.sin(2 * chiC);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC98("sin(3*phiS)") {
            double calcValue(Map<String, Double> v) {
                Double phiS = v.get("phiS");
                if (ProteinPredictor.checkVars(phiS)) {
                    return Math.sin(3 * phiS);
                } else {
                    return 0.0;
                }
            }
        },
        FUNC99("sin(psiC)*BULK_S") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                Double BULK_S = v.get("BULK_S");
                if (ProteinPredictor.checkVars(psiC, BULK_S)) {
                    return Math.sin(psiC) * BULK_S;
                } else {
                    return 0.0;
                }
            }
        },
        FUNC100("contacts") {
            double calcValue(Map<String, Double> v) {
                Double contacts = v.get("contacts");
                if (ProteinPredictor.checkVars(contacts)) {
                    return contacts;
                } else {
                    return 0.0;
                }
            }
        },


        ;

        abstract double calcValue(Map<String, Double> v);

        String functionName;

        PredProps(String functionName) {
            this.functionName = functionName;
        }

    }

    public List<String> getValueNames() {
        List<String> names = new ArrayList<>();
        for (PredProps predProps : PredProps.values()) {
            names.add(predProps.functionName);
        }
        return names;
    }

    double[] getValues(Map<String, Double> valueMap) {
        double[] values = new double[PredProps.values().length];
        int i = 0;
        for (PredProps predProps : PredProps.values()) {
            values[i++] = predProps.calcValue(valueMap);
        }
        return values;
    }

    public Map<String, Double> getValueMap(Map<String, Double> valueMap) {
        Map<String, Double> map = new LinkedHashMap<>();
        int i = 0;
        for (PredProps predProps : PredProps.values()) {
            map.put(predProps.functionName, predProps.calcValue(valueMap));
        }
        return map;
    }

}

