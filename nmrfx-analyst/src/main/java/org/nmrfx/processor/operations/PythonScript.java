/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.operations;

import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.datasets.MatrixType;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;
import org.python.core.PyJavaType;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;

import static org.nmrfx.utils.FormatUtils.formatStringForPythonInterpreter;

/**
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class PythonScript extends MatrixOperation {

    /**
     * The command run on each Operation evaluation.
     */
    private final String script;

    /**
     * An optional command to run when initializing the interpreter.
     */
    private final String initialScript;

    /**
     * An optional fileName to exec when initializing the interpreter.
     */
    private final String execFileName;

    /**
     * If True, each evaluation creates a new interpreter; no variable sharing between an Operation's evaluation on a
     * different vector.
     */
    private final boolean encapsulate;
    private PythonInterpreter interpreter;

    public PythonScript(String script) {
        this(script, "", "", true);
    }

    /**
     * @param script        The script to run at each Operation evaluation
     * @param initialScript An optional script to run when initializing the interpreter
     * @param encapsulate   Whether the interpreter should persist between evaluations
     */
    public PythonScript(String script, String initialScript, String execFileName, boolean encapsulate) {
        this.script = script;
        this.encapsulate = encapsulate;
        this.initialScript = initialScript;
        this.execFileName = execFileName;
        if (!this.encapsulate) {
            interpreter = new PythonInterpreter();
            if (execFileName.length() != 0) {
                interpreter.execfile(formatStringForPythonInterpreter(execFileName));
            }
            if (initialScript.length() != 0) {
                interpreter.exec(formatStringForPythonInterpreter(initialScript));
            }
        }
    }

    @Override
    public Operation eval(Vec vector) throws ProcessingException {
        /**
         * If the interpreter is created in a per-Operation basis, then we could share variables between all vectors
         * which are being evaluated by the PythonScript Operation.
         */
        if (encapsulate) {
            interpreter = new PythonInterpreter();
            if (execFileName.length() != 0) {
                interpreter.execfile(formatStringForPythonInterpreter(execFileName));
            }
            if (initialScript.length() != 0) {
                interpreter.exec(formatStringForPythonInterpreter(initialScript));
            }
        }
        PyObject pyObject = PyJavaType.wrapJavaObject(vector);
        try {
            interpreter.set("vec", pyObject);
            interpreter.set("vecmat", pyObject);
            interpreter.exec(formatStringForPythonInterpreter(script));
        } catch (Exception e) {
            throw new ProcessingException(e.getLocalizedMessage());
        }
        return this;
    }

    @Override
    public Operation evalMatrix(MatrixType matrix) {
        /**
         * If the interpreter is created in a per-Operation basis, then we could share variables between all vectors
         * which are being evaluated by the PythonScript Operation.
         */
        if (encapsulate) {
            interpreter = new PythonInterpreter();
            interpreter.exec(formatStringForPythonInterpreter(initialScript));
        }
        PyObject pyObject = PyJavaType.wrapJavaObject(matrix);
        try {
            interpreter.set("matrix", pyObject);
            interpreter.set("vecmat", pyObject);
            interpreter.exec(formatStringForPythonInterpreter(script));
        } catch (Exception e) {
            throw new ProcessingException(e.getLocalizedMessage());
        }

        return this;

    }

    public PythonScript clone() {
        return new PythonScript(script, initialScript, execFileName, encapsulate);
    }

}
