package com.example.akplayer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Genre starting points, in millibels, ordered from bass to treble. */
final class EqPresets {
    private static final int[] FREQUENCIES = {
            30,45,60,90,120,180,250,380,500,750,1000,1500,2000,3000,4000,6000,8000,12000,14000,18000
    };

    static final class Preset {
        final String id, name;
        private final int[] gains;

        Preset(String id, String name, int... gains) {
            if (gains.length != FREQUENCIES.length) throw new IllegalArgumentException("Expected 20 bands");
            for (int gain : gains) if (Math.abs(gain) > 500) throw new IllegalArgumentException("Gain exceeds EQ range");
            this.id = "genre_" + id;
            this.name = name;
            this.gains = gains.clone();
        }

        int gainAt(int frequencyMillihertz) {
            double hz = frequencyMillihertz / 1000.0;
            if (hz <= FREQUENCIES[0]) return gains[0];
            for (int i = 1; i < FREQUENCIES.length; i++) {
                if (hz <= FREQUENCIES[i]) {
                    // Native fallback EQs have different band centers. Interpolate on a log axis.
                    double mix = Math.log(hz / FREQUENCIES[i - 1])
                            / Math.log((double) FREQUENCIES[i] / FREQUENCIES[i - 1]);
                    return (int) Math.round(gains[i - 1] + mix * (gains[i] - gains[i - 1]));
                }
            }
            return gains[gains.length - 1];
        }
    }

    static final List<Preset> ALL = Collections.unmodifiableList(Arrays.asList(
            new Preset("hiphop", "Hip-hop", 300,300,280,250,200,100,0,-50,-100,-100,-50,0,50,100,100,150,150,100,50,0),
            new Preset("rap", "Rap", 250,280,300,250,150,50,-50,-100,-100,-50,50,100,150,200,150,100,50,0,0,-50),
            new Preset("ballad", "Ballad", 50,50,75,100,75,25,-50,-75,-25,50,100,150,200,150,100,50,75,100,50,0),
            new Preset("pop", "Pop", 150,150,175,150,100,25,-50,-75,-50,0,50,100,150,175,150,150,175,150,100,50),
            new Preset("rock", "Rock", 150,175,200,200,150,75,0,-50,-75,-50,0,75,150,200,200,150,150,100,50,0),
            new Preset("jazz", "Jazz", 50,75,100,100,75,50,25,0,0,25,50,75,100,75,50,75,100,100,75,25),
            new Preset("classical", "Classical", 50,50,50,25,0,-25,-25,0,0,0,25,25,50,50,75,100,125,150,100,50),
            new Preset("acoustic", "Acoustic", -50,-25,0,25,50,50,25,-25,0,50,100,150,175,150,100,100,125,100,50,0),
            new Preset("edm", "EDM", 300,300,275,250,175,75,-25,-100,-150,-100,-50,0,50,100,150,200,250,200,150,100),
            new Preset("rnb", "R&B", 200,225,250,200,150,100,25,-25,-50,0,75,125,150,125,100,75,100,100,50,0),
            new Preset("metal", "Metal", 100,125,150,175,125,25,-75,-125,-100,-50,50,125,200,200,150,75,25,0,-50,-100),
            new Preset("country", "Country", 0,25,50,75,50,0,-25,0,25,50,100,150,175,150,125,100,125,100,50,0),
            new Preset("blues", "Blues", 75,100,125,100,75,50,25,0,25,50,75,125,150,125,75,25,0,-25,-50,-75),
            new Preset("reggae", "Reggae", 250,275,300,275,200,125,50,0,-50,-50,0,50,75,100,75,50,75,50,0,-50),
            new Preset("latin", "Latin", 150,175,200,200,150,75,0,-25,0,25,50,100,150,175,200,200,175,150,100,50),
            new Preset("kpop", "K-pop", 175,200,200,175,100,25,-50,-75,-25,25,75,125,175,200,175,150,200,175,100,50),
            new Preset("vpop", "V-pop", 100,125,150,125,75,25,-25,-50,0,50,100,175,200,175,125,100,125,100,50,0),
            new Preset("lofi", "Lo-fi", 100,125,150,125,100,75,50,25,0,-25,-50,-75,-100,-125,-150,-175,-200,-250,-275,-300),
            new Preset("soul", "Soul", 100,125,150,125,100,75,25,0,25,50,100,150,175,125,75,50,75,50,25,0),
            new Preset("instrumental", "Instrumental", 25,50,50,25,0,-25,-50,-25,0,25,75,100,125,100,100,125,150,125,75,25)
    ));

    static Preset find(String id) {
        for (Preset preset : ALL) if (preset.id.equals(id)) return preset;
        return null;
    }

    static int[] centerFrequencies() { return FREQUENCIES.clone(); }
}
