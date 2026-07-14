package org.nmrfx.analyst.gui.molecule3D;

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.structure.chemistry.Molecule;
import org.python.util.PythonInterpreter;

import static org.nmrfx.analyst.gui.molecule3D.StructureCalculator.StructureMode.*;

public class StructureCalculator {
    public enum StructureMode {INIT, NUCLEIC_ACID, REFINE, CFF, ANNEAL, FULL}

    MolSceneController controller;
    private final StructureCalculatorService structureCalculatorService = new StructureCalculatorService();
    StructureMode mode;

    public StructureCalculator(MolSceneController controller) {
        this.controller = controller;
    }

    public void restart() {
        ((Service<?>) structureCalculatorService.worker).restart();
    }

    public void setMode(StructureMode mode) {
        this.mode = mode;
    }

    class StructureCalculatorService {
        String script;
        private final Worker<Integer> worker;


        private StructureCalculatorService() {
            worker = new Service<>() {

                protected Task<Integer> createTask() {
                    return new Task<>() {
                        protected Integer call() {
                            script = getScript(mode);
                            try (PythonInterpreter processInterp = new PythonInterpreter()) {
                                controller.updateStatus("Start calculating");
                                updateTitle("Start calculating");
                                processInterp.exec("import os\nfrom refine import *\nfrom molio import readYamlString\nimport osfiles");
                                String yamlString = genYaml(mode);
                                if (!yamlString.isBlank()) {
                                    processInterp.set("yamlString", yamlString);
                                    processInterp.exec(script);
                                }
                            }
                            return 0;
                        }
                    };
                }
            };

            ((Service<Integer>) worker).setOnSucceeded(event -> finishProcessing());
            ((Service<Integer>) worker).setOnCancelled(event -> {
                controller.setProcessingOff();
                controller.setProcessingStatus("cancelled", false);
            });
            ((Service<Integer>) worker).setOnFailed(event -> {
                controller.setProcessingOff();
                final Throwable exception = worker.getException();
                controller.setProcessingStatus(exception.getMessage(), false, exception);

            });

        }
    }

    String getScript(StructureCalculator.StructureMode mode) {
        StringBuilder scriptB = new StringBuilder();
        scriptB.append("homeDir = os.getcwd()\n");
        scriptB.append("print yamlString\n");
        scriptB.append("data=readYamlString(yamlString)\n");
        scriptB.append("global refiner\n");
        scriptB.append("dataDir=homeDir+'/'\n");
        scriptB.append("refiner=refine()\n");
        scriptB.append("osfiles.setOutFiles(refiner,dataDir,0)\n");
        scriptB.append("refiner.rootName = 'temp'\n");
        if (mode == NUCLEIC_ACID) {
            scriptB.append("refiner.loadFromYaml(data,0)\n");
            scriptB.append("refiner.init(save=False)\n");
        } else if (mode == INIT) {
            scriptB.append("refiner.loadFromYaml(data,0)\n");
            scriptB.append("refiner.init(save=False)\n");
        } else if (mode == REFINE) {
            scriptB.append("refiner.initAnneal(data)\n");
            scriptB.append("refiner.refine(refiner.dOpt)\n");
        } else if (mode == ANNEAL) {
            scriptB.append("refiner.initAnneal(data)\n");
            scriptB.append("refiner.refine(refiner.dOpt,'anneal')\n");
        } else if (mode == CFF) {
            scriptB.append("refiner.initAnneal(data)\n");
            scriptB.append("refiner.refine(refiner.dOpt,'cff')\n");
        } else if (mode == FULL) {
            scriptB.append("refiner.anneal(refiner.dOpt)\n");
        }
        return scriptB.toString();
    }

    String genYaml(StructureMode mode) {
        if (mode == NUCLEIC_ACID) {
            return genRNAYaml();
        } else {
            return genAnnealYaml();
        }
    }

    boolean alreadyLinked() {
        Molecule molecule = Molecule.getActive();
        boolean linked = false;
        for (Atom atom : molecule.getAtoms()) {
            if (atom.getName().startsWith("X")) {
                linked = true;
                break;
            }
        }
        return linked;
    }

    String genRNAYaml() {
        Molecule molecule = Molecule.getActive();
        if (alreadyLinked()) {
            return "";
        }
        boolean isNucliecAcid = false;
        if (!molecule.getPolymers().isEmpty()) {
            isNucliecAcid = molecule.getPolymers().getFirst().isRNA() || molecule.getPolymers().getFirst().isDNA();
        }
        StringBuilder yamlBuilder = new StringBuilder();
        if (isNucliecAcid && (mode == NUCLEIC_ACID)) {
            yamlBuilder.append("rna:\n");
            yamlBuilder.append("    ribose : Constrain\n");
            String dotBracket = molecule.getDotBracket();
            if (!dotBracket.isEmpty()) {
                yamlBuilder.append("    vienna : ");
                yamlBuilder.append("'").append(dotBracket).append("'\n");
            }
            yamlBuilder.append("""
                        planarity : 1
                        autolink : True
                    tree:
                    initialize:
                        vienna :
                            restrain : True
                            lockfirst: False
                            locklast: True
                            lockloop: False
                            lockbulge: False
                    anneal:
                        dynOptions :

                    """);
        }
        return yamlBuilder.toString();
    }

    String genAnnealYaml() {

        return """
                anneal:
                    dynOptions :
                        steps : 15000
                        highTemp : 5000.0
                        dfreeSteps : 0
                    force :
                        tors : 0.1
                        irp : 0.0
                        tors : 0.1
                    stage4.1 :
                        nStepVal : 5000
                        tempVal : [100.0]
                        param:
                            dislim : 6.0
                        force :
                            cffnb : 1
                            repel : -1""";

    }


    void finishProcessing() {
        controller.updateStatus("Done calculating");
    }

}
