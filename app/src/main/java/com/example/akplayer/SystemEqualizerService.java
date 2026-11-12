package com.example.akplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/** Owns one output-mix effect, independently of the player and activity lifetime. */
public class SystemEqualizerService extends Service {
    static final String SHOW_EQ = "com.example.akplayer.SHOW_EQ";
    private static final String CHANNEL = "system_equalizer";
    private static final int NOTIFICATION = 20;
    private final LocalBinder binder = new LocalBinder();
    private DeviceEqualizer equalizer;
    private SharedPreferences preferences;
    private boolean foregroundActive;

    final class LocalBinder extends Binder {
        SystemEqualizerService getService() { return SystemEqualizerService.this; }
    }

    @Override public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences(EqPreferences.FILE, MODE_PRIVATE);
        // Opening the library while EQ is off must not attach an output-mix effect.
        if (preferences.getBoolean("eq_enabled", false)) retryEqualizer();
    }

    DeviceEqualizer getEqualizer() { return equalizer; }

    void restoreIfEnabled() {
        if (preferences.getBoolean("eq_enabled", false) && !foregroundActive) setEqEnabled(true);
    }

    void retryEqualizer() {
        if (equalizer != null) return;
        try {
            // Session zero is the output mix. Never silently fall back to a local session.
            equalizer = new DeviceEqualizer(0);
            EqPreferences.apply(preferences, equalizer);
            if (equalizer.setEnabled(false) != 0) throw new IllegalStateException("Cannot disable EQ");
        } catch (RuntimeException e) {
            releaseEqualizer();
            Log.w("CrimsonEQ", "System output EQ is unavailable", e);
        }
    }

    void setEqEnabled(boolean enabled) {
        if (enabled && equalizer == null) retryEqualizer();
        if (equalizer == null || !equalizer.hasControl())
            throw new IllegalStateException("System EQ control is unavailable");
        if (enabled && foregroundActive && equalizer.getEnabled()) return;
        if (enabled) {
            // If foreground startup fails, leave the effect and saved switch off.
            showNotification();
            try {
                setEffectEnabled(true);
                startService(new Intent(this, SystemEqualizerService.class));
            } catch (RuntimeException e) {
                equalizer.setEnabled(false);
                stopForeground(true);
                foregroundActive = false;
                stopSelf();
                throw e;
            }
        } else {
            setEffectEnabled(false);
            stopForeground(true);
            foregroundActive = false;
            stopSelf();
        }
        preferences.edit().putBoolean("eq_enabled", enabled).apply();
    }

    private void setEffectEnabled(boolean enabled) {
        if (equalizer.getEnabled() == enabled) return;
        if (equalizer.setEnabled(enabled) != 0 || equalizer.getEnabled() != enabled)
            throw new IllegalStateException("System EQ did not accept the change");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!preferences.getBoolean("eq_enabled", false)) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        try {
            if (equalizer == null) throw new IllegalStateException("System EQ is unavailable");
            showNotification();
            setEffectEnabled(true);
            return START_STICKY;
        } catch (RuntimeException e) {
            Log.w("CrimsonEQ", "Could not restore system EQ", e);
            preferences.edit().putBoolean("eq_enabled", false).apply();
            releaseEqualizer();
            stopForeground(true);
            foregroundActive = false;
            stopSelf(startId);
            return START_NOT_STICKY;
        }
    }

    private void showNotification() {
        if (foregroundActive) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                    "System equalizer", NotificationManager.IMPORTANCE_LOW));
        }
        Intent open = new Intent(this, MainActivity.class).setAction(SHOW_EQ);
        PendingIntent content = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        startForeground(NOTIFICATION, builder.setSmallIcon(R.drawable.ic_stat_crimson)
                .setContentTitle("System equalizer is on")
                .setContentText("EQ applies to the Android audio mix. Tap to adjust or turn off.")
                .setContentIntent(content).setOngoing(true).setShowWhen(false).build());
        foregroundActive = true;
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    private void releaseEqualizer() {
        if (equalizer != null) { equalizer.release(); equalizer = null; }
    }

    @Override public void onDestroy() {
        releaseEqualizer();
        super.onDestroy();
    }
}
