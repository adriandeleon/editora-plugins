# GnuPG plugin

Encrypt, decrypt, sign, and verify the active buffer with **GnuPG** (the external `gpg` CLI). Everything runs
through `gpg` **stdin → stdout** with ASCII armor, so plaintext never hits a temp file and the result stays
text. The in-place actions replace the buffer (undoable via `setText`, so a single Undo restores it) — save
afterwards, or *Save As* to keep the encrypted copy separate. The **to-file** actions instead write `gpg`'s
output to a new file you choose and leave the buffer untouched.

Cross-platform: macOS, Linux, and Windows.

## Requirements

Install GnuPG and make sure `gpg` is on your `PATH` (the plugin also augments `PATH` with the usual install
dirs so a Finder/Start-menu-launched app still finds it):

| OS | Install |
| --- | --- |
| macOS | [GPG Suite](https://gpgtools.org), or `brew install gnupg` |
| Linux | `apt install gnupg` / `dnf install gnupg2` / your package manager |
| Windows | [Gpg4win](https://gpg4win.org), or the `gpg` bundled with Git for Windows |

If it isn't auto-detected, set an explicit path in the tool window's **gpg path** field.

## Commands

Open the command palette (`M-x`) — all are prefixed **GnuPG:**

| Command | What it does |
| --- | --- |
| **Encrypt to Recipient** | Public-key encrypt the buffer for the recipient selected in the tool window (ASCII-armored), replacing the buffer. |
| **Encrypt to New File** | Public-key encrypt the buffer and write the armored ciphertext to a new file you choose (default `‹file›.asc`); the buffer is left untouched, then the new file opens. |
| **Decrypt** | Decrypt the buffer (gpg-agent/pinentry prompts for your passphrase). |
| **Encrypt with Passphrase (Symmetric)** | Symmetric-encrypt with a passphrase — no keys needed. |
| **Clear-Sign** | Wrap the buffer in a clear-text signature (inline, replacing the buffer). |
| **Sign to New File (Detached)** | Write a **detached** armored signature (`--detach-sign`) to a new file you choose (default `‹file›.sig`) beside the original; the buffer is left untouched. |
| **Verify Signature** | Verify a signed/clear-signed buffer; result shown in the tool window. |
| **Refresh Keys** | Reload the public-key list. |
| **GnuPG** | Toggle the tool window. |

## Tool window

A **GnuPG** tool window (padlock icon, docked bottom) shows the detected `gpg` version + key count, a
**recipient** picker built from your public keys (remembered between sessions), buttons for every action, an
optional **gpg path** override, and an output pane for `gpg`'s messages.

## Key management

Out of scope by design — this plugin encrypts/decrypts/signs/verifies; it doesn't administer keys. It only
**lists your public keys** so you can pick an encryption recipient (remembered between sessions). To create,
import, export, or edit keys, use GPG Keychain (GPG Suite) or `gpg` directly — e.g. `gpg --import`,
`gpg --export --armor <id>`, or `gpg --full-generate-key`.

## Troubleshooting

**`gpg: problem with the agent: Inappropriate ioctl for device`** (or "error creating passphrase: Operation
cancelled") — gpg-agent has no way to ask for your passphrase because the app was launched from the GUI (no
terminal) and no **graphical** pinentry is configured. Install one and point the agent at it:

```sh
# macOS (Homebrew gnupg — GPG Suite already includes pinentry-mac)
brew install pinentry-mac
echo "pinentry-program $(brew --prefix)/bin/pinentry-mac" >> ~/.gnupg/gpg-agent.conf
gpgconf --kill gpg-agent
```

On Linux install `pinentry-gnome3`/`pinentry-qt` and set `pinentry-program` in `~/.gnupg/gpg-agent.conf`
(then `gpgconf --kill gpg-agent`). On Windows, Gpg4win bundles a GUI pinentry. The plugin prints this tip in
its output pane when it detects the error.

## Security

The plugin **never sees, stores, or passes passphrases** — `gpg-agent`/pinentry handles them in its own
dialog, exactly as on the command line. Encryption/decryption happens entirely inside `gpg`; Editora only
pipes text in and out.

## Build

`./build.sh` (point `EDITORA_HOME` at your Editora checkout) produces `gpg-tools.jar` + `gpg-tools.zip`.
Install via *Settings → Plugins → Install from file…*, or copy into `<configDir>/plugins/gpg-tools/`. The
plugin must be enabled (Settings → Plugins) and is loaded at startup. See
[`../../docs/plugins.md`](../../docs/plugins.md).
