package org.nmrfx.processor.datasets.vendor.rs2d;

import org.nmrfx.processor.datasets.vendor.VendorPar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.nmrfx.processor.datasets.vendor.rs2d.RS2DParam.PHASE_0;
import static org.nmrfx.processor.datasets.vendor.rs2d.XmlUtil.*;

public class RS2DHeader {
    private static final Logger log = LoggerFactory.getLogger(RS2DData.class);

    private final HashMap<String, List<String>> params = new LinkedHashMap<>(200);
    private final Document document;

    public RS2DHeader(Path headerPath) throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {
        document = readDocument(headerPath);

        var parNames = getParams(document);
        for (String parName : parNames) {
            List<String> parValues = getParamValue(document, parName);
            params.put(parName, parValues);
        }
    }

    public Document getDocument() {
        return document;
    }

    public List<VendorPar> toVendorPars() {
        return params.keySet().stream()
                .map(name -> new VendorPar(name, getString(name)))
                .collect(Collectors.toList());
    }


    public String getString(RS2DParam param) {
        return getString(param.name());
    }

    public String getString(String name) {
        return String.join(",", params.get(name));
    }

    public List<String> getStrings(RS2DParam param) {
        return getStrings(param.name());
    }

    public List<String> getStrings(String name) {
        return params.get(name);
    }

    public Boolean getBoolean(RS2DParam param) {
        return getBoolean(param.name());
    }

    public Boolean getBoolean(String name) {
        return asBoolean(getString(name));
    }

    public Double getDouble(RS2DParam param) {
        return getDouble(param.name());
    }

    public Double getDouble(String name) {
        return asDouble(getString(name));
    }

    public List<Double> getDoubles(RS2DParam param) {
        return getDoubles(param.name());
    }

    public List<Double> getDoubles(String names) {
        return getStrings(names).stream()
                .map(RS2DHeader::asDouble)
                .collect(Collectors.toList());
    }

    public Integer getInt(RS2DParam param) {
        return getInt(param.name());
    }

    public Integer getInt(String name) {
        return asInteger(getString(name));
    }

    public void writeParam(String name, String value) throws XPathExpressionException {
        setParam(document, name, value);
        params.put(name, List.of(value));
    }

    public void writeParam(String name, List<String> values) throws XPathExpressionException {
        setParams(document, PHASE_0.name(), values);
        params.put(name, values);
    }

    public void writeTo(File headerFile) throws IOException, TransformerException {
        writeDocument(document, headerFile);
    }

    private static Boolean asBoolean(String s) {
        if(s == null)
            return null;

        return Boolean.valueOf(s);
    }

    private static Double asDouble(String s) {
        if(s == null)
            return null;

        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            log.warn("Parse exception, was expected a double, but got '{}' instead.", s, e);
            return null;
        }
    }

    private static Integer asInteger(String s) {
        if(s == null)
            return null;

        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            log.warn("Parse exception, was expected an int, but got '{}' instead.", s, e);
            return null;
        }
    }
}
