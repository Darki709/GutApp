package com.example.gutapp.data.drawing;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * DrawingPersistence — saves/loads USER drawings for a ticker to SharedPreferences.
 *
 * Key: "drawings_<SYMBOL>"  (timeframe-agnostic — drawings appear on all timeframes)
 * Anchors are stored as UNIX TIMESTAMPS (seconds), not candle indices, so the
 * correct position is recomputed for any timeframe on load.
 */
public class DrawingPersistence {

    private static final String TAG       = "DrawingPersistence";
    private static final String PREFS     = "chart_drawings";
    private static final String PREFIX    = "drawings_";

    // Style fields
    private static final String F_TYPE       = "type";
    private static final String F_COLOR      = "color";
    private static final String F_WIDTH      = "width";
    private static final String F_DASHED     = "dashed";
    private static final String F_FILLED     = "filled";
    private static final String F_FILL_COLOR = "fillColor";
    private static final String F_LAYER      = "layer";
    // Anchor fields (all timestamps)
    private static final String F_PRICE      = "price";
    private static final String F_LABEL      = "label";
    private static final String F_START_TS   = "startTs";
    private static final String F_START_PX   = "startPrice";
    private static final String F_END_TS     = "endTs";
    private static final String F_END_PX     = "endPrice";
    private static final String F_ANCHOR_TS  = "anchorTs";
    private static final String F_ANCHOR_PX  = "anchorPrice";
    private static final String F_CANDLE_TS  = "candleTs";
    private static final String F_DRAW_CH    = "drawChannel";
    private static final String F_LEVELS     = "levels";
    private static final String F_PRICE_HI   = "priceHigh";
    private static final String F_PRICE_LO   = "priceLow";
    private static final String F_TEXT       = "text";
    private static final String F_MID_PRICE  = "midPrice";
    private static final String F_P0_TS      = "p0ts";
    private static final String F_P0_PX      = "p0price";
    private static final String F_P1_TS      = "p1ts";
    private static final String F_P1_PX      = "p1price";
    private static final String F_P2_TS      = "p2ts";
    private static final String F_P2_PX      = "p2price";
    private static final String F_EXT_LEFT   = "extendLeft";
    private static final String F_EXT_RIGHT  = "extendRight";

    private final SharedPreferences prefs;

    public DrawingPersistence(Context ctx) {
        this.prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ── Save ──────────────────────────────────────────────────────────

    public void save(String symbol, DrawingManager mgr) {
        try {
            JSONArray arr = new JSONArray();
            for (ChartDrawing d : mgr.getAll()) {
                if (d.source != ChartDrawing.Source.USER) continue;
                JSONObject o = serialize(d);
                if (o != null) arr.put(o);
            }
            prefs.edit().putString(PREFIX + symbol.toUpperCase(), arr.toString()).apply();
            Log.d(TAG, "Saved " + arr.length() + " drawings for " + symbol);
        } catch (Exception e) {
            Log.e(TAG, "Save failed for " + symbol, e);
        }
    }

    // ── Load ──────────────────────────────────────────────────────────

    public void load(String symbol, DrawingManager mgr) {
        String json = prefs.getString(PREFIX + symbol.toUpperCase(), null);
        if (json == null || json.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray(json);
            int count = 0;
            for (int i = 0; i < arr.length(); i++) {
                ChartDrawing d = deserialize(arr.getJSONObject(i));
                if (d != null) { mgr.add(d); count++; }
            }
            Log.d(TAG, "Loaded " + count + " drawings for " + symbol);
        } catch (Exception e) {
            Log.e(TAG, "Load failed for " + symbol, e);
        }
    }

    /** Wipe all saved drawings for a ticker (user-triggered Clear All). */
    public void clear(String symbol) {
        prefs.edit().remove(PREFIX + symbol.toUpperCase()).apply();
    }

    public int savedCount(String symbol) {
        String json = prefs.getString(PREFIX + symbol.toUpperCase(), null);
        if (json == null) return 0;
        try { return new JSONArray(json).length(); } catch (Exception e) { return 0; }
    }

    // ── Serialization ─────────────────────────────────────────────────

    private JSONObject serialize(ChartDrawing d) throws JSONException {
        JSONObject o = new JSONObject();
        o.put(F_TYPE,  d.getType().name());
        o.put(F_LAYER, d.layer.name());
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
                o.put(F_START_TS, t.startTs); o.put(F_START_PX, t.startPrice);
                o.put(F_END_TS,   t.endTs);   o.put(F_END_PX,   t.endPrice);
                o.put(F_EXT_LEFT, t.extendLeft); o.put(F_EXT_RIGHT, t.extendRight);
                break;
            }
            case RAY_LINE: {
                ChartDrawing.RayLine r = (ChartDrawing.RayLine) d;
                o.put(F_START_TS,  r.startTs);  o.put(F_START_PX,  r.startPrice);
                o.put(F_ANCHOR_TS, r.anchorTs); o.put(F_ANCHOR_PX, r.anchorPrice);
                break;
            }
            case EXTENDED_LINE: {
                ChartDrawing.ExtendedLine el = (ChartDrawing.ExtendedLine) d;
                o.put(F_START_TS, el.startTs); o.put(F_START_PX, el.startPrice);
                o.put(F_END_TS,   el.endTs);   o.put(F_END_PX,   el.endPrice);
                break;
            }
            case VERTICAL_LINE: {
                ChartDrawing.VerticalLine v = (ChartDrawing.VerticalLine) d;
                o.put(F_CANDLE_TS, v.candleTs);
                o.put(F_LABEL,     v.label != null ? v.label : "");
                break;
            }
            case LINEAR_REGRESSION: {
                ChartDrawing.LinearRegression lr = (ChartDrawing.LinearRegression) d;
                o.put(F_START_TS, lr.startTs); o.put(F_END_TS, lr.endTs);
                o.put(F_DRAW_CH,  lr.drawChannel);
                break;
            }
            case FIB_RETRACEMENT: {
                ChartDrawing.FibRetracement fib = (ChartDrawing.FibRetracement) d;
                o.put(F_START_TS, fib.startTs); o.put(F_START_PX, fib.highPrice);
                o.put(F_END_TS,   fib.endTs);   o.put(F_END_PX,   fib.lowPrice);
                if (fib.levels != null) {
                    JSONArray lvls = new JSONArray();
                    for (float lv : fib.levels) lvls.put(lv);
                    o.put(F_LEVELS, lvls);
                }
                break;
            }
            case PRICE_RANGE: {
                ChartDrawing.PriceRange pr = (ChartDrawing.PriceRange) d;
                o.put(F_PRICE_HI, pr.priceHigh); o.put(F_PRICE_LO, pr.priceLow);
                break;
            }
            case RECTANGLE: {
                ChartDrawing.Rectangle r = (ChartDrawing.Rectangle) d;
                o.put(F_START_TS, r.startTs); o.put(F_START_PX, r.startPrice);
                o.put(F_END_TS,   r.endTs);   o.put(F_END_PX,   r.endPrice);
                break;
            }
            case ELLIPSE: {
                ChartDrawing.Ellipse el = (ChartDrawing.Ellipse) d;
                o.put(F_START_TS, el.startTs); o.put(F_START_PX, el.startPrice);
                o.put(F_END_TS,   el.endTs);   o.put(F_END_PX,   el.endPrice);
                break;
            }
            case TEXT_ANNOTATION: {
                ChartDrawing.TextAnnotation ta = (ChartDrawing.TextAnnotation) d;
                o.put(F_CANDLE_TS, ta.candleTs); o.put(F_PRICE, ta.price);
                o.put(F_TEXT,      ta.text != null ? ta.text : "");
                break;
            }
            case ARROW: {
                ChartDrawing.Arrow ar = (ChartDrawing.Arrow) d;
                o.put(F_START_TS, ar.startTs); o.put(F_START_PX, ar.startPrice);
                o.put(F_END_TS,   ar.endTs);   o.put(F_END_PX,   ar.endPrice);
                break;
            }
            case PARALLEL_CHANNEL: {
                ChartDrawing.ParallelChannel pc = (ChartDrawing.ParallelChannel) d;
                o.put(F_START_TS,  pc.startTs);  o.put(F_START_PX, pc.startPrice);
                o.put(F_END_TS,    pc.endTs);    o.put(F_END_PX,   pc.endPrice);
                o.put(F_MID_PRICE, pc.midPrice);
                break;
            }
            case PITCHFORK: {
                ChartDrawing.Pitchfork pf = (ChartDrawing.Pitchfork) d;
                o.put(F_P0_TS, pf.p0Ts); o.put(F_P0_PX, pf.p0Price);
                o.put(F_P1_TS, pf.p1Ts); o.put(F_P1_PX, pf.p1Price);
                o.put(F_P2_TS, pf.p2Ts); o.put(F_P2_PX, pf.p2Price);
                break;
            }
            case GANN_FAN: {
                ChartDrawing.GannFan gf = (ChartDrawing.GannFan) d;
                o.put(F_START_TS, gf.startTs); o.put(F_START_PX, gf.startPrice);
                o.put(F_END_TS,   gf.endTs);   o.put(F_END_PX,   gf.endPrice);
                break;
            }
        }
        return o;
    }

    private void serializeStyle(JSONObject o, ChartDrawing.DrawingStyle s) throws JSONException {
        if (s == null) return;
        o.put(F_COLOR,      s.color);
        o.put(F_WIDTH,      s.strokeWidth);
        o.put(F_DASHED,     s.dashed);
        o.put(F_FILLED,     s.filled);
        o.put(F_FILL_COLOR, s.fillColor);
    }

    // ── Deserialization ───────────────────────────────────────────────

    private ChartDrawing deserialize(JSONObject o) throws JSONException {
        String typeName = o.getString(F_TYPE);
        ChartDrawing.DrawingType type;
        try { type = ChartDrawing.DrawingType.valueOf(typeName); }
        catch (IllegalArgumentException e) { Log.w(TAG,"Unknown type: "+typeName); return null; }

        ChartDrawing.DrawingStyle style = deserializeStyle(o);
        ChartDrawing.Source src = ChartDrawing.Source.USER;
        ChartDrawing.Layer layer = ChartDrawing.Layer.BEHIND_CANDLES;
        try { layer = ChartDrawing.Layer.valueOf(o.optString(F_LAYER, "BEHIND_CANDLES")); }
        catch (Exception ignored) {}

        ChartDrawing d;
        switch (type) {
            case HORIZONTAL_LINE:
                d = new ChartDrawing.HorizontalLine(
                        o.getDouble(F_PRICE), o.optString(F_LABEL,""), style, src);
                break;
            case TREND_LINE: {
                ChartDrawing.TrendLine tl = new ChartDrawing.TrendLine(
                        o.getLong(F_START_TS), o.getDouble(F_START_PX),
                        o.getLong(F_END_TS),   o.getDouble(F_END_PX), style, src);
                tl.extendLeft  = o.optBoolean(F_EXT_LEFT,  false);
                tl.extendRight = o.optBoolean(F_EXT_RIGHT, false);
                d = tl; break;
            }
            case RAY_LINE:
                d = new ChartDrawing.RayLine(
                        o.getLong(F_START_TS),  o.getDouble(F_START_PX),
                        o.getLong(F_ANCHOR_TS), o.getDouble(F_ANCHOR_PX), style, src);
                break;
            case EXTENDED_LINE:
                d = new ChartDrawing.ExtendedLine(
                        o.getLong(F_START_TS), o.getDouble(F_START_PX),
                        o.getLong(F_END_TS),   o.getDouble(F_END_PX), style, src);
                break;
            case VERTICAL_LINE:
                d = new ChartDrawing.VerticalLine(
                        o.getLong(F_CANDLE_TS), o.optString(F_LABEL,""), style, src);
                break;
            case LINEAR_REGRESSION: {
                ChartDrawing.LinearRegression lr = new ChartDrawing.LinearRegression(
                        o.getLong(F_START_TS), o.getLong(F_END_TS), style, src);
                lr.drawChannel = o.optBoolean(F_DRAW_CH, false);
                d = lr; break;
            }
            case FIB_RETRACEMENT: {
                ChartDrawing.FibRetracement fib = new ChartDrawing.FibRetracement(
                        o.getLong(F_START_TS), o.getDouble(F_START_PX),
                        o.getLong(F_END_TS),   o.getDouble(F_END_PX), style, src);
                JSONArray lvls = o.optJSONArray(F_LEVELS);
                if (lvls != null) {
                    float[] la = new float[lvls.length()];
                    for (int i = 0; i < lvls.length(); i++) la[i] = (float) lvls.getDouble(i);
                    fib.levels = la;
                }
                d = fib; break;
            }
            case PRICE_RANGE:
                d = new ChartDrawing.PriceRange(
                        o.getDouble(F_PRICE_HI), o.getDouble(F_PRICE_LO), style, src);
                break;
            case RECTANGLE:
                d = new ChartDrawing.Rectangle(
                        o.getLong(F_START_TS), o.getDouble(F_START_PX),
                        o.getLong(F_END_TS),   o.getDouble(F_END_PX), style, src);
                break;
            case ELLIPSE:
                d = new ChartDrawing.Ellipse(
                        o.getLong(F_START_TS), o.getDouble(F_START_PX),
                        o.getLong(F_END_TS),   o.getDouble(F_END_PX), style, src);
                break;
            case TEXT_ANNOTATION:
                d = new ChartDrawing.TextAnnotation(
                        o.getLong(F_CANDLE_TS), o.getDouble(F_PRICE),
                        o.optString(F_TEXT,""), style, src);
                break;
            case ARROW:
                d = new ChartDrawing.Arrow(
                        o.getLong(F_START_TS), o.getDouble(F_START_PX),
                        o.getLong(F_END_TS),   o.getDouble(F_END_PX), style, src);
                break;
            case PARALLEL_CHANNEL:
                d = new ChartDrawing.ParallelChannel(
                        o.getLong(F_START_TS),   o.getDouble(F_START_PX),
                        o.getLong(F_END_TS),     o.getDouble(F_END_PX),
                        o.getDouble(F_MID_PRICE), style, src);
                break;
            case PITCHFORK:
                d = new ChartDrawing.Pitchfork(
                        o.getLong(F_P0_TS), o.getDouble(F_P0_PX),
                        o.getLong(F_P1_TS), o.getDouble(F_P1_PX),
                        o.getLong(F_P2_TS), o.getDouble(F_P2_PX), style, src);
                break;
            case GANN_FAN:
                d = new ChartDrawing.GannFan(
                        o.getLong(F_START_TS), o.getDouble(F_START_PX),
                        o.getLong(F_END_TS),   o.getDouble(F_END_PX), style, src);
                break;
            default: return null;
        }
        d.layer = layer;
        return d;
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