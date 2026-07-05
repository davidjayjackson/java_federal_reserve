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



