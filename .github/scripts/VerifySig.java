import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Verify-only counterpart to Editora's {@code scripts/PluginSigningTool.java}, used by CI to confirm
 * {@code index.json.sig} is a valid Ed25519 signature of {@code index.json} under the bundled registry
 * public key. JDK-only (no dependencies); run with the single-file launcher:
 *
 * <pre>java .github/scripts/VerifySig.java &lt;public-key&gt; &lt;file&gt; &lt;sig&gt;</pre>
 *
 * Encodings match the in-app verifier ({@code com.editora.plugin.PluginSignature}): public key = base64
 * X.509, signature = base64 Ed25519 over the exact file bytes. Exits 1 on FAIL.
 */
public class VerifySig {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("usage: java VerifySig.java <public-key> <file> <sig>");
            System.exit(2);
        }
        byte[] der = Base64.getDecoder().decode(Files.readString(Path.of(args[0])).strip());
        PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
        Signature s = Signature.getInstance("Ed25519");
        s.initVerify(key);
        s.update(Files.readAllBytes(Path.of(args[1])));
        boolean ok = s.verify(Base64.getDecoder().decode(Files.readString(Path.of(args[2])).strip()));
        System.out.println(ok ? "OK — index.json.sig verifies" : "FAIL — signature does NOT verify");
        if (!ok) {
            System.exit(1);
        }
    }
}
