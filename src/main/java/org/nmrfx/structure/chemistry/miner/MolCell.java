package org.nmrfx.structure.chemistry.miner;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.GeneralPath;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

public class MolCell extends JComponent implements TableCellRenderer {

    String className = null;
    int active = 0;
    Color curColor = Color.WHITE;
    byte[] nanoWorkSpace = new byte[1024];
    PMol pmol = null;
    String nanoString = null;
    String testString = " 1\\>+#,%^R(!  @  (%54R%#B18QFD2IDJ5+F#)IVL2IDT>E< 42A$@\"@4AA% -&0>0D1TZ\"B)JTM$D<K>;9BR#.*NK-@BS_@OO /R4R\\S$$>?US+;( ?83[$0^ JTY>%>M5L:&E*I07>=#L!]DL))";
    int lastHeight = 0;
    int lastWidth = 0;
    double scale = 100.0;

    void calculateScale() {
        double deltaX = pmol.getDeltaX();
        double deltaY = pmol.getDeltaY();
        double scaleX = getWidth() / Math.abs(deltaX);
        double scaleY = getHeight() / Math.abs(deltaY);
        double testScale = (scaleX < scaleY) ? scaleX : scaleY;
        testScale *= 0.8;

        if (testScale < scale) {
            scale = testScale;
        }
    }

    public void paint(Graphics g) {
        if ((lastWidth != getWidth()) || (lastHeight != getHeight())) {
            scale = 100.0;
        }

        lastWidth = getWidth();
        lastHeight = getHeight();
        genMol();
        g.setColor(curColor);
        g.fillRect(0, 0, getWidth() - 1, getHeight() - 1);
        g.setColor(Color.BLACK);

        if (pmol != null) {
            GeneralPath gPath = pmol.genPath(getWidth(), getHeight(), scale);
            Graphics2D g2 = (Graphics2D) g;
            g2.draw(gPath);
            pmol.drawAtoms(g2);
        }
    }

    void genMol() {
        pmol = null;

        if (nanoString != null) {
            // don't trim string as the first or last bytes of uucode could be spaces
            if (nanoWorkSpace.length < (nanoString.length() * 3)) { // *3 is generous
                nanoWorkSpace = new byte[nanoString.length() * 3];
            }

            int nBytes = NanoMol.uuDecode(nanoString, nanoWorkSpace);
            byte[] nanoBytes = new byte[nBytes];
            System.arraycopy(nanoWorkSpace, 0, nanoBytes, 0, nBytes);

            try {
                NanoMol nanoMol = new NanoMol();
                nanoMol.unCompressNanoRep(nanoBytes);
                pmol = nanoMol.genLinesSpheres(0, 0);
            } catch (Exception e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }

            calculateScale();
        }
    }

    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        if (value != null) {
            nanoString = value.toString();
        }

        nanoString = testString;

        return this;
    }
}
