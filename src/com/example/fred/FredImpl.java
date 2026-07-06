package com.example.fred;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.sun.star.lang.IllegalArgumentException;
import com.sun.star.lang.Locale;
import com.sun.star.lang.XSingleComponentFactory;
import com.sun.star.lib.uno.helper.Factory;
import com.sun.star.lib.uno.helper.WeakBase;
import com.sun.star.registry.XRegistryKey;
import com.sun.star.uno.Any;
import com.sun.star.uno.Type;
import com.sun.star.uno.TypeClass;

/**
 * LibreOffice Calc add-in exposing FRED worksheet functions.
 *
 * <p>Implements the custom {@link XFred} interface plus the standard add-in
 * plumbing ({@code com.sun.star.sheet.XAddIn}, {@code XServiceName},
 * {@code XServiceInfo}). Function display names, descriptions and per-argument
 * help live in config/CalcAddIns.xcu; the {@code XAddIn} accessors below return
 * the programmatic names as a safe fallback.
 *
 * <p>Errors are surfaced as thrown {@link IllegalArgumentException}s, which Calc
 * renders as error values (e.g. #VALUE!) in the cell rather than as exception
 * strings.
 */
public final class FredImpl extends WeakBase
        implements XFred,
                   com.sun.star.sheet.XAddIn,
                   com.sun.star.lang.XServiceName,
                   com.sun.star.lang.XServiceInfo {

    /** Implementation name: must match the AddInInfo node in CalcAddIns.xcu. */
    private static final String IMPLEMENTATION_NAME = "com.example.fred.FredImpl";

    /** The one service that marks this component as a Calc add-in. */
    private static final String ADDIN_SERVICE = "com.sun.star.sheet.AddIn";

    private static final String[] SERVICE_NAMES = { ADDIN_SERVICE, IMPLEMENTATION_NAME };

    /** Current locale (tracked for XLocalizable; metadata is English-only here). */
    private Locale locale = new Locale("en", "US", "");

    // ------------------------------------------------------------------ //
    // XFred - the actual worksheet functions                             //
    // ------------------------------------------------------------------ //

    /** {@inheritDoc} */
    public Object[][] fredSeries(String seriesId, Object startDate, Object endDate,
                                 Object apiKey, Object headers)
            throws IllegalArgumentException {
        String id = requireId(seriesId);
        String start = optDate(startDate);
        String end = optDate(endDate);
        String key = optString(apiKey);
        boolean withHeader = truthy(headers);
        try {
            List<Object[]> rows = FredClient.observations(id, start, end, key);
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("No observations returned for " + id);
            }
            int offset = withHeader ? 1 : 0;
            Object[][] out = new Object[rows.size() + offset][2];
            if (withHeader) {
                out[0][0] = "Date";
                out[0][1] = "Value";
            }
            for (int r = 0; r < rows.size(); r++) {
                Object[] row = rows.get(r);
                out[r + offset][0] = row[0];              // ISO date string
                out[r + offset][1] = valueCell(row[1]);   // Double or empty (VOID)
            }
            return out;
        } catch (RuntimeException e) {
            throw asCalcError(e);
        }
    }

    /** {@inheritDoc} */
    public String fredDescription(String seriesId, Object apiKey) throws IllegalArgumentException {
        String id = requireId(seriesId);
        try {
            Object title = FredClient.seriesMeta(id, optString(apiKey)).get("title");
            if (title == null) {
                throw new IllegalArgumentException("Series " + id + " has no title");
            }
            return String.valueOf(title);
        } catch (RuntimeException e) {
            throw asCalcError(e);
        }
    }

    /** {@inheritDoc} */
    public String fredMeta(String seriesId, String field, Object apiKey) throws IllegalArgumentException {
        String id = requireId(seriesId);
        if (field == null || field.trim().isEmpty()) {
            throw new IllegalArgumentException("field is required");
        }
        try {
            Map<String, Object> meta = FredClient.seriesMeta(id, optString(apiKey));
            String key = field.trim().toLowerCase();
            if (!meta.containsKey(key)) {
                throw new IllegalArgumentException(
                        "Unknown metadata field '" + field + "' for " + id);
            }
            Object v = meta.get(key);
            return v == null ? "" : String.valueOf(v);
        } catch (RuntimeException e) {
            throw asCalcError(e);
        }
    }

    /** {@inheritDoc} */
    public double fredLatest(String seriesId, Object apiKey) throws IllegalArgumentException {
        String id = requireId(seriesId);
        try {
            List<Object[]> rows = FredClient.observations(id, null, null, optString(apiKey));
            // Walk backwards to the most recent non-missing observation.
            for (int r = rows.size() - 1; r >= 0; r--) {
                Object value = rows.get(r)[1];
                if (value instanceof Double) {
                    return (Double) value;
                }
            }
            throw new IllegalArgumentException("No numeric observation for " + id);
        } catch (RuntimeException e) {
            throw asCalcError(e);
        }
    }

    // ------------------------------------------------------------------ //
    // Argument / value helpers                                           //
    // ------------------------------------------------------------------ //

    private static String requireId(String seriesId) throws IllegalArgumentException {
        if (seriesId == null || seriesId.trim().isEmpty()) {
            throw new IllegalArgumentException("series_id is required");
        }
        return seriesId.trim();
    }

    /** The spreadsheet date epoch (LibreOffice/Excel default null date). */
    private static final LocalDate EPOCH = LocalDate.of(1899, 12, 30);

    /** Unwrap a 1x1 matrix (a single-cell reference may arrive as Object[][]). */
    private static Object scalar(Object arg) {
        if (arg instanceof Object[][]) {
            Object[][] m = (Object[][]) arg;
            return (m.length > 0 && m[0].length > 0) ? m[0][0] : null;
        }
        return arg;
    }

    /** Interpret an optional boolean-ish argument; VOID/empty/0/"" -> false. */
    private static boolean truthy(Object arg) {
        arg = scalar(arg);
        if (arg == null || arg instanceof Any) {
            return false;
        }
        if (arg instanceof Boolean) {
            return (Boolean) arg;
        }
        if (arg instanceof Number) {
            return ((Number) arg).doubleValue() != 0;
        }
        String s = String.valueOf(arg).trim().toLowerCase();
        return s.equals("1") || s.equals("true") || s.equals("yes")
                || s.equals("y") || s.equals("t");
    }

    /** Interpret an optional string argument (series/key); VOID/empty -> null. */
    private static String optString(Object arg) {
        arg = scalar(arg);
        if (arg == null || arg instanceof Any) {
            return null; // omitted argument arrives as VOID Any
        }
        String s = String.valueOf(arg).trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * Interpret an optional date argument as ISO YYYY-MM-DD. Accepts either an
     * ISO string (used as-is) or a spreadsheet date serial from a date-typed
     * cell (converted using the default 1899-12-30 epoch). VOID/empty -> null.
     */
    private static String optDate(Object arg) {
        arg = scalar(arg);
        if (arg == null || arg instanceof Any) {
            return null;
        }
        if (arg instanceof Number) {
            long days = (long) Math.floor(((Number) arg).doubleValue());
            return EPOCH.plusDays(days).toString(); // ISO-8601
        }
        String s = String.valueOf(arg).trim();
        return s.isEmpty() ? null : s;
    }

    /** Map a missing value (null) to a VOID Any so Calc shows an empty cell. */
    private static Object valueCell(Object value) {
        return value == null ? new Any(new Type(TypeClass.VOID), null) : value;
    }

    /** Normalize any thrown error into a Calc-facing IllegalArgumentException. */
    private static IllegalArgumentException asCalcError(RuntimeException e) {
        if (e instanceof IllegalArgumentException) {
            return (IllegalArgumentException) e;
        }
        return new IllegalArgumentException(e.getMessage());
    }

    // ------------------------------------------------------------------ //
    // XAddIn - function metadata                                         //
    //                                                                    //
    // Calc uses getDisplayFunctionName() as the AUTHORITATIVE display    //
    // (formula) name; CalcAddIns.xcu only supplies wizard help. So these //
    // must map programmatic <-> display names explicitly, or the cell    //
    // formula (=FRED_SERIES(...)) resolves to #NAME?.                    //
    // ------------------------------------------------------------------ //

    /** { programmatic, display } for every exposed function. */
    private static final String[][] FUNCS = {
        { "fredSeries",      "FRED_SERIES" },
        { "fredDescription", "FRED_DESCRIPTION" },
        { "fredMeta",        "FRED_META" },
        { "fredLatest",      "FRED_LATEST" },
    };

    /** Per-function one-line descriptions (function wizard). */
    private static String funcDescription(String prog) {
        if ("fredSeries".equals(prog)) {
            return "Returns a FRED series' observations as a (date, value) array.";
        }
        if ("fredDescription".equals(prog)) {
            return "Returns the title of a FRED series.";
        }
        if ("fredMeta".equals(prog)) {
            return "Returns a single metadata field for a FRED series.";
        }
        if ("fredLatest".equals(prog)) {
            return "Returns the most recent non-missing observation value.";
        }
        return "";
    }

    private static final String ARG_KEY = "api_key";
    private static final String ARG_KEY_DESC =
        "Optional. FRED API key; if omitted, the FRED_API_KEY environment variable is used.";

    /** Per-function argument display names, indexed by position. */
    private static String[] argNames(String prog) {
        if ("fredSeries".equals(prog)) return new String[] { "series_id", "start_date", "end_date", ARG_KEY, "headers" };
        if ("fredMeta".equals(prog))   return new String[] { "series_id", "field", ARG_KEY };
        if ("fredDescription".equals(prog) || "fredLatest".equals(prog)) return new String[] { "series_id", ARG_KEY };
        return new String[0];
    }

    /** Per-function argument descriptions, indexed by position. */
    private static String[] argDescriptions(String prog) {
        if ("fredSeries".equals(prog)) {
            return new String[] {
                "FRED series identifier, e.g. \"GDP\".",
                "Optional. Inclusive start date: ISO YYYY-MM-DD string or a date cell.",
                "Optional. Inclusive end date: ISO YYYY-MM-DD string or a date cell.",
                ARG_KEY_DESC,
                "Optional. 1/TRUE prepends a \"Date\",\"Value\" header row (default 0).",
            };
        }
        if ("fredMeta".equals(prog)) {
            return new String[] {
                "FRED series identifier, e.g. \"GDP\".",
                "Metadata field, e.g. units, frequency, seasonal_adjustment.",
                ARG_KEY_DESC,
            };
        }
        if ("fredDescription".equals(prog) || "fredLatest".equals(prog)) {
            return new String[] { "FRED series identifier, e.g. \"GDP\".", ARG_KEY_DESC };
        }
        return new String[0];
    }

    public String getProgrammaticFuntionName(String displayName) {
        for (String[] f : FUNCS) {
            if (f[1].equals(displayName)) return f[0];
        }
        return "";
    }

    public String getDisplayFunctionName(String programmaticName) {
        for (String[] f : FUNCS) {
            if (f[0].equals(programmaticName)) return f[1];
        }
        return "";
    }

    public String getFunctionDescription(String programmaticName) {
        return funcDescription(programmaticName);
    }

    public String getDisplayArgumentName(String programmaticName, int argument) {
        String[] a = argNames(programmaticName);
        return (argument >= 0 && argument < a.length) ? a[argument] : "";
    }

    public String getArgumentDescription(String programmaticName, int argument) {
        String[] a = argDescriptions(programmaticName);
        return (argument >= 0 && argument < a.length) ? a[argument] : "";
    }

    public String getProgrammaticCategoryName(String programmaticName) {
        return "Add-In";
    }

    public String getDisplayCategoryName(String programmaticName) {
        return "Add-In";
    }

    // ------------------------------------------------------------------ //
    // XLocalizable (inherited via XAddIn)                                //
    // ------------------------------------------------------------------ //

    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    public Locale getLocale() {
        return locale;
    }

    // ------------------------------------------------------------------ //
    // XServiceName / XServiceInfo                                        //
    // ------------------------------------------------------------------ //

    public String getServiceName() {
        return IMPLEMENTATION_NAME;
    }

    public String getImplementationName() {
        return IMPLEMENTATION_NAME;
    }

    public boolean supportsService(String service) {
        for (String s : SERVICE_NAMES) {
            if (s.equals(service)) return true;
        }
        return false;
    }

    public String[] getSupportedServiceNames() {
        return SERVICE_NAMES.clone();
    }

    // ------------------------------------------------------------------ //
    // UNO component registration entry points                           //
    // ------------------------------------------------------------------ //

    public static XSingleComponentFactory __getComponentFactory(String implName) {
        if (IMPLEMENTATION_NAME.equals(implName)) {
            return Factory.createComponentFactory(FredImpl.class, SERVICE_NAMES);
        }
        return null;
    }

    public static boolean __writeRegistryServiceInfo(XRegistryKey regKey) {
        return Factory.writeRegistryServiceInfo(IMPLEMENTATION_NAME, SERVICE_NAMES, regKey);
    }
}
