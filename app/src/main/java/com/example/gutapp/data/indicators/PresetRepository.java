package com.example.gutapp.data.indicators;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PresetRepository — stores indicator presets as JSON in SharedPreferences.
 *
 * Two kinds of storage:
 *  A) Named presets: user-created, shown on ProfileActivity.
 *  B) Per-ticker auto-save: last-used indicator state for each symbol.
 *
 * JSON format for a preset:
 * {
 *   "name": "Trend Setup",
 *   "indicators": [
 *     {"typeId":"ma","instanceId":"uuid","color":-1,"params":[{"key":"period","value":20}]},
 *     ...
 *   ]
 * }
 */
public class PresetRepository {

    private static final String PREFS_NAME     = "indicator_presets";
    private static final String KEY_PRESETS    = "presets_json";          // all named presets
    private static final String KEY_AUTO_PREFIX = "auto_";                // auto_AAPL, auto_BTC…

    private final SharedPreferences prefs;

    public PresetRepository(Context ctx) {
        prefs = ctx.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ── Named Presets ─────────────────────────────────────────────────

    public static class Preset {
        public String name;
        public List<Indicator.IndicatorSnapshot> snapshots;
        public Preset(String name, List<Indicator.IndicatorSnapshot> s) {
            this.name = name; this.snapshots = s;
        }
    }

    /** Return all named presets */
    public List<Preset> getAllPresets() {
        List<Preset> result = new ArrayList<>();
        String raw = prefs.getString(KEY_PRESETS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String name = obj.getString("name");
                List<Indicator.IndicatorSnapshot> snaps = parseSnapshots(obj.getJSONArray("indicators"));
                result.add(new Preset(name, snaps));
            }
        } catch (JSONException ignored) {}
        return result;
    }

    /** Save (create or replace) a named preset */
    public void savePreset(Preset preset) {
        List<Preset> all = getAllPresets();
        // replace if name already exists
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).name.equals(preset.name)) { all.set(i, preset); writeAll(all); return; }
        }
        all.add(preset);
        writeAll(all);
    }

    /** Delete a named preset by name */
    public void deletePreset(String name) {
        List<Preset> all = getAllPresets();
        all.removeIf(p -> p.name.equals(name));
        writeAll(all);
    }

    /** Rename a preset */
    public void renamePreset(String oldName, String newName) {
        List<Preset> all = getAllPresets();
        for (Preset p : all) { if (p.name.equals(oldName)) { p.name = newName; break; } }
        writeAll(all);
    }

    private void writeAll(List<Preset> presets) {
        try {
            JSONArray arr = new JSONArray();
            for (Preset p : presets) arr.put(serializePreset(p));
            prefs.edit().putString(KEY_PRESETS, arr.toString()).apply();
        } catch (JSONException ignored) {}
    }

    // ── Per-ticker Auto-save ──────────────────────────────────────────

    /** Auto-save the current session state for a given symbol */
    public void autoSave(String symbol, IndicatorSession session) {
        try {
            JSONArray arr = new JSONArray();
            for (Indicator.IndicatorSnapshot s : session.savePreset()) arr.put(serializeSnapshot(s));
            prefs.edit().putString(KEY_AUTO_PREFIX + symbol.toUpperCase(), arr.toString()).apply();
        } catch (JSONException ignored) {}
    }

    /** Load the auto-saved session for a given symbol into the session (clears it first) */
    public void autoLoad(String symbol, IndicatorSession session) {
        String raw = prefs.getString(KEY_AUTO_PREFIX + symbol.toUpperCase(), null);
        if (raw == null) return;
        try {
            List<Indicator.IndicatorSnapshot> snaps = parseSnapshots(new JSONArray(raw));
            session.loadPreset(snaps);
        } catch (JSONException ignored) {}
    }

    // ── Serialization helpers ─────────────────────────────────────────

    private JSONObject serializePreset(Preset preset) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("name", preset.name);
        JSONArray arr = new JSONArray();
        for (Indicator.IndicatorSnapshot s : preset.snapshots) arr.put(serializeSnapshot(s));
        obj.put("indicators", arr);
        return obj;
    }

    private JSONObject serializeSnapshot(Indicator.IndicatorSnapshot s) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("typeId", s.typeId);
        obj.put("instanceId", s.instanceId);
        obj.put("color", s.color);
        JSONArray pArr = new JSONArray();
        for (Indicator.Param p : s.params) {
            JSONObject pObj = new JSONObject();
            pObj.put("key", p.key);
            pObj.put("value", p.value);
            pArr.put(pObj);
        }
        obj.put("params", pArr);
        return obj;
    }

    private List<Indicator.IndicatorSnapshot> parseSnapshots(JSONArray arr) throws JSONException {
        List<Indicator.IndicatorSnapshot> result = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String typeId      = obj.getString("typeId");
            String instanceId  = obj.getString("instanceId");
            int    color       = obj.getInt("color");
            JSONArray pArr     = obj.getJSONArray("params");
            // Reconstruct params list from the type prototype
            Indicator proto = IndicatorRegistry.getInstance().getType(typeId);
            if (proto == null) continue;
            List<Indicator.Param> params = proto.copyParams();
            for (int j = 0; j < pArr.length(); j++) {
                JSONObject pObj = pArr.getJSONObject(j);
                String key = pObj.getString("key");
                float  val = (float) pObj.getDouble("value");
                for (Indicator.Param p : params) { if (p.key.equals(key)) { p.value = val; break; } }
            }
            result.add(new Indicator.IndicatorSnapshot(typeId, instanceId, color, params));
        }
        return result;
    }
}