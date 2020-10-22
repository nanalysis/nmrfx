package org.nmrfx.structure.chemistry.miner;

import java.util.ArrayList;

class PMol {

    static String[] symbols = {
        "X", "H", "He", "Li", "Be", "B", "C", "N", "O", "F", "Ne", "Na", "Mg",
        "Al", "Si", "P", "S", "Cl", "Ar", "K", "Ca", "Sc"
    };
    ArrayList atoms = new ArrayList();
    ArrayList bonds = new ArrayList();
    double minX = 0;
    double minY = 0;
    double maxX = 0;
    double maxY = 0;
    double deltaX = 0.0;
    double deltaY = 0.0;
    double centerX = 0.0;
    double centerY = 0.0;
    int width = 0;
    int height = 0;
    double scale = 1;

    public void addBond(double x1, double y1, double x2, double y2, int order, int wedge) {
        Bond bond = new Bond(x1, y1, x2, y2, order, wedge);
        bonds.add(bond);
    }

    public void addAtom(double x1, double y1, int element) {
        Atom atom = new Atom(x1, y1, element);
        atoms.add(atom);
    }

    public void setMinMax(double minX, double minY, double maxX, double maxY) {
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
        centerX = (maxX + minX) / 2;
        centerY = (maxY + minY) / 2;
        deltaX = (maxX - minX);
        deltaY = (maxY - minY);
    }

    double getDeltaX() {
        return deltaX;
    }

    double getDeltaY() {
        return deltaY;
    }

    float getX(double x) {
        float newX = (float) (((x - centerX) * scale) + (width / 2));

        return newX;
    }

    float getY(double y) {
        float newY = (float) (((y - centerY) * scale) + (height / 2));

        return newY;
    }

//    public void drawAtoms(Graphics2D g2) {
//        for (int i = 0, n = atoms.size(); i < n; i++) {
//            Atom atom = (Atom) atoms.get(i);
//            FontMetrics fm = g2.getFontMetrics();
//            String label = symbols[atom.element];
//            Rectangle2D rect2D = fm.getStringBounds(label, (Graphics) g2);
//            float dX = (float) (rect2D.getWidth() / 2);
//            float dY = (float) (fm.getAscent() / 2);
//            float x = getX(atom.x) - dX;
//            float y = getY(atom.y) + dY;
//            Rectangle rect = new Rectangle();
//            rect.setRect(rect2D);
//            rect.translate((int) x, (int) y);
//            g2.setColor(Color.WHITE);
//            g2.fill(rect);
//            g2.setColor(Color.BLACK);
//            g2.drawString(label, x, y);
//        }
//    }
//
//    GeneralPath genPath(int width, int height, double scale) {
//        GeneralPath gPath = new GeneralPath();
//        this.width = width;
//        this.height = height;
//        this.scale = scale;
//
//        for (int i = 0, n = bonds.size(); i < n; i++) {
//            Bond bond = (Bond) bonds.get(i);
//            gPath.moveTo(getX(bond.x1), getY(bond.y1));
//            gPath.lineTo(getX(bond.x2), getY(bond.y2));
//        }
//
//        return gPath;
//    }

    class Atom {

        double x = 0.0;
        double y = 0.0;
        int element = 6;

        public Atom(double x, double y, int element) {
            this.x = x;
            this.y = y;

            if ((element < 0) || (element >= symbols.length)) {
                element = 0;
            }

            this.element = element;
        }
    }

    class Bond {

        double x1 = 0.0;
        double y1 = 0.0;
        double x2 = 0.0;
        double y2 = 0.0;
        int order = 0;
        int wedge = 0;

        public Bond(double x1, double y1, double x2, double y2, int order,
                int wedge) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.order = order;
            this.wedge = wedge;
        }
    }
}
