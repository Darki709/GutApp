package com.example.gutapp.database;

import static com.example.gutapp.database.DB_Helper.DB_LOG_TAG;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * ChartStateDao — local SQLite cache for all user-authored chart state.
 *
 * One unified table backs three kinds of records (see {@link Kind}):
 *   drawings    key = SYMBOL      payload = JSON array of user drawings
 *   indicators  key = SYMBOL      payload = JSON array of indicator snapshots (per-symbol auto-save)
 *   preset      key = preset name payload = JSON array of indicator snapshots (named preset)
 *
 * Sync model (per (kind,key), last-write-wins):
 *   - updated_at : epoch millis of the last local OR remote write that won.
 *   - dirty      : 1 = local change not yet pushed to the server.
 *   - deleted    : 1 = tombstone (soft delete) so "clear" syncs as a deletion.
 *
 * This replaces the old SharedPreferences storage in DrawingPersistence /
 * PresetRepository, which now delegate here.
 */
public class ChartStateDao {

    // ── Kinds ─────────────────────────────────────────────────────────
    public static final String KIND_DRAWINGS   = "drawings";
    public static final String KIND_INDICATORS = "indicators";
    public static final String KIND_PRESET     = "preset";

    // ── Table / columns ───────────────────────────────────────────────
    public static final String TABLE         = "chart_state";
    public static final String COL_KIND      = "kind";
    public static final String COL_KEY       = "item_key";
    public static final String COL_PAYLOAD   = "payload";
    public static final String COL_UPDATED   = "updated_at";
    public static final String COL_DIRTY     = "dirty";
    public static final String COL_DELETED   = "deleted";

    /** One row per record. */
    public static class Row {
        public final String kind;
        public final String key;
        public final String payload;
        public final long   updatedAt;
        public final boolean deleted;
        public Row(String kind, String key, String payload, long updatedAt, boolean deleted) {
            this.kind = kind; this.key = key; this.payload = payload;
            this.updatedAt = updatedAt; this.deleted = deleted;
        }
    }

    private final DB_Helper db;

    // Local-change hook: ChartSyncManager registers a Runnable here so any local
    // write schedules a debounced push. Kept as a bare Runnable so the database
    // package has no dependency on the session/sync layer.
    private static volatile Runnable localChangeListener;
    public static void setLocalChangeListener(Runnable r) { localChangeListener = r; }
    private static void fireLocalChange() {
        Runnable r = localChangeListener;
        if (r != null) { try { r.run(); } catch (Exception ignored) {} }
    }

    public ChartStateDao(DB_Helper db) {
        this.db = db;
    }

    /** Wipe the whole cache (used on logout so the next user starts clean). */
    public void clearAll() {
        try {
            db.getWritableDatabase().delete(TABLE, null, null);
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "ChartStateDao.clearAll failed", e);
        }
    }

    // ── DDL ───────────────────────────────────────────────────────────
    public static String createTable() {
        return "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                COL_KIND    + " TEXT NOT NULL, " +
                COL_KEY     + " TEXT NOT NULL, " +
                COL_PAYLOAD + " TEXT NOT NULL DEFAULT '', " +
                COL_UPDATED + " INTEGER NOT NULL DEFAULT 0, " +
                COL_DIRTY   + " INTEGER NOT NULL DEFAULT 0, " +
                COL_DELETED + " INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (" + COL_KIND + ", " + COL_KEY + "))";
    }

    // ── Local reads ───────────────────────────────────────────────────
    /** Returns the live payload for (kind,key), or null if missing/deleted. */
    public String get(String kind, String key) {
        try (Cursor c = db.getReadableDatabase().query(
                TABLE, new String[]{COL_PAYLOAD, COL_DELETED},
                COL_KIND + "=? AND " + COL_KEY + "=?",
                new String[]{kind, key}, null, null, null)) {
            if (c.moveToFirst()) {
                if (c.getInt(1) == 1) return null;             // tombstoned
                return c.getString(0);
            }
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "ChartStateDao.get failed", e);
        }
        return null;
    }

    /** Returns all live (non-deleted) keys of a kind. */
    public List<String> listKeys(String kind) {
        List<String> keys = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().query(
                TABLE, new String[]{COL_KEY},
                COL_KIND + "=? AND " + COL_DELETED + "=0",
                new String[]{kind}, null, null, COL_KEY + " ASC")) {
            while (c.moveToNext()) keys.add(c.getString(0));
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "ChartStateDao.listKeys failed", e);
        }
        return keys;
    }

    // ── Local writes (mark dirty for the next push) ───────────────────
    /** Insert or replace the payload for (kind,key); stamps now, marks dirty, clears tombstone. */
    public void upsertLocal(String kind, String key, String payload) {
        ContentValues cv = new ContentValues();
        cv.put(COL_KIND,    kind);
        cv.put(COL_KEY,     key);
        cv.put(COL_PAYLOAD, payload != null ? payload : "");
        cv.put(COL_UPDATED, System.currentTimeMillis());
        cv.put(COL_DIRTY,   1);
        cv.put(COL_DELETED, 0);
        try {
            db.getWritableDatabase().insertWithOnConflict(
                    TABLE, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);
            fireLocalChange();
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "ChartStateDao.upsertLocal failed", e);
        }
    }

    /** Soft-delete (tombstone) a record so the deletion can sync; stamps now, marks dirty. */
    public void softDelete(String kind, String key) {
        ContentValues cv = new ContentValues();
        cv.put(COL_KIND,    kind);
        cv.put(COL_KEY,     key);
        cv.put(COL_PAYLOAD, "");
        cv.put(COL_UPDATED, System.currentTimeMillis());
        cv.put(COL_DIRTY,   1);
        cv.put(COL_DELETED, 1);
        try {
            db.getWritableDatabase().insertWithOnConflict(
                    TABLE, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);
            fireLocalChange();
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "ChartStateDao.softDelete failed", e);
        }
    }

    // ── Sync support ──────────────────────────────────────────────────
    /** All rows with unpushed local changes (includes tombstones). */
    public List<Row> getDirty() {
        List<Row> rows = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().query(
                TABLE, null, COL_DIRTY + "=1", null, null, null, null)) {
            int iKind = c.getColumnIndexOrThrow(COL_KIND);
            int iKey  = c.getColumnIndexOrThrow(COL_KEY);
            int iPay  = c.getColumnIndexOrThrow(COL_PAYLOAD);
            int iUpd  = c.getColumnIndexOrThrow(COL_UPDATED);
            int iDel  = c.getColumnIndexOrThrow(COL_DELETED);
            while (c.moveToNext()) {
                rows.add(new Row(c.getString(iKind), c.getString(iKey), c.getString(iPay),
                        c.getLong(iUpd), c.getInt(iDel) == 1));
            }
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "ChartStateDao.getDirty failed", e);
        }
        return rows;
    }

    /**
     * Clear the dirty flag for a row only if it has not changed since it was pushed
     * (updatedAt still matches). Avoids racing with a concurrent local edit.
     */
    public void markSynced(String kind, String key, long pushedUpdatedAt) {
        ContentValues cv = new ContentValues();
        cv.put(COL_DIRTY, 0);
        try {
            db.getWritableDatabase().update(TABLE, cv,
                    COL_KIND + "=? AND " + COL_KEY + "=? AND " + COL_UPDATED + "=?",
                    new String[]{kind, key, String.valueOf(pushedUpdatedAt)});
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "ChartStateDao.markSynced failed", e);
        }
    }

    /**
     * Apply a row received from the server using last-write-wins:
     * overwrite the local row only when the remote copy is strictly newer.
     * The applied row is NOT marked dirty (it already matches the server).
     * @return true if the local cache changed.
     */
    public boolean applyRemote(String kind, String key, String payload,
                               long remoteUpdatedAt, boolean deleted) {
        long localUpdated = -1;
        try (Cursor c = db.getReadableDatabase().query(
                TABLE, new String[]{COL_UPDATED},
                COL_KIND + "=? AND " + COL_KEY + "=?",
                new String[]{kind, key}, null, null, null)) {
            if (c.moveToFirst()) localUpdated = c.getLong(0);
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "ChartStateDao.applyRemote read failed", e);
            return false;
        }

        if (remoteUpdatedAt <= localUpdated) return false;   // local is newer or equal — keep it

        ContentValues cv = new ContentValues();
        cv.put(COL_KIND,    kind);
        cv.put(COL_KEY,     key);
        cv.put(COL_PAYLOAD, payload != null ? payload : "");
        cv.put(COL_UPDATED, remoteUpdatedAt);
        cv.put(COL_DIRTY,   0);
        cv.put(COL_DELETED, deleted ? 1 : 0);
        try {
            db.getWritableDatabase().insertWithOnConflict(
                    TABLE, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);
            return true;
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "ChartStateDao.applyRemote write failed", e);
            return false;
        }
    }
}
