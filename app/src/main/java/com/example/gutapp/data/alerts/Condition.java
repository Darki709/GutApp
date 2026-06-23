package com.example.gutapp.data.alerts;

/**
 * Condition — the pluggable logic half of an Alert.
 *
 * To add a new condition type:
 *  1. Extend this class.
 *  2. Implement check(), getNotification(), getTypeName(), serialize().
 *  3. Register a deserializer in ConditionRegistry.
 *
 * The system is intentionally generic: Condition knows nothing about alerts,
 * symbols, or persistence. It only answers "is the condition currently true?"
 * given a PriceResource data source.  This makes conditions reusable for
 * screeners, strategy back-tests, or any future feature that needs boolean
 * market logic.
 */
public abstract class Condition {

    // ── Core contract ─────────────────────────────────────────────────

    /**
     * Evaluate whether this condition is currently satisfied.
     *
     * Called on the AlertManager worker thread — safe to do light computation
     * and DB reads via the PriceResource, but do NOT perform network I/O.
     *
     * @param resource  Live + cached price data source.
     * @return true if the condition is met and the alert should fire.
     */
    public abstract boolean check(PriceResource resource);

    /**
     * Human-readable string used as the notification body when this condition
     * fires.  Should be specific enough to be useful without the app open,
     * e.g. "AAPL rose above $200.00".
     */
    public abstract String getNotification();

    /**
     * Short stable type key used by ConditionRegistry for polymorphic
     * deserialization.  Must be unique across all Condition subclasses and
     * must never change once data has been persisted.
     *
     * Convention: SCREAMING_SNAKE_CASE, e.g. "PRICE_THRESHOLD".
     */
    public abstract String getTypeName();

    /**
     * Serialize this condition's parameters to a JSON string.
     * The JSON only needs to contain what's necessary to reconstruct
     * the condition via ConditionRegistry — it does NOT need to store
     * the type name (that's stored separately in the DB column).
     */
    public abstract String serialize();

    // ── UI metadata ───────────────────────────────────────────────────

    /**
     * Short display name shown in the "Add Alert" UI chip list.
     * Default: the type name, title-cased.  Override for friendlier text.
     */
    public String getDisplayName() {
        String raw = getTypeName().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!word.isEmpty())
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1).toLowerCase()).append(' ');
        }
        return sb.toString().trim();
    }

    /**
     * One-line description of what this condition watches.
     * Shown as a subtitle in the alert list.  Example:
     *   "Price ≥ 200.00 on AAPL"
     */
    public abstract String getSummary();
}
