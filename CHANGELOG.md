# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.0.0] — Unreleased

Initial release of `opentmf-json-patch`, a lightweight, Jackson 3-native RFC 6902 JSON Patch library.

### Added
- **Fluent builder API** — construct RFC 6902 patch documents with type-safe methods for all six
  operations: `add`, `remove`, `replace`, `move`, `copy`, `test`. Accepts strings, numbers,
  booleans, `JsonNode` instances, and arbitrary POJOs (serialized via Jackson).
- **JSON parsing** — `fromJson(String)` and `fromJsonNode(JsonNode)` factory methods for parsing
  existing JSON Patch documents.
- **Patch application engine** — `apply(JsonNode)` applies the patch to a target document with
  full atomicity: the original document is deep-copied before mutation, and a failed operation
  aborts the entire patch.
- **RFC 6901 JSON Pointer resolution** — internal `JsonPointer` implementation supporting nested
  paths, array indices, the append token (`-`), and escape sequences (`~0` for `~`, `~1` for `/`).
- **`JsonPatchException`** — unchecked exception for clear error reporting when a patch operation
  cannot be applied (missing paths, value mismatches, invalid moves, etc.).
- **Builder path validation** — pointer syntax is validated at build time; paths must be empty
  (root) or start with `/`.
- **Immutable patches** — `build()` returns a snapshot; subsequent builder modifications do not
  affect previously built patches.
- **100% test coverage** — 128 tests covering all classes, lines, instructions, and branches,
  enforced by JaCoCo.
- **Release profile** — Maven source, Javadoc, GPG signing, and Central Publishing plugins
  configured for release to Maven Central.
