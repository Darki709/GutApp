package com.example.gutapp.data.alerts;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.gutapp.GutApp;
import com.example.gutapp.data.models.Candle;
import com.example.gutapp.database.AlertDBHelper;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.StockDataHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

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
    public interface ServiceRequestInterface {
        void requestPriceData(String symbol);
        void stopPriceData(String symbol);
    }

    public interface TriggerListener {
        void onAlertTriggered(Alert alert);
    }

    // ── State ─────────────────────────────────────────────────────────
    private final BlockingQueue<CandleUpdate> updateQueue = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Alert>> activeAlerts = new ConcurrentHashMap<>();
    private final Set<String> subscriptions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ConcurrentHashMap<String, Candle> latestPrices = new ConcurrentHashMap<>();

    // alertDb is initialised lazily via ensureDb() so addAlert() works
    // even before start() is called (e.g. from AlertsActivity directly).
    private AlertDBHelper alertDb = null;
    private StockDataHelper stockDb;
    private ServiceRequestInterface networkInterface;
    private TriggerListener triggerListener;
    private Context appContext;

    private volatile boolean isRunning = false;
    private Thread workerThread;


    // ── Cooldown Configurations ──────────────────────────────────────
    private static final long RECONNECT_COOLDOWN_MS = 5000; // 5-second hard safety limit
    private long lastReconnectTimestamp = 0;

    // Backup debounce handler to ensure the last dropped reconnection signal still fires safely
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingReconnectRunnable = null;

    // ── Lazy DB init ──────────────────────────────────────────────────
    /** Ensures alertDb/stockDb are initialised. Must be called on any path that touches the DB. */
    private synchronized void ensureDb() {
        if (alertDb != null) return;
        if (appContext == null) {
            Log.e(TAG, "ensureDb: no context — call init(context) first");
            return;
        }
        DB_Helper dbHelper = DB_Helper.getInstance(appContext);
        alertDb = new AlertDBHelper(dbHelper);
        stockDb = new StockDataHelper(dbHelper);
    }

    /**
     * Must be called once (e.g. from GutApplication or NetworkService.onCreate)
     * before any DB operations. Safe to call multiple times.
     */
    public synchronized void init(Context context) {
        if (appContext == null) appContext = context.getApplicationContext();
        ensureDb();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────
    public synchronized void start(DB_Helper dbHelper) {
        if (isRunning) return;
        if (alertDb == null) {
            alertDb = new AlertDBHelper(dbHelper);
            stockDb = new StockDataHelper(dbHelper);
        }
        isRunning = true;

        List<Alert> active = alertDb.getActiveAlerts();
        for (Alert a : active) addToMemory(a);

        workerThread = new Thread(this::processLoop, "AlertManagerWorker");
        workerThread.setDaemon(true);
        workerThread.start();

        refreshSubscriptions();
        Log.i(TAG, "Started with " + active.size() + " active alert(s)");
    }

    public synchronized void stop() {
        isRunning = false;
        if (workerThread != null) { workerThread.interrupt(); workerThread = null; }
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
    public synchronized void addAlert(Alert alert) {
        ensureDb();
        if (alertDb == null) { Log.e(TAG, "addAlert: DB not ready"); return; }
        alertDb.insertAlert(alert);
        if (alert.getStatus() == Alert.Status.ACTIVE) {
            addToMemory(alert);
            refreshSubscriptions();
        }
        Log.i(TAG, "Added alert id=" + alert.getId() + " for " + alert.getSymbol());
    }

    public synchronized void removeAlert(long alertId) {
        ensureDb();
        if (alertDb == null) return;
        for (CopyOnWriteArrayList<Alert> list : activeAlerts.values())
            list.removeIf(a -> a.getId() == alertId);
        for (Alert a : alertDb.getAllAlerts()) {
            if (a.getId() == alertId) { alertDb.deleteAlert(a); break; }
        }
        cleanupSubscriptions();
    }

    public synchronized void setAlertStatus(long alertId, Alert.Status newStatus) {
        ensureDb();
        if (alertDb == null) return;
        for (Alert a : alertDb.getAllAlerts()) {
            if (a.getId() == alertId) {
                a.setStatus(newStatus);
                alertDb.updateAlertStatus(a);
                if (newStatus == Alert.Status.ACTIVE) { addToMemory(a); refreshSubscriptions(); }
                else { removeFromMemory(alertId); cleanupSubscriptions(); }
                break;
            }
        }
    }

    public List<Alert> getAllAlerts() {
        ensureDb();
        return alertDb != null ? alertDb.getAllAlerts() : new ArrayList<>();
    }

    public List<Alert> getAlertsForSymbol(String symbol) {
        ensureDb();
        return alertDb != null ? alertDb.getAlertsForSymbol(symbol) : new ArrayList<>();
    }

    // ── Price feed ────────────────────────────────────────────────────
    public void onNewPrice(String symbol, Candle candle) {
        updateQueue.offer(new CandleUpdate(symbol, candle));
    }

    // ── Worker loop ───────────────────────────────────────────────────
    private void processLoop() {
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                CandleUpdate update = updateQueue.take();
                latestPrices.put(update.symbol, update.candle);
                evaluateAlerts(update.symbol);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Log.e(TAG, "Error in process loop", e);
            }
        }
    }

    private void evaluateAlerts(String symbol) {
        CopyOnWriteArrayList<Alert> list = activeAlerts.get(symbol);
        if (list == null || list.isEmpty()) return;
        for (Alert alert : list) {
            if (!alert.isEvaluatable()) {
                if (alert.getStatus() == Alert.Status.TRIGGERED && alert.getRepeatMode() == Alert.RepeatMode.ONCE)
                    list.remove(alert);
                continue;
            }
            try {
                if (alert.getCondition().check(this)) {
                    alert.onTriggered();
                    if (alertDb != null) alertDb.updateAlertStatus(alert);
                    if (triggerListener != null) triggerListener.onAlertTriggered(alert);
                    Log.i(TAG, "TRIGGERED: " + alert.getNotification());
                    if (alert.getRepeatMode() == Alert.RepeatMode.ONCE) list.remove(alert);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking alert id=" + alert.getId(), e);
            }
        }
    }

    // ── PriceResource ─────────────────────────────────────────────────
    @Override public Candle getLatestPrice(String symbol) { return latestPrices.get(symbol); }

    @Override
    public List<Candle> getCachedStockData(String symbol, StockDataHelper.Timeframe timeframe, int count) {
        if (stockDb == null) { ensureDb(); }
        return stockDb != null ? stockDb.getStockData(symbol, timeframe, count) : new ArrayList<>();
    }

    // ── Subscription management ───────────────────────────────────────
    private synchronized void refreshSubscriptions() {
        if (networkInterface == null) return;
        for (String symbol : activeAlerts.keySet()) {
            if (!subscriptions.contains(symbol) && !activeAlerts.get(symbol).isEmpty()) {
                networkInterface.requestPriceData(symbol);
                subscriptions.add(symbol);
            }
        }
    }

    /**
     * Attempts a network reconnection.
     * Implements a rate-limiting cooldown and trailing debounce to prevent abuse
     * while guaranteeing that the client ultimately resubscribes once the network stabilizes.
     */
    public synchronized void networkReconnect() {
        long currentTime = System.currentTimeMillis();
        long timeElapsed = currentTime - lastReconnectTimestamp;

        // 1. Cancel any trailing reconnect task that was previously queued
        if (pendingReconnectRunnable != null) {
            debounceHandler.removeCallbacks(pendingReconnectRunnable);
            pendingReconnectRunnable = null;
        }

        // 2. If the request happens within the cooldown window, debounce it
        if (timeElapsed < RECONNECT_COOLDOWN_MS) {
            long remainingDelay = RECONNECT_COOLDOWN_MS - timeElapsed;

            // Queue a delayed execution so the final connection state change is never permanently ignored
            pendingReconnectRunnable = () -> {
                synchronized (AlertManager.this) {
                    executeRefresh();
                }
            };
            debounceHandler.postDelayed(pendingReconnectRunnable, remainingDelay);
            return;
        }

        // 3. Safe to execute immediately if outside the cooldown boundary
        executeRefresh();
    }

    private void executeRefresh() {
        lastReconnectTimestamp = System.currentTimeMillis();
        pendingReconnectRunnable = null;

        Log.d(TAG, "Refreshing subscriptions...");
        //starting the alert manager again
        start(DB_Helper.getInstance(GutApp.getInstance().getApplicationContext()));
    }

    public void networkLost(){
        Log.d(TAG, "Network lost, stopping alert manager to save resources");
        stop();
    }

    private synchronized void cleanupSubscriptions() {
        if (networkInterface == null) return;
        Iterator<String> it = subscriptions.iterator();
        while (it.hasNext()) {
            String sym = it.next();
            CopyOnWriteArrayList<Alert> list = activeAlerts.get(sym);
            if (list == null || list.isEmpty()) { networkInterface.stopPriceData(sym); it.remove(); }
        }
    }

    private void addToMemory(Alert alert) {
        activeAlerts.computeIfAbsent(alert.getSymbol(), k -> new CopyOnWriteArrayList<>()).add(alert);
    }

    private void removeFromMemory(long alertId) {
        for (CopyOnWriteArrayList<Alert> list : activeAlerts.values())
            list.removeIf(a -> a.getId() == alertId);
    }

    private static class CandleUpdate {
        final String symbol; final Candle candle;
        CandleUpdate(String s, Candle c) { symbol = s; candle = c; }
    }
}
