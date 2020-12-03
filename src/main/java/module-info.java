/*
 * NMRFx Processor : A Program for Processing NMR Data 
 * Copyright (C) 2004-2018 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

open module org.nmrfx.processor.gui {
    exports org.nmrfx.processor.gui;
    requires org.nmrfx.core;
    requires org.nmrfx.processor;
    requires org.nmrfx.utils;
//    requires org.nmrfx.utils.properties;
//    requires org.nmrfx.chart;
 //  requires org.nmrfx.graphicsio;

    requires jnr.posix;
    requires jnr.ffi;
    requires java.logging;
    requires java.prefs;
    requires java.desktop;

    requires org.objectweb.asm.tree.analysis;
    requires org.objectweb.asm.tree;
    requires jnr.a64asm;
    requires jnr.x86asm;
    requires jnr.constants;
    requires jdistlib;
    requires JTransforms;
    requires JLargeArrays;
    requires commons.math3;
    requires com.google.common;
    requires failureaccess;
    requires listenablefuture;
    requires jsr305;
    requires org.checkerframework.checker.qual;
    requires com.google.errorprone.annotations;
    requires j2objc.annotations;
    requires org.yaml.snakeyaml;
    requires janino;
    requires commons.compiler;
    requires io.netty.all;
    requires jython.slim;
    requires antlr;
    requires ST4;
    requires antlr.runtime;
    requires org.objectweb.asm;
    requires org.objectweb.asm.commons;
    requires org.objectweb.asm.util;
    requires jffi;
    requires fontawesomefx;
    requires pdfbox;
    requires fontbox;
    requires org.controlsfx.controls;
    requires javafx.controlsEmpty;
    requires javafx.controls;
    requires javafx.baseEmpty;
    requires javafx.base;
    requires commons.beanutils;
    requires commons.logging;
    requires commons.collections;
    requires org.fxmisc.richtext;
    requires reactfx;
    requires undofx;
    requires flowless;
    requires wellbehavedfx;
    requires org.eclipse.jgit;
    requires jsch;
    requires jzlib;
    requires JavaEWAH;
    requires slf4j.api;
    requires org.bouncycastle.pg;
    requires org.bouncycastle.provider;
    requires org.bouncycastle.pkix;
    requires centerdevice.nsmenufx;
    requires javafx.graphicsEmpty;
    requires javafx.graphics;
    requires javafx.fxmlEmpty;
    requires javafx.fxml;
    requires javafx.webEmpty;
    requires javafx.web;
    requires javafx.mediaEmpty;
    requires javafx.media;
}
