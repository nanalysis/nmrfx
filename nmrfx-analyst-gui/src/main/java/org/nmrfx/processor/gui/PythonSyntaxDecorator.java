package org.nmrfx.processor.gui;

import jfx.incubator.scene.control.richtext.SyntaxDecorator;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;
import jfx.incubator.scene.control.richtext.model.RichParagraph;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PythonSyntaxDecorator implements SyntaxDecorator {
    // Define compiled regex patterns matching Python specific token spaces
    private static final String KEYWORD_PATTERN = "\\b(false|None|True|and|as|assert|async|await|break|class|continue|def|del|elif|else|except|finally|for|from|global|if|import|in|is|lambda|nonlocal|not|or|pass|raise|return|try|while|with|yield)\\b";
    private static final String BUILTIN_PATTERN = "\\b(print|len|range|str|int|float|list|dict|set|tuple|open|abs|type|enumerate|zip)\\b";
    private static final String COMMENT_PATTERN = "#[^\n]*";
    private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'";
    private static final String NUMBER_PATTERN = "\\b\\d+(\\.\\d+)?\\b";
    private static final String DECORATOR_PATTERN = "@[a-zA-Z_]\\w*";
    // Matches any word followed by '(' but doesn't style the parenthesis itself
    private static final String METHOD_PATTERN = "[a-zA-Z_]\\w*(?=\\s*\\()";


    private static final Pattern PYTHON_PATTERN = Pattern.compile(
            "(?<METHOD>" + METHOD_PATTERN + ")"
                    + "|(?<KEYWORD>" + KEYWORD_PATTERN + ")"
                    + "|(?<BUILTIN>" + BUILTIN_PATTERN + ")"
                    + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
                    + "|(?<STRING>" + STRING_PATTERN + ")"
                    + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
                    + "|(?<DECORATOR>" + DECORATOR_PATTERN + ")"
    );

    @Override
    public RichParagraph createRichParagraph(CodeTextModel model, int index) {
        // Retrieve the clean string content for the current request line
        String text = model.getPlainText(index);

        // Build the styled line using the rich paragraph framework builder
        RichParagraph.Builder builder = RichParagraph.builder();
        Matcher matcher = PYTHON_PATTERN.matcher(text);
        int lastMatchEnd = 0;

        while (matcher.find()) {
            String styleClass = null;
            if (matcher.group("KEYWORD") != null) {
                styleClass = "py-keyword";
            } else if (matcher.group("BUILTIN") != null) {
                styleClass = "py-builtin";
            } else if (matcher.group("METHOD") != null) {
                styleClass = "py-method";
            } else if (matcher.group("COMMENT") != null) {
                styleClass = "py-comment";
            } else if (matcher.group("STRING") != null) {
                styleClass = "py-string";
            } else if (matcher.group("NUMBER") != null) {
                styleClass = "py-number";
            } else if (matcher.group("DECORATOR") != null) {
                styleClass = "py-decorator";
            }

            // Append plain unstyled text lying between match groups
            if (matcher.start() > lastMatchEnd) {
                builder.addWithStyleNames(text.substring(lastMatchEnd, matcher.start()), "py-default");
            }

            // Append the token assigned with its programmatic CSS class name
            builder.addWithStyleNames(matcher.group(), styleClass);
            lastMatchEnd = matcher.end();
        }

        // Add remaining trailing line segments safely
        if (lastMatchEnd < text.length()) {
            builder.addWithStyleNames(text.substring(lastMatchEnd), "py-default");
        }

        return builder.build();
    }

    @Override
    public void handleChange(CodeTextModel m, TextPos start, TextPos end, int charsTop, int linesAdded, int charsBottom) {
        // no need to handle this as syntax can be determined from a single paragraph
    }
}
