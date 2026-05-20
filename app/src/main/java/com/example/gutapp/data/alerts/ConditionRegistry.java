package com.example.gutapp.data.alerts;

import android.util.Log;

import com.example.gutapp.data.alerts.conditions.PriceThresholdCondition;
import com.example.gutapp.data.alerts.conditions.SMACrossoverCondition;
import com.example.gutapp.data.alerts.conditions.VolatilityCondition;
import com.example.gutapp.data.alerts.conditions.RSICondition;
import com.example.gutapp.data.alerts.conditions.PriceChangePercentCondition;
import com.example.gutapp.data.alerts.conditions.VolumeSpikeCondition;

import java.util.HashMap;
import java.util.Map;

/**
 * ConditionRegistry — the single place that maps a String type key to a
 * deserializer lambda.  No reflection, no Gson type tokens, no fragile
 * class-name storage.
 *
 * HOW TO ADD A NEW CONDITION TYPE
 * ─────────────────────────────────
 * 1. Create MyCondition extends Condition in the conditions/ package.
 * 2. Give it a stable getTypeName() like "MY_CONDITION".
 * 3. Add one line in registerAll():
 *       register("MY_CONDITION", MyCondition::fromJson);
 *    where fromJson(String json) is a static factory method on MyCondition.
 * 4. Done — the DB layer, AlertManager, and UI all pick it up automatically.
 */
public class ConditionRegistry {

    private static final String TAG = "ConditionRegistry";

    /** Deserializer lambda: takes the stored JSON and returns a Condition. */
    public interface ConditionDeserializer {
        Condition deserialize(String json);
    }

    private static final Map<String, ConditionDeserializer> registry = new HashMap<>();

    static {
        registerAll();
    }

    private static void registerAll() {
        register(PriceThresholdCondition.TYPE,     PriceThresholdCondition::fromJson);
        register(SMACrossoverCondition.TYPE,        SMACrossoverCondition::fromJson);
        register(VolatilityCondition.TYPE,          VolatilityCondition::fromJson);
        register(RSICondition.TYPE,                 RSICondition::fromJson);
        register(PriceChangePercentCondition.TYPE,  PriceChangePercentCondition::fromJson);
        register(VolumeSpikeCondition.TYPE,         VolumeSpikeCondition::fromJson);
    }

    public static void register(String typeName, ConditionDeserializer deserializer) {
        registry.put(typeName, deserializer);
    }

    /**
     * Deserialize a Condition from its stored type name + JSON payload.
     * Returns null and logs an error if the type is unknown or JSON is malformed.
     */
    public static Condition deserialize(String typeName, String json) {
        ConditionDeserializer d = registry.get(typeName);
        if (d == null) {
            Log.e(TAG, "Unknown condition type: '" + typeName + "' — was it registered?");
            return null;
        }
        try {
            return d.deserialize(json);
        } catch (Exception e) {
            Log.e(TAG, "Failed to deserialize condition type=" + typeName + " json=" + json, e);
            return null;
        }
    }
}
