package com.example.gutapp.data.indicators;

import android.content.Context;

import com.example.gutapp.database.ChartStateDao;
import com.example.gutapp.database.ChartStateMigration;
import com.example.gutapp.database.DB_Helper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * PresetRepository — stores indicator presets in the SQLite {@link ChartStateDao} cache
 * (which also drives the cross-device server sync).
 *
 * Two kinds of storage:
 *  A) Named presets: user-created, shown on ProfileActivity   → kind = "preset", key = name.
 *  B) Per-ticker auto-save: last-used indicator state          → kind = "indicators", key = SYMBOL.
 *
 * Each record's payload is the indicator-snapshot JSON array (unchanged format):
 *   [ {"typeId":"ma","instanceId":"uuid","color":-1,"params":[{"key":"period","value":20}]}, ... ]
 */
public class PresetRepository {

    private final ChartStateDao dao;

    public PresetRepository(Context ctx) {
        this.dao = new ChartStateDao(DB_Helper.getInstance(ctx));
        ChartStateMigration.migrateIfNeeded(ctx);
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
        for (String name : dao.listKeys(ChartStateDao.KIND_PRESET)) {
            String raw = dao.get(ChartStateDao.KIND_PRESET, name);
            if (raw == null) continue;
            try {
                List<Indicator.IndicatorSnapshot> snaps = parseSnapshots(new JSONArray(raw));
                result.add(new Preset(name, snaps));
            } catch (JSONException ignored) {}
        }
        return result;
    }

    /** Save (create or replace) a named preset */
    public void savePreset(Preset preset) {
        dao.upsertLocal(ChartStateDao.KIND_PRESET, preset.name, snapshotsToJson(preset.snapshots));
    }

    /** Delete a named preset by name */
    public void deletePreset(String name) {
        dao.softDelete(ChartStateDao.KIND_PRESET, name);
    }

    /** Rename a preset (carries its indicator payload to the new name) */
    public void renamePreset(String oldName, String newName) {
        if (oldName.equals(newName)) return;
        String raw = dao.get(ChartStateDao.KIND_PRESET, oldName);
        if (raw == null) return;
        dao.upsertLocal(ChartStateDao.KIND_PRESET, newName, raw);
        dao.softDelete(ChartStateDao.KIND_PRESET, oldName);
    }

    // ── Per-ticker Auto-save ──────────────────────────────────────────

    /** Auto-save the current session state for a given symbol */
    public void autoSave(String symbol, IndicatorSession session) {
        dao.upsertLocal(ChartStateDao.KIND_INDICATORS, symbol.toUpperCase(),
                snapshotsToJson(session.savePreset()));
    }

    /** Load the auto-saved session for a given symbol into the session (clears it first) */
    public void autoLoad(String symbol, IndicatorSession session) {
        String raw = dao.get(ChartStateDao.KIND_INDICATORS, symbol.toUpperCase());
        if (raw == null) return;
        try {
            List<Indicator.IndicatorSnapshot> snaps = parseSnapshots(new JSONArray(raw));
            session.loadPreset(snaps);
        } catch (JSONException ignored) {}
    }

    // ── Serialization helpers ─────────────────────────────────────────

    /** Serialize a snapshot list to the stored JSON-array payload. */
    private String snapshotsToJson(List<Indicator.IndicatorSnapshot> snaps) {
        JSONArray arr = new JSONArray();
        try { for (Indicator.IndicatorSnapshot s : snaps) arr.put(serializeSnapshot(s)); }
        catch (JSONException ignored) {}
        return arr.toString();
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