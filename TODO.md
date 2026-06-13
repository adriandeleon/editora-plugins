# Editora plugins — TODO / Roadmap

A backlog of planned improvements for the plugin registry. Unordered within each section.

## Recently shipped
- [x] CI/CD via GitHub Actions — `validate.yml` (index.json shape/consistency + Ed25519
      signature verification), `verify-assets.yml` (download each release zip and check its
      SHA-256), and `release.yml` (on a `<id>-v<version>` tag, build the plugin against
      Editora and publish `<id>.zip` as the release asset)
- [x] Signed registry — detached Ed25519 `index.json.sig` verified against the public key
      bundled in Editora; CI fails on a stale/invalid signature
- [x] 19 plugins published, each with full source under `plugins/<id>/` and a reproducible
      `build.sh`
- [x] Project docs — LICENSE (MIT), AUTHORS.md, CHANGELOG.md, README status badges

## Registry & tooling
- [ ] A JSON Schema for `index.json` (validate in CI against the schema, and ship it for
      editor autocompletion)
- [ ] CI strictness: fail (not just warn) when a `release.yml` tag's version doesn't match the
      `index.json` entry, and when a `plugins/<id>/` source dir isn't listed in the index
- [ ] Optional auto-update of `index.json` (version + sha256) at the end of `release.yml`,
      opened as a PR for the maintainer to review and re-sign
- [ ] Optional CI signing — re-sign `index.json` from a `release.yml` job using a private key
      stored as a GitHub secret (decision deferred; signing is manual today)
- [ ] Compile-check every plugin against Editora's API on push/PR (deferred originally for
      build cost; revisit with `~/.m2` caching)
- [ ] Dependabot / pinned action SHAs for the workflows

## Registry metadata & discovery
- [ ] Richer `index.json` entries — icon, tags/categories, screenshots, longer description
- [ ] A browsable registry page (GitHub Pages) generated from `index.json`
- [ ] Per-plugin CHANGELOG / release notes surfaced in the registry

## More plugins
- [ ] Solicit/curate community plugins with a contribution guide (`CONTRIBUTING.md`) and a PR
      checklist (source present, builds, signed index updated)
- [ ] More first-party examples covering remaining extension points
