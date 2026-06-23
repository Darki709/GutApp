package com.example.gutapp.database;

import static com.example.gutapp.database.DB_Helper.DB_LOG_TAG;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.gutapp.data.alerts.Alert;
import com.example.gutapp.data.alerts.Condition;
import com.example.gutapp.data.alerts.ConditionRegistry;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AlertDBHelper — all SQLite persistence for the alert system.
 *
 * Schema stores each Alert as one row. The Condition is split into
 * two columns: a stable type-key (used by ConditionRegistry to pick the
 * right deserializer) and a JSON payload holding the condition's parameters.
 *
 * This avoids fragile class-name storage and survives package refactors.
 *
 * Sync model (per-alert, last-write-wins, keyed by {@link #COL_UUID}):
 *   - uuid       : stable cross-device identity (the local _id is per-device only).
 *   - updated_at : epoch millis of the last local OR remote write that won.
 *   - dirty      : 1 = local change not yet pushed to the server.
 *   - deleted    : 1 = tombstone (soft delete) so a deletion propagates to other devices.
 * All user-visible queries filter {@code deleted = 0}. Automatic trigger-state writes
 * ({@link #updateAlertStatus}) deliberately do NOT mark the row dirty, so background
 * firing never spams the network — only user edits sync.
 */
public class AlertDBHelper {

    // ── Table / Column names ──────────────────────────────────────────
    public static final String TABLE_ALERTS         = "alerts";
    public static final String COL_ID               = "_id";
    public static final String COL_SYMBOL           = "symbol";
    public static final String COL_LABEL            = "label";
    public static final String COL_STATUS           = "status";
    public static final String COL_REPEAT_MODE      = "repeat_mode";
    public static final String COL_COOLDOWN_SECS    = "cooldown_secs";
    public static final String COL_LAST_TRIGGERED   = "last_triggered_at";
    public static final String COL_EXPIRES_AT       = "expires_at";
    public static final String COL_PRIORITY         = "priority";
    public static final String COL_CONDITION_TYPE   = "condition_type";
    public static final String COL_CONDITION_JSON   = "condition_json";
    // Sync metadata
    public static final String COL_UUID             = "uuid";
    public static final String COL_UPDATED_AT       = "updated_at";
    public static final String COL_DIRTY            = "dirty";
    public static final String COL_DELETED          = "deleted";

    private final DB_Helper db;

    public AlertDBHelper(DB_Helper db) {
        this.db = db;
    }

    // ── Local-change hook (mirrors ChartStateDao) ─────────────────────
    // AlertSyncManager registers a Runnable here so any local write schedules a
    // debounced push. Kept as a bare Runnable so the database package has no
    // dependency on the session/sync layer.
    private static volatile Runnable localChangeListener;
    public static void setLocalChangeListener(Runnable r) { localChangeListener = r; }
    private static void fireLocalChange() {
        Runnable r = localChangeListener;
        if (r != null) { try { r.run(); } catch (Exception ignored) {} }
    }

    /** One alert as a sync unit: opaque payload keyed by uuid, with LWW metadata. */
    public static class SyncRow {
        public final String  uuid;
        public final String  payload;     // JSON of the alert's fields (opaque to the server)
        public final long    updatedAt;   // epoch millis
        public final boolean deleted;     // tombstone
        public SyncRow(String uuid, String payload, long updatedAt, boolean deleted) {
            this.uuid = uuid; this.payload = payload;
            this.updatedAt = updatedAt; this.deleted = deleted;
        }
    }

    // ── DDL ───────────────────────────────────────────────────────────
    public static String createTable() {
        return "CREATE TABLE IF NOT EXISTS " + TABLE_ALERTS + " (" +
                COL_ID             + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_SYMBOL         + " TEXT NOT NULL, " +
                COL_LABEL          + " TEXT NOT NULL, " +
                COL_STATUS         + " TEXT NOT NULL, " +
                COL_REPEAT_MODE    + " TEXT NOT NULL, " +
                COL_COOLDOWN_SECS  + " INTEGER NOT NULL DEFAULT 0, " +
                COL_LAST_TRIGGERED + " INTEGER NOT NULL DEFAULT 0, " +
                COL_EXPIRES_AT     + " INTEGER NOT NULL DEFAULT 0, " +
                COL_PRIORITY       + " TEXT NOT NULL, " +
                COL_CONDITION_TYPE + " TEXT NOT NULL, " +
                COL_CONDITION_JSON + " TEXT NOT NULL, " +
                COL_UUID           + " TEXT, " +
                COL_UPDATED_AT     + " INTEGER NOT NULL DEFAULT 0, " +
                COL_DIRTY          + " INTEGER NOT NULL DEFAULT 0, " +
                COL_DELETED        + " INTEGER NOT NULL DEFAULT 0)";
    }

    // ── Insert ────────────────────────────────────────────────────────
    /**
     * Persists a new alert and assigns the generated DB ID + UUID back to the object.
     * Stamps updated_at = now and marks the row dirty so it is pushed on the next sync.
     */
    public void insertAlert(Alert alert) {
        try {
            if (alert.getUuid() == null || alert.getUuid().isEmpty())
                alert.setUuid(UUID.randomUUID().toString());
            long now = System.currentTimeMillis();
            alert.setUpdatedAt(now);

            ContentValues cv = toValues(alert);
            cv.put(COL_UUID,       alert.getUuid());
            cv.put(COL_UPDATED_AT, now);
            cv.put(COL_DIRTY,      1);
            cv.put(COL_DELETED,    0);

            long id = db.getWritableDatabase().insert(TABLE_ALERTS, null, cv);
            alert.setId(id);
            fireLocalChange();
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "insertAlert failed", e);
        }
    }

    // ── Queries (live rows only) ──────────────────────────────────────
    /** Returns all ACTIVE, non-deleted alerts (loaded at start-up into AlertManager). */
    public List<Alert> getActiveAlerts() {
        return queryAlerts(COL_STATUS + "=? AND " + COL_DELETED + "=0",
                new String[]{Alert.Status.ACTIVE.name()});
    }

    /** Returns every non-deleted alert for a given symbol (for the management UI). */
    public List<Alert> getAlertsForSymbol(String symbol) {
        return queryAlerts(COL_SYMBOL + "=? AND " + COL_DELETED + "=0", new String[]{symbol});
    }

    /** Returns all non-deleted alerts regardless of status (for the global alerts list UI). */
    public List<Alert> getAllAlerts() {
        return queryAlerts(COL_DELETED + "=0", null);
    }

    // ── Updates ───────────────────────────────────────────────────────
    /**
     * Persists every mutable field of an Alert after a user edit. Stamps updated_at = now
     * and marks the row dirty for sync. Uses _id as the key.
     */
    public void updateAlert(Alert alert) {
        if (alert.getId() < 0) {
            Log.w(DB_LOG_TAG, "updateAlert called on unpersisted alert — inserting instead");
            insertAlert(alert);
            return;
        }
        try {
            long now = System.currentTimeMillis();
            alert.setUpdatedAt(now);
            ContentValues cv = toValues(alert);
            cv.put(COL_UPDATED_AT, now);
            cv.put(COL_DIRTY,      1);
            cv.put(COL_DELETED,    0);
            db.getWritableDatabase().update(
                    TABLE_ALERTS, cv,
                    COL_ID + "=?",
                    new String[]{String.valueOf(alert.getId())});
            fireLocalChange();
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "updateAlert failed for id=" + alert.getId(), e);
        }
    }

    /**
     * Convenience: only write the status + trigger timestamp columns. This is the
     * AUTOMATIC trigger-state path (called from the evaluation worker). It intentionally
     * does NOT bump updated_at / dirty, so a firing alert never floods the sync channel.
     */
    public void updateAlertStatus(Alert alert) {
        if (alert.getId() < 0) return;
        ContentValues cv = new ContentValues();
        cv.put(COL_STATUS,          alert.getStatus().name());
        cv.put(COL_LAST_TRIGGERED,  alert.getLastTriggeredAt());
        try {
            db.getWritableDatabase().update(
                    TABLE_ALERTS, cv,
                    COL_ID + "=?",
                    new String[]{String.valueOf(alert.getId())});
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "updateAlertStatus failed", e);
        }
    }

    /**
     * User-initiated status change (the active/paused toggle in the UI). Unlike
     * {@link #updateAlertStatus}, this DOES stamp updated_at + dirty so the change syncs.
     */
    public void updateAlertStatusByUser(Alert alert) {
        if (alert.getId() < 0) return;
        long now = System.currentTimeMillis();
        alert.setUpdatedAt(now);
        ContentValues cv = new ContentValues();
        cv.put(COL_STATUS,         alert.getStatus().name());
        cv.put(COL_LAST_TRIGGERED, alert.getLastTriggeredAt());
        cv.put(COL_UPDATED_AT,     now);
        cv.put(COL_DIRTY,          1);
        try {
            db.getWritableDatabase().update(
                    TABLE_ALERTS, cv,
                    COL_ID + "=?",
                    new String[]{String.valueOf(alert.getId())});
            fireLocalChange();
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "updateAlertStatusByUser failed", e);
        }
    }

    // ── Delete (soft, so the deletion syncs) ──────────────────────────
    /**
     * Soft-delete: marks the row as a tombstone (deleted=1, dirty=1, updated_at=now) so the
     * deletion is pushed and propagates to the user's other devices. Live queries skip it.
     */
    public void deleteAlert(Alert alert) {
        if (alert.getId() < 0) return;
        try {
            ContentValues cv = new ContentValues();
            cv.put(COL_DELETED,    1);
            cv.put(COL_DIRTY,      1);
            cv.put(COL_UPDATED_AT, System.currentTimeMillis());
            db.getWritableDatabase().update(
                    TABLE_ALERTS, cv,
                    COL_ID + "=?",
                    new String[]{String.valueOf(alert.getId())});
            fireLocalChange();
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "deleteAlert failed", e);
        }
    }

    /** Soft-delete every alert for a symbol (tombstoned + dirty so the deletions sync). */
    public void deleteAllForSymbol(String symbol) {
        try {
            ContentValues cv = new ContentValues();
            cv.put(COL_DELETED,    1);
            cv.put(COL_DIRTY,      1);
            cv.put(COL_UPDATED_AT, System.currentTimeMillis());
            db.getWritableDatabase().update(
                    TABLE_ALERTS, cv,
                    COL_SYMBOL + "=? AND " + COL_DELETED + "=0",
                    new String[]{symbol});
            fireLocalChange();
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "deleteAllForSymbol failed", e);
        }
    }

    // ── Sync support ──────────────────────────────────────────────────
    /** All rows with unpushed local changes (includes tombstones). */
    public List<SyncRow> getDirty() {
        List<SyncRow> rows = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().query(
                TABLE_ALERTS, null, COL_DIRTY + "=1", null, null, null, null)) {
            while (c.moveToNext()) {
                String uuid = getStr(c, COL_UUID);
                if (uuid == null || uuid.isEmpty()) continue; // can't sync without identity
                long updatedAt = c.getLong(c.getColumnIndexOrThrow(COL_UPDATED_AT));
                boolean deleted = c.getInt(c.getColumnIndexOrThrow(COL_DELETED)) == 1;
                rows.add(new SyncRow(uuid, payloadFromCursor(c), updatedAt, deleted));
            }
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "AlertDBHelper.getDirty failed", e);
        }
        return rows;
    }

    /**
     * Clear the dirty flag for a row only if it has not changed since it was pushed
     * (updated_at still matches). Avoids racing with a concurrent local edit.
     */
    public void markSynced(String uuid, long pushedUpdatedAt) {
        ContentValues cv = new ContentValues();
        cv.put(COL_DIRTY, 0);
        try {
            db.getWritableDatabase().update(TABLE_ALERTS, cv,
                    COL_UUID + "=? AND " + COL_UPDATED_AT + "=?",
                    new String[]{uuid, String.valueOf(pushedUpdatedAt)});
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "AlertDBHelper.markSynced failed", e);
        }
    }

    /**
     * Apply a row received from the server using last-write-wins: write only when the remote
     * copy is strictly newer than the local one. The applied row is NOT marked dirty (it
     * already matches the server) and does NOT fire the local-change hook.
     * @return true if the local store changed.
     */
    public boolean applyRemote(String uuid, String payload, long remoteUpdatedAt, boolean deleted) {
        if (uuid == null || uuid.isEmpty()) return false;
        SQLiteDatabase wdb = db.getWritableDatabase();

        long localId = -1, localUpdated = -1;
        try (Cursor c = wdb.query(TABLE_ALERTS, new String[]{COL_ID, COL_UPDATED_AT},
                COL_UUID + "=?", new String[]{uuid}, null, null, null)) {
            if (c.moveToFirst()) { localId = c.getLong(0); localUpdated = c.getLong(1); }
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "AlertDBHelper.applyRemote read failed", e);
            return false;
        }

        boolean exists = localId >= 0;
        if (exists && remoteUpdatedAt <= localUpdated) return false; // local newer/equal — keep

        try {
            if (deleted) {
                if (!exists) return false; // nothing local to tombstone
                ContentValues cv = new ContentValues();
                cv.put(COL_DELETED,    1);
                cv.put(COL_DIRTY,      0);
                cv.put(COL_UPDATED_AT, remoteUpdatedAt);
                wdb.update(TABLE_ALERTS, cv, COL_ID + "=?", new String[]{String.valueOf(localId)});
                return true;
            }

            ContentValues cv = valuesFromPayload(payload);
            if (cv == null) return false; // malformed payload — ignore rather than corrupt
            cv.put(COL_UUID,       uuid);
            cv.put(COL_UPDATED_AT, remoteUpdatedAt);
            cv.put(COL_DIRTY,      0);
            cv.put(COL_DELETED,    0);
            if (exists)
                wdb.update(TABLE_ALERTS, cv, COL_ID + "=?", new String[]{String.valueOf(localId)});
            else
                wdb.insert(TABLE_ALERTS, null, cv);
            return true;
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "AlertDBHelper.applyRemote write failed", e);
            return false;
        }
    }

    // ── Internals ─────────────────────────────────────────────────────
    private List<Alert> queryAlerts(String selection, String[] args) {
        List<Alert> results = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().query(
                TABLE_ALERTS, null, selection, args,
                null, null, COL_ID + " ASC")) {
            while (c.moveToNext()) {
                Alert a = fromCursor(c);
                if (a != null) results.add(a);
            }
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "queryAlerts failed", e);
        }
        return results;
    }

    private ContentValues toValues(Alert a) {
        ContentValues cv = new ContentValues();
        cv.put(COL_SYMBOL,          a.getSymbol());
        cv.put(COL_LABEL,           a.getLabel());
        cv.put(COL_STATUS,          a.getStatus().name());
        cv.put(COL_REPEAT_MODE,     a.getRepeatMode().name());
        cv.put(COL_COOLDOWN_SECS,   a.getCooldownSeconds());
        cv.put(COL_LAST_TRIGGERED,  a.getLastTriggeredAt());
        cv.put(COL_EXPIRES_AT,      a.getExpiresAt());
        cv.put(COL_PRIORITY,        a.getPriority().name());
        cv.put(COL_CONDITION_TYPE,  a.getCondition().getTypeName());
        cv.put(COL_CONDITION_JSON,  a.getCondition().serialize());
        return cv;
    }

    private Alert fromCursor(Cursor c) {
        try {
            long   id            = c.getLong(c.getColumnIndexOrThrow(COL_ID));
            String symbol        = c.getString(c.getColumnIndexOrThrow(COL_SYMBOL));
            String label         = c.getString(c.getColumnIndexOrThrow(COL_LABEL));
            String statusStr     = c.getString(c.getColumnIndexOrThrow(COL_STATUS));
            String repeatStr     = c.getString(c.getColumnIndexOrThrow(COL_REPEAT_MODE));
            int    cooldown      = c.getInt(c.getColumnIndexOrThrow(COL_COOLDOWN_SECS));
            long   lastTriggered = c.getLong(c.getColumnIndexOrThrow(COL_LAST_TRIGGERED));
            long   expiresAt     = c.getLong(c.getColumnIndexOrThrow(COL_EXPIRES_AT));
            String priorityStr   = c.getString(c.getColumnIndexOrThrow(COL_PRIORITY));
            String condType      = c.getString(c.getColumnIndexOrThrow(COL_CONDITION_TYPE));
            String condJson      = c.getString(c.getColumnIndexOrThrow(COL_CONDITION_JSON));

            Condition condition = ConditionRegistry.deserialize(condType, condJson);
            if (condition == null) return null; // unknown/corrupt type

            Alert a = new Alert(
                    id, symbol, label, condition,
                    Alert.Status.valueOf(statusStr),
                    Alert.RepeatMode.valueOf(repeatStr),
                    cooldown, lastTriggered, expiresAt,
                    Alert.Priority.valueOf(priorityStr));
            a.setUuid(getStr(c, COL_UUID));
            int iUpd = c.getColumnIndex(COL_UPDATED_AT);
            if (iUpd >= 0) a.setUpdatedAt(c.getLong(iUpd));
            return a;
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "fromCursor failed", e);
            return null;
        }
    }

    /** Serialize the alert columns of the current cursor row into the sync payload JSON. */
    private String payloadFromCursor(Cursor c) {
        try {
            JSONObject o = new JSONObject();
            o.put(COL_SYMBOL,         getStr(c, COL_SYMBOL));
            o.put(COL_LABEL,          getStr(c, COL_LABEL));
            o.put(COL_STATUS,         getStr(c, COL_STATUS));
            o.put(COL_REPEAT_MODE,    getStr(c, COL_REPEAT_MODE));
            o.put(COL_COOLDOWN_SECS,  c.getInt(c.getColumnIndexOrThrow(COL_COOLDOWN_SECS)));
            o.put(COL_LAST_TRIGGERED, c.getLong(c.getColumnIndexOrThrow(COL_LAST_TRIGGERED)));
            o.put(COL_EXPIRES_AT,     c.getLong(c.getColumnIndexOrThrow(COL_EXPIRES_AT)));
            o.put(COL_PRIORITY,       getStr(c, COL_PRIORITY));
            o.put(COL_CONDITION_TYPE, getStr(c, COL_CONDITION_TYPE));
            o.put(COL_CONDITION_JSON, getStr(c, COL_CONDITION_JSON));
            return o.toString();
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "payloadFromCursor failed", e);
            return "{}";
        }
    }

    /** Parse a sync payload JSON back into the alert core columns, or null if malformed. */
    private ContentValues valuesFromPayload(String payload) {
        try {
            JSONObject o = new JSONObject(payload == null ? "" : payload);
            ContentValues cv = new ContentValues();
            cv.put(COL_SYMBOL,          o.optString(COL_SYMBOL, ""));
            cv.put(COL_LABEL,           o.optString(COL_LABEL, ""));
            cv.put(COL_STATUS,          o.optString(COL_STATUS, Alert.Status.ACTIVE.name()));
            cv.put(COL_REPEAT_MODE,     o.optString(COL_REPEAT_MODE, Alert.RepeatMode.ONCE.name()));
            cv.put(COL_COOLDOWN_SECS,   o.optInt(COL_COOLDOWN_SECS, 0));
            cv.put(COL_LAST_TRIGGERED,  o.optLong(COL_LAST_TRIGGERED, 0));
            cv.put(COL_EXPIRES_AT,      o.optLong(COL_EXPIRES_AT, 0));
            cv.put(COL_PRIORITY,        o.optString(COL_PRIORITY, Alert.Priority.MEDIUM.name()));
            cv.put(COL_CONDITION_TYPE,  o.optString(COL_CONDITION_TYPE, ""));
            cv.put(COL_CONDITION_JSON,  o.optString(COL_CONDITION_JSON, "{}"));
            // Guard: an alert with no condition type can never be evaluated — reject it so it
            // doesn't poison getAllAlerts() (fromCursor would return null and drop it anyway).
            if (cv.getAsString(COL_CONDITION_TYPE).isEmpty()) return null;
            return cv;
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "valuesFromPayload failed", e);
            return null;
        }
    }

    private static String getStr(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        return (i >= 0 && !c.isNull(i)) ? c.getString(i) : null;
    }

    public void clear(){
        // Hard wipe — used on logout so the next user on this device starts clean (the
        // server is the source of truth and re-populates on the next login pull).
        db.getWritableDatabase().delete(TABLE_ALERTS, null, null);
        // Reset the auto-increment counter back to 0
        db.getWritableDatabase().delete("sqlite_sequence", "name = ?", new String[]{ TABLE_ALERTS });
    }
}
