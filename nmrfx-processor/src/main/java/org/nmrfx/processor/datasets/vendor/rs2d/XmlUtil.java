/*
 * NMRFx: A Program for Processing NMR Data
 * Copyright (C) 2004-2022 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.processor.datasets.vendor.rs2d;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * XML related code used by RS2D file support.
 */
public class XmlUtil {

    public static void writeDocument(Document document, File outFile) throws TransformerException, IOException {
        DOMSource source = new DOMSource(document);
        StreamResult result =  new StreamResult(new StringWriter());
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

        Transformer transformer = transformerFactory.newTransformer();
        transformer.transform(source, result);
        String xmlString = result.getWriter().toString();
        Files.writeString(outFile.toPath(),xmlString);
    }


    public static Document readDocument(Path filePath) throws ParserConfigurationException, IOException, SAXException {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(filePath.toFile());
    }


    protected static List<String> getParams(Document xml) throws XPathExpressionException {
        String expression = "/header/params/entry/key/text()";
        XPath path = XPathFactory.newInstance().newXPath();
        XPathExpression expr = path.compile(expression);
        NodeList nodes = (NodeList) expr.evaluate(xml, XPathConstants.NODESET);
        var nodeValues = new ArrayList<String>();
        for (int i = 0; i < nodes.getLength(); i++) {
            nodeValues.add(nodes.item(i).getNodeValue());
        }
        return nodeValues;
    }

    protected static List<Node> getParamNode(Document xml, String paramName) throws XPathExpressionException {
        if (!paramName.contains("'")) {
            paramName = "'" + paramName + "'";
        } else if (!paramName.contains("\"")) {
            paramName = "\"" + paramName + "\"";
        } else {
            paramName = "concat('" + paramName.replace("'", "',\"'\",'") + "')";
        }
        String expression = "/header/params/entry/key[text()=" + paramName + "]/../value/value";
        XPath path = XPathFactory.newInstance().newXPath();
        XPathEvaluationResult<?> result = path.evaluateExpression(expression, xml.getDocumentElement());
        List<Node> nodeResult = new ArrayList<>();
        switch (result.type()) {
            case NODESET:
                XPathNodes nodes = (XPathNodes) result.value();
                for (Node node : nodes) {
                    nodeResult.add(node);
                }
                break;
            case NODE:
                Node node = (Node) result.value();
                nodeResult.add(node);
        }
        return nodeResult;
    }

    protected static List<String> getParamValue(Document xml, String paramName) throws XPathExpressionException {
        if (!paramName.contains("'")) {
            paramName = "'" + paramName + "'";
        } else if (!paramName.contains("\"")) {
            paramName = "\"" + paramName + "\"";
        } else {
            paramName = "concat('" + paramName.replace("'", "',\"'\",'") + "')";
        }
        String expression = "/header/params/entry/key[text()=" + paramName + "]/../value/value";
        XPath path = XPathFactory.newInstance().newXPath();
        XPathEvaluationResult<?> result = path.evaluateExpression(expression, xml.getDocumentElement());
        var parList = new ArrayList<String>();
        switch (result.type()) {
            case NODESET:
                XPathNodes nodes = (XPathNodes) result.value();
                for (Node node : nodes) {
                    parList.add(node.getTextContent());
                }
                break;
            case NODE:
                Node node = (Node) result.value();
                parList.add(node.getTextContent());
                break;
        }
        return parList;
    }

}
