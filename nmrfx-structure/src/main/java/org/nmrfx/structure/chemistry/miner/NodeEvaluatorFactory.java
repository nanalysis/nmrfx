/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.miner;

import org.codehaus.commons.compiler.CompilerFactoryFactory;
import org.codehaus.commons.compiler.IClassBodyEvaluator;

import java.io.StringReader;

/**
 * @author Bruce Johnson
 */
public class NodeEvaluatorFactory {

    public NodeValidatorInterface build(String classBody) throws Exception {

        // Compile the class body.
        IClassBodyEvaluator cbe = CompilerFactoryFactory.getDefaultCompilerFactory().newClassBodyEvaluator();
        Class[] iIface = {NodeValidatorInterface.class};
        cbe.setImplementedInterfaces(iIface);
        cbe.setParentClassLoader(NodeEvaluatorFactory.class.getClassLoader());
        NodeValidatorInterface nodeValidator = (NodeValidatorInterface) cbe.createInstance(new StringReader(classBody));
        return nodeValidator;
    }

    public static NodeValidatorInterface getDefault() {
        return new NodeValidator();
    }
}
