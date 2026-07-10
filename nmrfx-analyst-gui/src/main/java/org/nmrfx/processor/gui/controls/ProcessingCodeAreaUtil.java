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
package org.nmrfx.processor.gui.controls;

import javafx.concurrent.Task;
import org.nmrfx.processor.gui.OperationInfo;
import org.nmrfx.processor.gui.RefManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;



public class ProcessingCodeAreaUtil {

    private static final Logger log = LoggerFactory.getLogger(ProcessingCodeAreaUtil.class);

    private static final String[] OPS = OperationInfo.opOrders;
    private static final String[] PROPS = RefManager.propNames;

    private static final String[] KEYWORDS = new String[]{
            "DIM", "FID", "CREATE", "run"
    };

    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String OP_PATTERN = "\\b(" + String.join("|", OPS) + ")\\b";
    private static final String PROP_PATTERN = "\\b(" + String.join("|", PROPS) + "|acqOrder)\\b";
    private static final String PAREN_PATTERN = "\\(|\\)";
    private static final String BRACE_PATTERN = "\\{|\\}";
    private static final String BRACKET_PATTERN = "\\[|\\]";
    private static final String SEMICOLON_PATTERN = "\\;";
    private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"";
    private static final String SNGLSTRING_PATTERN = "\'([^\'\\\\]|\\\\.)*\'";
    private static final String COMMENT_PATTERN = "//[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/";

    private static final Pattern PATTERN = Pattern.compile(
            "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
                    + "|(?<OPS>" + OP_PATTERN + ")"
                    + "|(?<PROPS>" + PROP_PATTERN + ")"
                    + "|(?<PAREN>" + PAREN_PATTERN + ")"
                    + "|(?<BRACE>" + BRACE_PATTERN + ")"
                    + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
                    + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
                    + "|(?<STRING>" + STRING_PATTERN + ")"
                    + "|(?<SNGLSTRING>" + SNGLSTRING_PATTERN + ")"
                    + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
    );

    private CodeArea codeArea;
    private ExecutorService executor;

    public ProcessingCodeAreaUtil(CodeArea codeArea) {
        this.codeArea = codeArea;

        init();
    }

    public void init() {

    }

}
