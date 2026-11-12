package com.example.akplayer;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.Build;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

/** Hardware integration: profile transactions and the real output-mix service lifetime. */
public class SystemEqualizerTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();

    private void main(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null);
        instrumentation.runOnMainSync(task);
        task.get(10, TimeUnit.SECONDS);
    }

    private final class Connection implements ServiceConnection {
        final CountDownLatch ready = new CountDownLatch(1);
        SystemEqualizerService service;
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((SystemEqualizerService.LocalBinder) binder).getService();
            ready.countDown();
        }
        @Override public void onServiceDisconnected(ComponentName name) { service = null; }
        void bind() throws Exception {
            assertTrue(context.bindService(new Intent(context, SystemEqualizerService.class), this, Context.BIND_AUTO_CREATE));
            assertTrue("EQ service did not connect", ready.await(10, TimeUnit.SECONDS));
        }
    }

    @Test public void genrePresetsAndNativeBandMapping() {
        SharedPreferences prefs = context.getSharedPreferences("eq-preset-integration-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        try {
            assertEquals(20, EqPresets.ALL.size());
            Set<String> ids = new HashSet<>();
            for (EqPresets.Preset preset : EqPresets.ALL) {
                assertTrue("Preset IDs must be unique", ids.add(preset.id));
                assertTrue(EqPreferences.profileIds(prefs).contains(preset.id));
                assertEquals(preset.name, EqPreferences.profileName(prefs, preset.id));
            }
            EqPresets.Preset rap = EqPresets.find("genre_rap");
            assertNotNull(rap);
            assertEquals(250, rap.gainAt(20000)); // below the first band
            assertEquals(-50, rap.gainAt(22000000)); // above the last band
            assertEquals(265, rap.gainAt(80000)); // native 80 Hz band, log interpolation

            prefs.edit().putString("eq_profile_selected", "genre_hiphop").apply();
            assertEquals(300, EqPreferences.savedGain(prefs, 30000));
            prefs.edit().putInt("eq_profile_genre_hiphop_gain_30000", -100).apply();
            assertEquals(-100, EqPreferences.savedGain(prefs, 30000));
            prefs.edit().putString("eq_profile_selected", "default").putInt("eq_gain_30000", 75).apply();
            assertEquals(75, EqPreferences.savedGain(prefs, 30000));
        } finally {
            prefs.edit().clear().commit();
        }
    }

    @Test public void timelineWaitsForSeekAcknowledgement() {
        SeekPositionTracker tracker = new SeekPositionTracker();
        tracker.begin(60000, 10000);
        assertEquals(60000, tracker.displayedPosition(12000, 10500, 9500));
        assertEquals(60000, tracker.displayedPosition(60000, 10600, 9500));
        assertTrue(tracker.isPending());
        assertEquals(60200, tracker.displayedPosition(60200, 10700, 10600));
        assertFalse(tracker.isPending());
        assertEquals(61000, tracker.displayedPosition(61000, 11500, 10600));
        tracker.begin(30000, 12000);
        tracker.begin(90000, 12100);
        assertEquals(90000, tracker.displayedPosition(30000, 12200, 12050));
        assertEquals(91000, tracker.displayedPosition(91000, 13000, 12900));
        tracker.begin(45000, 14000);
        assertEquals(91000, tracker.displayedPosition(91000, 18000, 13900));
        assertFalse(tracker.isPending());
    }

    @Test public void profilesAndBackgroundLifetime() throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(EqPreferences.FILE, Context.MODE_PRIVATE);
        Map<String, ?> original = new HashMap<>(prefs.getAll());
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Connection first = new Connection();
        Connection second = null;
        Connection restarted = null;
        boolean firstBound = false, secondBound = false, restartedBound = false;
        try {
            first.bind(); firstBound = true;
            main(()->first.service.retryEqualizer());
            assertNotNull("Firmware must support an output-mix effect", first.service.getEqualizer());
            main(() -> {
                DeviceEqualizer eq = first.service.getEqualizer();
                SharedPreferences.Editor clean = prefs.edit();
                for (String key : prefs.getAll().keySet()) {
                    if (key.startsWith("eq_profile_genre_hiphop_gain_")
                            || key.startsWith("eq_profile_genre_ballad_gain_")) clean.remove(key);
                }
                clean.apply();
                prefs.edit().putString("eq_profile_selected", "integration-test-a").apply();
                EqPreferences.revert(prefs, eq);
                eq.setBandLevel((short) 0, (short) -200);
                prefs.edit().putInt(EqPreferences.draftKey(prefs, eq.getCenterFreq((short) 0)), -200).apply();
                assertTrue(EqPreferences.isDirty(prefs, eq));
                EqPreferences.save(prefs, eq);
                assertFalse(EqPreferences.isDirty(prefs, eq));
                eq.setBandLevel((short) 0, (short) -400);
                prefs.edit().putInt(EqPreferences.draftKey(prefs, eq.getCenterFreq((short) 0)), -400).apply();
                EqPreferences.revert(prefs, eq);
                assertEquals(-200, eq.getBandLevel((short) 0));
                assertFalse(EqPreferences.isDirty(prefs, eq));

                // Switching profiles preserves each draft without overwriting saved gains.
                eq.setBandLevel((short) 0, (short) -300);
                prefs.edit().putInt(EqPreferences.draftKey(prefs, eq.getCenterFreq((short) 0)), -300)
                        .putString("eq_profile_selected", "integration-test-b").apply();
                EqPreferences.apply(prefs, eq);
                assertEquals(0, eq.getBandLevel((short) 0));
                prefs.edit().putString("eq_profile_selected", "integration-test-a").apply();
                EqPreferences.apply(prefs, eq);
                assertEquals(-300, eq.getBandLevel((short) 0));
                EqPreferences.revert(prefs, eq);

                // Built-ins use genre gains until Save overrides them; Revert uses that save.
                prefs.edit().putString("eq_profile_selected", "genre_hiphop").apply();
                EqPreferences.apply(prefs, eq);
                assertEquals(300, eq.getBandLevel((short) 0));
                assertFalse(EqPreferences.isDirty(prefs, eq));
                eq.setBandLevel((short) 0, (short) -150);
                EqPreferences.save(prefs, eq);
                eq.setBandLevel((short) 0, (short) -300);
                EqPreferences.revert(prefs, eq);
                assertEquals(-150, eq.getBandLevel((short) 0));
                prefs.edit().putString("eq_profile_selected", "genre_ballad").apply();
                EqPreferences.apply(prefs, eq);
                assertEquals(50, eq.getBandLevel((short) 0));
                prefs.edit().putString("eq_profile_selected", "genre_hiphop").apply();
                EqPreferences.apply(prefs, eq);
                assertEquals(-150, eq.getBandLevel((short) 0));
                prefs.edit().putString("eq_profile_selected", "integration-test-a").apply();
                EqPreferences.apply(prefs, eq);
                first.service.setEqEnabled(true);
                assertTrue(eq.getEnabled());
                activity.finish();
            });
            instrumentation.waitForIdleSync();
            long deadline = SystemClock.uptimeMillis() + 5000;
            while (!activity.isDestroyed() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
            assertTrue("Activity did not finish", activity.isDestroyed());
            context.unbindService(first); firstBound = false;
            instrumentation.waitForIdleSync();
            second = new Connection(); second.bind(); secondBound = true;
            assertSame("Enabled EQ must survive the activity and all clients unbinding", first.service, second.service);
            assertTrue(second.service.getEqualizer().getEnabled());
            assertEquals(-200, second.service.getEqualizer().getBandLevel((short) 0));
            DeviceEqualizer runningEq = second.service.getEqualizer();
            MainActivity reopened = (MainActivity) instrumentation.startActivitySync(
                    new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            try {
                instrumentation.waitForIdleSync();
                assertSame("Reopening the app must reuse the running output effect", runningEq, second.service.getEqualizer());
                assertTrue(runningEq.getEnabled());
                assertEquals(-200, runningEq.getBandLevel((short) 0));
                final SystemEqualizerService runningService = second.service;
                main(() -> { for (int i = 0; i < 5; i++) runningService.restoreIfEnabled(); });
                assertSame(runningEq, second.service.getEqualizer());
            } finally {
                main(reopened::finish);
                deadline = SystemClock.uptimeMillis() + 5000;
                while (!reopened.isDestroyed() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
                assertTrue(reopened.isDestroyed());
            }
            final SystemEqualizerService service = second.service;
            main(() -> {
                service.setEqEnabled(false);
                assertFalse(service.getEqualizer().getEnabled());
                assertFalse(prefs.getBoolean("eq_enabled", true));
            });
            context.unbindService(second); secondBound = false;
            instrumentation.waitForIdleSync();
            deadline = SystemClock.uptimeMillis() + 5000;
            while (service.getEqualizer() != null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
            assertNull("Disabled unbound service did not release its effect", service.getEqualizer());
            // A system recreation must restore both the saved curve and enabled state.
            prefs.edit().putBoolean("eq_enabled", true).commit();
            Intent restart = new Intent(context, SystemEqualizerService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(restart);
            else context.startService(restart);
            restarted = new Connection(); restarted.bind(); restartedBound = true;
            instrumentation.waitForIdleSync();
            assertNotSame(service, restarted.service);
            assertTrue(restarted.service.getEqualizer().getEnabled());
            assertEquals(-200, restarted.service.getEqualizer().getBandLevel((short) 0));
        } finally {
            final SystemEqualizerService service = restartedBound ? restarted.service
                    : secondBound ? second.service : firstBound ? first.service : null;
            main(() -> {
                if (service != null && service.getEqualizer() != null) service.setEqEnabled(false);
                restore(prefs, original);
                if (service != null && service.getEqualizer() != null) {
                    EqPreferences.apply(prefs, service.getEqualizer());
                    if (prefs.getBoolean("eq_enabled", false)) service.setEqEnabled(true);
                }
                if (!activity.isFinishing()) activity.finish();
            });
            if (firstBound) context.unbindService(first);
            if (secondBound) context.unbindService(second);
            if (restartedBound) context.unbindService(restarted);
        }
    }

    @SuppressWarnings("unchecked")
    private void restore(SharedPreferences prefs, Map<String, ?> original) {
        SharedPreferences.Editor edit = prefs.edit().clear();
        for (Map.Entry<String, ?> entry : original.entrySet()) {
            String key = entry.getKey(); Object value = entry.getValue();
            if (value instanceof String) edit.putString(key, (String) value);
            else if (value instanceof Integer) edit.putInt(key, (Integer) value);
            else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
            else if (value instanceof Long) edit.putLong(key, (Long) value);
            else if (value instanceof Float) edit.putFloat(key, (Float) value);
            else if (value instanceof Set) edit.putStringSet(key, (Set<String>) value);
        }
        assertTrue(edit.commit());
    }
}
