# calc_federal_reserve
Task: Build a LibreOffice Calc Add-In (UNO component) in Java that exposes custom worksheet functions for pulling FRED economic data and series metadata directly into cells.
Environment & constraints:

Target: LibreOffice Calc on Windows, LibreOffice SDK installed.
Language: Java, using com.sun.star.sheet.AddIn so functions are callable as =FRED_SERIES(...) etc.
Package as a deployable .oxt extension. Prefer only JDK standard library (java.net.http.HttpClient, javax.json or minimal hand-rolled JSON parsing) so no third-party jars are bundled unless justified. Flag any dependency you add and why.
FRED formula args in Calc use semicolons as separators (e.g. =FRED_SERIES("GDP"; "2020-01-01"; "2024-01-01")).

FRED API:

Base: https://api.stlouisfed.org/fred/
Requires a free api_key query param and file_type=json.
Key endpoints: series/observations (data), series (metadata/description).
API key handling: do not hardcode. Read from an environment variable or a config cell/named range; document the chosen approach.

Functions to implement (minimum):

FRED_SERIES(series_id; [start_date]; [end_date]) → returns observations as a spillable 2-column array (date, value). Handle . (missing value) sentinels; return numeric values, not strings.
FRED_DESCRIPTION(series_id) → returns the series title.
FRED_META(series_id; field) → returns a single metadata field (units, frequency, seasonal_adjustment, last_updated, notes).
FRED_LATEST(series_id) → most recent observation value.

Requirements:

Provide the full Maven/Ant build setup and the required IDL/type definitions, manifest.xml, description.xml, and add-in registration (ProtocolHandler/CalcAddIns.xcu or the appropriate .xcu for function metadata: display names, descriptions, argument help).
Return errors as proper Calc error values (e.g. #VALUE! / #N/A) rather than exception strings; degrade gracefully on network failure or bad series IDs.
Cache responses per session to avoid hammering the API on recalc.
Include: the exact idlc/javamaker/jar/packaging commands, a step-by-step compile-and-install sequence, and a minimal test spreadsheet demonstrating each function.

Workflow: Scaffold the complete project tree first, then implement file by file. After each build-blocking issue I report, iterate.

---

## Implementation

A Java UNO add-in (`com.sun.star.sheet.AddIn`), packaged as `build/FRED.oxt`.
Built, installed, and **verified end-to-end** against live FRED data on
LibreOffice 26.2 (Windows).

### Layout

| Path | Purpose |
|------|---------|
| `idl/com/example/fred/XFred.idl` | Custom UNO interface (the 4 functions) |
| `src/com/example/fred/FredImpl.java` | The add-in: `XFred` + `XAddIn` + `XServiceName`/`XServiceInfo`, display↔programmatic name mapping, UNO registration |
| `src/com/example/fred/FredClient.java` | `HttpURLConnection` client + per-session response cache; API key from `FRED_API_KEY` |
| `src/com/example/fred/Json.java` | Hand-rolled JSON parser (no third-party jars) |
| `registration/CalcAddIns.xcu` | Function display names, descriptions, argument help |
| `registration/{manifest,description}.xml`, `MANIFEST.MF` | `.oxt` manifest, extension metadata, jar `RegistrationClassName` |
| `build.ps1` | `unoidl-write` → `javamaker` → `javac` (--release 8) → `jar` → zip `.oxt` |
| `tools/test_fred.py` | Headless end-to-end test (all 4 functions + error paths) |
| `tools/build_demo.py` | Regenerates the demo spreadsheet |
| `test/fred_demo.ods` | Demo spreadsheet with live formulas |
| `docs/INSTALL.md` | Full build / install / run instructions |

### Quick start

```powershell
$env:FRED_API_KEY = 'your_key'                                   # never hardcoded
pwsh -File build.ps1 -Jdk 'C:\Program Files\Android\Android Studio\jbr'
& 'C:\Program Files\LibreOffice\program\unopkg.exe' add --force build\FRED.oxt
# then launch LibreOffice from an environment where FRED_API_KEY is set
```

See `docs/INSTALL.md` for details. Key implementation notes:

- **Runtime**: compiled to Java 8 bytecode using `java.net.HttpURLConnection`
  (both JDK standard library), so it runs on the Oracle JRE 8 LibreOffice
  accepts by default — no runtime JRE reconfiguration. (`java.net.http.HttpClient`
  would need Java 11+, but LibreOffice's vendor allow-list here rejects the only
  11+ JRE on the machine.)
- **Errors** (bad series, unknown field, missing key, network failure) surface as
  Calc error values (`Err:502`), not exception strings.
- **Missing values** (FRED `.` sentinel) become empty cells; `FRED_LATEST` skips
  them. Responses are cached per session so recalculation does not re-hit the API.

### Verified results

```
=FRED_DESCRIPTION("GDP")                         -> Gross Domestic Product
=FRED_META("GDP"; "units")                       -> Billions of Dollars
=FRED_META("GDP"; "frequency")                   -> Quarterly
=FRED_LATEST("UNRATE")                           -> 4.2
=FRED_SERIES("GDP"; "2023-01-01"; "2023-12-31")  -> 4 rows (date, value)
```



