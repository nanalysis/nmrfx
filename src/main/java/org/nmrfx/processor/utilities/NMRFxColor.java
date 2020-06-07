/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.utilities;

/**
 *
 * @author brucejohnson
 */
public class NMRFxColor {

    public static NMRFxColor BLACK = new NMRFxColor(0, 0, 0);
    public static NMRFxColor RED = new NMRFxColor(255, 0, 0);
    final int r;
    final int g;
    final int b;
    final int alpha;

    public NMRFxColor(int r, int g, int b) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.alpha = 255;
    }

    public NMRFxColor(int r, int g, int b, int alpha) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.alpha = alpha;
    }

    public int getRed() {
        return r;
    }

    public int getGreen() {
        return g;
    }

    public int getBlue() {
        return b;
    }

    public int getAlpha() {
        return alpha;
    }

    public String toRGBCode() {
        if (alpha == 255) {
            return ColorUtil.toRGBCode(r, g, b);
        } else {
            return ColorUtil.toRGBCode(r, g, b, alpha);
        }
    }

    public static NMRFxColor fromRGBCode(String rgbCode) {
        int[] rgb = ColorUtil.fromRGBCode(rgbCode);
        if (rgb.length == 4) {
            return new NMRFxColor(rgb[0], rgb[1], rgb[2], rgb[3]);
        } else {
            return new NMRFxColor(rgb[0], rgb[1], rgb[2]);
        }
    }

}
