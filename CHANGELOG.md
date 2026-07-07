# Changelog

All notable changes to the FRED LibreOffice Calc add-in are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

[1.0.2]: https://github.com/davidjayjackson/java_federal_reserve/releases/tag/v1.0.2
[1.0.1]: https://github.com/davidjayjackson/java_federal_reserve/releases/tag/v1.0.1
[1.0.0]: https://github.com/davidjayjackson/java_federal_reserve/releases/tag/v1.0.0
