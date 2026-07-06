"""Test FRED_SERIES' optional headers argument.

With headers TRUE the array's first row is ("Date","Value"); omitted, the array
is data-only (backward compatible). Run against a headless LibreOffice; pass the
key as argv[1]. Prints RESULT: PASS / FAIL.
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
        raise SystemExit("usage: test_headers.py <FRED_API_KEY>")
    key = sys.argv[1]

    ctx = connect()
    smgr = ctx.ServiceManager
    desktop = smgr.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)
    doc = desktop.loadComponentFromURL("private:factory/scalc", "_blank", 0, ())
    try:
        sh = doc.Sheets.getByIndex(0)
        # headers TRUE -> 1 header row + 4 data rows
        with_h = sh.getCellRangeByName("A1:B5")
        with_h.setArrayFormula(
            '=FRED_SERIES("GDP";"2023-01-01";"2023-12-31";"%s";TRUE())' % key)
        # headers omitted -> 4 data rows (backward compatible)
        without_h = sh.getCellRangeByName("D1:E4")
        without_h.setArrayFormula(
            '=FRED_SERIES("GDP";"2023-01-01";"2023-12-31";"%s")' % key)
        doc.calculateAll()
        wh = with_h.getDataArray()
        nh = without_h.getDataArray()
    finally:
        doc.close(False)
        desktop.terminate()

    print("with headers row0 :", wh[0])
    print("with headers row1 :", wh[1])
    print("no headers   row0 :", nh[0])

    checks = {
        "header_row": wh[0] == ("Date", "Value"),
        "data_after_header": wh[1][0] == "2023-01-01" and isinstance(wh[1][1], float),
        "backcompat_no_header": nh[0][0] == "2023-01-01",
    }
    for name, ok in checks.items():
        print("CHECK %-22s %s" % (name, "PASS" if ok else "FAIL"))
    ok = all(checks.values())
    print("RESULT:", "PASS" if ok else "FAIL")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
