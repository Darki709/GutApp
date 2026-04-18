package com.example.gutapp.data.indicators;

import androidx.annotation.Nullable;

/**
 * CurrentSessionHolder — thin static bridge.
 *
 * Problem: ProfileActivity needs to read the current IndicatorSession
 * to save it as a named preset, but it has no direct reference to ChartActivity.
 *
 * Solution: ChartActivity calls CurrentSessionHolder.set(session) in onCreate/onResume,
 * and clears it in onDestroy. ProfileActivity reads it via get().
 *
 * This is intentionally minimal — no memory leak because ChartActivity clears it on destroy.
 */
public class CurrentSessionHolder {

    @Nullable
    private static IndicatorSession current;

    public static void set(@Nullable IndicatorSession session) {
        current = session;
    }

    @Nullable
    public static IndicatorSession getSession() {
        return current;
    }
}