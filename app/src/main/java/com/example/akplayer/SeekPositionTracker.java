package com.example.akplayer;

/** Holds the scrubbed position until the source acknowledges the seek. Times are elapsed realtime. */
final class SeekPositionTracker {
    private static final long TIMEOUT_MS = 4000;
    private static final long ACK_TOLERANCE_MS = 1500;
    private long target = -1, requestedAt;

    void begin(long position, long now) { target = position; requestedAt = now; }
    void clear() { target = -1; }
    boolean isPending() { return target >= 0; }

    long displayedPosition(long actual, long now, long sourceUpdatedAt) {
        if (!isPending()) return actual;
        if (now - requestedAt >= TIMEOUT_MS
                || (sourceUpdatedAt >= requestedAt && Math.abs(actual - target) <= ACK_TOLERANCE_MS)) {
            clear();
            return actual;
        }
        return target;
    }
}
