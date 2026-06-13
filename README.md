# Editora plugins

A plugin registry for the [Editora](https://github.com/adriandeleon) text editor. The `index.json` at the
repo root lists installable plugins; Editora fetches it from
`https://raw.githubusercontent.com/adriandeleon/editora-plugins/main/index.json` for
**Settings → Plugins → Browse plugins…**.

> ⚠️ Plugins run with **full trust** (no sandbox), like VS Code / IntelliJ extensions. Only list and
> install plugins you trust.

## How a user installs from here

1. In Editora, enable **Settings → Plugins → Enable plugins**.
2. Click **Browse plugins…**, pick a plugin, confirm. Editora downloads its `.zip`, **verifies the
   SHA-256**, and unpacks it; restart to load it.

The registry URL is configurable in *Settings → Plugins → Registry URL* (this repo is the baked-in
default), so anyone can point Editora at their own fork.

## Repository layout

```
index.json          # the registry Editora fetches
plugins/<id>/        # the SOURCE for each published plugin
  ├── plugin.json    # manifest
  ├── src/…          # Java sources (compiled against Editora's exported API)
  ├── snippets/…     # declarative assets (optional)
  ├── build.sh       # builds <id>.jar + <id>.zip (+ sha-256)
  └── README.md
```

Every plugin listed in `index.json` has its full source here under `plugins/<id>/`, so each published
release asset is reproducible and auditable.

## Building a plugin

The plugins compile against Editora's exported API on a plain classpath (they load via a child
`URLClassLoader`). Point `EDITORA_HOME` at your Editora checkout:

```sh
cd plugins/<id>
EDITORA_HOME=/path/to/Editora-V2 ./build.sh    # → <id>.jar, <id>.zip, and its sha-256
```

(The script also auto-detects an `Editora-V2` checkout beside this repo or under `~/src/adl`.)

## Adding a plugin to the registry

A plugin is distributed as a **`.zip`** whose top level is the plugin folder contents (`plugin.json` + the
jar + optional `snippets/`/`templates/` dirs) — unzipping it yields exactly what lives under
`<configDir>/plugins/<id>/`.

1. Build the `.zip` and compute its SHA-256 (e.g. Editora's `examples/example-plugin/build.sh` prints both).
2. Attach the `.zip` to a **GitHub Release** in this (or your) repo.
3. Add an entry to `index.json`:

```json
{
  "id": "your-plugin",
  "name": "Your Plugin",
  "version": "1.0.0",
  "description": "What it does.",
  "author": "you",
  "homepage": "https://github.com/you/your-plugin",
  "download": "https://github.com/you/your-plugin/releases/download/v1.0.0/your-plugin.zip",
  "sha256": "<lowercase-hex sha-256 of the zip>",
  "minEditoraVersion": "1.0.0"
}
```

`download` and the registry URL must be **HTTPS**; the `sha256` is mandatory (a mismatch aborts the
install). Entries needing a newer Editora than the user's are listed but not installable
(`minEditoraVersion`). The zip stores file timestamps, so its hash changes on each rebuild — always use the
hash of the exact asset you upload.

## Signing the index

Editora verifies a detached **Ed25519** signature of `index.json` (`index.json.sig`) against a public key
bundled in the app, and blocks installs from an unsigned/unverified registry when *Require signed plugins*
(default on) is set. **Re-sign whenever you edit `index.json`:**

```sh
# in Editora's checkout (one keypair, kept secret):
java scripts/PluginSigningTool.java sign <registry-private-key> index.json   # writes index.json.sig
```

Commit `index.json.sig` next to `index.json`. (The matching public key ships inside Editora; a fork with
its own key bundled can run its own signed registry.)

See the Editora plugin guide for the full plugin API and manifest format.
