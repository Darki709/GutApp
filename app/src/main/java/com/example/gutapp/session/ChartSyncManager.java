package com.example.gutapp.session;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.gutapp.database.ChartStateDao;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.session.Requests.SyncChartPull;
import com.example.gutapp.session.Requests.SyncChartPush;

import java.util.List;

/**
 * ChartSyncManager — keeps the local {@link ChartStateDao} cache (drawings, indicators,
 * presets) in sync with the per-user server copy. Automatic, per-symbol last-write-wins:
 *
 *  - {@link #pullAll()}   : download the user's server rows, merge LWW into the cache,
 *                           then push anything still locally newer.
 *  - {@link #schedulePush()} : debounced upload of dirty rows; wired to every local DAO
 *                           write via {@link ChartStateDao#setLocalChangeListener}.
 *  - {@link #flush()}     : push immediately (e.g. when leaving a chart).
 *
 * It acts as the per-request {@link SessionCallback} so responses route back here.
 * Network sends go through the existing async {@code SessionManager} queue, so failures
 * degrade gracefully — dirty rows simply stay dirty and retry on the next trigger.
 */
public class ChartSyncManager implements SessionCallback {

    private static final String TAG = "ChartSync";
    private static final long PUSH_DEBOUNCE_MS = 1500;

    private static ChartSyncManager instance;

    private final Context appCtx;
    private final ChartStateDao dao;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable pushTask = this::pushNow;
    @Nullable private Runnable remoteChangeListener;

    private ChartSyncManager(Context ctx) {
        this.appCtx = ctx.getApplicationContext();
        this.dao = new ChartStateDao(DB_Helper.getInstance(appCtx));
        // Any local drawing/indicator/preset write now schedules a push.
        ChartStateDao.setLocalChangeListener(this::schedulePush);
    }

    public static synchronized ChartSyncManager init(Context ctx) {
        if (instance == null) instance = new ChartSyncManager(ctx);
        return instance;
    }

    @Nullable public static ChartSyncManager get() { return instance; }

    /**
     * Register a callback fired (on the main thread) after a pull actually changed the
     * local cache, so an open chart can reload. Pass null to unregister.
     */
    public void setRemoteChangeListener(@Nullable Runnable r) { this.remoteChangeListener = r; }

    // ── Pull ──────────────────────────────────────────────────────────
    /** Request all of the user's chart-state rows from the server. */
    public void pullAll() {
        try {
            NetworkClient.getInstance(appCtx).getSessionManager()
                    .pushRequest(new SyncChartPull(this));
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
        List<ChartStateDao.Row> dirty = dao.getDirty();
        if (dirty.isEmpty()) return;
        try {
            NetworkClient.getInstance(appCtx).getSessionManager()
                    .pushRequest(new SyncChartPush(dirty, this));
        } catch (Exception e) {
            Log.e(TAG, "push enqueue failed", e);
        }
    }

    // ── Response handling (per-request callback) ─────────────────────────
    @Override
    @SuppressWarnings("unchecked")
    public void onDataReceived(DataType type, Object data) {
        switch (type) {
            case CHART_SYNC_PULLED: {
                boolean changed = false;
                if (data instanceof List) {
                    for (ChartStateDao.Row r : (List<ChartStateDao.Row>) data) {
                        changed |= dao.applyRemote(r.kind, r.key, r.payload, r.updatedAt, r.deleted);
                    }
                }
                // Upload anything that stayed locally newer than the server copy.
                schedulePush();
                if (changed && remoteChangeListener != null) {
                    main.post(() -> {
                        Runnable r = remoteChangeListener;
                        if (r != null) r.run();
                    });
                }
                break;
            }
            case CHART_SYNC_PUSHED: {
                if (data instanceof List) {
                    for (ChartStateDao.Row r : (List<ChartStateDao.Row>) data) {
                        dao.markSynced(r.kind, r.key, r.updatedAt);
                    }
                }
                break;
            }
            case CHART_SYNC_ERROR:
                // Non-fatal: dirty rows remain and retry on the next trigger.
                Log.w(TAG, "chart sync error: " + data);
                break;
            default:
                break;
        }
    }

    @Override
    public void onActionRequired(int actionType, @Nullable Object data) { /* not used */ }
}
