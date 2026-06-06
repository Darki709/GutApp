package com.example.gutapp.database;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

/**
 * ChartStateMigration — one-time copy of the legacy SharedPreferences storage
 * (drawings + indicator presets) into the {@link ChartStateDao} SQLite cache.
 *
 * Runs at most once per install, guarded by a flag in its own tiny prefs file.
 * Migrated rows are written via {@code upsertLocal} so they are marked dirty and
 * propagate to the server on the first sync (the user's existing data is kept).
 *
 * Legacy layout (see old DrawingPersistence / PresetRepository):
 *   prefs "chart_drawings"    : "drawings_<SYMBOL>" -> JSON array
 *   prefs "indicator_presets" : "auto_<SYMBOL>"     -> JSON array (per-symbol session)
 *                               "presets_json"      -> [ {name, indicators:[...]} , ... ]
 */
public final class ChartStateMigration {

    private static final String META_PREFS = "chart_state_meta";
    private static final String FLAG       = "migrated_v1";

    // Legacy prefs/keys (kept here so the old classes no longer need them).
    private static final String DRAW_PREFS   = "chart_drawings";
    private static final String DRAW_PREFIX  = "drawings_";
    private static final String IND_PREFS    = "indicator_presets";
    private static final String IND_AUTO     = "auto_";
    private static final String IND_PRESETS  = "presets_json";

    private ChartStateMigration() {}

    public static synchronized void migrateIfNeeded(Context ctx) {
        Context app = ctx.getApplicationContext();
        SharedPreferences meta = app.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE);
        if (meta.getBoolean(FLAG, false)) return;

        try {
            ChartStateDao dao = new ChartStateDao(DB_Helper.getInstance(app));
            migrateDrawings(app, dao);
            migrateIndicators(app, dao);
        } catch (Exception e) {
            Log.e(DB_Helper.DB_LOG_TAG, "ChartStateMigration failed", e);
        } finally {
            // Mark done regardless — never re-run (avoids clobbering newer SQLite data on retry).
            meta.edit().putBoolean(FLAG, true).apply();
        }
    }

    /** Clear the legacy SharedPreferences blobs (called on logout alongside the SQLite wipe). */
    public static void clearLegacyPrefs(Context ctx) {
        Context app = ctx.getApplicationContext();
        app.getSharedPreferences(DRAW_PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        app.getSharedPreferences(IND_PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static void migrateDrawings(Context app, ChartStateDao dao) {
        SharedPreferences p = app.getSharedPreferences(DRAW_PREFS, Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
            String k = e.getKey();
            if (k == null || !k.startsWith(DRAW_PREFIX)) continue;
            Object v = e.getValue();
            if (!(v instanceof String) || ((String) v).isEmpty()) continue;
            String symbol = k.substring(DRAW_PREFIX.length());
            dao.upsertLocal(ChartStateDao.KIND_DRAWINGS, symbol.toUpperCase(), (String) v);
        }
    }

    private static void migrateIndicators(Context app, ChartStateDao dao) {
        SharedPreferences p = app.getSharedPreferences(IND_PREFS, Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (k == null || !(v instanceof String) || ((String) v).isEmpty()) continue;

            if (k.startsWith(IND_AUTO)) {
                String symbol = k.substring(IND_AUTO.length());
                dao.upsertLocal(ChartStateDao.KIND_INDICATORS, symbol.toUpperCase(), (String) v);
            } else if (k.equals(IND_PRESETS)) {
                // Split the single presets blob into one row per named preset.
                try {
                    JSONArray arr = new JSONArray((String) v);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String name = obj.optString("name", "");
                        JSONArray inds = obj.optJSONArray("indicators");
                        if (name.isEmpty() || inds == null) continue;
                        dao.upsertLocal(ChartStateDao.KIND_PRESET, name, inds.toString());
                    }
                } catch (Exception ex) {
                    Log.e(DB_Helper.DB_LOG_TAG, "migrate presets_json failed", ex);
                }
            }
        }
    }
}
