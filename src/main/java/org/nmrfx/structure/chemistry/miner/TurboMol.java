package org.nmrfx.structure.chemistry.miner;

import java.io.*;
import java.util.Vector;
import org.nmrfx.structure.chemistry.Molecule;

public class TurboMol {

    String inputData = null;
    String inFileName = null;
    String outValue = null;
    AtomContainer inMolecule = null;
    BufferedReader bufReader = null;

    public AtomContainer getMolecule() {
        return inMolecule;
    }
    public void setMolecule(AtomContainer iMol) {
        this.inMolecule = iMol;
    }

    public IAtom getAtom(final int index) {
        IAtom atom = null;
        if (inMolecule != null) {
            atom = inMolecule.getAtom(index);
        }
        return atom;
    }

    public int getAtomCount(final int element) {
        int atomCount = 0;
        if (inMolecule != null) {
            for (IAtom atom : inMolecule.atoms()) {
                if (element == atom.getAtomicNumber()) {
                    atomCount++;
                }
            }
        }
        return atomCount;
    }

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

    public void setInputFromFile(String fileName) {
        BufferedReader bufReader = null;

        if (fileName.equals("-")) {
            bufReader = new BufferedReader(new InputStreamReader(System.in));
        } else {
            try {
                bufReader = new BufferedReader(new FileReader(fileName));
            } catch (FileNotFoundException fnfE) {
                return;
            }
        }

        StringBuffer sBuf = new StringBuffer();
        String s = null;

        try {
            while ((s = bufReader.readLine()) != null) {
                sBuf.append(s);
                sBuf.append('\n');
            }
        } catch (IOException ioE) {
        }

        inputData = sBuf.toString();
    }

    public void setInputFile(String string) {
        inFileName = string;
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
        outValue = toPDB(inMolecule);
    }

    String toPDB(AtomContainer mol) {


        String result = "";


        return result;
    }

    public void toMOL() {
        outValue = toMDL(inMolecule);
    }

    String toMDL(AtomContainer mol) {

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

    public String generateSmiles(AtomContainer molecule) {
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
