package com.example.akplayer;

import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Shared with the service; retains profiles saved by the activity before system EQ. */
final class EqPreferences {
    static final String FILE = "MainActivity";

    static Set<String> profileIds(SharedPreferences prefs) {
        Set<String> ids = new HashSet<>(prefs.getStringSet("eq_profile_ids", Collections.singleton("default")));
        for (EqPresets.Preset preset : EqPresets.ALL) ids.add(preset.id);
        return ids;
    }

    static String profileName(SharedPreferences prefs, String id) {
        EqPresets.Preset preset = EqPresets.find(id);
        return prefs.getString("eq_profile_name_" + id, preset == null ? "User EQ 01" : preset.name);
    }

    private static String savedKey(SharedPreferences prefs, int frequency) {
        return "eq_profile_" + prefs.getString("eq_profile_selected", "default") + "_gain_" + frequency;
    }

    static String draftKey(SharedPreferences prefs, int frequency) {
        return savedKey(prefs, frequency) + "_draft";
    }

    static int savedGain(SharedPreferences prefs, int frequency) {
        String profile = prefs.getString("eq_profile_selected", "default");
        EqPresets.Preset preset = EqPresets.find(profile);
        int legacy = preset != null ? preset.gainAt(frequency)
                : "default".equals(profile) ? prefs.getInt("eq_gain_" + frequency, 0) : 0;
        return prefs.getInt(savedKey(prefs, frequency), legacy);
    }

    static void apply(SharedPreferences prefs, DeviceEqualizer eq) {
        short[] range = eq.getBandLevelRange();
        for (short band = 0; band < eq.getNumberOfBands(); band++) {
            int frequency = eq.getCenterFreq(band);
            int gain = prefs.getInt(draftKey(prefs, frequency), savedGain(prefs, frequency));
            eq.setBandLevel(band, (short) Math.max(range[0], Math.min(range[1], gain)));
        }
    }

    static void save(SharedPreferences prefs, DeviceEqualizer eq) {
        SharedPreferences.Editor edit = prefs.edit();
        for (short band = 0; band < eq.getNumberOfBands(); band++) {
            int frequency = eq.getCenterFreq(band);
            edit.putInt(savedKey(prefs, frequency), eq.getBandLevel(band));
            edit.remove(draftKey(prefs, frequency));
        }
        edit.apply();
    }

    static void revert(SharedPreferences prefs, DeviceEqualizer eq) {
        SharedPreferences.Editor edit = prefs.edit();
        short[] range = eq.getBandLevelRange();
        for (short band = 0; band < eq.getNumberOfBands(); band++) {
            int frequency = eq.getCenterFreq(band);
            int gain = savedGain(prefs, frequency);
            eq.setBandLevel(band, (short) Math.max(range[0], Math.min(range[1], gain)));
            edit.remove(draftKey(prefs, frequency));
        }
        edit.apply();
    }

    static boolean isDirty(SharedPreferences prefs, DeviceEqualizer eq) {
        for (short band = 0; band < eq.getNumberOfBands(); band++) {
            if (eq.getBandLevel(band) != savedGain(prefs, eq.getCenterFreq(band))) return true;
        }
        return false;
    }
}
