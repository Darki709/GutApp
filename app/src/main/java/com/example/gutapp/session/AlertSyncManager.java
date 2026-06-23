package com.example.gutapp.session;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.gutapp.data.alerts.AlertManager;
import com.example.gutapp.database.AlertDBHelper;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.session.Requests.SyncAlertPull;
import com.example.gutapp.session.Requests.SyncAlertPush;

import java.util.List;

/**
 * AlertSyncManager — keeps the local {@link AlertDBHelper} alert store in sync with the
 * per-user server copy so a user's alerts survive logout and follow them across devices.
 * Per-alert last-write-wins, keyed by the alert UUID. Mirrors {@link ChartSyncManager}.
 *
 *  - {@link #pullAll()}      : ON-DEMAND download of the user's server alerts (call when
 *                              entering Home / opening the alerts screen), merged LWW into
 *                              the local store, then push anything still locally newer.
 *  - {@link #schedulePush()} : EVENT-DRIVEN upload of dirty rows; wired to every local
 *                              AlertDBHelper write via {@link AlertDBHelper#setLocalChangeListener},
 *                              so a user edit uploads as soon as it happens (a short debounce
 *                              only coalesces a rapid burst). Automatic trigger-state writes
 *                              are NOT marked dirty, so background firing never spams the server.
 *  - {@link #flush()}        : push immediately (e.g. when leaving a screen).
 *
 * Network sends go through the existing async {@code SessionManager} queue, so failures
 * degrade gracefully — dirty rows stay dirty and retry on the next trigger. The alert
 * system keeps working entirely from the local store if the server is unreachable.
 */
public class AlertSyncManager implements SessionCallback {

    private static final String TAG = "AlertSync";
    private static final long PUSH_DEBOUNCE_MS = 250;

    private static AlertSyncManager instance;

    private final Context appCtx;
    private final AlertDBHelper dao;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable pushTask = this::pushNow;
    @Nullable private Runnable remoteChangeListener;

    private AlertSyncManager(Context ctx) {
        this.appCtx = ctx.getApplicationContext();
        this.dao = new AlertDBHelper(DB_Helper.getInstance(appCtx));
        // Any local alert add/edit/toggle/delete now schedules a push.
        AlertDBHelper.setLocalChangeListener(this::schedulePush);
    }

    public static synchronized AlertSyncManager init(Context ctx) {
        if (instance == null) instance = new AlertSyncManager(ctx);
        return instance;
    }

    @Nullable public static AlertSyncManager get() { return instance; }

    /**
     * Register a callback fired (on the main thread) after a pull actually changed the local
     * store, so an open screen (alerts list, drawer badge) can refresh. null to unregister.
     */
    public void setRemoteChangeListener(@Nullable Runnable r) { this.remoteChangeListener = r; }

    // ── Pull (manual / on-demand) ──────────────────────────────────────
    public void pullAll() {
        try {
            NetworkClient.getInstance(appCtx).getSessionManager()
                    .pushRequest(new SyncAlertPull(this));
        } catch (Exception e) {
            Log.e(TAG, "pullAll enqueue failed", e);
        }
    }

    // ── Push (debounced) ────────────────────────────────────────────────
    public void schedulePush() {
        main.removeCallbacks(pushTask);
        main.postDelayed(pushTask, PUSH_DEBOUNCE_MS);
    }

    /** Push any dirty rows right now (e.g. from Activity.onPause). */
    public void flush() {
        main.removeCallbacks(pushTask);
        pushNow();
    }

    private void pushNow() {
        List<AlertDBHelper.SyncRow> dirty = dao.getDirty();
        if (dirty.isEmpty()) return;
        try {
            NetworkClient.getInstance(appCtx).getSessionManager()
                    .pushRequest(new SyncAlertPush(dirty, this));
        } catch (Exception e) {
            Log.e(TAG, "push enqueue failed", e);
        }
    }

    // ── Response handling (per-request callback) ─────────────────────────
    @Override
    @SuppressWarnings("unchecked")
    public void onDataReceived(DataType type, Object data) {
        switch (type) {
            case ALERT_SYNC_PULLED: {
                boolean changed = false;
                if (data instanceof List) {
                    for (AlertDBHelper.SyncRow r : (List<AlertDBHelper.SyncRow>) data) {
                        changed |= dao.applyRemote(r.uuid, r.payload, r.updatedAt, r.deleted);
                    }
                }
                // Upload anything that stayed locally newer than the server copy.
                schedulePush();
                if (changed) {
                    // Rebuild the background evaluator's active set, then refresh any open UI.
                    try { AlertManager.getInstance().reloadActiveAlerts(); }
                    catch (Exception e) { Log.e(TAG, "reloadActiveAlerts failed", e); }
                    if (remoteChangeListener != null) {
                        main.post(() -> {
                            Runnable r = remoteChangeListener;
                            if (r != null) r.run();
                        });
                    }
                }
                break;
            }
            case ALERT_SYNC_PUSHED: {
                if (data instanceof List) {
                    for (AlertDBHelper.SyncRow r : (List<AlertDBHelper.SyncRow>) data) {
                        dao.markSynced(r.uuid, r.updatedAt);
                    }
                }
                break;
            }
            case ALERT_SYNC_ERROR:
                // Non-fatal: dirty rows remain and retry on the next trigger.
                Log.w(TAG, "alert sync error: " + data);
                break;
            default:
                break;
        }
    }

    @Override
    public void onActionRequired(int actionType, @Nullable Object data) { /* not used */ }
}
