package com.example.fred;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin client over the FRED REST API.
 *
 * <ul>
 *   <li>Uses only the JDK: {@link java.net.HttpURLConnection} for I/O and the
 *       hand-rolled {@link Json} parser, so no third-party jars are bundled.
 *       (HttpURLConnection is used rather than java.net.http.HttpClient so the
 *       component runs on the Java 8 runtime LibreOffice accepts on this
 *       machine; both are JDK standard library.)</li>
 *   <li>Caches every raw JSON response by request URL in a process-wide map so
 *       a Calc recalculation (which may re-invoke every formula) does not hammer
 *       the API. The cache lives for the office session, i.e. as long as the
 *       component stays loaded.</li>
 *   <li>The API key is never hardcoded: it is read from the {@code FRED_API_KEY}
 *       environment variable, falling back to the {@code fred.api.key} Java
 *       system property. See docs/INSTALL.md.</li>
 * </ul>
 */
final class FredClient {

    private static final String BASE = "https://api.stlouisfed.org/fred/";

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;

    /** Shared per-session cache: request URL -> parsed JSON root. */
    private static final Map<String, Object> CACHE = new ConcurrentHashMap<String, Object>();

    private FredClient() {
    }

    /** Resolve the API key from the environment or a system property. */
    private static String apiKey() {
        String k = System.getenv("FRED_API_KEY");
        if (k == null || k.trim().isEmpty()) {
            k = System.getProperty("fred.api.key");
        }
        if (k == null || k.trim().isEmpty()) {
            throw new IllegalStateException(
                    "FRED API key not set. Define the FRED_API_KEY environment "
                    + "variable (or the fred.api.key system property).");
        }
        return k.trim();
    }

    private static String enc(String v) {
        try {
            return URLEncoder.encode(v, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 unavailable", e); // never happens
        }
    }

    /** Read an input stream fully as a UTF-8 string (Java 8 compatible). */
    private static String readAll(InputStream in) throws java.io.IOException {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toString("UTF-8");
    }

    /**
     * GET the given FRED endpoint with the supplied query params, returning the
     * parsed JSON root. Responses are cached by full URL for the session.
     *
     * @param endpoint e.g. "series" or "series/observations"
     * @param params   alternating key, value, key, value, ... (api_key and
     *                 file_type are added automatically)
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> get(String endpoint, String... params) {
        StringBuilder url = new StringBuilder(BASE).append(endpoint)
                .append("?file_type=json&api_key=").append(enc(apiKey()));
        for (int p = 0; p + 1 < params.length; p += 2) {
            String value = params[p + 1];
            if (value == null || value.isEmpty()) continue;
            url.append('&').append(params[p]).append('=').append(enc(value));
        }
        String key = url.toString();

        Object cached = CACHE.get(key);
        if (cached != null) {
            return (Map<String, Object>) cached;
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(key).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            int status = conn.getResponseCode();
            if (status != 200) {
                // Drain the error stream so the message can mention the reason.
                String body = readAll(conn.getErrorStream());
                throw new IllegalArgumentException(
                        "FRED returned HTTP " + status + " for " + endpoint
                        + (body.isEmpty() ? "" : ": " + body));
            }
            Object root = Json.parse(readAll(conn.getInputStream()));
            if (!(root instanceof Map)) {
                throw new IllegalArgumentException("Unexpected FRED response shape");
            }
            Map<String, Object> map = (Map<String, Object>) root;
            CACHE.put(key, map);
            return map;
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // Network failure, timeout, DNS, TLS, etc. -> caller maps to a Calc
            // error value.
            throw new IllegalArgumentException("FRED request failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Return the metadata map for a single series (the first "seriess" entry). */
    @SuppressWarnings("unchecked")
    static Map<String, Object> seriesMeta(String seriesId) {
        Map<String, Object> root = get("series", "series_id", seriesId);
        Object list = root.get("seriess");
        if (!(list instanceof List) || ((List<Object>) list).isEmpty()) {
            throw new IllegalArgumentException("No such series: " + seriesId);
        }
        Object first = ((List<Object>) list).get(0);
        if (!(first instanceof Map)) {
            throw new IllegalArgumentException("Malformed series metadata for " + seriesId);
        }
        return (Map<String, Object>) first;
    }

    /**
     * Return observations as rows of {ISO-date String, value}. A missing value
     * (the FRED "." sentinel) is returned as {@code null} so callers can render
     * an empty cell instead of a string.
     */
    @SuppressWarnings("unchecked")
    static List<Object[]> observations(String seriesId, String start, String end) {
        Map<String, Object> root = get("series/observations",
                "series_id", seriesId,
                "observation_start", start,
                "observation_end", end);
        Object obs = root.get("observations");
        if (!(obs instanceof List)) {
            throw new IllegalArgumentException("No observations for " + seriesId);
        }
        List<Object[]> rows = new ArrayList<Object[]>();
        for (Object o : (List<Object>) obs) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> row = (Map<String, Object>) o;
            String date = String.valueOf(row.get("date"));
            String raw = String.valueOf(row.get("value"));
            Double value;
            if (raw == null || raw.equals(".") || raw.isEmpty() || raw.equals("null")) {
                value = null; // missing sentinel
            } else {
                try {
                    value = Double.valueOf(raw);
                } catch (NumberFormatException nfe) {
                    value = null;
                }
            }
            rows.add(new Object[] { date, value });
        }
        return rows;
    }
}
