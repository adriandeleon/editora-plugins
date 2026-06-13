#!/usr/bin/env python3
"""Validate the Editora plugin-registry index.json.

Checks the registry is internally consistent and matches the on-disk plugin
sources, without touching the network. Exits non-zero (and prints every problem
it found) if anything is wrong.

Run from the repo root:  python3 .github/scripts/validate_index.py
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
INDEX = REPO_ROOT / "index.json"
PLUGINS_DIR = REPO_ROOT / "plugins"

REQUIRED_FIELDS = [
    "id",
    "name",
    "version",
    "description",
    "author",
    "homepage",
    "download",
    "sha256",
    "minEditoraVersion",
]

ID_RE = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
SEMVER_RE = re.compile(r"^\d+\.\d+\.\d+$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")

errors: list[str] = []


def err(msg: str) -> None:
    errors.append(msg)


def main() -> int:
    if not INDEX.is_file():
        print(f"::error::index.json not found at {INDEX}")
        return 1

    try:
        text = INDEX.read_text(encoding="utf-8")
        data = json.loads(text)
    except json.JSONDecodeError as e:
        print(f"::error file=index.json::index.json is not valid JSON: {e}")
        return 1

    if not isinstance(data, dict):
        err("top-level value must be a JSON object")
        return report()

    if data.get("schemaVersion") != 1:
        err(f"schemaVersion must be 1 (got {data.get('schemaVersion')!r})")

    plugins = data.get("plugins")
    if not isinstance(plugins, list) or not plugins:
        err("'plugins' must be a non-empty array")
        return report()

    seen_ids: dict[str, int] = {}

    for i, p in enumerate(plugins):
        where = f"plugins[{i}]"
        if not isinstance(p, dict):
            err(f"{where}: must be an object")
            continue

        pid = p.get("id", "<missing>")
        where = f"plugins[{i}] (id={pid})"

        for field in REQUIRED_FIELDS:
            if field not in p:
                err(f"{where}: missing required field '{field}'")
            elif not isinstance(p[field], str) or not p[field].strip():
                err(f"{where}: field '{field}' must be a non-empty string")

        if isinstance(pid, str):
            if not ID_RE.match(pid):
                err(f"{where}: id must be lowercase kebab-case [a-z0-9-]")
            if pid in seen_ids:
                err(f"{where}: duplicate id (also at plugins[{seen_ids[pid]}])")
            else:
                seen_ids[pid] = i

        version = p.get("version")
        if isinstance(version, str) and not SEMVER_RE.match(version):
            err(f"{where}: version must be X.Y.Z (got {version!r})")

        mev = p.get("minEditoraVersion")
        if isinstance(mev, str) and not SEMVER_RE.match(mev):
            err(f"{where}: minEditoraVersion must be X.Y.Z (got {mev!r})")

        sha = p.get("sha256")
        if isinstance(sha, str) and not SHA256_RE.match(sha):
            err(f"{where}: sha256 must be 64 lowercase hex chars")

        for url_field in ("download", "homepage"):
            url = p.get(url_field)
            if isinstance(url, str) and not url.startswith("https://"):
                err(f"{where}: {url_field} must be an https:// URL (got {url!r})")

        download = p.get("download")
        if isinstance(pid, str) and isinstance(version, str) and isinstance(download, str):
            expected_tail = f"/{pid}-v{version}/{pid}.zip"
            if not download.endswith(expected_tail):
                err(
                    f"{where}: download URL should end with '{expected_tail}' "
                    f"to match the release-asset scheme (got '{download}')"
                )

        # Every listed plugin must have its source under plugins/<id>/.
        if isinstance(pid, str) and ID_RE.match(pid):
            src = PLUGINS_DIR / pid
            manifest = src / "plugin.json"
            if not src.is_dir():
                err(f"{where}: no source directory plugins/{pid}/")
            elif not manifest.is_file():
                err(f"{where}: missing plugins/{pid}/plugin.json")
            else:
                try:
                    m = json.loads(manifest.read_text(encoding="utf-8"))
                    if m.get("id") != pid:
                        err(
                            f"{where}: plugins/{pid}/plugin.json id is "
                            f"{m.get('id')!r}, expected {pid!r}"
                        )
                except json.JSONDecodeError as e:
                    err(f"{where}: plugins/{pid}/plugin.json is invalid JSON: {e}")

    # Reverse check: source dirs that aren't listed in the index (warn only).
    if PLUGINS_DIR.is_dir():
        for d in sorted(PLUGINS_DIR.iterdir()):
            if d.is_dir() and d.name not in seen_ids:
                print(
                    f"::warning::plugins/{d.name}/ has source but is not listed "
                    f"in index.json"
                )

    return report()


def report() -> int:
    if errors:
        for e in errors:
            print(f"::error file=index.json::{e}")
        print(f"\nindex.json validation FAILED with {len(errors)} problem(s).")
        return 1
    print("index.json validation passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
