package com.example.gutapp.session.background;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.gutapp.R;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.SessionCallback;

public class NetworkService extends Service implements SessionCallback {


    private static final String CHANNEL_ID = "StockAlertChannel";

    @Override
    public void onCreate() {
        super.onCreate();
        //create the secure connection to the server and assign the service as a callback for anything alert related
        NetworkClient.getInstance(this).getSessionManager().setPushResponseCallback(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        // 2. Build the "I am alive" notification
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Market Monitor Active")
                .setContentText("Gut is making you money\uD83D\uDCC8")
                .setSmallIcon(R.drawable.logo)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        // 3. Become a Foreground Service
        startForeground(101, notification);

        return START_STICKY; // Tell Android: "If you kill me, restart me."
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Stock Alerts", NotificationManager.IMPORTANCE_DEFAULT);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    public void showSystemAlertNotification(String message) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        Notification alert = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Price Alert Triggered!")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        manager.notify((int) System.currentTimeMillis(), alert);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {

    }

    @Override
    public void onActionRequired(int actionType, @Nullable Object data) {
    }
}
