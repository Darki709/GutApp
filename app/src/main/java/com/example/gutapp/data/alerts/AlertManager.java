package com.example.gutapp.data.alerts;

import android.util.Log;

import androidx.annotation.Nullable;

import com.example.gutapp.data.models.Candle;
import com.example.gutapp.database.AlertDBHelper;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.LastFetchCacheHelper;
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.RequestTickerData;
import com.example.gutapp.session.SessionCallback;
import com.example.gutapp.session.background.NetworkService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * AlertManager — singleton that ties together:
 *   - Live tick feed       (via ServiceRequestInterface / NetworkService)
 *   - Condition evaluation (on a dedicated worker thread, off the UI)
 *   - DB persistence       (via AlertDBHelper)
 *   - Notifications        (via TriggerListener callback → NetworkService)
 *
 * Thread model
 * ─────────────
 *   Main / Network threads → onNewPrice() → updateQueue (non-blocking offer)
 *   Worker thread          → drains queue, evaluates conditions, updates DB
 *   Any thread             → addAlert / removeAlert (thread-safe via synchronized)
 */
public class AlertManager implements PriceResource {

    private static final String TAG = "AlertManager";

    // ── Singleton ─────────────────────────────────────────────────────
    private static volatile AlertManager instance;

    public static AlertManager getInstance() {
        if (instance == null) {
            synchronized (AlertManager.class) {
                if (instance == null) instance = new AlertManager();
            }
        }
        return instance;
    }

    private AlertManager() {}

    // ── Interfaces ────────────────────────────────────────────────────

    /** NetworkService (or any transport layer) implements this so AlertManager
     *  can subscribe/unsubscribe to live price streams per symbol. */
    public interface ServiceRequestInterface {
        void requestPriceData(String symbol);
        void stopPriceData(String symbol);
    }

    /** Receives trigger events — typically NetworkService, which then posts
     *  the actual Android system notification. */
    public interface TriggerListener {
        void onAlertTriggered(Alert alert);
    }

    // ── State ─────────────────────────────────────────────────────────
    private final BlockingQueue<CandleUpdate>              updateQueue    = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Alert>> activeAlerts = new ConcurrentHashMap<>();
    private final Set<String>                              subscriptions  = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ConcurrentHashMap<String, Candle>        latestPrices   = new ConcurrentHashMap<>();

    private AlertDBHelper          alertDb;
    private StockDataHelper        stockDb;
    private ServiceRequestInterface networkInterface;
    private TriggerListener        triggerListener;

    private volatile boolean isRunning = false;
    private Thread workerThread;

    // ── Lifecycle ─────────────────────────────────────────────────────

    /**
     * Call once from NetworkService.onCreate() (or GutApplication) after the DB
     * is ready.  Loads all ACTIVE alerts from the DB and subscribes to their symbols.
     */
    public synchronized void start(DB_Helper dbHelper) {
        if (isRunning) return;

        alertDb = new AlertDBHelper(dbHelper);
        stockDb = new StockDataHelper(dbHelper);

        isRunning = true;

        // Load all active alerts from DB
        List<Alert> active = alertDb.getActiveAlerts();
        for (Alert a : active) addToMemory(a);

        // Start evaluation worker
        workerThread = new Thread(this::processLoop, "AlertManagerWorker");
        workerThread.setDaemon(true);
        workerThread.start();

        refreshSubscriptions();
        Log.i(TAG, "Started with " + active.size() + " active alert(s)");
    }

    public synchronized void stop() {
        isRunning = false;
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
        updateQueue.clear();
        latestPrices.clear();
        subscriptions.clear();
        activeAlerts.clear();
    }

    public void setNetworkInterface(ServiceRequestInterface iface) {
        this.networkInterface = iface;
        refreshSubscriptions();
    }

    public void setTriggerListener(TriggerListener listener) {
        this.triggerListener = listener;
    }

    // ── Public alert management ────────────────────────────────────────

    /**
     * Adds a new alert: persists to DB, loads into memory, subscribes to symbol.
     */
    public synchronized void addAlert(Alert alert) {
        alertDb.insertAlert(alert);          // assigns alert.id
        if (alert.getStatus() == Alert.Status.ACTIVE) {
            addToMemory(alert);
            refreshSubscriptions();
        }
        Log.i(TAG, "Added alert id=" + alert.getId() + " for " + alert.getSymbol());
    }

    /**
     * Removes an alert from memory and DB by its DB id.
     */
    public synchronized void removeAlert(long alertId) {
        for (CopyOnWriteArrayList<Alert> list : activeAlerts.values()) {
            list.removeIf(a -> a.getId() == alertId);
        }
        // We need the full alert to call deleteAlert — query DB
        List<Alert> all = alertDb.getAllAlerts();
        for (Alert a : all) {
            if (a.getId() == alertId) {
                alertDb.deleteAlert(a);
                break;
            }
        }
        cleanupSubscriptions();
    }

    /**
     * Pause/resume an alert. Persists the new status.
     */
    public synchronized void setAlertStatus(long alertId, Alert.Status newStatus) {
        List<Alert> all = alertDb.getAllAlerts();
        for (Alert a : all) {
            if (a.getId() == alertId) {
                a.setStatus(newStatus);
                alertDb.updateAlertStatus(a);
                if (newStatus == Alert.Status.ACTIVE) {
                    addToMemory(a);
                    refreshSubscriptions();
                } else {
                    removeFromMemory(alertId);
                    cleanupSubscriptions();
                }
                break;
            }
        }
    }

    /** Returns all alerts (any status) for UI display. */
    public List<Alert> getAllAlerts() {
        return alertDb != null ? alertDb.getAllAlerts() : new ArrayList<>();
    }

    /** Returns all alerts for a specific symbol. */
    public List<Alert> getAlertsForSymbol(String symbol) {
        return alertDb != null ? alertDb.getAlertsForSymbol(symbol) : new ArrayList<>();
    }

    // ── Price feed (producer side) ────────────────────────────────────

    /**
     * Called by the network layer (RequestTickerData / NetworkService) when a
     * new tick arrives.  Non-blocking — just enqueues and returns immediately.
     */
    public void onNewPrice(String symbol, Candle candle) {
        updateQueue.offer(new CandleUpdate(symbol, candle));
    }

    // ── Worker loop (consumer side) ───────────────────────────────────

    private void processLoop() {
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                CandleUpdate update = updateQueue.take(); // blocks until data arrives

                // 1. Refresh in-memory price cache
                latestPrices.put(update.symbol, update.candle);

                // 2. Evaluate all active alerts for this symbol
                evaluateAlerts(update.symbol);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Log.e(TAG, "Error in process loop", e);
            }
        }
        Log.i(TAG, "Worker thread exiting");
    }

    private void evaluateAlerts(String symbol) {
        CopyOnWriteArrayList<Alert> list = activeAlerts.get(symbol);
        if (list == null || list.isEmpty()) return;

        Iterator<Alert> it = list.iterator();
        while (it.hasNext()) {
            Alert alert = it.next();

            if (!alert.isEvaluatable()) {
                // ONCE mode + TRIGGERED → clean up memory (still in DB for history)
                if (alert.getStatus() == Alert.Status.TRIGGERED
                        && alert.getRepeatMode() == Alert.RepeatMode.ONCE) {
                    list.remove(alert);
                }
                continue;
            }

            try {
                if (alert.getCondition().check(this)) {
                    alert.onTriggered();
                    alertDb.updateAlertStatus(alert);

                    // Notify the UI layer
                    if (triggerListener != null) {
                        triggerListener.onAlertTriggered(alert);
                    }
                    Log.i(TAG, "TRIGGERED: " + alert.getNotification());

                    // Remove ONCE alerts from memory immediately
                    if (alert.getRepeatMode() == Alert.RepeatMode.ONCE) {
                        list.remove(alert);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking alert id=" + alert.getId(), e);
            }
        }
    }

    // ── PriceResource implementation ──────────────────────────────────

    @Override
    public Candle getLatestPrice(String symbol) {
        return latestPrices.get(symbol);
    }

    @Override
    public List<Candle> getCachedStockData(String symbol,
                                            StockDataHelper.Timeframe timeframe,
                                            int count) {
        if (stockDb == null) throw new IllegalStateException("StockDataHelper not initialized");
        //load the cache with new prices synchronously
        final CountDownLatch latch = new CountDownLatch(1);
        long last = (new LastFetchCacheHelper(DB_Helper.getInstance(null))).getLastFetchTime(symbol, timeframe);
        RequestTickerData request = new RequestTickerData(symbol, timeframe, last, 0, true, false, new SessionCallback() {
            @Override
            public void onDataReceived(DataType msgType, Object parsedData) {}

            @Override
            public void onActionRequired(int actionType, @Nullable Object data) {
                if(actionType == RequestTickerData.CACHE_END) latch.countDown();
            }
        });
        NetworkClient.getInstance(null).getSessionManager().pushRequest(request);
        try {
            latch.await();
        } catch (InterruptedException e) {
            Log.e(NetworkService.TAG, "Synchronous price request interrupted", e);
        }
        return stockDb.getStockData(symbol, timeframe, count);
    }

    // ── Subscription management ───────────────────────────────────────

    private synchronized void refreshSubscriptions() {
        if (networkInterface == null) return;
        for (String symbol : activeAlerts.keySet()) {
            if (!subscriptions.contains(symbol)
                    && !activeAlerts.get(symbol).isEmpty()) {
                networkInterface.requestPriceData(symbol);
                subscriptions.add(symbol);
                Log.i(TAG, "Subscribed to stream for " + symbol);
            }
        }
    }

    /** Unsubscribes from symbols that no longer have any active alerts. */
    private synchronized void cleanupSubscriptions() {
        if (networkInterface == null) return;
        Iterator<String> it = subscriptions.iterator();
        while (it.hasNext()) {
            String sym = it.next();
            CopyOnWriteArrayList<Alert> list = activeAlerts.get(sym);
            if (list == null || list.isEmpty()) {
                networkInterface.stopPriceData(sym);
                it.remove();
                Log.i(TAG, "Unsubscribed from stream for " + sym);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void addToMemory(Alert alert) {
        activeAlerts
            .computeIfAbsent(alert.getSymbol(), k -> new CopyOnWriteArrayList<>())
            .add(alert);
    }

    private void removeFromMemory(long alertId) {
        for (CopyOnWriteArrayList<Alert> list : activeAlerts.values()) {
            list.removeIf(a -> a.getId() == alertId);
        }
    }

    private static class CandleUpdate {
        final String symbol;
        final Candle candle;
        CandleUpdate(String s, Candle c) { this.symbol = s; this.candle = c; }
    }
}
