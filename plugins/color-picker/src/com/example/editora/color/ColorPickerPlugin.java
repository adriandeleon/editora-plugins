package com.example.editora.color;

import java.util.function.Supplier;

import com.editora.plugin.Plugin;
import com.editora.plugin.PluginContext;
import com.editora.plugin.ToolWindowSide;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/**
 * A color picker tool window: choose a color, choose an output format (HEX / rgb() / rgba()), and insert it
 * at the caret. Demonstrates a JavaFX-rich tool window plus {@code insertAtCaret}.
 */
public class ColorPickerPlugin implements Plugin {

    @Override
    public void start(PluginContext ctx) {
        ColorPicker picker = new ColorPicker(Color.web("#4F86C6"));
        ComboBox<String> format = new ComboBox<>();
        format.getItems().addAll("HEX", "rgb()", "rgba()");
        format.setValue("HEX");

        Label preview = new Label();
        preview.setMinWidth(160);
        Runnable refresh = () -> preview.setText(format(picker.getValue(), format.getValue()));
        picker.valueProperty().addListener((o, a, b) -> refresh.run());
        format.valueProperty().addListener((o, a, b) -> refresh.run());
        refresh.run();

        Button insert = new Button("Insert at caret");
        insert.setOnAction(e -> ctx.activeEditor().insertAtCaret(format(picker.getValue(), format.getValue())));

        Label title = new Label("Color Picker");
        title.getStyleClass().add("tool-window-title");
        HBox row = new HBox(8, picker, format, insert);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(8, title, row, preview);
        box.setPadding(new Insets(10));
        Region content = box;

        ctx.registerToolWindow("color", "Color Picker", ToolWindowSide.RIGHT, content, null, icon());
    }

    /** Formats a JavaFX color as a CSS string. Pure. */
    static String format(Color c, String fmt) {
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        return switch (fmt == null ? "HEX" : fmt) {
            case "rgb()" -> "rgb(" + r + ", " + g + ", " + b + ")";
            case "rgba()" -> "rgba(" + r + ", " + g + ", " + b + ", "
                    + String.format(java.util.Locale.ROOT, "%.2f", c.getOpacity()) + ")";
            default -> String.format("#%02x%02x%02x", r, g, b);
        };
    }

    /** Material "palette" glyph — fits a color picker. */
    private static Supplier<Node> icon() {
        return () -> {
            SVGPath svg = new SVGPath();
            svg.setContent("M12 2C6.49 2 2 6.49 2 12s4.49 10 10 10c1.38 0 2.5-1.12 2.5-2.5 0-.61-.23-1.2-.64-1.67-."
                    + "08-.1-.13-.21-.13-.33 0-.28.22-.5.5-.5H16c3.31 0 6-2.69 6-6 0-4.96-4.49-9-10-9zm5.5 11c-.83 "
                    + "0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm-3-4c-.83 0-1.5-.67-1.5-1.5S13."
                    + "67 6 14.5 6s1.5.67 1.5 1.5S15.33 9 14.5 9zM5 11.5c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5S7.33 13 6.5 "
                    + "13 5 12.33 5 11.5zm6-4C11 8.33 10.33 9 9.5 9S8 8.33 8 7.5 8.67 6 9.5 6s1.5.67 1.5 1.5z");
            svg.getStyleClass().add("toolbar-icon");
            svg.setScaleX(0.8);
            svg.setScaleY(0.8);
            return new Group(svg);
        };
    }
}
