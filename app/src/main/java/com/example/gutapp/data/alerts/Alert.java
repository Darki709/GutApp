package com.example.gutapp.data.alerts;

import androidx.annotation.Nullable;

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
public class Alert {

    // ── Identity ──────────────────────────────────────────────────────
    /** Assigned by DB on insert; -1 until persisted. */
    private long id = -1;

    private final String symbol;
    private String label;           // User-defined display name, e.g. "BTC breakout"

    // ── Condition ─────────────────────────────────────────────────────
    private final Condition condition;

    // ── Status ────────────────────────────────────────────────────────
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
    private long lastTriggeredAt;

    // ── Expiry ────────────────────────────────────────────────────────
    /**
     * Optional Unix-epoch timestamp (seconds) after which the alert
     * auto-deactivates. 0 = no expiry.
     */
    private long expiresAt;

    // ── Priority ──────────────────────────────────────────────────────
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

    // ── Getters / setters ─────────────────────────────────────────────
    public long   getId()             { return id; }
    public void   setId(long id)      { this.id = id; }
    public String getSymbol()         { return symbol; }
    public String getLabel()          { return label; }
    public void   setLabel(String l)  { this.label = l; }
    public Condition getCondition()   { return condition; }
    public Status getStatus()         { return status; }
    public void   setStatus(Status s) { this.status = s; }
    public RepeatMode getRepeatMode() { return repeatMode; }
    public int getCooldownSeconds()   { return cooldownSeconds; }
    public long getLastTriggeredAt()  { return lastTriggeredAt; }
    public void setLastTriggeredAt(long t) { this.lastTriggeredAt = t; }
    public long getExpiresAt()        { return expiresAt; }
    public Priority getPriority()     { return priority; }
    public void setPriority(Priority p) { this.priority = p; }

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
