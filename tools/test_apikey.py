"""Test the optional api_key argument.

Must be run against a headless LibreOffice whose environment does NOT set
FRED_API_KEY, so the only way a call can succeed is via the api_key argument.
Pass the key as argv[1]:

    "C:\\Program Files\\LibreOffice\\program\\python.exe" tools\\test_apikey.py <KEY>

Checks:
  1. No key anywhere      -> error value (Err:502).
  2. Key as literal arg   -> succeeds.
  3. Key via cell ref     -> succeeds.
Prints RESULT: PASS / FAIL.
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
        except Exception as e:
            last = e
            time.sleep(0.5)
    raise SystemExit("could not connect: %s" % last)


def main():
    if len(sys.argv) < 2:
        raise SystemExit("usage: test_apikey.py <FRED_API_KEY>")
    key = sys.argv[1]

    ctx = connect()
    smgr = ctx.ServiceManager
    desktop = smgr.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)
    doc = desktop.loadComponentFromURL("private:factory/scalc", "_blank", 0, ())
    try:
        sh = doc.Sheets.getByIndex(0)

        # Key parked in a cell (B1) for the cell-reference case.
        sh.getCellByPosition(1, 0).setString(key)

        no_key = sh.getCellByPosition(0, 0)      # A1
        no_key.setFormula('=FRED_DESCRIPTION("GDP")')

        lit_key = sh.getCellByPosition(0, 1)     # A2
        lit_key.setFormula('=FRED_DESCRIPTION("GDP";"%s")' % key)

        ref_key = sh.getCellByPosition(0, 2)     # A3  (references B1)
        ref_key.setFormula('=FRED_DESCRIPTION("GDP";$B$1)')

        # Also prove FRED_SERIES accepts the key in its 4th argument.
        rng = sh.getCellRangeByName("D1:E4")
        rng.setArrayFormula('=FRED_SERIES("GDP";"2023-01-01";"2023-12-31";$B$1)')

        doc.calculateAll()

        out = {
            "no_key_err": no_key.getError(),
            "lit": (lit_key.getError(), lit_key.getString()),
            "ref": (ref_key.getError(), ref_key.getString()),
            "series0": rng.getDataArray()[0],
        }
    finally:
        doc.close(False)
        desktop.terminate()

    print("no key (env unset)   : err=%d (expect nonzero)" % out["no_key_err"])
    print("literal key arg      : err=%d %r" % out["lit"])
    print("cell-ref key arg     : err=%d %r" % out["ref"])
    print("series w/ key arg[0] :", out["series0"])

    checks = {
        "no_key_is_error": out["no_key_err"] != 0,
        "literal_ok": out["lit"][0] == 0 and "Gross Domestic Product" in out["lit"][1],
        "cellref_ok": out["ref"][0] == 0 and "Gross Domestic Product" in out["ref"][1],
        "series_ok": out["series0"][0] == "2023-01-01" and isinstance(out["series0"][1], float),
    }
    print("---")
    for name, ok in checks.items():
        print("CHECK %-18s %s" % (name, "PASS" if ok else "FAIL"))
    ok = all(checks.values())
    print("RESULT:", "PASS" if ok else "FAIL")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
