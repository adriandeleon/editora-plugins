package com.example.editora.gpg;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Thin, dependency-free wrapper over the GnuPG ({@code gpg}) CLI: locates the binary cross-platform and runs
 * it <b>stdin → stdout</b> without blocking on pipe buffers. Pure helper — no JavaFX, no Editora internals.
 */
final class Gpg {

    private Gpg() {}

    static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    /** A public key usable as an encryption recipient (primary-key fingerprint + first user id). */
    record Key(String fingerprint, String keyId, String uid) {
        /** The value passed to {@code gpg -r} — the fingerprint when known, else the long key id. */
        String recipient() {
            return !fingerprint.isBlank() ? fingerprint : keyId;
        }

        @Override
        public String toString() {
            String shortId = keyId.length() > 8 ? keyId.substring(keyId.length() - 8) : keyId;
            String who = uid.isBlank() ? "(no user id)" : uid;
            return who + "  [" + shortId + "]";
        }
    }

    /** Result of a gpg invocation: exit code + raw stdout + stderr text. */
    record Result(int code, byte[] out, String err) {
        boolean ok() {
            return code == 0;
        }

        String outText() {
            return new String(out, StandardCharsets.UTF_8);
        }
    }

    // ---- binary resolution -------------------------------------------------

    /** Common install dirs to fold into PATH so a GUI-launched (Finder/.desktop) app can still find gpg. */
    private static List<String> extraDirs() {
        List<String> dirs = new ArrayList<>();
        if (WINDOWS) {
            for (String env : new String[] {"ProgramFiles", "ProgramFiles(x86)", "ProgramW6432"}) {
                String base = System.getenv(env);
                if (base != null && !base.isBlank()) {
                    dirs.add(base + "\\GnuPG\\bin");
                    dirs.add(base + "\\Git\\usr\\bin"); // Git for Windows bundles gpg
                }
            }
        } else {
            String home = System.getProperty("user.home", "");
            dirs.add("/opt/homebrew/bin"); // Homebrew (Apple Silicon)
            dirs.add("/usr/local/bin"); // Homebrew (Intel) / common
            dirs.add("/usr/local/MacGPG2/bin"); // GPG Suite (macOS)
            dirs.add("/opt/local/bin"); // MacPorts
            dirs.add("/usr/bin");
            dirs.add("/bin");
            if (!home.isBlank()) {
                dirs.add(home + "/.local/bin");
            }
        }
        return dirs;
    }

    static String augmentedPath() {
        StringBuilder sb = new StringBuilder();
        String inherited = System.getenv("PATH");
        if (inherited != null && !inherited.isBlank()) {
            sb.append(inherited);
        }
        for (String d : extraDirs()) {
            if (sb.indexOf(d) < 0) {
                if (sb.length() > 0) {
                    sb.append(File.pathSeparatorChar);
                }
                sb.append(d);
            }
        }
        return sb.toString();
    }

    /**
     * Resolves the gpg executable: a valid configured path if given, else {@code gpg}/{@code gpg.exe} found
     * on the augmented PATH, else the bare name (let the OS resolve it / surface a clear "not found").
     */
    static String resolve(String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path p = Paths.get(configuredPath.trim());
            if (Files.isRegularFile(p)) {
                return p.toString();
            }
        }
        String[] names = WINDOWS ? new String[] {"gpg.exe", "gpg"} : new String[] {"gpg"};
        for (String dir : augmentedPath().split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            for (String n : names) {
                Path c = Paths.get(dir, n);
                if (Files.isRegularFile(c)) {
                    return c.toString();
                }
            }
        }
        return WINDOWS ? "gpg.exe" : "gpg";
    }

    // ---- invocation --------------------------------------------------------

    /**
     * Runs gpg with {@code args}, feeding {@code input} (may be null) to stdin and returning its output.
     * stdin is written and stderr drained on separate daemon threads, so it never deadlocks on a full pipe.
     */
    static Result run(String gpgPath, List<String> args, byte[] input) {
        List<String> cmd = new ArrayList<>();
        cmd.add(gpgPath);
        cmd.addAll(args);
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("PATH", augmentedPath());
            Process p = pb.start();

            Thread writer = new Thread(
                    () -> {
                        try (OutputStream os = p.getOutputStream()) {
                            if (input != null) {
                                os.write(input);
                            }
                        } catch (IOException ignored) {
                            // gpg may close stdin early (e.g. bad input); nothing to do.
                        }
                    },
                    "gpg-stdin");
            writer.setDaemon(true);
            writer.start();

            byte[][] errBox = new byte[1][];
            Thread errReader = new Thread(
                    () -> {
                        try {
                            errBox[0] = p.getErrorStream().readAllBytes();
                        } catch (IOException e) {
                            errBox[0] = new byte[0];
                        }
                    },
                    "gpg-stderr");
            errReader.setDaemon(true);
            errReader.start();

            byte[] out = p.getInputStream().readAllBytes();
            errReader.join();
            writer.join();
            int code = p.waitFor();
            byte[] err = errBox[0] == null ? new byte[0] : errBox[0];
            return new Result(code, out, new String(err, StandardCharsets.UTF_8));
        } catch (IOException e) {
            // Most commonly: gpg isn't installed / not on PATH.
            String m = e.getMessage() == null ? e.toString() : e.getMessage();
            return new Result(127, new byte[0], m);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(130, new byte[0], "interrupted");
        }
    }

    /** First line of {@code gpg --version} (e.g. "gpg (GnuPG) 2.4.5"), or null if gpg couldn't run. */
    static String version(String gpgPath) {
        Result r = run(gpgPath, List.of("--version"), null);
        if (!r.ok()) {
            return null;
        }
        String text = r.outText().strip();
        int nl = text.indexOf('\n');
        return nl < 0 ? text : text.substring(0, nl);
    }

    /** Parses {@code gpg --list-keys --with-colons} into recipient keys (primary key fingerprint + 1st uid). */
    static List<Key> parseKeys(String colons) {
        List<Key> keys = new ArrayList<>();
        String keyId = "";
        String fpr = "";
        String uid = "";
        boolean open = false;
        for (String line : colons.split("\n")) {
            String[] f = line.split(":", -1);
            if (f.length == 0) {
                continue;
            }
            switch (f[0]) {
                case "pub" -> {
                    if (open) {
                        keys.add(new Key(fpr, keyId, uid));
                    }
                    keyId = f.length > 4 ? f[4] : "";
                    fpr = "";
                    uid = "";
                    open = true;
                }
                case "fpr" -> {
                    if (open && fpr.isBlank() && f.length > 9) {
                        fpr = f[9];
                    }
                }
                case "uid" -> {
                    if (open && uid.isBlank() && f.length > 9) {
                        uid = f[9];
                    }
                }
                default -> {
                    // sub/grp/etc. — ignored for recipient selection.
                }
            }
        }
        if (open) {
            keys.add(new Key(fpr, keyId, uid));
        }
        return keys;
    }
}
