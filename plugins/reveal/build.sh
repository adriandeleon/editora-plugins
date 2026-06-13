#!/usr/bin/env bash
# Builds <id>.jar + the distributable <id>.zip (+ its sha-256) for a plugin in this registry repo.
# Compiles against Editora's exported API on a plain classpath (plugins load via a child URLClassLoader).
#
# Point EDITORA_HOME at your Editora checkout (the repo containing pom.xml), or keep one beside this repo:
#   EDITORA_HOME=/path/to/Editora-V2 ./build.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ID="$(grep -o '"id"[^,}]*' "$HERE/plugin.json" | head -1 | sed -E 's/.*"id"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/')"

ROOT="${EDITORA_HOME:-}"
if [ -z "$ROOT" ]; then
  for c in "$HERE/../../../Editora-V2" "$HOME/src/adl/Editora-V2" "$HOME/Editora-V2"; do
    [ -f "$c/pom.xml" ] && ROOT="$c" && break
  done
fi
if [ -z "$ROOT" ] || [ ! -f "$ROOT/pom.xml" ]; then
  echo "Set EDITORA_HOME to your Editora checkout (the dir with pom.xml). Example:" >&2
  echo "  EDITORA_HOME=/path/to/Editora-V2 ./build.sh" >&2
  exit 1
fi

echo "==> Building plugin '$ID' against Editora at $ROOT"
( cd "$ROOT" && ./mvnw -q -o compile )

CP_FILE="$HERE/.classpath"
( cd "$ROOT" && ./mvnw -q -o dependency:build-classpath -Dmdep.outputFile="$CP_FILE" >/dev/null )
CP="$ROOT/target/classes:$(cat "$CP_FILE")"
rm -f "$CP_FILE"

OUT="$HERE/.build"; rm -rf "$OUT"; mkdir -p "$OUT"
find "$HERE/src" -name '*.java' > "$OUT/sources.txt"
javac --release 25 -cp "$CP" -d "$OUT" @"$OUT/sources.txt"
( cd "$OUT" && jar cf "$HERE/$ID.jar" com )
rm -rf "$OUT"

# The zip's top level is the plugin folder contents, so unzipping yields what lives under plugins/<id>/.
STAGE="$HERE/.zipstage"; rm -rf "$STAGE"; mkdir -p "$STAGE"
cp "$HERE/plugin.json" "$HERE/$ID.jar" "$STAGE/"
[ -d "$HERE/snippets" ] && cp -r "$HERE/snippets" "$STAGE/"
[ -d "$HERE/templates" ] && cp -r "$HERE/templates" "$STAGE/"
rm -f "$HERE/$ID.zip"
( cd "$STAGE" && zip -qr "$HERE/$ID.zip" . )
rm -rf "$STAGE"

if command -v shasum >/dev/null 2>&1; then SHA="$(shasum -a 256 "$HERE/$ID.zip" | cut -d' ' -f1)"
elif command -v sha256sum >/dev/null 2>&1; then SHA="$(sha256sum "$HERE/$ID.zip" | cut -d' ' -f1)"
else SHA="(install shasum/sha256sum)"; fi

echo
echo "Built $HERE/$ID.zip"
echo "sha-256($ID.zip) = $SHA"
echo "Upload $ID.zip as the $ID-v<version> release asset and set this sha in ../../index.json."
