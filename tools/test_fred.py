"""End-to-end test for the FRED Calc add-in.

Run with LibreOffice's bundled Python (it ships the `uno` module) against a
headless instance that has FRED_API_KEY in its environment and is listening on
a UNO socket:

    set FRED_API_KEY=...   (must be visible to the soffice process)
    soffice --headless --norestore --accept="socket,host=localhost,port=2002;urp;"
    "C:\\Program Files\\LibreOffice\\program\\python.exe" tools\\test_fred.py

Exercises FRED_DESCRIPTION, FRED_META, FRED_LATEST and FRED_SERIES against live
data. Prints RESULT: PASS / FAIL and exits non-zero on failure.
"""
import sys
import time
import uno


def connect(port=2002, tries=60):
    local = uno.getComponentContext()
    resolver = local.ServiceManager.createInstanceWithContext(
        "com.sun.star.bridge.UnoUrlResolver", local)
    url = "uno:socket,host=localhost,port=%d;urp;StarOffice.ComponentContext" % port
    last = None
    for _ in range(tries):
        try:
            return resolver.resolve(url)
        except Exception as e:  # not yet listening
            last = e
            time.sleep(0.5)
    raise SystemExit("could not connect to LibreOffice: %s" % last)


def main():
    ctx = connect()
    smgr = ctx.ServiceManager
    desktop = smgr.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)
    doc = desktop.loadComponentFromURL("private:factory/scalc", "_blank", 0, ())
    results = {}
    try:
        sheet = doc.Sheets.getByIndex(0)

        # --- scalar functions ---------------------------------------------
        c_desc = sheet.getCellByPosition(0, 0)   # A1
        c_desc.setFormula('=FRED_DESCRIPTION("GDP")')

        c_units = sheet.getCellByPosition(0, 1)  # A2
        c_units.setFormula('=FRED_META("GDP";"units")')

        c_freq = sheet.getCellByPosition(0, 2)   # A3
        c_freq.setFormula('=FRED_META("GDP";"frequency")')

        c_latest = sheet.getCellByPosition(0, 3)  # A4
        c_latest.setFormula('=FRED_LATEST("UNRATE")')

        # --- spillable array: GDP quarterly for 2023 -> 4 rows x 2 cols ----
        rng = sheet.getCellRangeByName("D1:E4")
        rng.setArrayFormula('=FRED_SERIES("GDP";"2023-01-01";"2023-12-31")')

        doc.calculateAll()

        results["desc"] = (c_desc.getString(), c_desc.getError())
        results["units"] = (c_units.getString(), c_units.getError())
        results["freq"] = (c_freq.getString(), c_freq.getError())
        results["latest"] = (c_latest.getValue(), c_latest.getError())
        results["series"] = (rng.getDataArray(), )
    finally:
        doc.close(False)
        desktop.terminate()

    (desc, desc_err) = results["desc"]
    (units, units_err) = results["units"]
    (freq, freq_err) = results["freq"]
    (latest, latest_err) = results["latest"]
    series = results["series"][0]

    print("FRED_DESCRIPTION('GDP') :", repr(desc), "err=", desc_err)
    print("FRED_META('GDP','units'):", repr(units), "err=", units_err)
    print("FRED_META('GDP','freq') :", repr(freq), "err=", freq_err)
    print("FRED_LATEST('UNRATE')   :", latest, "err=", latest_err)
    print("FRED_SERIES(GDP 2023)   :")
    for row in series:
        print("   ", row)

    checks = {
        "desc_no_error": desc_err == 0,
        "desc_is_gdp": "Gross Domestic Product" in desc,
        "units_no_error": units_err == 0 and len(units) > 0,
        "freq_quarterly": "Quarter" in freq,
        "latest_no_error": latest_err == 0 and latest > 0,
        "series_4_rows": len(series) == 4,
        "series_first_date": series and series[0][0] == "2023-01-01",
        "series_values_numeric": all(
            isinstance(r[1], float) and r[1] > 0 for r in series),
    }
    print("---")
    for name, ok in checks.items():
        print("CHECK %-22s %s" % (name, "PASS" if ok else "FAIL"))

    ok = all(checks.values())
    print("RESULT:", "PASS" if ok else "FAIL")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
