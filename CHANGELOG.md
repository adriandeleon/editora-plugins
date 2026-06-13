# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

This changelog tracks the **registry** itself (the workflows, tooling, and docs in this
repository). Individual plugins are versioned by their own `<id>-v<version>` release tags
and `index.json` entries.

## [Unreleased]

### Added

- **CI/CD via GitHub Actions** under `.github/workflows/`:
  - `validate.yml` (every push/PR) — validates `index.json` for shape and consistency
    (required fields, HTTPS-only URLs, 64-hex-lowercase `sha256`, unique kebab-case ids, the
    `…/<id>-v<version>/<id>.zip` download scheme, and a matching `plugins/<id>/plugin.json`),
    and verifies `index.json.sig` is a valid Ed25519 signature of `index.json` under the
    bundled registry public key.
  - `verify-assets.yml` (on `index.json` change, weekly, or manual) — downloads each plugin's
    release `.zip` and confirms its SHA-256 matches `index.json`.
  - `release.yml` — on a `<id>-v<version>` tag, builds the plugin against Editora's exported
    API and publishes `<id>.zip` as the matching GitHub Release asset, printing its SHA-256.
- Supporting CI tooling: `validate_index.py`, a verify-only `VerifySig.java`, and a copy of
  the registry public key under `.github/keys/`.
- README status badges (Validate / Verify assets / live plugin count / Ed25519-signed) and a
  Continuous integration section documenting the workflows and release flow.
- Project docs: `LICENSE` (MIT), `AUTHORS.md`, this `CHANGELOG.md`, and `TODO.md`.

## 2026-06-13 — Initial registry

### Added

- Initial signed plugin registry: `index.json` (schema 1) listing **19 plugins**, each with
  full source under `plugins/<id>/` and a `build.sh` that compiles against Editora's exported
  API and emits the distributable `<id>.zip` (+ its SHA-256).
- Ed25519 signing: a detached `index.json.sig` verified against the public key bundled in
  Editora, blocking installs from an unsigned/unverified registry when *Require signed plugins*
  is on.
- Plugins published: example, lorem-ipsum, text-tools, insert-tools, scratchpad, format-runner,
  encode-tools, hash-tools, markdown-toc, regex-tester, json-tools, slug-tools, box-banner,
  color-picker, word-count, calculator, open-on-github, reveal, run-task.
