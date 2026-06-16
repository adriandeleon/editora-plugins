package com.example.editora.regex;

import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.editora.plugin.Plugin;
import com.editora.plugin.PluginContext;
import com.editora.plugin.ToolWindowSide;

import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

/**
 * A live regular-expression tester tool window: enter a pattern + flags + a test string and see the match
 * count, each match's span, and its capture groups — recomputed as you type. Uses {@link java.util.regex}.
 */
public class RegexTesterPlugin implements Plugin {

    private static final int MAX_MATCHES = 500;

    @Override
    public void start(PluginContext ctx) {
        TextField pattern = new TextField();
        pattern.setPromptText("Regular expression, e.g. (\\w+)@(\\w+)");
        CheckBox ignoreCase = new CheckBox("Ignore case");
        CheckBox multiline = new CheckBox("Multiline (^$)");
        CheckBox dotAll = new CheckBox("Dot matches newline");
        HBox flags = new HBox(12, ignoreCase, multiline, dotAll);

        TextArea subject = new TextArea();
        subject.setPromptText("Test string…");
        subject.setWrapText(true);
        VBox.setVgrow(subject, Priority.ALWAYS);

        TextArea results = new TextArea();
        results.setEditable(false);
        results.setWrapText(true);
        results.getStyleClass().add("regex-results");
        VBox.setVgrow(results, Priority.ALWAYS);

        Runnable update = () -> results.setText(evaluate(
                pattern.getText(), subject.getText(),
                ignoreCase.isSelected(), multiline.isSelected(), dotAll.isSelected()));

        pattern.textProperty().addListener((o, a, b) -> update.run());
        subject.textProperty().addListener((o, a, b) -> update.run());
        ignoreCase.selectedProperty().addListener((o, a, b) -> update.run());
        multiline.selectedProperty().addListener((o, a, b) -> update.run());
        dotAll.selectedProperty().addListener((o, a, b) -> update.run());

        Label title = new Label("Regex Tester");
        title.getStyleClass().add("tool-window-title");
        VBox box = new VBox(6, title, pattern, flags, new Label("Test string"), subject,
                new Label("Matches"), results);
        box.setPadding(new Insets(8));
        Region content = box;

        ctx.registerToolWindow("regex", "Regex Tester", ToolWindowSide.BOTTOM, content, null, icon());
    }

    /** Compiles + runs the pattern, returning a human-readable report (or the syntax error). Pure-ish. */
    private static String evaluate(String regex, String text, boolean ic, boolean ml, boolean dotAll) {
        if (regex == null || regex.isEmpty()) {
            return "Enter a pattern.";
        }
        int flags = 0;
        if (ic) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        if (ml) {
            flags |= Pattern.MULTILINE;
        }
        if (dotAll) {
            flags |= Pattern.DOTALL;
        }
        Pattern p;
        try {
            p = Pattern.compile(regex, flags);
        } catch (PatternSyntaxException e) {
            return "Invalid pattern: " + e.getDescription() + " (index " + e.getIndex() + ")";
        }
        Matcher m = p.matcher(text == null ? "" : text);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        while (m.find() && n < MAX_MATCHES) {
            n++;
            sb.append(n).append(". [").append(m.start()).append('-').append(m.end()).append("] ")
                    .append(quote(m.group())).append('\n');
            for (int g = 1; g <= m.groupCount(); g++) {
                sb.append("     group ").append(g).append(": ").append(quote(m.group(g))).append('\n');
            }
            if (m.end() == m.start()) { // zero-width match: avoid an infinite loop
                if (m.end() >= text.length()) {
                    break;
                }
                m.region(m.end() + 1, text.length());
            }
        }
        String header = n == 0 ? "No matches." : (n + (n == MAX_MATCHES ? "+ matches:\n" : " match(es):\n"));
        return header + sb;
    }

    private static String quote(String s) {
        return s == null ? "(null)" : "“" + s + "”";
    }

    /** Material "search" (magnifier) glyph — fits a regex match tester. */
    private static Supplier<Node> icon() {
        return () -> {
            SVGPath svg = new SVGPath();
            svg.setContent("M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 "
                    + "3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 "
                    + "0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z");
            svg.getStyleClass().add("toolbar-icon");
            svg.setScaleX(0.8);
            svg.setScaleY(0.8);
            return new Group(svg);
        };
    }
}
