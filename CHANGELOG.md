# Changelog

All notable changes to the FRED LibreOffice Calc add-in are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.6] - 2026-07-07

### Changed
- Deleted the `v1.0.0`–`v1.0.2` GitHub Release entries (tags left intact).
  Repointed this file's `v1.0.0`–`v1.0.2` links at the tagged repo tree
  instead of the now-nonexistent release pages.
- Bumped the `.oxt` extension package version to `1.0.6`.

No functional changes to the add-in since 1.0.0.

## [1.0.5] - 2026-07-07

### Changed
- Regenerated `test/fred_demo.ods` against the published `v1.0.4` release
  asset (installed via `unopkg`) to confirm it still works end-to-end.
- Bumped the `.oxt` extension package version to `1.0.5`.

No functional changes to the add-in since 1.0.0.

## [1.0.4] - 2026-07-07

### Added
- `docs/INSTALL.md` now documents building and installing on Linux/macOS
  alongside Windows: acquiring a JDK 8 and LibreOffice + SDK without root,
  the Temurin `javavendors.xml` allow-list fix needed for `unopkg` to load
  the Java bridge, `build.sh` usage (and its manual by-hand equivalent),
  `unopkg` install, and the headless test workflow.

### Changed
- Bumped the `.oxt` extension package version to `1.0.4`.
- Regenerated `test/fred_demo.ods` against a live rebuild.

No functional changes to the add-in since 1.0.0.

## [1.0.3] - 2026-07-07

### Added
- `build.sh`, a POSIX shell port of `build.ps1` for building `FRED.oxt` on
  Linux/macOS (resolves LibreOffice/JDK from `--libreoffice`/`--jdk` flags or
  `LO_HOME`/`JAVA_HOME`).

### Fixed
- Release links in the README and CHANGELOG pointed at the old repo name
  `java_federal_reserve-` (trailing hyphen); they now point directly at
  `java_federal_reserve`.

### Changed
- Bumped the `.oxt` extension package version to `1.0.3`.

No functional changes to the add-in since 1.0.0.

## [1.0.2] - 2026-07-05

### Added
- `CHANGELOG.md` documenting the release history, linked from the README.

### Changed
- Bumped the `.oxt` extension package version to `1.0.2`.

No functional changes to the add-in since 1.0.0.

## [1.0.1] - 2026-07-05

### Added
- README **Install (prebuilt)** section linking the GitHub release download
  (`unopkg add FRED.oxt`), so the add-in can be installed without building from
  source.

### Changed
- Bumped the `.oxt` extension package version to `1.0.1`.

No functional changes to the add-in since 1.0.0.

## [1.0.0] - 2026-07-05

First release. A Java UNO add-in (`com.sun.star.sheet.AddIn`) exposing Federal
Reserve Economic Data (FRED) as LibreOffice Calc worksheet functions, packaged
as `FRED.oxt`.

### Added
- Worksheet functions:
  - `FRED_SERIES(id; [start]; [end]; [api_key]; [headers])` — observations as a
    spillable `(date, value)` array; accepts ISO strings or date-typed cells;
    optional `headers` prepends a `Date`/`Value` row.
  - `FRED_DESCRIPTION(id; [api_key])` — series title.
  - `FRED_META(id; field; [api_key])` — a single metadata field.
  - `FRED_FIELDS(id; [api_key]; [headers])` — lists a series' metadata fields
    (the valid `FRED_META` field names) as a `(field, value)` array.
  - `FRED_LATEST(id; [api_key])` — most recent non-missing observation.
- Optional trailing `api_key` argument on every function; falls back to the
  `FRED_API_KEY` environment variable (or `fred.api.key` system property) when
  omitted.
- Per-session response cache to avoid re-hitting the API on recalc.
- Build pipeline (`build.ps1`: `unoidl-write` → `javamaker` → `javac --release 8`
  → `jar` → `.oxt`) and headless test suite (`tools/test_*.py`).
- Demo spreadsheet (`test/fred_demo.ods`) showcasing every function.
- MIT license.

### Notes
- Pure JDK implementation: `HttpURLConnection` + a hand-rolled JSON parser, no
  third-party jars. Compiled to Java 8 bytecode so it runs on the Oracle JRE 8
  that LibreOffice accepts by default.
- Errors surface as Calc error values (`Err:502`), not exception strings;
  missing values (the FRED `.` sentinel) become empty cells.

[1.0.6]: https://github.com/davidjayjackson/java_federal_reserve/releases/tag/v1.0.6
[1.0.5]: https://github.com/davidjayjackson/java_federal_reserve/releases/tag/v1.0.5
[1.0.4]: https://github.com/davidjayjackson/java_federal_reserve/releases/tag/v1.0.4
[1.0.3]: https://github.com/davidjayjackson/java_federal_reserve/releases/tag/v1.0.3
[1.0.2]: https://github.com/davidjayjackson/java_federal_reserve/tree/v1.0.2
[1.0.1]: https://github.com/davidjayjackson/java_federal_reserve/tree/v1.0.1
[1.0.0]: https://github.com/davidjayjackson/java_federal_reserve/tree/v1.0.0

<!-- GitHub Release entries for v1.0.0-v1.0.2 were removed; the tags above
     still exist, so those links point at the tagged tree instead. -->
