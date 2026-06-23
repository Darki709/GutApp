package com.example.gutapp.data.alerts;


import lombok.Getter;
import lombok.Setter;

/**
 * Alert — a named, persisted trigger that watches one symbol via a Condition.
 *
 * Design goals:
 *  - Modular: the Condition field is fully polymorphic; any Condition subclass works.
 *  - Repeat modes: ONCE fires once and moves to TRIGGERED; PERSISTENT re-arms itself
 *    after each trigger; COOLDOWN re-arms only after a quiet period.
 *  - Expiry: optional Unix-epoch timestamp after which the alert auto-deactivates.
 *  - Priority: LOW / MEDIUM / HIGH → drives Android notification priority.
 *  - DB-round-trip safe: all fields are primitive types or Strings.
 */
@Getter
public class Alert {

    // ── Identity ──────────────────────────────────────────────────────
    // ── Getters / setters ─────────────────────────────────────────────
    /** Assigned by DB on insert; -1 until persisted. */
    @Setter
    private long id = -1;

    /**
     * Stable, device-independent identity used for server sync (last-write-wins key).
     * The local {@code id} is a per-device autoincrement and is NOT portable; this UUID is.
     * Assigned by the DB layer on first persist; null until then.
     */
    @Setter
    private String uuid;

    /** Epoch millis of the last local OR remote write that won (drives sync LWW). */
    @Setter
    private long updatedAt;

    private final String symbol;
    @Setter
    private String label;           // User-defined display name, e.g. "BTC breakout"

    // ── Condition ─────────────────────────────────────────────────────
    private final Condition condition;

    // ── Status ────────────────────────────────────────────────────────
    @Setter
    private Status status;

    public enum Status {
        ACTIVE,     // being evaluated on every tick
        INACTIVE,   // user-paused; not evaluated
        TRIGGERED;  // fired at least once (for ONCE mode, stays here)

        public static Status fromInt(int i) { return values()[i]; }
    }

    // ── Repeat policy ─────────────────────────────────────────────────
    private RepeatMode repeatMode;

    public enum RepeatMode {
        /**
         * Fire exactly once, then permanently move to TRIGGERED status.
         * Best for: "alert me when AAPL hits $200 for the first time."
         */
        ONCE,
        /**
         * After firing, immediately re-arm (status → ACTIVE) so it can
         * fire again on the very next tick that satisfies the condition.
         * Best for: "keep beeping while price stays above SMA."
         */
        PERSISTENT,
        /**
         * After firing, enter a cooldown period (cooldownSeconds) before
         * re-arming. Prevents notification spam for volatile conditions.
         * Best for: "alert me every 5 min max if BTC is above $70k."
         */
        COOLDOWN
    }

    // ── Cooldown state ────────────────────────────────────────────────
    /** Seconds before re-arming; only meaningful when repeatMode == COOLDOWN. */
    private int cooldownSeconds;

    /** Unix epoch (seconds) of the last trigger; 0 if never triggered. */
    @Setter
    private long lastTriggeredAt;

    // ── Expiry ────────────────────────────────────────────────────────
    /**
     * Optional Unix-epoch timestamp (seconds) after which the alert
     * auto-deactivates. 0 = no expiry.
     */
    private long expiresAt;

    // ── Priority ──────────────────────────────────────────────────────
    @Setter
    private Priority priority;

    public enum Priority {
        LOW,     // silent notification
        MEDIUM,  // default importance
        HIGH     // heads-up notification + sound
    }

    // ── Constructors ─────────────────────────────────────────────────
    /** Full constructor used by the DB layer when re-hydrating. */
    public Alert(long id, String symbol, String label, Condition condition,
                 Status status, RepeatMode repeatMode,
                 int cooldownSeconds, long lastTriggeredAt,
                 long expiresAt, Priority priority) {
        this.id               = id;
        this.symbol           = symbol;
        this.label            = label;
        this.condition        = condition;
        this.status           = status;
        this.repeatMode       = repeatMode;
        this.cooldownSeconds  = cooldownSeconds;
        this.lastTriggeredAt  = lastTriggeredAt;
        this.expiresAt        = expiresAt;
        this.priority         = priority;
    }

    /** Convenience constructor for creating new alerts before DB insertion. */
    public Alert(String symbol, String label, Condition condition,
                 RepeatMode repeatMode, int cooldownSeconds,
                 long expiresAt, Priority priority) {
        this(-1, symbol, label, condition, Status.ACTIVE,
             repeatMode, cooldownSeconds, 0L, expiresAt, priority);
    }

    /** Simplest constructor: once-firing, medium priority, no expiry. */
    public Alert(String symbol, Condition condition) {
        this(symbol, condition.getNotification(), condition,
             RepeatMode.ONCE, 0, 0L, Priority.MEDIUM);
    }

    // ── Evaluation logic ──────────────────────────────────────────────
    /**
     * Returns true if this alert should be evaluated right now.
     * Reasons it might NOT be evaluated:
     *  - Status is INACTIVE (user-paused)
     *  - Status is TRIGGERED and mode is ONCE (permanently done)
     *  - Status is TRIGGERED and mode is COOLDOWN but cooldown hasn't elapsed
     *  - expiresAt has passed → auto-deactivate
     */
    public boolean isEvaluatable() {
        if (status == Status.INACTIVE) return false;

        long now = System.currentTimeMillis() / 1000L;

        // Auto-expire
        if (expiresAt > 0 && now > expiresAt) {
            status = Status.INACTIVE;
            return false;
        }

        if (status == Status.TRIGGERED) {
            if (repeatMode == RepeatMode.ONCE) return false;
            if (repeatMode == RepeatMode.COOLDOWN) {
                return (now - lastTriggeredAt) >= cooldownSeconds;
            }
            // PERSISTENT: re-arm immediately (status stays TRIGGERED until next check cycle)
            status = Status.ACTIVE;
        }

        return true;
    }

    /**
     * Called by AlertManager after the condition fires.
     * Updates status and timestamps according to the repeat policy.
     */
    public void onTriggered() {
        long now = System.currentTimeMillis() / 1000L;
        lastTriggeredAt = now;
        status = Status.TRIGGERED;
        // PERSISTENT re-arms on the very next isEvaluatable() call.
        // COOLDOWN re-arms after the cooldown elapses.
        // ONCE stays TRIGGERED permanently.
    }

    public String getNotification() { return condition.getNotification(); }
}
