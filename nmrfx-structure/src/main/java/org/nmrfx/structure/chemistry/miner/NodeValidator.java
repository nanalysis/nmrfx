package org.nmrfx.structure.chemistry.miner;

import org.nmrfx.annotations.PluginAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@PluginAPI("residuegen")
public class NodeValidator implements NodeValidatorInterface {

    boolean[][] p;
    int[] modes = {
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            1,
            1,
            1,
            1,
            1,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,
            2,};
    int[][] atomPatterns = {
            {0, 0},
            {0, 0},
            {0, 0, 0},
            {1},
            {0, 2},
            {2},
            {3},
            {4},
            {5},
            {0, 0, 0, 0, 0},
            {0, 0, 0, 0},
            {6},
            {0, 0, 0},
            {7},
            {0, 0},
            {8},
            {0, 0, 0, 9},
            {0, 0, 0, 0, 9},
            {0, 0, 0, 0, 0, 9},
            {10},
            {11, 11, 11, 11, 11, 12},
            {13, 11, 11, 11, 11, 12},
            {11, 11, 11, 11, 12},
            {13, 11, 11, 11, 12},
            {14},
            {13, 15, 16},
            {13, 17, 16},
            {13, 18, 16},
            {19, 20, 15, 16, 21},
            {0},
            {16},
            {13},
            {22},
            {23},
            {24},
            {20},
            {25},
            {26},
            {15, 0},
            {27},
            {28},
            {16, 0},
            {29},
            {30},
            {31},
            {32, 33, 32},
            {34},
            {35, 0},
            {36},
            {37},
            {0, 13, 0},
            {0, 13, 0},
            {38},
            {39, 0},
            {40, 0},
            {35, 0},
            {0, 41, 0},
            {0, 42, 0},
            {43},
            {44},
            {45},
            {46},
            {47},
            {48, 0},
            {0, 18, 0},
            {49},
            {50, 0},
            {50, 0},
            {17},
            {51},
            {51, 0},
            {52},
            {53},
            {54, 15, 54},
            {54, 15, 15, 54},
            {54, 15, 15, 54},
            {54, 15, 15, 54},
            {54, 27, 27, 54},
            {54, 27, 27, 27, 54},
            {55},
            {56},
            {57},
            {58},
            {59},
            {60},
            {61},
            {62},
            {63},
            {28},
            {64},
            {65},};
    int[][] bondPatterns = {
            {-1, 0},
            {-1, 1},
            {-1, 0, 0},
            {-1},
            {-1, 2},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1, 3, 3, 3, 3},
            {-1, 3, 3, 3},
            {-1},
            {-1, 3, 3},
            {-1},
            {-1, 3},
            {-1},
            {-1, 3, 3, 3},
            {-1, 3, 3, 3, 3},
            {-1, 3, 3, 3, 3, 3},
            {-1},
            {-1, 3, 3, 3, 3, 3},
            {-1, 3, 3, 3, 3, 3},
            {-1, 3, 3, 3, 3},
            {-1, 3, 3, 3, 3},
            {-1},
            {-1, 2, 0},
            {-1, 2, 0},
            {-1, 2, 0},
            {-1, 2, 2, 0, 2},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1, 1},
            {-1},
            {-1},
            {-1, 0},
            {-1},
            {-1},
            {-1},
            {-1, 3, 3},
            {-1},
            {-1, 1},
            {-1},
            {-1},
            {-1, 0, 0},
            {-1, 2, 1},
            {-1},
            {-1, 0},
            {-1, 0},
            {-1, 0},
            {-1, 2, 2},
            {-1, 2, 2},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1, 0},
            {-1, 0, 0},
            {-1},
            {-1, 2},
            {-1, 0},
            {-1},
            {-1},
            {-1, 0},
            {-1},
            {-1},
            {-1, 2, 2},
            {-1, 2, 2, 2},
            {-1, 4, 2, 5},
            {-1, 4, 2, 4},
            {-1, 2, 3, 2},
            {-1, 2, 3, 3, 2},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},};
    int[][] jumpPositions = {
            {-1, -1},
            {-1, -1},
            {-1, -1, -1},
            {-1},
            {-1, -1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1, 0, 0, 0, -1},
            {-1, 0, 0, -1},
            {-1},
            {-1, 0, -1},
            {-1},
            {-1, -1},
            {-1},
            {-1, -1, -1, -1},
            {-1, -1, -1, -1, -1},
            {-1, -1, -1, -1, -1, -1},
            {-1},
            {-1, -1, -1, -1, -1, -1},
            {-1, -1, -1, -1, -1, -1},
            {-1, -1, -1, -1, -1},
            {-1, -1, -1, -1, -1},
            {-1},
            {-1, -1, -1},
            {-1, -1, -1},
            {-1, -1, -1},
            {-1, -1, -1, 2, -1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1, -1},
            {-1},
            {-1},
            {-1, -1},
            {-1},
            {-1},
            {-1},
            {-1, -1, -1},
            {-1},
            {-1, -1},
            {-1},
            {-1},
            {-1, -1, -1},
            {-1, -1, -1},
            {-1},
            {-1, -1},
            {-1, -1},
            {-1, -1},
            {-1, -1, -1},
            {-1, -1, -1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1, -1},
            {-1, -1, -1},
            {-1},
            {-1, -1},
            {-1, -1},
            {-1},
            {-1},
            {-1, -1},
            {-1},
            {-1},
            {-1, -1, -1},
            {-1, -1, -1, -1},
            {-1, -1, -1, -1},
            {-1, -1, -1, -1},
            {-1, -1, -1, -1},
            {-1, -1, -1, -1, -1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},};
    int[][] propertyValues = {
            {0, 0},
            {1, 1},
            {-1, 1, -1},
            {2},
            {3, -1},
            {4},
            {4},
            {4},
            {4},
            {5, -1, -1, -1, -1},
            {6, -1, -1, -1},
            {7},
            {8, -1, -1},
            {9},
            {10, -1},
            {11},
            {12, 12, 12, 12},
            {13, 13, 13, 13, 13},
            {14, 14, 14, 14, 14, 14},
            {15},
            {16, 16, 16, 16, 16, 16},
            {16, 16, 16, 16, 16, 16},
            {17, 17, 17, 17, 17},
            {17, 17, 17, 17, 17},
            {18},
            {19, 20, 21},
            {19},
            {19},
            {22, 23, 24, 25, -1},
            {-1},
            {-1},
            {-1},
            {-1},
            {-1},
            {0},
            {5},
            {10},
            {15},
            {20, -1},
            {25},
            {30},
            {35, -1},
            {40},
            {47},
            {52},
            {-1, 57, -1},
            {62},
            {67, -1},
            {72},
            {79},
            {-1, 86, -1},
            {-1, 93, -1},
            {100},
            {105, -1},
            {110, -1},
            {117, -1},
            {-1, 124, -1},
            {-1, 131, -1},
            {138},
            {143},
            {148},
            {153},
            {158},
            {165, -1},
            {-1, 170, -1},
            {175},
            {180, -1},
            {187, -1},
            {192},
            {197},
            {204, -1},
            {209},
            {214},
            {},
            {},
            {},
            {},
            {},
            {},
            {221},
            {224},
            {227},
            {230},
            {233},
            {236},
            {239},
            {242},
            {245},
            {248},
            {251},
            {254},};
    String[] propertyNames = {
            "sp2",
            "sp",
            "sp3",
            "conj",
            "res",
            "x4",
            "x3temp",
            "x3",
            "x2temp",
            "x2",
            "x1temp",
            "x1",
            "r4",
            "r5",
            "r6",
            "r",
            "ar6",
            "ar5",
            "ar",
            "namide",
            "c_amide",
            "o_amide",
            "n_aa",
            "ca_aa",
            "c_aa",
            "o_aa",};
    String[] paramValues = {
            "4",
            "elec",
            "27.4",
            "hard",
            "73.9",
            "4",
            "elec",
            "30.8",
            "hard",
            "78.4",
            "4",
            "elec",
            "33.6",
            "hard",
            "76.4",
            "4",
            "elec",
            "37.0",
            "hard",
            "65.3",
            "4",
            "elec",
            "40.0",
            "hard",
            "98.5",
            "4",
            "elec",
            "34.6",
            "hard",
            "84.7",
            "4",
            "elec",
            "45.7",
            "hard",
            "92.6",
            "4",
            "elec",
            "49.5",
            "hard",
            "86.1",
            "6",
            "elec",
            "49.3",
            "hard",
            "25.0",
            "charge",
            "-1.0",
            "4",
            "elec",
            "45.9",
            "hard",
            "137.0",
            "4",
            "elec",
            "44.0",
            "hard",
            "87.6",
            "4",
            "elec",
            "43.6",
            "hard",
            "94.4",
            "4",
            "elec",
            "44.0",
            "hard",
            "72.7",
            "4",
            "elec",
            "57.0",
            "hard",
            "111.0",
            "6",
            "elec",
            "42.8",
            "hard",
            "188.0",
            "charge",
            "1.0",
            "6",
            "elec",
            "37.6",
            "hard",
            "41.5",
            "charge",
            "1.0",
            "6",
            "elec",
            "24.0",
            "hard",
            "104.0",
            "charge",
            "1.0",
            "6",
            "elec",
            "39.4",
            "hard",
            "29.7",
            "charge",
            "1.0",
            "4",
            "elec",
            "43.4",
            "hard",
            "136.0",
            "4",
            "elec",
            "53.0",
            "hard",
            "102.0",
            "6",
            "elec",
            "38.7",
            "hard",
            "8.64",
            "charge",
            "1.0",
            "6",
            "elec",
            "31.9",
            "hard",
            "129.0",
            "charge",
            "-1.0",
            "6",
            "elec",
            "28.3",
            "hard",
            "20.9",
            "charge",
            "-1.0",
            "6",
            "elec",
            "43.6",
            "hard",
            "0.176",
            "charge",
            "-1.0",
            "4",
            "elec",
            "37.6",
            "hard",
            "53.5",
            "4",
            "elec",
            "45.2",
            "hard",
            "96.8",
            "4",
            "elec",
            "40.1",
            "hard",
            "75.3",
            "4",
            "elec",
            "37.4",
            "hard",
            "69.1",
            "6",
            "elec",
            "31.8",
            "hard",
            "93.9",
            "charge",
            "1.0",
            "4",
            "elec",
            "35.8",
            "hard",
            "93.1",
            "4",
            "elec",
            "31.7",
            "hard",
            "83.2",
            "4",
            "elec",
            "33.8",
            "hard",
            "88.9",
            "6",
            "elec",
            "44.5",
            "hard",
            "24.8",
            "charge",
            "-1.0",
            "4",
            "elec",
            "47.5",
            "hard",
            "74.3",
            "4",
            "elec",
            "37.9",
            "hard",
            "72.5",
            "6",
            "elec",
            "29.6",
            "hard",
            "108.5",
            "charge",
            "1.0",
            "4",
            "elec",
            "33.0",
            "hard",
            "86.6",
            "4",
            "elec",
            "41.3",
            "hard",
            "109.0",
            "6",
            "elec",
            "34.1",
            "hard",
            "10.8",
            "charge",
            "1.0",
            "2",
            "nH",
            "0",
            "2",
            "nH",
            "1",
            "2",
            "nH",
            "2",
            "2",
            "nH",
            "3",
            "2",
            "nH",
            "0",
            "2",
            "nH",
            "1",
            "2",
            "nH",
            "2",
            "2",
            "nH",
            "0",
            "2",
            "nH",
            "1",
            "2",
            "nH",
            "0",
            "2",
            "nH",
            "1",
            "2",
            "nH",
            "3",};

    public NodeValidator() {
    }

    public boolean checkAtom(int aNum, boolean visited, final int[] currentPath, final int patternIndex, final int pathIndex, final int atomIndex, Map bondMap) {
        int atomRule = atomPatterns[patternIndex][pathIndex];
        if (visited) {
            return false;
        }
        boolean atomValid = false;
        switch (atomRule) {

            case 0:
                atomValid = true; // X
                break;

            case 1:
                atomValid = !p[atomIndex][0] && !p[atomIndex][1]; // !sp2 && !sp
                break;

            case 2:
                atomValid = p[atomIndex][1] || p[atomIndex][0]; // sp || sp2
                break;

            case 3:
                atomValid = (aNum == 7) && p[atomIndex][2] && p[atomIndex][3]; // N && sp3 && conj
                break;

            case 4:
                atomValid = (aNum == 8) && p[atomIndex][2] && p[atomIndex][3]; // O && sp3 && conj
                break;

            case 5:
                atomValid = (aNum == 16) && p[atomIndex][2] && p[atomIndex][3]; // S && sp3 && conj
                break;

            case 6:
                atomValid = p[atomIndex][6] && !p[atomIndex][5]; // x3temp && !x4
                break;

            case 7:
                atomValid = p[atomIndex][8] && !(p[atomIndex][7] || p[atomIndex][5]); // x2temp && !(x3 || x4)
                break;

            case 8:
                atomValid = p[atomIndex][10] && !(p[atomIndex][9] || p[atomIndex][7] || p[atomIndex][5]); // x1temp && !(x2 || x3 || x4)
                break;

            case 9:
                atomValid = true && ringClosed(currentPath, 0, atomIndex, 0, bondMap); // X
                break;

            case 10:
                atomValid = p[atomIndex][12] || p[atomIndex][13] || p[atomIndex][14]; // r4 || r5 || r6
                break;

            case 11:
                atomValid = p[atomIndex][4]; // res
                break;

            case 12:
                atomValid = p[atomIndex][4] && ringClosed(currentPath, 0, atomIndex, 0, bondMap); // res
                break;

            case 13:
                atomValid = (aNum == 7); // N
                break;

            case 14:
                atomValid = p[atomIndex][17] || p[atomIndex][16]; // ar5 || ar6
                break;

            case 15:
                atomValid = (aNum == 6); // C
                break;

            case 16:
                atomValid = (aNum == 8); // O
                break;

            case 17:
                atomValid = (aNum == 15); // P
                break;

            case 18:
                atomValid = (aNum == 16); // S
                break;

            case 19:
                atomValid = p[atomIndex][19] || ((aNum == 7) && p[atomIndex][2]); // namide || (N && sp3)
                break;

            case 20:
                atomValid = (aNum == 6) && p[atomIndex][2]; // C && sp3
                break;

            case 21:
                atomValid = (aNum == 8) || p[atomIndex][19]; // O || namide
                break;

            case 22:
                atomValid = (aNum == 6) | (aNum == 14) | (aNum == 16) | (aNum == 34) | (aNum == 15) | (aNum == 33); // C|Si|S|Se|P|As
                break;

            case 23:
                atomValid = ((aNum == 9) | (aNum == 17) | (aNum == 35) | (aNum == 53)) && p[atomIndex][11]; // (F|Cl|Br|I) && x1
                break;

            case 24:
                atomValid = (aNum == 1) && p[atomIndex][2]; // H && sp3
                break;

            case 25:
                atomValid = (aNum == 6) && p[atomIndex][0]; // C && sp2
                break;

            case 26:
                atomValid = (aNum == 6) && p[atomIndex][1]; // C && sp
                break;

            case 27:
                atomValid = (aNum == 6) && p[atomIndex][18]; // C && ar
                break;

            case 28:
                atomValid = (aNum == 8) && p[atomIndex][2] && p[atomIndex][9]; // O && sp3 && x2
                break;

            case 29:
                atomValid = (aNum == 8) && p[atomIndex][2] && p[atomIndex][11]; // O && sp3 && x1
                break;

            case 30:
                atomValid = (aNum == 8) && p[atomIndex][18]; // O && ar
                break;

            case 31:
                atomValid = (aNum == 7) && p[atomIndex][2]; // N && sp3
                break;

            case 32:
                atomValid = p[atomIndex][3]; // conj
                break;

            case 33:
                atomValid = (aNum == 7) && p[atomIndex][2] && p[atomIndex][13]; // N && sp3 && r5
                break;

            case 34:
                atomValid = (aNum == 7) && p[atomIndex][0]; // N && sp2
                break;

            case 35:
                atomValid = (aNum == 7) && p[atomIndex][11]; // N && x1
                break;

            case 36:
                atomValid = (aNum == 7) && p[atomIndex][2] && p[atomIndex][5]; // N && sp3 && x4
                break;

            case 37:
                atomValid = (aNum == 7) && p[atomIndex][7] && p[atomIndex][0]; // N && x3 && sp2
                break;

            case 38:
                atomValid = (aNum == 7) && p[atomIndex][18]; // N && ar
                break;

            case 39:
                atomValid = (aNum == 7) && p[atomIndex][18] && p[atomIndex][9]; // N && ar && x2
                break;

            case 40:
                atomValid = (aNum == 7) && p[atomIndex][18] && p[atomIndex][7]; // N && ar && x3
                break;

            case 41:
                atomValid = (aNum == 7) && p[atomIndex][9]; // N && x2
                break;

            case 42:
                atomValid = (aNum == 7) && p[atomIndex][9] && (p[atomIndex][13] || p[atomIndex][12]); // N && x2 && (r5 || r4)
                break;

            case 43:
                atomValid = (aNum == 17); // Cl
                break;

            case 44:
                atomValid = (aNum == 9); // F
                break;

            case 45:
                atomValid = (aNum == 35); // Br
                break;

            case 46:
                atomValid = (aNum == 16) && p[atomIndex][2] && p[atomIndex][9]; // S && sp3 && x2
                break;

            case 47:
                atomValid = (aNum == 16) && p[atomIndex][2] && p[atomIndex][7]; // S && sp3 && x3
                break;

            case 48:
                atomValid = (aNum == 16) && p[atomIndex][7]; // S && x3
                break;

            case 49:
                atomValid = (aNum == 16) && p[atomIndex][18]; // S && ar
                break;

            case 50:
                atomValid = (aNum == 16) && p[atomIndex][11]; // S && x1
                break;

            case 51:
                atomValid = (aNum == 15) && p[atomIndex][5]; // P && x4
                break;

            case 52:
                atomValid = (aNum == 53); // I
                break;

            case 53:
                atomValid = (aNum == 53) && p[atomIndex][9]; // I && x2
                break;

            case 54:
                atomValid = (aNum == 1); // H
                break;

            case 55:
                atomValid = (aNum == 6) && p[atomIndex][2] && p[atomIndex][5]; // C && sp3 && x4
                break;

            case 56:
                atomValid = (aNum == 6) && p[atomIndex][2] && p[atomIndex][7]; // C && sp3 && x3
                break;

            case 57:
                atomValid = (aNum == 6) && p[atomIndex][2] && p[atomIndex][9]; // C && sp3 && x2
                break;

            case 58:
                atomValid = (aNum == 6) && p[atomIndex][2] && p[atomIndex][11]; // C && sp3 && x1
                break;

            case 59:
                atomValid = (aNum == 6) && p[atomIndex][0] && p[atomIndex][7]; // C && sp2 && x3
                break;

            case 60:
                atomValid = (aNum == 6) && p[atomIndex][0] && p[atomIndex][9]; // C && sp2 && x2
                break;

            case 61:
                atomValid = (aNum == 6) && p[atomIndex][0] && p[atomIndex][11]; // C && sp2 && x1
                break;

            case 62:
                atomValid = (aNum == 6) && p[atomIndex][1] && p[atomIndex][9]; // C && sp && x2
                break;

            case 63:
                atomValid = (aNum == 6) && p[atomIndex][1] && p[atomIndex][11]; // C && sp && x1
                break;

            case 64:
                atomValid = (aNum == 8) && p[atomIndex][0] && p[atomIndex][11]; // O && sp2 && x1
                break;

            case 65:
                atomValid = (aNum == 7) && p[atomIndex][2] && p[atomIndex][7]; // N && sp3 && x3
                break;

        }
        return atomValid;
    }

    public boolean checkBond(int order, final int[] currentPath, final int patternIndex, final int pathIndex, final int bondIndex) {
        int bondRule = bondPatterns[patternIndex][pathIndex];
        boolean bondValid = false;
        switch (bondRule) {

            case 0:
                bondValid = (order == 2);
                break;

            case 1:
                bondValid = (order == 3);
                break;

            case 2:
                bondValid = (order == 1);
                break;

            case 3:
                bondValid = true;
                break;

            case 4:
                bondValid = (order == 5);
                break;

            case 5:
                bondValid = (order == 4);
                break;

        }
        return bondValid;
    }


    public void init(int nAtoms) {
        p = new boolean[nAtoms][propertyNames.length];
    }

    public int patternCount() {
        return atomPatterns.length;
    }

    public int getMode(int index) {
        return modes[index];
    }

    public int pathSize(int patternIndex) {
        return atomPatterns[patternIndex].length;
    }

    public int getJump(int patternIndex, final int pathIndex) {
        return jumpPositions[patternIndex][pathIndex];
    }

    public List getParams(List path, final int patternIndex) {
        List<Object> result = new ArrayList<>();
        int[] props = propertyValues[patternIndex];
        for (int i = 0; i < props.length; i++) {
            if (props[i] >= 0) {
                int atomIndex = (Integer) path.get(i);
                result.add(atomIndex);
                int k = props[i];
                int nStr = Integer.parseInt(paramValues[k]) / 2;
                for (int j = 0; j < nStr; j++) {
                    String name = paramValues[++k];
                    String value = paramValues[++k];
                    result.add(name);
                    result.add(value);
                }
            }
        }
        return result;
    }

    public void assignProps(List path, final int patternIndex) {
        int[] props = propertyValues[patternIndex];
        for (int i = 0; i < props.length; i++) {
            if (props[i] >= 0) {
                int atomIndex = (Integer) path.get(i);
                p[atomIndex][props[i]] = true;
            }
        }
    }

    void assignParams(List path, final int patternIndex) {
        int[] props = propertyValues[patternIndex];
        for (int i = 0; i < props.length; i++) {
            if (props[i] >= 0) {
                int atomIndex = (Integer) path.get(i);
                p[atomIndex][props[i]] = true;
            }
        }
    }

    public String[] getPropertyNames() {
        return propertyNames;
    }

    public boolean[][] getProperties() {
        return p;
    }

    public boolean ringClosed(final int[] currentPath, final int bondOrder, final int atomIndex, final int ringIndex, Map bondMap) {
        String key = currentPath[ringIndex] + " " + atomIndex;
        Integer order = (Integer) bondMap.get(key);
        boolean value = false;
        if (order != null) {
            if (bondOrder == 0) {
                value = true;
            } else {
                if ((bondOrder == 0) || (order == bondOrder)) {
                    value = true;
                }
            }
        }
        return value;
    }

}