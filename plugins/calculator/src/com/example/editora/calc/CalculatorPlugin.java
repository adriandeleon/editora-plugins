package com.example.editora.calc;

import java.util.function.Supplier;

import com.editora.plugin.Plugin;
import com.editora.plugin.PluginContext;
import com.editora.plugin.ToolWindowSide;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

/**
 * A calculator tool window: type an arithmetic expression, see the result live, and insert it at the caret.
 * Pure evaluator in {@link Calc} (+ - * / % ^, parentheses, unary minus).
 */
public class CalculatorPlugin implements Plugin {

    @Override
    public void start(PluginContext ctx) {
        TextField input = new TextField();
        input.setPromptText("e.g. (2 + 3) * 4 ^ 2");
        Label result = new Label("=");
        result.getStyleClass().add("calc-result");

        Runnable compute = () -> {
            String expr = input.getText();
            if (expr == null || expr.isBlank()) {
                result.setText("=");
                return;
            }
            try {
                result.setText("= " + Calc.format(Calc.eval(expr)));
            } catch (RuntimeException e) {
                result.setText("⚠ " + e.getMessage());
            }
        };
        input.textProperty().addListener((o, a, b) -> compute.run());

        Button insert = new Button("Insert result");
        insert.setOnAction(e -> {
            String expr = input.getText();
            try {
                ctx.activeEditor().insertAtCaret(Calc.format(Calc.eval(expr)));
            } catch (RuntimeException ex) {
                ctx.setStatus("Calculator: " + ex.getMessage());
            }
        });
        input.setOnAction(e -> insert.fire()); // Enter inserts

        Label title = new Label("Calculator");
        title.getStyleClass().add("tool-window-title");
        HBox row = new HBox(8, input, insert);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(input, javafx.scene.layout.Priority.ALWAYS);
        VBox box = new VBox(8, title, row, result);
        box.setPadding(new Insets(10));
        Region content = box;

        ctx.registerToolWindow("calculator", "Calculator", ToolWindowSide.RIGHT, content, null, icon());
    }

    /** Material "functions" (Σ / fx) glyph — fits an arithmetic-expression evaluator. */
    private static Supplier<Node> icon() {
        return () -> {
            SVGPath svg = new SVGPath();
            svg.setContent("M18 4H6v2l6.5 6L6 18v2h12v-3h-7l5-5-5-5h7z");
            svg.getStyleClass().add("toolbar-icon");
            svg.setScaleX(0.8);
            svg.setScaleY(0.8);
            return new Group(svg);
        };
    }
}
