package org.nmrfx.structure.chemistry.predict;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProteinPredictorGen {

    enum PredProps {
        func0("cos(chiC)*sin(chi2C)") {
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
        func1("sin(2*chi2C)") {
            double calcValue(Map<String, Double> v) {
                Double chi2C = v.get("chi2C");
                if (ProteinPredictor.checkVars(chi2C)) {
                    return Math.sin(2 * chi2C);
                } else {
                    return 0.0;
                }
            }
        },
        func2("cos(psiC)*cos(psiP)") {
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
        func3("sin(3*psiP)") {
            double calcValue(Map<String, Double> v) {
                Double psiP = v.get("psiP");
                if (ProteinPredictor.checkVars(psiP)) {
                    return Math.sin(3 * psiP);
                } else {
                    return 0.0;
                }
            }
        },
        func4("cos(2*psiS)") {
            double calcValue(Map<String, Double> v) {
                Double psiS = v.get("psiS");
                if (ProteinPredictor.checkVars(psiS)) {
                    return Math.cos(2 * psiS);
                } else {
                    return 0.0;
                }
            }
        },
        func5("sin(phiC)*PRO_P") {
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
        func6("cos(2*chiC)") {
            double calcValue(Map<String, Double> v) {
                Double chiC = v.get("chiC");
                if (ProteinPredictor.checkVars(chiC)) {
                    return Math.cos(2 * chiC);
                } else {
                    return 0.0;
                }
            }
        },
        func7("sin(phiP)") {
            double calcValue(Map<String, Double> v) {
                Double phiP = v.get("phiP");
                if (ProteinPredictor.checkVars(phiP)) {
                    return Math.sin(phiP);
                } else {
                    return 0.0;
                }
            }
        },
        func8("sin(3*psiC)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                if (ProteinPredictor.checkVars(psiC)) {
                    return Math.sin(3 * psiC);
                } else {
                    return 0.0;
                }
            }
        },
        func9("cos(psiP)") {
            double calcValue(Map<String, Double> v) {
                Double psiP = v.get("psiP");
                if (ProteinPredictor.checkVars(psiP)) {
                    return Math.cos(psiP);
                } else {
                    return 0.0;
                }
            }
        },
        func10("cos(2*phiP)") {
            double calcValue(Map<String, Double> v) {
                Double phiP = v.get("phiP");
                if (ProteinPredictor.checkVars(phiP)) {
                    return Math.cos(2 * phiP);
                } else {
                    return 0.0;
                }
            }
        },
        func11("cos(phiC)*cos(chiC)") {
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
        func12("sin(psiC)*ARO_S") {
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
        func13("sin(3*phiP)") {
            double calcValue(Map<String, Double> v) {
                Double phiP = v.get("phiP");
                if (ProteinPredictor.checkVars(phiP)) {
                    return Math.sin(3 * phiP);
                } else {
                    return 0.0;
                }
            }
        },
        func14("h3") {
            double calcValue(Map<String, Double> v) {
                Double h3 = v.get("h3");
                if (ProteinPredictor.checkVars(h3)) {
                    return h3;
                } else {
                    return 0.0;
                }
            }
        },
        func15("sin(psiC)*cos(chi2C)") {
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
        func16("sin(chi2C)") {
            double calcValue(Map<String, Double> v) {
                Double chi2C = v.get("chi2C");
                if (ProteinPredictor.checkVars(chi2C)) {
                    return Math.sin(chi2C);
                } else {
                    return 0.0;
                }
            }
        },
        func17("cos(phiP)") {
            double calcValue(Map<String, Double> v) {
                Double phiP = v.get("phiP");
                if (ProteinPredictor.checkVars(phiP)) {
                    return Math.cos(phiP);
                } else {
                    return 0.0;
                }
            }
        },
        func18("sin(3*psiS)") {
            double calcValue(Map<String, Double> v) {
                Double psiS = v.get("psiS");
                if (ProteinPredictor.checkVars(psiS)) {
                    return Math.sin(3 * psiS);
                } else {
                    return 0.0;
                }
            }
        },
        func19("cos(3*psiS)") {
            double calcValue(Map<String, Double> v) {
                Double psiS = v.get("psiS");
                if (ProteinPredictor.checkVars(psiS)) {
                    return Math.cos(3 * psiS);
                } else {
                    return 0.0;
                }
            }
        },
        func20("cos(psiC)*cos(chiC)") {
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
        func21("cos(phiC)*BULK_P") {
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
        func22("ring") {
            double calcValue(Map<String, Double> v) {
                Double ring = v.get("ring");
                if (ProteinPredictor.checkVars(ring)) {
                    return ring;
                } else {
                    return 0.0;
                }
            }
        },
        func23("cos(2*psiC)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                if (ProteinPredictor.checkVars(psiC)) {
                    return Math.cos(2 * psiC);
                } else {
                    return 0.0;
                }
            }
        },
        func24("cos(phiC)*ARO_P") {
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
        func25("cos(phiC)*cos(chi2C)") {
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
        func26("cos(psiC)*cos(phiC)") {
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
        func27("sin(2*psiC)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                if (ProteinPredictor.checkVars(psiC)) {
                    return Math.sin(2 * psiC);
                } else {
                    return 0.0;
                }
            }
        },
        func28("sin(phiC)*ARO_P") {
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
        func29("cos(psiC)*ARO_S") {
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
        func30("cos(psiC)*CHRG_S") {
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
        func31("cos(psiC)*HPHB_S") {
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
        func32("sin(psiC)*cos(psiP)") {
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
        func33("cos(psiS)") {
            double calcValue(Map<String, Double> v) {
                Double psiS = v.get("psiS");
                if (ProteinPredictor.checkVars(psiS)) {
                    return Math.cos(psiS);
                } else {
                    return 0.0;
                }
            }
        },
        func34("cos(chi2C)") {
            double calcValue(Map<String, Double> v) {
                Double chi2C = v.get("chi2C");
                if (ProteinPredictor.checkVars(chi2C)) {
                    return Math.cos(chi2C);
                } else {
                    return 0.0;
                }
            }
        },
        func35("hshift3") {
            double calcValue(Map<String, Double> v) {
                Double hshift3 = v.get("hshift3");
                if (ProteinPredictor.checkVars(hshift3)) {
                    return hshift3;
                } else {
                    return 0.0;
                }
            }
        },
        func36("hshift2") {
            double calcValue(Map<String, Double> v) {
                Double hshift2 = v.get("hshift2");
                if (ProteinPredictor.checkVars(hshift2)) {
                    return hshift2;
                } else {
                    return 0.0;
                }
            }
        },
        func37("hshift1") {
            double calcValue(Map<String, Double> v) {
                Double hshift1 = v.get("hshift1");
                if (ProteinPredictor.checkVars(hshift1)) {
                    return hshift1;
                } else {
                    return 0.0;
                }
            }
        },
        func38("sin(phiC)*sin(chiC)") {
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
        func39("sin(psiC)*PRO_S") {
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
        func40("sin(phiC)*sin(chi2C)") {
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
        func41("cos(psiC)*BULK_S") {
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
        func42("cos(chiC)*cos(chi2C)") {
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
        func43("cos(3*psiC)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                if (ProteinPredictor.checkVars(psiC)) {
                    return Math.cos(3 * psiC);
                } else {
                    return 0.0;
                }
            }
        },
        func44("sin(psiS)") {
            double calcValue(Map<String, Double> v) {
                Double psiS = v.get("psiS");
                if (ProteinPredictor.checkVars(psiS)) {
                    return Math.sin(psiS);
                } else {
                    return 0.0;
                }
            }
        },
        func45("cos(2*psiP)") {
            double calcValue(Map<String, Double> v) {
                Double psiP = v.get("psiP");
                if (ProteinPredictor.checkVars(psiP)) {
                    return Math.cos(2 * psiP);
                } else {
                    return 0.0;
                }
            }
        },
        func46("intercept") {
            double calcValue(Map<String, Double> v) {
                Double intercept = v.get("intercept");
                if (ProteinPredictor.checkVars(intercept)) {
                    return intercept;
                } else {
                    return 0.0;
                }
            }
        },
        func47("cos(2*phiC)") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                if (ProteinPredictor.checkVars(phiC)) {
                    return Math.cos(2 * phiC);
                } else {
                    return 0.0;
                }
            }
        },
        func48("sin(psiC)*sin(chiC)") {
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
        func49("sin(psiC)*HPHB_S") {
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
        func50("cos(2*phiS)") {
            double calcValue(Map<String, Double> v) {
                Double phiS = v.get("phiS");
                if (ProteinPredictor.checkVars(phiS)) {
                    return Math.cos(2 * phiS);
                } else {
                    return 0.0;
                }
            }
        },
        func51("sin(2*psiS)") {
            double calcValue(Map<String, Double> v) {
                Double psiS = v.get("psiS");
                if (ProteinPredictor.checkVars(psiS)) {
                    return Math.sin(2 * psiS);
                } else {
                    return 0.0;
                }
            }
        },
        func52("sin(phiS)") {
            double calcValue(Map<String, Double> v) {
                Double phiS = v.get("phiS");
                if (ProteinPredictor.checkVars(phiS)) {
                    return Math.sin(phiS);
                } else {
                    return 0.0;
                }
            }
        },
        func53("cos(phiC)*sin(chi2C)") {
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
        func54("sin(2*phiP)") {
            double calcValue(Map<String, Double> v) {
                Double phiP = v.get("phiP");
                if (ProteinPredictor.checkVars(phiP)) {
                    return Math.sin(2 * phiP);
                } else {
                    return 0.0;
                }
            }
        },
        func55("sin(psiP)") {
            double calcValue(Map<String, Double> v) {
                Double psiP = v.get("psiP");
                if (ProteinPredictor.checkVars(psiP)) {
                    return Math.sin(psiP);
                } else {
                    return 0.0;
                }
            }
        },
        func56("cos(phiS)") {
            double calcValue(Map<String, Double> v) {
                Double phiS = v.get("phiS");
                if (ProteinPredictor.checkVars(phiS)) {
                    return Math.cos(phiS);
                } else {
                    return 0.0;
                }
            }
        },
        func57("cos(3*phiS)") {
            double calcValue(Map<String, Double> v) {
                Double phiS = v.get("phiS");
                if (ProteinPredictor.checkVars(phiS)) {
                    return Math.cos(3 * phiS);
                } else {
                    return 0.0;
                }
            }
        },
        func58("cos(psiC)*sin(chi2C)") {
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
        func59("sin(phiC)*cos(chi2C)") {
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
        func60("sin(psiC)*CHRG_S") {
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
        func61("sin(2*phiC)") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                if (ProteinPredictor.checkVars(phiC)) {
                    return Math.sin(2 * phiC);
                } else {
                    return 0.0;
                }
            }
        },
        func62("cos(psiC)*sin(chiC)") {
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
        func63("cos(phiC)") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                if (ProteinPredictor.checkVars(phiC)) {
                    return Math.cos(phiC);
                } else {
                    return 0.0;
                }
            }
        },
        func64("sin(phiC)") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                if (ProteinPredictor.checkVars(phiC)) {
                    return Math.sin(phiC);
                } else {
                    return 0.0;
                }
            }
        },
        func65("cos(3*phiC)") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                if (ProteinPredictor.checkVars(phiC)) {
                    return Math.cos(3 * phiC);
                } else {
                    return 0.0;
                }
            }
        },
        func66("sin(chiC)*sin(chi2C)") {
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
        func67("cos(psiC)*PRO_S") {
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
        func68("sin(psiC)*cos(chiC)") {
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
        func69("sin(psiC)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                if (ProteinPredictor.checkVars(psiC)) {
                    return Math.sin(psiC);
                } else {
                    return 0.0;
                }
            }
        },
        func70("sin(chiC)*cos(chi2C)") {
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
        func71("cos(phiC)*HPHB_P") {
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
        func72("cos(phiC)*CHRG_P") {
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
        func73("cos(psiC)") {
            double calcValue(Map<String, Double> v) {
                Double psiC = v.get("psiC");
                if (ProteinPredictor.checkVars(psiC)) {
                    return Math.cos(psiC);
                } else {
                    return 0.0;
                }
            }
        },
        func74("sin(2*phiS)") {
            double calcValue(Map<String, Double> v) {
                Double phiS = v.get("phiS");
                if (ProteinPredictor.checkVars(phiS)) {
                    return Math.sin(2 * phiS);
                } else {
                    return 0.0;
                }
            }
        },
        func75("cos(chiC)") {
            double calcValue(Map<String, Double> v) {
                Double chiC = v.get("chiC");
                if (ProteinPredictor.checkVars(chiC)) {
                    return Math.cos(chiC);
                } else {
                    return 0.0;
                }
            }
        },
        func76("cos(phiC)*PRO_P") {
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
        func77("sin(phiC)*CHRG_P") {
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
        func78("cos(psiC)*sin(phiC)") {
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
        func79("sin(3*phiC)") {
            double calcValue(Map<String, Double> v) {
                Double phiC = v.get("phiC");
                if (ProteinPredictor.checkVars(phiC)) {
                    return Math.sin(3 * phiC);
                } else {
                    return 0.0;
                }
            }
        },
        func80("cos(3*psiP)") {
            double calcValue(Map<String, Double> v) {
                Double psiP = v.get("psiP");
                if (ProteinPredictor.checkVars(psiP)) {
                    return Math.cos(3 * psiP);
                } else {
                    return 0.0;
                }
            }
        },
        func81("cos(psiC)*sin(psiP)") {
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
        func82("DIS") {
            double calcValue(Map<String, Double> v) {
                Double DIS = v.get("DIS");
                if (ProteinPredictor.checkVars(DIS)) {
                    return DIS;
                } else {
                    return 0.0;
                }
            }
        },
        func83("sin(psiC)*sin(psiP)") {
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
        func84("eshift") {
            double calcValue(Map<String, Double> v) {
                Double eshift = v.get("eshift");
                if (ProteinPredictor.checkVars(eshift)) {
                    return eshift;
                } else {
                    return 0.0;
                }
            }
        },
        func85("sin(2*psiP)") {
            double calcValue(Map<String, Double> v) {
                Double psiP = v.get("psiP");
                if (ProteinPredictor.checkVars(psiP)) {
                    return Math.sin(2 * psiP);
                } else {
                    return 0.0;
                }
            }
        },
        func86("sin(phiC)*cos(chiC)") {
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
        func87("sin(psiC)*sin(phiC)") {
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
        func88("sin(chiC)") {
            double calcValue(Map<String, Double> v) {
                Double chiC = v.get("chiC");
                if (ProteinPredictor.checkVars(chiC)) {
                    return Math.sin(chiC);
                } else {
                    return 0.0;
                }
            }
        },
        func89("sin(phiC)*BULK_P") {
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
        func90("sin(psiC)*cos(phiC)") {
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
        func91("sin(psiC)*sin(chi2C)") {
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
        func92("cos(psiC)*cos(chi2C)") {
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
        func93("cos(2*chi2C)") {
            double calcValue(Map<String, Double> v) {
                Double chi2C = v.get("chi2C");
                if (ProteinPredictor.checkVars(chi2C)) {
                    return Math.cos(2 * chi2C);
                } else {
                    return 0.0;
                }
            }
        },
        func94("sin(phiC)*HPHB_P") {
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
        func95("cos(3*phiP)") {
            double calcValue(Map<String, Double> v) {
                Double phiP = v.get("phiP");
                if (ProteinPredictor.checkVars(phiP)) {
                    return Math.cos(3 * phiP);
                } else {
                    return 0.0;
                }
            }
        },
        func96("cos(phiC)*sin(chiC)") {
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
        func97("sin(2*chiC)") {
            double calcValue(Map<String, Double> v) {
                Double chiC = v.get("chiC");
                if (ProteinPredictor.checkVars(chiC)) {
                    return Math.sin(2 * chiC);
                } else {
                    return 0.0;
                }
            }
        },
        func98("sin(3*phiS)") {
            double calcValue(Map<String, Double> v) {
                Double phiS = v.get("phiS");
                if (ProteinPredictor.checkVars(phiS)) {
                    return Math.sin(3 * phiS);
                } else {
                    return 0.0;
                }
            }
        },
        func99("sin(psiC)*BULK_S") {
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
        func100("contacts") {
            double calcValue(Map<String, Double> v) {
                return v.get("contacts");
            }
        };


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

    public static ProteinPredictorResult predict(Map<String, Double> valueMap, double[] coefs, double[] minMax, boolean explain) {
        double[] attrValue = new double[100];
        Double psiC = valueMap.get("psiC");
        Double h3 = valueMap.get("h3");
        Double psiS = valueMap.get("psiS");
        Double ARO_P = valueMap.get("ARO_P");
        Double ARO_S = valueMap.get("ARO_S");
        Double hshift3 = valueMap.get("hshift3");
        Double hshift2 = valueMap.get("hshift2");
        Double ring = valueMap.get("ring");
        Double CHRG_S = valueMap.get("CHRG_S");
        Double hshift1 = valueMap.get("hshift1");
        Double CHRG_P = valueMap.get("CHRG_P");
        Double eshift = valueMap.get("eshift");
        Double chi2C = valueMap.get("chi2C");
        Double psiP = valueMap.get("psiP");
        Double chiC = valueMap.get("chiC");
        Double DIS = valueMap.get("DIS");
        Double BULK_S = valueMap.get("BULK_S");
        Double BULK_P = valueMap.get("BULK_P");
        Double phiC = valueMap.get("phiC");
        Double HPHB_P = valueMap.get("HPHB_P");
        Double HPHB_S = valueMap.get("HPHB_S");
        Double intercept = valueMap.get("intercept");
        Double phiS = valueMap.get("phiS");
        Double phiP = valueMap.get("phiP");
        Double PRO_P = valueMap.get("PRO_P");
        Double PRO_S = valueMap.get("PRO_S");
        if (ProteinPredictor.checkAngles(chiC, chi2C)) {
            attrValue[0] = Math.cos(chiC) * Math.sin(chi2C);
        } else {
            attrValue[0] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chi2C)) {
            attrValue[1] = Math.sin(2 * chi2C);
        } else {
            attrValue[1] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP, psiC)) {
            attrValue[2] = Math.cos(psiC) * Math.cos(psiP);
        } else {
            attrValue[2] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP)) {
            attrValue[3] = Math.sin(3 * psiP);
        } else {
            attrValue[3] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiS)) {
            attrValue[4] = Math.cos(2 * psiS);
        } else {
            attrValue[4] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[5] = Math.sin(phiC) * PRO_P;
        } else {
            attrValue[5] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chiC)) {
            attrValue[6] = Math.cos(2 * chiC);
        } else {
            attrValue[6] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiP)) {
            attrValue[7] = Math.sin(phiP);
        } else {
            attrValue[7] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[8] = Math.sin(3 * psiC);
        } else {
            attrValue[8] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP)) {
            attrValue[9] = Math.cos(psiP);
        } else {
            attrValue[9] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiP)) {
            attrValue[10] = Math.cos(2 * phiP);
        } else {
            attrValue[10] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC, chiC)) {
            attrValue[11] = Math.cos(phiC) * Math.cos(chiC);
        } else {
            attrValue[11] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[12] = Math.sin(psiC) * ARO_S;
        } else {
            attrValue[12] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiP)) {
            attrValue[13] = Math.sin(3 * phiP);
        } else {
            attrValue[13] = 0.0;
        }
        attrValue[14] = h3;
        if (ProteinPredictor.checkAngles(psiC, chi2C)) {
            attrValue[15] = Math.sin(psiC) * Math.cos(chi2C);
        } else {
            attrValue[15] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chi2C)) {
            attrValue[16] = Math.sin(chi2C);
        } else {
            attrValue[16] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiP)) {
            attrValue[17] = Math.cos(phiP);
        } else {
            attrValue[17] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiS)) {
            attrValue[18] = Math.sin(3 * psiS);
        } else {
            attrValue[18] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiS)) {
            attrValue[19] = Math.cos(3 * psiS);
        } else {
            attrValue[19] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, chiC)) {
            attrValue[20] = Math.cos(psiC) * Math.cos(chiC);
        } else {
            attrValue[20] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[21] = Math.cos(phiC) * BULK_P;
        } else {
            attrValue[21] = 0.0;
        }
        attrValue[22] = ring;
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[23] = Math.cos(2 * psiC);
        } else {
            attrValue[23] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[24] = Math.cos(phiC) * ARO_P;
        } else {
            attrValue[24] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC, chi2C)) {
            attrValue[25] = Math.cos(phiC) * Math.cos(chi2C);
        } else {
            attrValue[25] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, phiC)) {
            attrValue[26] = Math.cos(psiC) * Math.cos(phiC);
        } else {
            attrValue[26] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[27] = Math.sin(2 * psiC);
        } else {
            attrValue[27] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[28] = Math.sin(phiC) * ARO_P;
        } else {
            attrValue[28] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[29] = Math.cos(psiC) * ARO_S;
        } else {
            attrValue[29] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[30] = Math.cos(psiC) * CHRG_S;
        } else {
            attrValue[30] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[31] = Math.cos(psiC) * HPHB_S;
        } else {
            attrValue[31] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP, psiC)) {
            attrValue[32] = Math.sin(psiC) * Math.cos(psiP);
        } else {
            attrValue[32] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiS)) {
            attrValue[33] = Math.cos(psiS);
        } else {
            attrValue[33] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chi2C)) {
            attrValue[34] = Math.cos(chi2C);
        } else {
            attrValue[34] = 0.0;
        }
        attrValue[35] = hshift3;
        attrValue[36] = hshift2;
        attrValue[37] = hshift1;
        if (ProteinPredictor.checkAngles(phiC, chiC)) {
            attrValue[38] = Math.sin(phiC) * Math.sin(chiC);
        } else {
            attrValue[38] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[39] = Math.sin(psiC) * PRO_S;
        } else {
            attrValue[39] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC, chi2C)) {
            attrValue[40] = Math.sin(phiC) * Math.sin(chi2C);
        } else {
            attrValue[40] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[41] = Math.cos(psiC) * BULK_S;
        } else {
            attrValue[41] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chiC, chi2C)) {
            attrValue[42] = Math.cos(chiC) * Math.cos(chi2C);
        } else {
            attrValue[42] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[43] = Math.cos(3 * psiC);
        } else {
            attrValue[43] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiS)) {
            attrValue[44] = Math.sin(psiS);
        } else {
            attrValue[44] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP)) {
            attrValue[45] = Math.cos(2 * psiP);
        } else {
            attrValue[45] = 0.0;
        }
        attrValue[46] = 0.0;
        int interceptCoef = 46;
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[47] = Math.cos(2 * phiC);
        } else {
            attrValue[47] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, chiC)) {
            attrValue[48] = Math.sin(psiC) * Math.sin(chiC);
        } else {
            attrValue[48] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[49] = Math.sin(psiC) * HPHB_S;
        } else {
            attrValue[49] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiS)) {
            attrValue[50] = Math.cos(2 * phiS);
        } else {
            attrValue[50] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiS)) {
            attrValue[51] = Math.sin(2 * psiS);
        } else {
            attrValue[51] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiS)) {
            attrValue[52] = Math.sin(phiS);
        } else {
            attrValue[52] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC, chi2C)) {
            attrValue[53] = Math.cos(phiC) * Math.sin(chi2C);
        } else {
            attrValue[53] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiP)) {
            attrValue[54] = Math.sin(2 * phiP);
        } else {
            attrValue[54] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP)) {
            attrValue[55] = Math.sin(psiP);
        } else {
            attrValue[55] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiS)) {
            attrValue[56] = Math.cos(phiS);
        } else {
            attrValue[56] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiS)) {
            attrValue[57] = Math.cos(3 * phiS);
        } else {
            attrValue[57] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, chi2C)) {
            attrValue[58] = Math.cos(psiC) * Math.sin(chi2C);
        } else {
            attrValue[58] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC, chi2C)) {
            attrValue[59] = Math.sin(phiC) * Math.cos(chi2C);
        } else {
            attrValue[59] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[60] = Math.sin(psiC) * CHRG_S;
        } else {
            attrValue[60] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[61] = Math.sin(2 * phiC);
        } else {
            attrValue[61] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, chiC)) {
            attrValue[62] = Math.cos(psiC) * Math.sin(chiC);
        } else {
            attrValue[62] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[63] = Math.cos(phiC);
        } else {
            attrValue[63] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[64] = Math.sin(phiC);
        } else {
            attrValue[64] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[65] = Math.cos(3 * phiC);
        } else {
            attrValue[65] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chiC, chi2C)) {
            attrValue[66] = Math.sin(chiC) * Math.sin(chi2C);
        } else {
            attrValue[66] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[67] = Math.cos(psiC) * PRO_S;
        } else {
            attrValue[67] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, chiC)) {
            attrValue[68] = Math.sin(psiC) * Math.cos(chiC);
        } else {
            attrValue[68] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[69] = Math.sin(psiC);
        } else {
            attrValue[69] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chiC, chi2C)) {
            attrValue[70] = Math.sin(chiC) * Math.cos(chi2C);
        } else {
            attrValue[70] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[71] = Math.cos(phiC) * HPHB_P;
        } else {
            attrValue[71] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[72] = Math.cos(phiC) * CHRG_P;
        } else {
            attrValue[72] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[73] = Math.cos(psiC);
        } else {
            attrValue[73] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiS)) {
            attrValue[74] = Math.sin(2 * phiS);
        } else {
            attrValue[74] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chiC)) {
            attrValue[75] = Math.cos(chiC);
        } else {
            attrValue[75] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[76] = Math.cos(phiC) * PRO_P;
        } else {
            attrValue[76] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[77] = Math.sin(phiC) * CHRG_P;
        } else {
            attrValue[77] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, phiC)) {
            attrValue[78] = Math.cos(psiC) * Math.sin(phiC);
        } else {
            attrValue[78] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[79] = Math.sin(3 * phiC);
        } else {
            attrValue[79] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP)) {
            attrValue[80] = Math.cos(3 * psiP);
        } else {
            attrValue[80] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiP, psiC)) {
            attrValue[81] = Math.cos(psiC) * Math.sin(psiP);
        } else {
            attrValue[81] = 0.0;
        }
        attrValue[82] = DIS;
        if (ProteinPredictor.checkAngles(psiP, psiC)) {
            attrValue[83] = Math.sin(psiC) * Math.sin(psiP);
        } else {
            attrValue[83] = 0.0;
        }
        if (eshift != null) {
            attrValue[84] = eshift;
        }
        if (ProteinPredictor.checkAngles(psiP)) {
            attrValue[85] = Math.sin(2 * psiP);
        } else {
            attrValue[85] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC, chiC)) {
            attrValue[86] = Math.sin(phiC) * Math.cos(chiC);
        } else {
            attrValue[86] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, phiC)) {
            attrValue[87] = Math.sin(psiC) * Math.sin(phiC);
        } else {
            attrValue[87] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chiC)) {
            attrValue[88] = Math.sin(chiC);
        } else {
            attrValue[88] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[89] = Math.sin(phiC) * BULK_P;
        } else {
            attrValue[89] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, phiC)) {
            attrValue[90] = Math.sin(psiC) * Math.cos(phiC);
        } else {
            attrValue[90] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, chi2C)) {
            attrValue[91] = Math.sin(psiC) * Math.sin(chi2C);
        } else {
            attrValue[91] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC, chi2C)) {
            attrValue[92] = Math.cos(psiC) * Math.cos(chi2C);
        } else {
            attrValue[92] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chi2C)) {
            attrValue[93] = Math.cos(2 * chi2C);
        } else {
            attrValue[93] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC)) {
            attrValue[94] = Math.sin(phiC) * HPHB_P;
        } else {
            attrValue[94] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiP)) {
            attrValue[95] = Math.cos(3 * phiP);
        } else {
            attrValue[95] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiC, chiC)) {
            attrValue[96] = Math.cos(phiC) * Math.sin(chiC);
        } else {
            attrValue[96] = 0.0;
        }
        if (ProteinPredictor.checkAngles(chiC)) {
            attrValue[97] = Math.sin(2 * chiC);
        } else {
            attrValue[97] = 0.0;
        }
        if (ProteinPredictor.checkAngles(phiS)) {
            attrValue[98] = Math.sin(3 * phiS);
        } else {
            attrValue[98] = 0.0;
        }
        if (ProteinPredictor.checkAngles(psiC)) {
            attrValue[99] = Math.sin(psiC) * BULK_S;
        } else {
            attrValue[99] = 0.0;
        }

        double contactSum = valueMap.get("contacts");
        double scale = ProteinPredictor.calcDisorderScale(contactSum, minMax);
        double sum = coefs[interceptCoef];
        for (int i = 0; i < attrValue.length; i++) {
            sum += scale * attrValue[i] * coefs[i];
        }

        ProteinPredictorResult result;
        if (explain) {
            result = new ProteinPredictorResult(coefs, attrValue, sum);
        } else {
            result = new ProteinPredictorResult(sum);
        }
        return result;
    }

}
