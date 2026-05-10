package com.example.gutapp.data.drawing;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * DrawingPersistence — serializes / deserializes all USER drawings for a ticker.
 *
 * Storage: SharedPreferences file "chart_drawings", key = "drawings_<SYMBOL>"
 * Format: JSON array of drawing objects.
 *
 * Auto-save is called from:
 *   - DrawingChart.onDrawingCreated / onDrawingRemoved listeners
 *   - ChartActivity.onPause()
 *
 * Auto-load is called from ChartActivity.onCreate() after chart is ready.
 */
public class DrawingPersistence {

    private static final String TAG = "DrawingPersistence";
    private static final String PREFS_FILE = "chart_drawings";
    private static final String KEY_PREFIX = "drawings_";

    // JSON field names
    private static final String F_TYPE         = "type";
    private static final String F_COLOR        = "color";
    private static final String F_WIDTH        = "width";
    private static final String F_DASHED       = "dashed";
    private static final String F_FILLED       = "filled";
    private static final String F_FILL_COLOR   = "fillColor";
    private static final String F_PRICE        = "price";
    private static final String F_LABEL        = "label";
    private static final String F_START_IDX    = "startIdx";
    private static final String F_START_PRICE  = "startPrice";
    private static final String F_END_IDX      = "endIdx";
    private static final String F_END_PRICE    = "endPrice";
    private static final String F_ANCHOR_IDX   = "anchorIdx";
    private static final String F_ANCHOR_PRICE = "anchorPrice";
    private static final String F_CANDLE_IDX   = "candleIdx";
    private static final String F_DRAW_CHANNEL = "drawChannel";
    private static final String F_LEVELS       = "levels";
    private static final String F_PRICE_HIGH   = "priceHigh";
    private static final String F_PRICE_LOW    = "priceLow";
    // Extended fields
    private static final String F_TEXT         = "text";
    private static final String F_ANGLE        = "angle";
    private static final String F_EXTEND_LEFT  = "extendLeft";
    private static final String F_EXTEND_RIGHT = "extendRight";

    private final SharedPreferences prefs;

    public DrawingPersistence(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    }

    // ── Public API ────────────────────────────────────────────────────

    /** Save all USER drawings from the manager for the given ticker symbol. */
    public void save(String symbol, DrawingManager manager) {
        try {
            JSONArray arr = new JSONArray();
            for (ChartDrawing d : manager.getAll()) {
                if (d.source != ChartDrawing.Source.USER) continue;
                JSONObject obj = serialize(d);
                if (obj != null) arr.put(obj);
            }
            prefs.edit().putString(KEY_PREFIX + symbol.toUpperCase(), arr.toString()).apply();
            Log.d(TAG, "Saved " + arr.length() + " drawings for " + symbol);
        } catch (Exception e) {
            Log.e(TAG, "Save failed for " + symbol, e);
        }
    }

    /**
     * Load all USER drawings for this ticker into the manager.
     * Call after the chart has loaded candle data so coordinate mapping works.
     */
    public void load(String symbol, DrawingManager manager) {
        String json = prefs.getString(KEY_PREFIX + symbol.toUpperCase(), null);
        if (json == null || json.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray(json);
            int count = 0;
            for (int i = 0; i < arr.length(); i++) {
                ChartDrawing d = deserialize(arr.getJSONObject(i));
                if (d != null) { manager.add(d); count++; }
            }
            Log.d(TAG, "Loaded " + count + " drawings for " + symbol);
        } catch (Exception e) {
            Log.e(TAG, "Load failed for " + symbol, e);
        }
    }

    /** Clear all saved drawings for a ticker (user-triggered "Clear All"). */
    public void clear(String symbol) {
        prefs.edit().remove(KEY_PREFIX + symbol.toUpperCase()).apply();
    }

    /** How many drawings are saved for a given symbol (for UI badge). */
    public int savedCount(String symbol) {
        String json = prefs.getString(KEY_PREFIX + symbol.toUpperCase(), null);
        if (json == null) return 0;
        try { return new JSONArray(json).length(); } catch (Exception e) { return 0; }
    }

    // ── Serialization ─────────────────────────────────────────────────

    private JSONObject serialize(ChartDrawing d) throws JSONException {
        JSONObject o = new JSONObject();
        o.put(F_TYPE,  d.getType().name());
        serializeStyle(o, d.style);

        switch (d.getType()) {
            case HORIZONTAL_LINE: {
                ChartDrawing.HorizontalLine h = (ChartDrawing.HorizontalLine) d;
                o.put(F_PRICE, h.price);
                o.put(F_LABEL, h.label != null ? h.label : "");
                break;
            }
            case TREND_LINE: {
                ChartDrawing.TrendLine t = (ChartDrawing.TrendLine) d;
                o.put(F_START_IDX,   t.startIndex);
                o.put(F_START_PRICE, t.startPrice);
                o.put(F_END_IDX,     t.endIndex);
                o.put(F_END_PRICE,   t.endPrice);
                o.put(F_EXTEND_LEFT,  t.extendLeft);
                o.put(F_EXTEND_RIGHT, t.extendRight);
                break;
            }
            case RAY_LINE: {
                ChartDrawing.RayLine r = (ChartDrawing.RayLine) d;
                o.put(F_START_IDX,    r.startIndex);
                o.put(F_START_PRICE,  r.startPrice);
                o.put(F_ANCHOR_IDX,   r.anchorIndex);
                o.put(F_ANCHOR_PRICE, r.anchorPrice);
                break;
            }
            case VERTICAL_LINE: {
                ChartDrawing.VerticalLine v = (ChartDrawing.VerticalLine) d;
                o.put(F_CANDLE_IDX, v.candleIndex);
                o.put(F_LABEL, v.label != null ? v.label : "");
                break;
            }
            case LINEAR_REGRESSION: {
                ChartDrawing.LinearRegression lr = (ChartDrawing.LinearRegression) d;
                o.put(F_START_IDX,    lr.startIndex);
                o.put(F_END_IDX,      lr.endIndex);
                o.put(F_DRAW_CHANNEL, lr.drawChannel);
                break;
            }
            case FIB_RETRACEMENT: {
                ChartDrawing.FibRetracement fib = (ChartDrawing.FibRetracement) d;
                o.put(F_START_IDX,   fib.startIndex);
                o.put(F_START_PRICE, fib.highPrice);
                o.put(F_END_IDX,     fib.endIndex);
                o.put(F_END_PRICE,   fib.lowPrice);
                JSONArray lvls = new JSONArray();
                for (float lv : fib.levels) lvls.put(lv);
                o.put(F_LEVELS, lvls);
                break;
            }
            case PRICE_RANGE: {
                ChartDrawing.PriceRange pr = (ChartDrawing.PriceRange) d;
                o.put(F_PRICE_HIGH, pr.priceHigh);
                o.put(F_PRICE_LOW,  pr.priceLow);
                break;
            }
            case TEXT_ANNOTATION: {
                ChartDrawing.TextAnnotation ta = (ChartDrawing.TextAnnotation) d;
                o.put(F_CANDLE_IDX, ta.candleIndex);
                o.put(F_PRICE,      ta.price);
                o.put(F_TEXT,       ta.text != null ? ta.text : "");
                break;
            }
            case ARROW: {
                ChartDrawing.Arrow ar = (ChartDrawing.Arrow) d;
                o.put(F_START_IDX,   ar.startIndex);
                o.put(F_START_PRICE, ar.startPrice);
                o.put(F_END_IDX,     ar.endIndex);
                o.put(F_END_PRICE,   ar.endPrice);
                break;
            }
            case EXTENDED_LINE: {
                ChartDrawing.ExtendedLine el = (ChartDrawing.ExtendedLine) d;
                o.put(F_START_IDX,   el.startIndex);
                o.put(F_START_PRICE, el.startPrice);
                o.put(F_END_IDX,     el.endIndex);
                o.put(F_END_PRICE,   el.endPrice);
                break;
            }
            case PARALLEL_CHANNEL: {
                ChartDrawing.ParallelChannel pc = (ChartDrawing.ParallelChannel) d;
                o.put(F_START_IDX,   pc.startIndex);
                o.put(F_START_PRICE, pc.startPrice);
                o.put(F_END_IDX,     pc.endIndex);
                o.put(F_END_PRICE,   pc.endPrice);
                o.put("midPrice",    pc.midPrice);
                break;
            }
            case RECTANGLE: {
                ChartDrawing.Rectangle rect = (ChartDrawing.Rectangle) d;
                o.put(F_START_IDX,   rect.startIndex);
                o.put(F_START_PRICE, rect.startPrice);
                o.put(F_END_IDX,     rect.endIndex);
                o.put(F_END_PRICE,   rect.endPrice);
                break;
            }
            case ELLIPSE: {
                ChartDrawing.Ellipse el = (ChartDrawing.Ellipse) d;
                o.put(F_START_IDX,   el.startIndex);
                o.put(F_START_PRICE, el.startPrice);
                o.put(F_END_IDX,     el.endIndex);
                o.put(F_END_PRICE,   el.endPrice);
                break;
            }
            case PITCHFORK: {
                ChartDrawing.Pitchfork pf = (ChartDrawing.Pitchfork) d;
                o.put("p0idx",   pf.p0Index);  o.put("p0price", pf.p0Price);
                o.put("p1idx",   pf.p1Index);  o.put("p1price", pf.p1Price);
                o.put("p2idx",   pf.p2Index);  o.put("p2price", pf.p2Price);
                break;
            }
            case GANN_FAN: {
                ChartDrawing.GannFan gf = (ChartDrawing.GannFan) d;
                o.put(F_START_IDX,   gf.startIndex);
                o.put(F_START_PRICE, gf.startPrice);
                o.put(F_END_IDX,     gf.endIndex);
                o.put(F_END_PRICE,   gf.endPrice);
                break;
            }
        }
        return o;
    }

    private void serializeStyle(JSONObject o, ChartDrawing.DrawingStyle style) throws JSONException {
        if (style == null) return;
        o.put(F_COLOR,      style.color);
        o.put(F_WIDTH,      style.strokeWidth);
        o.put(F_DASHED,     style.dashed);
        o.put(F_FILLED,     style.filled);
        o.put(F_FILL_COLOR, style.fillColor);
    }

    private ChartDrawing deserialize(JSONObject o) throws JSONException {
        String typeName = o.getString(F_TYPE);
        ChartDrawing.DrawingType type;
        try {
            type = ChartDrawing.DrawingType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Unknown drawing type: " + typeName);
            return null;
        }

        ChartDrawing.DrawingStyle style = deserializeStyle(o);
        ChartDrawing.Source src = ChartDrawing.Source.USER;

        switch (type) {
            case HORIZONTAL_LINE:
                return new ChartDrawing.HorizontalLine(
                        o.getDouble(F_PRICE), o.optString(F_LABEL, ""), style, src);

            case TREND_LINE: {
                ChartDrawing.TrendLine tl = new ChartDrawing.TrendLine(
                        o.getInt(F_START_IDX), o.getDouble(F_START_PRICE),
                        o.getInt(F_END_IDX),   o.getDouble(F_END_PRICE),
                        style, src);
                tl.extendLeft  = o.optBoolean(F_EXTEND_LEFT, false);
                tl.extendRight = o.optBoolean(F_EXTEND_RIGHT, false);
                return tl;
            }
            case RAY_LINE:
                return new ChartDrawing.RayLine(
                        o.getInt(F_START_IDX),    o.getDouble(F_START_PRICE),
                        o.getInt(F_ANCHOR_IDX),   o.getDouble(F_ANCHOR_PRICE),
                        style, src);

            case VERTICAL_LINE:
                return new ChartDrawing.VerticalLine(
                        o.getInt(F_CANDLE_IDX),
                        o.optString(F_LABEL, ""), style, src);

            case LINEAR_REGRESSION: {
                ChartDrawing.LinearRegression lr = new ChartDrawing.LinearRegression(
                        o.getInt(F_START_IDX), o.getInt(F_END_IDX), style, src);
                lr.drawChannel = o.optBoolean(F_DRAW_CHANNEL, false);
                return lr;
            }
            case FIB_RETRACEMENT: {
                JSONArray lvls = o.optJSONArray(F_LEVELS);
                float[] levArr = lvls != null ? new float[lvls.length()] :
                        new float[]{0f, 0.236f, 0.382f, 0.5f, 0.618f, 0.786f, 1f};
                if (lvls != null) for (int i = 0; i < lvls.length(); i++) levArr[i] = (float) lvls.getDouble(i);
                ChartDrawing.FibRetracement fib = new ChartDrawing.FibRetracement(
                        o.getInt(F_START_IDX),   o.getDouble(F_START_PRICE),
                        o.getInt(F_END_IDX),     o.getDouble(F_END_PRICE),
                        style, src);
                fib.levels = levArr;
                return fib;
            }
            case PRICE_RANGE:
                return new ChartDrawing.PriceRange(
                        o.getDouble(F_PRICE_HIGH), o.getDouble(F_PRICE_LOW), style, src);

            case TEXT_ANNOTATION:
                return new ChartDrawing.TextAnnotation(
                        o.getInt(F_CANDLE_IDX), o.getDouble(F_PRICE),
                        o.optString(F_TEXT, "Note"), style, src);

            case ARROW:
                return new ChartDrawing.Arrow(
                        o.getInt(F_START_IDX), o.getDouble(F_START_PRICE),
                        o.getInt(F_END_IDX),   o.getDouble(F_END_PRICE),
                        style, src);

            case EXTENDED_LINE:
                return new ChartDrawing.ExtendedLine(
                        o.getInt(F_START_IDX), o.getDouble(F_START_PRICE),
                        o.getInt(F_END_IDX),   o.getDouble(F_END_PRICE),
                        style, src);

            case PARALLEL_CHANNEL:
                return new ChartDrawing.ParallelChannel(
                        o.getInt(F_START_IDX),   o.getDouble(F_START_PRICE),
                        o.getInt(F_END_IDX),     o.getDouble(F_END_PRICE),
                        o.getDouble("midPrice"), style, src);

            case RECTANGLE:
                return new ChartDrawing.Rectangle(
                        o.getInt(F_START_IDX), o.getDouble(F_START_PRICE),
                        o.getInt(F_END_IDX),   o.getDouble(F_END_PRICE),
                        style, src);

            case ELLIPSE:
                return new ChartDrawing.Ellipse(
                        o.getInt(F_START_IDX), o.getDouble(F_START_PRICE),
                        o.getInt(F_END_IDX),   o.getDouble(F_END_PRICE),
                        style, src);

            case PITCHFORK:
                return new ChartDrawing.Pitchfork(
                        o.getInt("p0idx"), o.getDouble("p0price"),
                        o.getInt("p1idx"), o.getDouble("p1price"),
                        o.getInt("p2idx"), o.getDouble("p2price"),
                        style, src);

            case GANN_FAN:
                return new ChartDrawing.GannFan(
                        o.getInt(F_START_IDX), o.getDouble(F_START_PRICE),
                        o.getInt(F_END_IDX),   o.getDouble(F_END_PRICE),
                        style, src);

            default:
                return null;
        }
    }

    private ChartDrawing.DrawingStyle deserializeStyle(JSONObject o) throws JSONException {
        ChartDrawing.DrawingStyle s = new ChartDrawing.DrawingStyle();
        s.color       = o.optInt(F_COLOR,      Color.WHITE);
        s.strokeWidth = (float) o.optDouble(F_WIDTH, 1.5);
        s.dashed      = o.optBoolean(F_DASHED, false);
        s.filled      = o.optBoolean(F_FILLED, false);
        s.fillColor   = o.optInt(F_FILL_COLOR, Color.argb(40,
                Color.red(s.color), Color.green(s.color), Color.blue(s.color)));
        return s;
    }
}