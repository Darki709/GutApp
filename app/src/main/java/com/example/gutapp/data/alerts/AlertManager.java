package com.example.gutapp.data.alerts;

import com.example.gutapp.data.models.Candle;
import com.example.gutapp.database.AlertDBHelper;
import com.example.gutapp.database.StockDataHelper;

import android.util.Log;
import com.google.gson.Gson;
import java.util.*;
import java.util.concurrent.*;

public class AlertManager implements PriceResource {
    private static AlertManager instance;

    // Background Processing
    private final BlockingQueue<CandleUpdate> updateQueue = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<String, List<Alert>> activeAlerts = new ConcurrentHashMap<>();
    private final Set<String> subscribedSymbols = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Internal Cache for PriceResource
    private final ConcurrentHashMap<String, Candle> latestPrices = new ConcurrentHashMap<>();

    private StockDataHelper priceHelper;
    private AlertDBHelper alertHelper;
    private ServiceRequestInterface networkInterface;
    private volatile boolean isRunning = false;
    private Thread workerThread;

    public interface ServiceRequestInterface {
        void requestPriceData(String symbol);
        void stopPriceData(String symbol);
    }

    private AlertManager() {}

    public static synchronized AlertManager getInstance() {
        if (instance == null) {
            instance = new AlertManager();
        }
        return instance;
    }

    /**
     * Initializes the manager and starts the background worker thread.
     */
    public void start(StockDataHelper helper, List<Alert> initialAlerts) {
        if (isRunning) return;
        // this.dbHelper = helper;
        this.isRunning = true;

        // Load initial alerts into memory
        for (Alert alert : initialAlerts) {
            addAlertToMap(alert);
        }

        workerThread = new Thread(this::processLoop, "AlertManagerThread");
        workerThread.start();

        // Initial sync of subscriptions
        refreshSubscriptions();
    }

    public void setNetworkInterface(ServiceRequestInterface networkInterface) {
        this.networkInterface = networkInterface;
    }

    /**
     * Producer: Called by NetworkService when a price packet arrives.
     */
    public void onNewPrice(String symbol, Candle candle) {
        updateQueue.offer(new CandleUpdate(symbol, candle));
    }

    /**
     * Consumer: The background loop that evaluates logic and updates DB.
     */
    private void processLoop() {
        while (isRunning) {
            try {
                CandleUpdate update = updateQueue.take();

                // 1. Update In-Memory Cache for PriceResource
                latestPrices.put(update.symbol, update.candle);

                // 2. Persist to SQLite to keep local data fresh
                priceHelper.saveStockData(update.symbol, StockDataHelper.Timeframe.ONE_MIN ,new ArrayList<>(List.of(update.candle)));

                // 3. Evaluate Alerts
                evaluateAlerts(update.symbol);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Log.e("AlertManager", "Error in process loop", e);
            }
        }
    }

    private void evaluateAlerts(String symbol) {
        List<Alert> alerts = activeAlerts.get(symbol);
        if (alerts == null) return;

        Iterator<Alert> iterator = alerts.iterator();
        while (iterator.hasNext()) {
            Alert alert = iterator.next();

            if (alert.getStatus() == Alert.Status.ACTIVE && alert.getCondition().check(this)) {
                // Logic triggered!
                handleTrigger(alert);

                // Update status to prevent double-firing
                alert.setStatus(Alert.Status.TRIGGERED);
               // AlertDBHelper.updateAlertStatus(alert); // Persist status change

                // Remove from active monitoring if you only want it to fire once
                iterator.remove();
            }
        }
    }

    public synchronized void refreshSubscriptions() {
        if (networkInterface == null) return;

        Set<String> requiredSymbols = activeAlerts.keySet();

        for (String symbol : requiredSymbols) {
            if (!subscribedSymbols.contains(symbol)) {
                networkInterface.requestPriceData(symbol);
                subscribedSymbols.add(symbol);
            }
        }
    }

    private void addAlertToMap(Alert alert) {
        activeAlerts.computeIfAbsent(alert.getSymbol(), k -> new CopyOnWriteArrayList<>()).add(alert);
    }

    public void stop() {
        isRunning = false;
        if (workerThread != null) workerThread.interrupt();
        updateQueue.clear();
        latestPrices.clear();
        subscribedSymbols.clear();
    }

    // --- PriceResource Implementation ---

    @Override
    public Candle getLatestPrice(String symbol) {
        return latestPrices.get(symbol);
    }

    @Override
    public List<Candle> getCachedStockData(String symbol, StockDataHelper.Timeframe timeframe, int count) {
        // Direct query to local DB for historical OHLCV data
        return null; //dbHelper.getStockData(symbol, timeframe, count);
    }

    private void handleTrigger(Alert alert) {
        Log.i("AlertManager", "ALERT TRIGGERED: " + alert.getNotification());
        // Call your NotificationHelper here to show the UI alert
    }

    // Simple helper class for the queue
    private static class CandleUpdate {
        String symbol;
        Candle candle;
        CandleUpdate(String s, Candle c) { this.symbol = s; this.candle = c; }
    }
}

