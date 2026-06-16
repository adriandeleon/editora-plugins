package com.example.editora.wordcount;

import java.util.function.Supplier;

import com.editora.plugin.Plugin;
import com.editora.plugin.PluginContext;
import com.editora.plugin.ToolWindowSide;

import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

/**
 * A live word-count tool window: lines / words / characters (with and without spaces) + an estimated
 * reading time for the active buffer. Refreshes on a ~1.5s timer (and via a Refresh button). Pure stats in
 * {@link WordStats}. Polling reads the buffer text only while the window is open.
 */
public class WordCountPlugin implements Plugin {

    private static final int MAX_CHARS = 2_000_000; // don't poll-copy huge buffers

    @Override
    public void start(PluginContext ctx) {
        Label stats = new Label();
        stats.setWrapText(true);
        Button refresh = new Button("Refresh");

        Runnable update = () -> {
            String text = ctx.activeEditor().text();
            if (text != null && text.length() > MAX_CHARS) {
                stats.setText("Buffer too large to count live (" + text.length() + " chars). Click Refresh.");
                return;
            }
            WordStats s = WordStats.of(text);
            stats.setText("Lines: " + s.lines + "\nWords: " + s.words + "\nCharacters: " + s.chars
                    + "\nCharacters (no spaces): " + s.charsNoSpace + "\nReading time: " + s.readingTime());
        };
        refresh.setOnAction(e -> update.run());
        update.run();

        Timeline ticker = new Timeline(new KeyFrame(Duration.seconds(1.5), e -> update.run()));
        ticker.setCycleCount(Timeline.INDEFINITE);
        ticker.play();

        Label title = new Label("Word Count");
        title.getStyleClass().add("tool-window-title");
        VBox box = new VBox(8, title, stats, refresh);
        box.setPadding(new Insets(10));
        Region content = box;

        ctx.registerToolWindow("wordcount", "Word Count", ToolWindowSide.RIGHT, content, null, icon());
    }

    /** Material "subject" (text lines) glyph — fits a word/line/character counter. */
    private static Supplier<Node> icon() {
        return () -> {
            SVGPath svg = new SVGPath();
            svg.setContent("M14 17H4v2h10v-2zm6-8H4v2h16V9zM4 15h16v-2H4v2zM4 5v2h16V5H4z");
            svg.getStyleClass().add("toolbar-icon");
            svg.setScaleX(0.8);
            svg.setScaleY(0.8);
            return new Group(svg);
        };
    }
}
