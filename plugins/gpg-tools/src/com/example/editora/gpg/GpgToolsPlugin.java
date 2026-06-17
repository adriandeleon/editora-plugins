package com.example.editora.gpg;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;

import com.editora.plugin.ActiveEditor;
import com.editora.plugin.Plugin;
import com.editora.plugin.PluginContext;
import com.editora.plugin.ToolWindowSide;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

/**
 * GnuPG integration: encrypt / decrypt / sign / verify the active buffer via the external {@code gpg} CLI,
 * plus very simple key management (list public keys, pick a recipient, import, generate). All crypto runs in
 * {@code gpg} over <b>stdin → stdout</b> with ASCII armor, so plaintext never touches a temp file and the
 * result stays text; the buffer is replaced via {@link ActiveEditor#setText} (undoable).
 *
 * <p>Passphrases are handled entirely by {@code gpg-agent}/pinentry — this plugin never sees or stores them.
 * The {@code gpg} binary is resolved against an augmented PATH (Homebrew / GPG Suite / Gpg4win / Git-for-
 * Windows) so a GUI-launched app finds it; an explicit path can be configured in the tool window.
 */
public class GpgToolsPlugin implements Plugin {

    private PluginContext ctx;
    private Path configFile;
    private final Properties config = new Properties();

    private String gpgPath = "gpg";

    private Label status;
    private ComboBox<Gpg.Key> recipients;
    private TextField gpgPathField;
    private TextArea output;

    @Override
    public void start(PluginContext ctx) {
        this.ctx = ctx;
        this.configFile = ctx.dataDir().resolve("gpg.properties");
        loadConfig();
        this.gpgPath = Gpg.resolve(config.getProperty("gpg.path", ""));

        buildToolWindow();
        registerCommands();

        // Probe gpg + load keys off the FX thread (spawning a process must not block UI).
        worker(this::refreshStatusAndKeys);
    }

    // ---- UI ----------------------------------------------------------------

    private void buildToolWindow() {
        status = new Label("Checking for GnuPG…");
        status.getStyleClass().add("tool-window-title");

        recipients = new ComboBox<>();
        recipients.setMaxWidth(Double.MAX_VALUE);
        recipients.setPromptText("Recipient (public key)");
        HBox.setHgrow(recipients, Priority.ALWAYS);
        recipients.valueProperty().addListener((o, was, now) -> {
            config.setProperty("recipient", now == null ? "" : now.recipient());
            saveConfig();
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> worker(this::refreshStatusAndKeys));
        HBox recipientRow = new HBox(8, new Label("Recipient:"), recipients, refresh);
        recipientRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        FlowPane actions = new FlowPane(8, 8);
        actions.getChildren().addAll(
                btn("Encrypt", this::encrypt),
                btn("Decrypt", this::decrypt),
                btn("Passphrase…", this::encryptSymmetric),
                btn("Sign", this::clearsign),
                btn("Verify", this::verify),
                btn("Import key", this::importKey),
                btn("Generate…", this::generateKey));

        gpgPathField = new TextField(config.getProperty("gpg.path", ""));
        gpgPathField.setPromptText("auto-detected — set an explicit gpg path if needed");
        HBox.setHgrow(gpgPathField, Priority.ALWAYS);
        Runnable applyPath = () -> {
            config.setProperty("gpg.path", gpgPathField.getText().trim());
            saveConfig();
            gpgPath = Gpg.resolve(gpgPathField.getText().trim());
            worker(this::refreshStatusAndKeys);
        };
        gpgPathField.setOnAction(e -> applyPath.run());
        gpgPathField.focusedProperty().addListener((o, was, focused) -> {
            if (!focused) {
                applyPath.run();
            }
        });
        HBox pathRow = new HBox(8, new Label("gpg path:"), gpgPathField);
        pathRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        output = new TextArea();
        output.setEditable(false);
        output.setWrapText(true);
        output.setPromptText("gpg output appears here.");
        output.getStyleClass().add("gpg-output");
        VBox.setVgrow(output, Priority.ALWAYS);

        VBox box = new VBox(8, status, recipientRow, actions, pathRow, output);
        box.setPadding(new Insets(10));
        Region content = box;

        ctx.registerToolWindow("gpg", "GnuPG", ToolWindowSide.BOTTOM, content, null, icon());
    }

    private Button btn(String label, Runnable action) {
        Button b = new Button(label);
        b.setOnAction(e -> action.run());
        return b;
    }

    // ---- commands ----------------------------------------------------------

    private void registerCommands() {
        ctx.registerCommand("gpg.encrypt", "GnuPG: Encrypt to Recipient", this::encrypt);
        ctx.registerCommand("gpg.decrypt", "GnuPG: Decrypt", this::decrypt);
        ctx.registerCommand("gpg.encryptSymmetric", "GnuPG: Encrypt with Passphrase (Symmetric)", this::encryptSymmetric);
        ctx.registerCommand("gpg.clearsign", "GnuPG: Clear-Sign", this::clearsign);
        ctx.registerCommand("gpg.verify", "GnuPG: Verify Signature", this::verify);
        ctx.registerCommand("gpg.importKey", "GnuPG: Import Key from Buffer", this::importKey);
        ctx.registerCommand("gpg.refreshKeys", "GnuPG: Refresh Keys", () -> worker(this::refreshStatusAndKeys));
        ctx.registerCommand("gpg.generateKey", "GnuPG: Generate Key…", this::generateKey);
    }

    // ---- operations (read buffer on FX thread, run gpg off it, apply back on it) --------------------------

    private void encrypt() {
        Gpg.Key key = recipients.getValue();
        if (key == null) {
            setStatus("Pick a recipient in the GnuPG tool window first");
            return;
        }
        transform(
                "Encrypt",
                List.of("--batch", "--yes", "--no-tty", "--armor", "--trust-model", "always", "--output", "-",
                        "--encrypt", "--recipient", key.recipient()));
    }

    private void decrypt() {
        // No --batch: let gpg-agent/pinentry prompt for the secret-key passphrase.
        transform("Decrypt", List.of("--yes", "--no-tty", "--output", "-", "--decrypt"));
    }

    private void encryptSymmetric() {
        // Passphrase is requested by pinentry; never handled here.
        transform("Symmetric encrypt", List.of("--yes", "--no-tty", "--armor", "--output", "-", "--symmetric"));
    }

    private void clearsign() {
        transform("Clear-sign", List.of("--yes", "--no-tty", "--output", "-", "--clearsign"));
    }

    private void verify() {
        byte[] input = bufferBytes("verify");
        if (input == null) {
            return;
        }
        setStatus("Verifying signature…");
        worker(() -> {
            Gpg.Result r = Gpg.run(gpgPath, List.of("--no-tty", "--verify", "-"), input);
            Platform.runLater(() -> {
                logBlock("Verify", r.err().isBlank() ? (r.ok() ? "Good signature." : "No valid signature.") : r.err());
                setStatus(r.ok() ? "Signature verified" : "Verification failed");
            });
        });
    }

    private void importKey() {
        byte[] input = bufferBytes("import");
        if (input == null) {
            return;
        }
        setStatus("Importing key…");
        worker(() -> {
            Gpg.Result r = Gpg.run(gpgPath, List.of("--batch", "--no-tty", "--import"), input);
            Platform.runLater(() -> {
                logBlock("Import", r.err().isBlank() ? r.outText() : r.err());
                setStatus(r.ok() ? "Key import done" : "Key import failed");
                refreshStatusAndKeys();
            });
        });
    }

    private void generateKey() {
        Optional<String[]> who = promptNameEmail();
        if (who.isEmpty()) {
            return;
        }
        String name = who.get()[0].trim();
        String email = who.get()[1].trim();
        if (name.isEmpty() && email.isEmpty()) {
            setStatus("Generate cancelled — a name or email is required");
            return;
        }
        String userId = email.isEmpty() ? name : (name.isEmpty() ? email : name + " <" + email + ">");
        setStatus("Generating key for " + userId + " (pinentry will ask for a passphrase)…");
        worker(() -> {
            // --quick-generate-key: default algo, no expiry; pinentry prompts for the passphrase.
            Gpg.Result r =
                    Gpg.run(gpgPath, List.of("--no-tty", "--quick-generate-key", userId, "default", "default", "never"), null);
            Platform.runLater(() -> {
                logBlock("Generate", r.err().isBlank() ? r.outText() : r.err());
                setStatus(r.ok() ? "Key generated" : "Key generation failed");
                refreshStatusAndKeys();
            });
        });
    }

    /** Encrypt/decrypt/sign family: read the buffer, run gpg, replace the buffer with the output on success. */
    private void transform(String opName, List<String> args) {
        byte[] input = bufferBytes(opName.toLowerCase());
        if (input == null) {
            return;
        }
        ActiveEditor ed = ctx.activeEditor();
        setStatus(opName + "…");
        worker(() -> {
            Gpg.Result r = Gpg.run(gpgPath, args, input);
            Platform.runLater(() -> {
                if (r.ok()) {
                    ed.setText(r.outText());
                    setStatus(opName + " — done");
                    if (!r.err().isBlank()) {
                        logBlock(opName, r.err());
                    }
                } else {
                    logBlock(opName + " failed", r.err().isBlank() ? "gpg exit " + r.code() : r.err());
                    setStatus(opName + " failed — see GnuPG output");
                }
            });
        });
    }

    /** The active buffer's bytes, or null (with a status message) when there's nothing to act on. */
    private byte[] bufferBytes(String op) {
        ActiveEditor ed = ctx.activeEditor();
        String text = ed.text();
        if (text == null || text.isEmpty()) {
            setStatus("Nothing to " + op);
            return null;
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }

    // ---- key listing / status ----------------------------------------------

    /** Runs on a worker thread: probe gpg version + reload public keys, applying both on the FX thread. */
    private void refreshStatusAndKeys() {
        String version = Gpg.version(gpgPath);
        List<Gpg.Key> keys = version == null
                ? List.of()
                : Gpg.parseKeys(Gpg.run(gpgPath, List.of("--list-keys", "--with-colons"), null).outText());
        Platform.runLater(() -> {
            if (version == null) {
                status.setText("GnuPG not found — install gpg or set its path below");
            } else {
                status.setText(version + " — " + keys.size() + " public key(s)");
            }
            String wanted = config.getProperty("recipient", "");
            recipients.getItems().setAll(keys);
            Gpg.Key restore = keys.stream().filter(k -> k.recipient().equals(wanted)).findFirst().orElse(null);
            if (restore != null) {
                recipients.setValue(restore);
            } else if (!keys.isEmpty() && recipients.getValue() == null) {
                recipients.setValue(keys.get(0));
            }
        });
    }

    // ---- helpers -----------------------------------------------------------

    private void setStatus(String msg) {
        // Transient messages go to the status-bar echo; the tool window's headline shows the gpg version +
        // key count and is refreshed by refreshStatusAndKeys().
        ctx.setStatus(msg);
    }

    private void logBlock(String title, String body) {
        if (output == null) {
            return;
        }
        String text = body == null ? "" : body.strip();
        StringBuilder sb = new StringBuilder();
        if (!output.getText().isEmpty()) {
            sb.append("\n");
        }
        sb.append("=== ").append(title).append(" ===\n").append(text).append("\n");
        String hint = pinentryHint(text);
        if (hint != null) {
            sb.append("\n").append(hint).append("\n");
        }
        output.appendText(sb.toString());
        output.positionCaret(output.getLength());
    }

    /**
     * When gpg's error is the classic "no terminal for the agent" failure (a GUI-launched app has no TTY and
     * no graphical pinentry is configured), returns an OS-specific tip; otherwise null.
     */
    private static String pinentryHint(String err) {
        String e = err.toLowerCase(java.util.Locale.ROOT);
        boolean agentPrompt = e.contains("inappropriate ioctl") || e.contains("no pinentry")
                || e.contains("problem with the agent") || e.contains("no such file or directory")
                || (e.contains("operation cancelled") && e.contains("passphrase"));
        if (!agentPrompt) {
            return null;
        }
        if (Gpg.WINDOWS) {
            return "Tip: gpg-agent could not show a passphrase dialog. Install Gpg4win (it bundles a GUI "
                    + "pinentry) and make sure gpg-agent is running.";
        }
        boolean mac = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
        if (mac) {
            return "Tip: gpg-agent has no graphical passphrase prompt. Install one and point the agent at it:\n"
                    + "  brew install pinentry-mac\n"
                    + "  echo \"pinentry-program $(brew --prefix)/bin/pinentry-mac\" >> ~/.gnupg/gpg-agent.conf\n"
                    + "  gpgconf --kill gpg-agent";
        }
        return "Tip: gpg-agent has no graphical passphrase prompt. Install a GUI pinentry "
                + "(e.g. pinentry-gnome3 or pinentry-qt), set 'pinentry-program /path/to/pinentry' in "
                + "~/.gnupg/gpg-agent.conf, then run 'gpgconf --kill gpg-agent'.";
    }

    private static void worker(Runnable r) {
        Thread t = new Thread(r, "gpg-op");
        t.setDaemon(true);
        t.start();
    }

    private void loadConfig() {
        try {
            if (Files.exists(configFile)) {
                try (InputStream in = Files.newInputStream(configFile)) {
                    config.load(in);
                }
            }
        } catch (IOException e) {
            ctx.log("GnuPG: could not read config: " + e.getMessage());
        }
    }

    private void saveConfig() {
        try {
            Files.createDirectories(configFile.getParent());
            try (OutputStream out = Files.newOutputStream(configFile)) {
                config.store(out, "Editora GnuPG plugin settings");
            }
        } catch (IOException e) {
            ctx.log("GnuPG: could not save config: " + e.getMessage());
        }
    }

    /** A small modal dialog asking for a name + email for key generation; empty when cancelled. */
    private Optional<String[]> promptNameEmail() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Generate GnuPG Key");
        dialog.setHeaderText("Create a new key pair. gpg-agent will ask for a passphrase.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField name = new TextField();
        name.setPromptText("Your Name");
        TextField email = new TextField();
        email.setPromptText("you@example.com");
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("Name:"), name);
        grid.addRow(1, new Label("Email:"), email);
        dialog.getDialogPane().setContent(grid);
        Platform.runLater(name::requestFocus);

        dialog.setResultConverter(bt -> bt == ButtonType.OK ? new String[] {name.getText(), email.getText()} : null);
        return dialog.showAndWait();
    }

    /** Material "lock" (padlock) glyph for the GnuPG tool window. */
    private static Supplier<Node> icon() {
        return () -> {
            SVGPath svg = new SVGPath();
            svg.setContent("M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 "
                    + "0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-"
                    + "1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z");
            svg.getStyleClass().add("toolbar-icon");
            svg.setScaleX(0.8);
            svg.setScaleY(0.8);
            return new Group(svg);
        };
    }
}
