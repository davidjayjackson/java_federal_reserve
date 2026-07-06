"""Generate test/fred_demo.ods using the installed FRED add-in.

Run against a headless LibreOffice (with FRED_API_KEY in its environment) that
is listening on a UNO socket; see docs/INSTALL.md. Produces a spreadsheet whose
cells contain live FRED formulas (scalars plus a real multi-cell array formula
for FRED_SERIES), with values already computed.
"""
import os
import sys
import time
import uno
from com.sun.star.beans import PropertyValue


def connect(port=2002, tries=60):
    local = uno.getComponentContext()
    resolver = local.ServiceManager.createInstanceWithContext(
        "com.sun.star.bridge.UnoUrlResolver", local)
    url = "uno:socket,host=localhost,port=%d;urp;StarOffice.ComponentContext" % port
    last = None
    for _ in range(tries):
        try:
            return resolver.resolve(url)
        except Exception as e:
            last = e
            time.sleep(0.5)
    raise SystemExit("could not connect to LibreOffice: %s" % last)


def main():
    out_path = os.path.abspath(
        os.path.join(os.path.dirname(__file__), "..", "test", "fred_demo.ods"))
    out_url = uno.systemPathToFileUrl(out_path)

    ctx = connect()
    smgr = ctx.ServiceManager
    desktop = smgr.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)
    doc = desktop.loadComponentFromURL("private:factory/scalc", "_blank", 0, ())
    try:
        sh = doc.Sheets.getByIndex(0)
        sh.Name = "FRED Demo"

        def put(col, row, value):
            sh.getCellByPosition(col, row).setString(value)

        def formula(col, row, f):
            sh.getCellByPosition(col, row).setFormula(f)

        put(0, 0, "FRED Calc Add-In - demo")
        put(0, 1, "Set FRED_API_KEY, launch LibreOffice, recalc with Ctrl+Shift+F9.")
        put(0, 2, 'Or pass the key per formula (last arg), e.g. =FRED_LATEST("UNRATE"; $B$1).')

        put(0, 3, "Function")
        put(1, 3, "Live result")
        put(2, 3, "Formula")

        rows = [
            ("FRED_DESCRIPTION", '=FRED_DESCRIPTION("GDP")'),
            ("FRED_META units", '=FRED_META("GDP";"units")'),
            ("FRED_META frequency", '=FRED_META("GDP";"frequency")'),
            ("FRED_META seasonal_adjustment", '=FRED_META("GDP";"seasonal_adjustment")'),
            ("FRED_META last_updated", '=FRED_META("GDP";"last_updated")'),
            ("FRED_LATEST", '=FRED_LATEST("UNRATE")'),
        ]
        r = 4
        for label, f in rows:
            put(0, r, label)
            formula(1, r, f)
            put(2, r, f)
            r += 1

        # FRED_SERIES as a real multi-cell array formula, with a header row.
        # GDP is quarterly; 2022-01-01..2023-12-31 = 8 rows, + header = 9 rows.
        r += 1
        put(0, r, "FRED_SERIES (array formula with headers, GDP quarterly 2022-2023)")
        r += 1
        first = r  # header row is the first row of the array
        rng = sh.getCellRangeByPosition(0, first, 1, first + 8)  # A..B, 9 rows
        series_f = '=FRED_SERIES("GDP";"2022-01-01";"2023-12-31";"";TRUE())'
        rng.setArrayFormula(series_f)
        put(2, first, "{" + series_f + "}  (select range, Ctrl+Shift+Enter)")

        # FRED_FIELDS as a real multi-cell array formula, with a header row.
        # It lists the metadata fields valid for FRED_META. GDP exposes 15
        # fields, + header = 16 rows.
        r = first + 9 + 1  # skip the 9-row FRED_SERIES block and one blank row
        put(0, r, "FRED_FIELDS (array formula with headers, GDP metadata fields)")
        r += 1
        ffirst = r
        frng = sh.getCellRangeByPosition(0, ffirst, 1, ffirst + 15)  # A..B, 16 rows
        fields_f = '=FRED_FIELDS("GDP";"";TRUE())'
        frng.setArrayFormula(fields_f)
        put(2, ffirst, "{" + fields_f + "}  (select range, Ctrl+Shift+Enter)")

        # Widen columns a little for readability.
        cols = sh.Columns
        for i in range(3):
            cols.getByIndex(i).Width = 6500

        doc.calculateAll()

        # Save as ODF spreadsheet (calc8 = .ods).
        fn = PropertyValue()
        fn.Name = "FilterName"
        fn.Value = "calc8"
        doc.storeToURL(out_url, (fn,))
        print("wrote", out_path)
    finally:
        doc.close(False)
        desktop.terminate()


if __name__ == "__main__":
    main()
