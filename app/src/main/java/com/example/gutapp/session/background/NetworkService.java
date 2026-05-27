package com.example.gutapp.session.background;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
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
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.CancelTickerStream;
import com.example.gutapp.session.Requests.RequestTickerData;
import com.example.gutapp.session.SessionCallback;
import com.example.gutapp.ui.AlertsActivity;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkService extends Service
        implements SessionCallback,
                   AlertManager.ServiceRequestInterface,
                   AlertManager.TriggerListener {

    private static final String TAG        = "NetworkService";
    private static final String CHANNEL_ID = "StockAlertChannel";
    private static final int    FG_ID      = 101;

    // symbol → integer reqId of the live stream request
    private final ConcurrentHashMap<String, Integer> streamReqIds = new ConcurrentHashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        AlertManager am = AlertManager.getInstance();
        am.init(this);                          // ensure DB context is set
        am.setNetworkInterface(this);
        am.setTriggerListener(this);
        am.start(DB_Helper.getInstance(this));  // load active alerts, start worker

        // Register as the push/stream callback so TICKER_STREAM arrives here
        NetworkClient.getInstance(this).getSessionManager().setPushResponseCallback(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(FG_ID, buildForegroundNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AlertManager.getInstance().stop();
    }

    @Nullable @Override public IBinder onBind(Intent i) { return null; }

    // ── SessionCallback ───────────────────────────────────────────────
    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {
        if (msgType == DataType.TICKER_STREAM && parsedData instanceof PriceChunk) {
            PriceChunk chunk = (PriceChunk) parsedData;
            if (chunk.chunk != null && !chunk.chunk.isEmpty()) {
                Candle latest = chunk.chunk.get(chunk.chunk.size() - 1);
                // Correlate reqId → symbol so AlertManager gets the right symbol
                String symbol = findSymbolByReqId(chunk.reqId);
                if (symbol != null) {
                    AlertManager.getInstance().onNewPrice(symbol, latest);
                }
            }
        }
    }

    @Override public void onActionRequired(int actionType, @Nullable Object data) {
        if(actionType == 0) AlertManager.getInstance().networkReconnect();
        if(actionType == 1) AlertManager.getInstance().networkLost();
    }

    // ── ServiceRequestInterface ───────────────────────────────────────
    @Override
    public void requestPriceData(String symbol) {
        RequestTickerData req = new RequestTickerData(
                symbol, StockDataHelper.Timeframe.ONE_MIN,
                0L, 0L, false, true, this);
        NetworkClient.getInstance(this).getSessionManager().pushRequest(req);
        // Store the integer reqId for correlation in onDataReceived
        streamReqIds.put(symbol, req.getReqId());
        Log.i(TAG, "Requested stream for " + symbol);
    }

    @Override
    public void stopPriceData(String symbol) {
        Integer reqId = streamReqIds.remove(symbol);
        if (reqId != null) {
            // Convert int back to 4-byte array for CancelTickerStream
            byte[] reqIdBytes = ByteBuffer.allocate(4).putInt(reqId).array();
            NetworkClient.getInstance(this).getSessionManager()
                    .pushRequest(new CancelTickerStream(reqIdBytes, symbol));
            Log.i(TAG, "Cancelled stream for " + symbol);
        }
    }

    // ── TriggerListener ───────────────────────────────────────────────
    @Override
    public void onAlertTriggered(Alert alert) {
        showAlertNotification(alert);
    }

    // ── Notification helpers ──────────────────────────────────────────
    private Notification buildForegroundNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Market Monitor Active")
                .setContentText("Gut is watching your back 📈")
                .setSmallIcon(R.drawable.logo)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void showAlertNotification(Alert alert) {
        Intent i = new Intent(this, AlertsActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, (int) alert.getId(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int priority = alert.getPriority() == Alert.Priority.HIGH
                ? NotificationCompat.PRIORITY_HIGH
                : alert.getPriority() == Alert.Priority.LOW
                        ? NotificationCompat.PRIORITY_LOW
                        : NotificationCompat.PRIORITY_DEFAULT;

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🔔 " + alert.getLabel())
                .setContentText(alert.getNotification())
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setPriority(priority)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();

        getSystemService(NotificationManager.class)
                .notify((int)(alert.getId() & 0x7FFFFFFF), n);
    }

    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Stock Alerts", NotificationManager.IMPORTANCE_DEFAULT);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private String findSymbolByReqId(int reqId) {
        for (ConcurrentHashMap.Entry<String, Integer> e : streamReqIds.entrySet()) {
            if (e.getValue() == reqId) return e.getKey();
        }
        return null;
    }
}
