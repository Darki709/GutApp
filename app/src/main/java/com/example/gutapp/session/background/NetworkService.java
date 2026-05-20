package com.example.gutapp.session.background;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.gutapp.R;
import com.example.gutapp.data.alerts.Alert;
import com.example.gutapp.data.alerts.AlertManager;
import com.example.gutapp.data.models.Candle;
import com.example.gutapp.data.models.PriceChunk;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.CancelTickerStream;
import com.example.gutapp.session.Requests.RequestTickerData;
import com.example.gutapp.session.SessionCallback;
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.ui.AlertsActivity;

import java.util.concurrent.ConcurrentHashMap;

/**
 * NetworkService — Android foreground service that:
 *  1. Keeps the network connection alive while the app is backgrounded.
 *  2. Bridges the live price stream to AlertManager.onNewPrice().
 *  3. Implements AlertManager.ServiceRequestInterface so AlertManager can
 *     subscribe/unsubscribe to symbols without knowing anything about networking.
 *  4. Implements AlertManager.TriggerListener to post system notifications
 *     when an alert fires.
 *
 * Lifecycle
 * ─────────
 *  Start:  startForegroundService(intent) from SessionActivity.onStart()
 *  Stop:   stopService() / service killed by OS
 *
 * The service starts AlertManager once the DB is ready, passing itself as
 * the ServiceRequestInterface and TriggerListener.
 */
public class NetworkService extends Service
        implements SessionCallback,
                   AlertManager.ServiceRequestInterface,
                   AlertManager.TriggerListener {

    public static final String TAG        = "NetworkService";
    private static final String CHANNEL_ID = "StockAlertChannel";
    private static final int    FG_NOTIF_ID = 101;

    // Maps symbol → the reqId of the streaming RequestTickerData, so we can cancel it.
    private final ConcurrentHashMap<String, byte[]> activeStreamReqIds = new ConcurrentHashMap<>();

    // ── Service lifecycle ─────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // Wire AlertManager
        AlertManager am = AlertManager.getInstance();
        am.setNetworkInterface(this);
        am.setTriggerListener(this);
        am.start(DB_Helper.getInstance(this));

        // Register as the push callback so TICKER_STREAM packets reach us
        NetworkClient.getInstance(this).getSessionManager().setPushResponseCallback(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(FG_NOTIF_ID, buildForegroundNotification());
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AlertManager.getInstance().stop();
        NetworkClient.getInstance(this).getSessionManager().setPushResponseCallback(null);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ── SessionCallback — receives ALL data from the server ───────────

    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {
        if (msgType == DataType.TICKER_STREAM && parsedData instanceof PriceChunk) {
            PriceChunk chunk = (PriceChunk) parsedData;
            if (!chunk.chunk.isEmpty()) {
                Candle latest = chunk.chunk.get(chunk.chunk.size() - 1);
                // Forward every tick to AlertManager for evaluation
                // We don't know which symbol this chunk belongs to from PriceChunk alone,
                // so we route by tracking which symbols have active alert subscriptions.
                // The reqId stored in activeStreamReqIds lets us correlate chunk → symbol.
                String symbol = findSymbolByReqId( chunk.reqId);
                if (symbol != null) {
                    AlertManager.getInstance().onNewPrice(symbol, latest);
                }
            }
        }
        // All other data types are handled by the foreground Activity's SessionCallback.
        // NetworkService intentionally ignores them to avoid double-processing.
    }

    @Override
    public void onActionRequired(int actionType, @Nullable Object data) {
        // Forwarded to whichever Activity is current — not handled by the service.
    }

    // ── ServiceRequestInterface — called by AlertManager on worker thread ──

    @Override
    public void requestPriceData(String symbol) {
        // Push a persistent stream request for this symbol.
        // startTs=0, endTs=0 → server sends only live ticks.
        RequestTickerData req = new RequestTickerData(
                symbol,
                StockDataHelper.Timeframe.ONE_MIN,
                0L, 0L,
                false, true,   // no snapshot, stream only
                this);

        NetworkClient.getInstance(this).getSessionManager().pushRequest(req);

        // Store reqId so we can cancel later (reqId is set after getBytes() is called
        // during send — we store it right after construction because AsyncRequest
        // assigns reqId in the constructor).
        activeStreamReqIds.put(symbol, req.reqId);
        Log.i(TAG, "Requested stream for " + symbol);
    }

    @Override
    public void stopPriceData(String symbol) {
        byte[] reqId = activeStreamReqIds.remove(symbol);
        if (reqId != null) {
            NetworkClient.getInstance(this).getSessionManager()
                    .pushRequest(new CancelTickerStream(reqId, symbol));
            Log.i(TAG, "Cancelled stream for " + symbol);
        }
    }

    // ── TriggerListener — called by AlertManager worker thread ────────

    @Override
    public void onAlertTriggered(Alert alert) {
        showAlertNotification(alert);
    }

    // ── Notification helpers ──────────────────────────────────────────

    private Notification buildForegroundNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Market Monitor Active")
                .setContentText("Gut is watching your alerts 📈")
                .setSmallIcon(R.drawable.logo)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void showAlertNotification(Alert alert) {
        // Tap notification → open AlertsActivity
        Intent intent = new Intent(this, AlertsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, (int) alert.getId(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int importance = alert.getPriority() == Alert.Priority.HIGH
                ? NotificationCompat.PRIORITY_HIGH
                : alert.getPriority() == Alert.Priority.LOW
                        ? NotificationCompat.PRIORITY_LOW
                        : NotificationCompat.PRIORITY_DEFAULT;

        Notification notif = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🔔 " + alert.getLabel())
                .setContentText(alert.getNotification())
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setPriority(importance)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();

        NotificationManager nm = getSystemService(NotificationManager.class);
        // Use a unique ID per alert so multiple alerts can stack
        nm.notify((int) (alert.getId() & 0x7FFFFFFF), notif);
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Stock Alerts",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("GutApp price alerts and monitoring");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private String findSymbolByReqId(int reqIdInt) {
        for (ConcurrentHashMap.Entry<String, byte[]> e : activeStreamReqIds.entrySet()) {
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(e.getValue());
            if (buf.getInt() == reqIdInt) return e.getKey();
        }
        return null;
    }
}
