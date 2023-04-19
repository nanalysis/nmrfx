package org.nmrfx.structure.chemistry.miner;

import java.io.*;
import java.util.List;
import java.util.Optional;

import com.actelion.research.chem.MolfileCreator;
import com.actelion.research.chem.StereoMolecule;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Entity;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.SDFile;
import org.nmrfx.structure.chemistry.OpenChemLibConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TurboMol {

    private static final Logger log = LoggerFactory.getLogger(TurboMol.class);
    String inputData = null;
    String inFileName = null;
    String outValue = null;
    Object inMolecule = null;
    BufferedReader bufReader = null;
    String currentLine = null;

    public Optional<MoleculeBase> getMolecule() {
        if (inMolecule instanceof MoleculeBase mol) {
            return Optional.of(mol);
        } else {
            return Optional.empty();
        }
    }

    public void setMolecule(Molecule iMol) {
        this.inMolecule = iMol;
    }

    public void labelAtoms() {
        getMolecule().ifPresent(mol ->{
            int i = 0;
            for (Entity entity : mol.entities.values()) {
                for (Atom atom : entity.getAtoms()) {
                    atom.setID(i++);
                }
            }
        });
    }

    public void removeHydrogens() {
        getMolecule().ifPresent(mol -> {
            int i = 0;
            mol.entities.values().forEach((entity) -> {
                entity.getAtoms().stream().filter((atom) -> (atom.getAtomicNumber() == 1)).forEachOrdered((atom) -> {
                    atom.remove();
                });
            });
        });
    }

    public void getNeighborCount() {
        getMolecule().ifPresent(mol -> {

            int i = 0;
            mol.entities.values().forEach((entity) -> {
                entity.getAtoms().stream().filter((atom) -> (atom.getAtomicNumber() == 1)).forEachOrdered((atom) -> {
                    atom.getConnected().size();
                });
            });
        });
    }

    //    public IAtom getAtom(final int index) {
//        IAtom atom = null;
//        if (inMolecule != null) {
//            atom = inMolecule.getAtom(index);
//        }
//        return atom;
//    }
//
//    public int getAtomCount(final int element) {
//        int atomCount = 0;
//        if (inMolecule != null) {
//            for (IAtom atom : inMolecule.atoms()) {
//                if (element == atom.getAtomicNumber()) {
//                    atomCount++;
//                }
//            }
//        }
//        return atomCount;
//    }
    public void setInputFromString(String dataString) {
        inputData = dataString;
    }

    public void openSDFile(String fileName) {
        try {
            bufReader = new BufferedReader(new FileReader(fileName));
        } catch (FileNotFoundException fnfE) {
            System.out.println(fnfE.toString());
            return;
        }
    }

    public boolean setInputFromNextSDMol() {
        if (bufReader == null) {
            return false;
        }

        try {
            String s = null;
            StringBuffer sBuf = new StringBuffer();

            while ((s = bufReader.readLine()) != null) {
                sBuf.append(s);
                sBuf.append('\n');

                if (s.startsWith("$$$$")) {
                    inputData = sBuf.toString();

                    return true;
                }
            }

            bufReader = null;

            return false;
        } catch (IOException ioE) {
            bufReader = null;

            return false;
        }
    }

    String getLine() {
        String s = null;
        try {
            if (currentLine == null) {
                s = bufReader.readLine();
            } else {
                s = currentLine;
                currentLine = null;
            }
        } catch (IOException ioE) {
            bufReader = null;
            s=null;
        }
        return s;
    }

    void restoreLine(String s) {
        currentLine = s;
    }

    /*
    dsgdb9nsd_000001,0,C,-0.012698135900000001,1.0858041578,0.008000995799999999
    dsgdb9nsd_000001,1,H,0.002150416,-0.0060313176,0.0019761204
    dsgdb9nsd_000001,2,H,1.0117308433,1.4637511618,0.0002765748
    dsgdb9nsd_000001,3,H,-0.540815069,1.4475266138,-0.8766437152
    dsgdb9nsd_000001,4,H,-0.5238136345000001,1.4379326443,0.9063972942
    dsgdb9nsd_000002,0,N,-0.0404260543,1.0241077531,0.0625637998

     */
    public boolean setInputFromNextXYZ() {
        if (bufReader == null) {
            return false;
        }
        String molName = null;
        String s = null;
        StringBuilder sBuf = new StringBuilder();
        while ((s = getLine()) != null) {
            int commaPos = s.indexOf(',');
            String thisMol = s.substring(0, commaPos);
            if (molName == null) {
                molName = thisMol;
                sBuf.append(s).append('\n');
            } else if (molName.equals(thisMol)) {
                sBuf.append(s).append('\n');
            } else {
                restoreLine(s);
                break;
            }
        }
        if (!sBuf.isEmpty()) {
            inputData = sBuf.toString();
            return true;
        }
        bufReader = null;
        return false;
    }

    public StereoMolecule fromXYZ()  {
        StereoMolecule stereoMolecule = null;
        try {
            stereoMolecule = OpenChemLibConverter.convertXYZToStereoMolecule(inputData);
            stereoMolecule.ensureHelperArrays(StereoMolecule.cHelperBitParities);
            inMolecule = stereoMolecule;
        } catch (Exception iE) {

        }
        return stereoMolecule;
    }


    public void setInputFromFile(String fileName) {

        StringBuffer sBuf = new StringBuffer();
        String s = null;

        try (BufferedReader bfReader = fileName.equals("-") ? new BufferedReader(new InputStreamReader(System.in)) : new BufferedReader(new FileReader(fileName))) {
            while ((s = bfReader.readLine()) != null) {
                sBuf.append(s);
                sBuf.append('\n');
            }
        } catch (FileNotFoundException fnfE) {
            return;
        } catch (IOException ioE) {
            log.warn(ioE.getMessage(), ioE);
        }

        inputData = sBuf.toString();
    }

    public void setInputFile(String string) {
        inFileName = string;
    }

    public void fromMol() throws MoleculeIOException {
        SDFile sdFile = new SDFile();
        inMolecule = sdFile.readMol("test", inputData);
    }

    public void fromMDL() {
    }

    public Molecule readMDLFile(String molData) {
        return null;
    }

    public void fromPDB() {
    }

    public void readPDBFile(String molData) {
    }

    public void fromSmiles() {
        System.out.println("input is " + inputData);
    }

    public void serializeToBytes() {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

        try {
            ObjectOutputStream s = new ObjectOutputStream(byteStream);
            s.writeObject(inMolecule);
            s.flush();
        } catch (IOException ioE) {
            System.out.println(ioE.getMessage());
        }

        System.out.println(byteStream.toString());
    }

    public void serializeToStream() {
        try {
            ObjectOutputStream s = new ObjectOutputStream(System.out);
            s.writeObject(inMolecule);
            s.flush();
        } catch (IOException ioE) {
            System.out.println(ioE.getMessage());
        }
    }

    public void fromSerialization() {
    }

    public void toNanoMol() {

    }

    public void fromNanoMol() {
        fromNanoMol(inputData);
    }

    public void fromNanoMol(String nanoString) {
    }

    public void superMol(Molecule mol2) {
    }

    public void execute() {
        fromSmiles();
        toMOL();
    }

    public void toPDB() {
        if (inMolecule instanceof MoleculeBase mol) {
            outValue = toPDB(mol);
        }
    }

    String toPDB(MoleculeBase mol) {

        String result = "";

        return result;
    }

    public void toMOL() {
        if (inMolecule instanceof StereoMolecule sMol) {
            var mfC = new MolfileCreator(sMol);
            outValue = mfC.getMolfile();
        }
    }

    public void toPred() {
        if (inMolecule instanceof StereoMolecule sMol) {
            List<List<Double>> result = ChemPropertyCalculator.getPredictionParameters(sMol);
            StringBuilder sBuilder = new StringBuilder();
            for (var outRowValues : result) {
                boolean first = true;
                for (double value : outRowValues) {
                    if (!first) {
                        sBuilder.append(",");
                    }
                    sBuilder.append(String.format("%.3f", value));
                    first = false;
                }
                sBuilder.append("\n");
            }
            outValue = sBuilder.toString();
        }
    }

    String toMDL(MoleculeBase mol) {

        String result = "";

        return result;
    }

    public String getOutput() {
        return outValue;
    }

    public String getInput() {
        if (inputData == null) {
            return "";
        } else {
            return inputData;
        }
    }

    public void toSmiles() {
        outValue = generateSmiles(inMolecule);
    }

    public String generateSmiles(Object molecule) {
        return "";
    }

    public Molecule parseSmiles(String smilesString) {
        return null;

    }

    public static void main(String[] argv) {
        TurboMol turboMol = new TurboMol();
        String inMode = "-";
        String inType = "mol";

        String inData = "";
        String outMode = "";

        String outType = "mol";

        if (argv.length != 4) {
            System.out.println("usage: dcmol inMode inType outMode outType");
            System.exit(1);
        }

        inMode = argv[0].toString();
        inType = argv[1].toString();
        outMode = argv[2].toString();
        outType = argv[3].toString();

        if (inMode.equals("--")) {
            turboMol.fromSerialization();
            inType = "serialization";
        } else {
            turboMol.setInputFromFile(inMode);
        }

        if (inType.equals("smiles")) {
            turboMol.fromSmiles();
        } else if (inType.equals("mol")) {
            turboMol.fromMDL();
        } else if (inType.equals("nano")) {
            turboMol.fromNanoMol();
        } else if (inType.equals("pdb")) {
            turboMol.fromPDB();
        }

        if (outType.equals("smiles")) {
            turboMol.toSmiles();
        } else if (outType.equals("mol")) {
            turboMol.toMOL();
        } else if (outType.equals("nano")) {
            turboMol.toNanoMol();
        } else if (outType.equals("pdb")) {
            turboMol.toPDB();
        }

        if (outMode.equals("-")) {
            System.out.println(turboMol.getOutput());
        } else if (outMode.equals("--")) {
            turboMol.serializeToStream();
        }
    }
}
