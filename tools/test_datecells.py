"""Regression test: FRED_SERIES accepts date-typed cell references.

A date-typed cell arrives at the add-in as a numeric serial, not a string;
the add-in must convert it to an ISO date. Mirrors a sheet like:

    B1: <api key>      B2: =DATE(2020,1,1)   B3: =DATE(2023,12,31)   B4: GDP
    A5: =FRED_SERIES(B4; B2; B3; B1)

Run against a headless LibreOffice (FRED_API_KEY may be unset; the key comes
from the cell). Pass the key as argv[1]. Prints RESULT: PASS / FAIL.
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
        raise SystemExit("usage: test_datecells.py <FRED_API_KEY>")
    key = sys.argv[1]

    ctx = connect()
    smgr = ctx.ServiceManager
    desktop = smgr.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)
    doc = desktop.loadComponentFromURL("private:factory/scalc", "_blank", 0, ())
    try:
        sh = doc.Sheets.getByIndex(0)
        sh.getCellByPosition(1, 0).setString(key)                 # B1 key
        sh.getCellByPosition(1, 1).setFormula("=DATE(2020;1;1)")  # B2 start (date)
        sh.getCellByPosition(1, 2).setFormula("=DATE(2023;12;31)")# B3 end (date)
        sh.getCellByPosition(1, 3).setString("GDP")               # B4 series
        rng = sh.getCellRangeByName("A5:B20")
        rng.setArrayFormula("=FRED_SERIES(B4;B2;B3;B1)")
        doc.calculateAll()
        a5_err = sh.getCellByPosition(0, 4).getError()
        first = rng.getDataArray()[0]
        b2_serial = sh.getCellByPosition(1, 1).getValue()
    finally:
        doc.close(False)
        desktop.terminate()

    print("B2 date serial     :", b2_serial, "(expect 43831 for 2020-01-01)")
    print("A5 error           :", a5_err, "(expect 0)")
    print("first result row   :", first)

    checks = {
        "no_error": a5_err == 0,
        "first_date_2020": first[0] == "2020-01-01",
        "value_numeric": isinstance(first[1], float) and first[1] > 0,
    }
    for name, ok in checks.items():
        print("CHECK %-16s %s" % (name, "PASS" if ok else "FAIL"))
    ok = all(checks.values())
    print("RESULT:", "PASS" if ok else "FAIL")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
