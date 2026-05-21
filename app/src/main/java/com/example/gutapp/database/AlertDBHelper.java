package com.example.gutapp.database;

import static com.example.gutapp.database.DB_Helper.DB_LOG_TAG;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import com.example.gutapp.data.alerts.Alert;
import com.example.gutapp.data.alerts.Condition;
import com.example.gutapp.data.alerts.ConditionRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * AlertDBHelper — all SQLite persistence for the alert system.
 *
 * Schema stores each Alert as one row. The Condition is split into
 * two columns: a stable type-key (used by ConditionRegistry to pick the
 * right deserializer) and a JSON payload holding the condition's parameters.
 *
 * This avoids fragile class-name storage and survives package refactors.
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

    private final DB_Helper db;

    public AlertDBHelper(DB_Helper db) {
        this.db = db;
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
                COL_CONDITION_JSON + " TEXT NOT NULL)";
    }

    // ── Insert ────────────────────────────────────────────────────────
    /**
     * Persists a new alert and assigns the generated DB ID back to the object.
     */
    public void insertAlert(Alert alert) {
        try {
            long id = db.getWritableDatabase().insert(
                    TABLE_ALERTS, null, toValues(alert));
            alert.setId(id);
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "insertAlert failed", e);
        }
    }

    // ── Queries ───────────────────────────────────────────────────────
    /**
     * Returns all ACTIVE alerts (loaded at start-up into AlertManager).
     */
    public List<Alert> getActiveAlerts() {
        return queryAlerts(COL_STATUS + "=?",
                new String[]{Alert.Status.ACTIVE.name()});
    }

    /**
     * Returns every alert for a given symbol (for the management UI).
     */
    public List<Alert> getAlertsForSymbol(String symbol) {
        return queryAlerts(COL_SYMBOL + "=?", new String[]{symbol});
    }

    /**
     * Returns all alerts regardless of status (for the global alerts list UI).
     */
    public List<Alert> getAllAlerts() {
        return queryAlerts(null, null);
    }

    // ── Updates ───────────────────────────────────────────────────────
    /**
     * Persists every mutable field of an Alert after it triggers or
     * is edited by the user. Uses _id as the key.
     */
    public void updateAlert(Alert alert) {
        if (alert.getId() < 0) {
            Log.w(DB_LOG_TAG, "updateAlert called on unpersisted alert — inserting instead");
            insertAlert(alert);
            return;
        }
        try {
            db.getWritableDatabase().update(
                    TABLE_ALERTS, toValues(alert),
                    COL_ID + "=?",
                    new String[]{String.valueOf(alert.getId())});
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "updateAlert failed for id=" + alert.getId(), e);
        }
    }

    /** Convenience: only write the status + trigger timestamp columns. */
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

    // ── Delete ────────────────────────────────────────────────────────
    public void deleteAlert(Alert alert) {
        if (alert.getId() < 0) return;
        try {
            db.getWritableDatabase().delete(
                    TABLE_ALERTS,
                    COL_ID + "=?",
                    new String[]{String.valueOf(alert.getId())});
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "deleteAlert failed", e);
        }
    }

    public void deleteAllForSymbol(String symbol) {
        try {
            db.getWritableDatabase().delete(
                    TABLE_ALERTS,
                    COL_SYMBOL + "=?",
                    new String[]{symbol});
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "deleteAllForSymbol failed", e);
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

            return new Alert(
                    id, symbol, label, condition,
                    Alert.Status.valueOf(statusStr),
                    Alert.RepeatMode.valueOf(repeatStr),
                    cooldown, lastTriggered, expiresAt,
                    Alert.Priority.valueOf(priorityStr));
        } catch (Exception e) {
            Log.e(DB_LOG_TAG, "fromCursor failed", e);
            return null;
        }
    }

    public void clear(){
        // 1. Delete all rows from your table
        db.getWritableDatabase().delete(TABLE_ALERTS, null, null);
        // 2. Reset the auto-increment counter back to 0
        db.getWritableDatabase().delete("sqlite_sequence", "name = ?", new String[]{ TABLE_ALERTS });
    }
}
