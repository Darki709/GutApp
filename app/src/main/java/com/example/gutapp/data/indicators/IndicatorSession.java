package com.example.gutapp.data.indicators;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IndicatorSession — manages the SET OF ACTIVE INDICATOR INSTANCES for a chart.
 *
 * Design:
 *  - IndicatorRegistry: immutable catalog of indicator TYPES (one per type).
 *  - IndicatorSession: the live list of instances the user has added.
 *
 * Multiple instances of the same type are fully supported:
 *   session.addInstance("ma")  → MA(20), color amber
 *   session.addInstance("ma")  → MA(50), color blue  ← second instance, different UUID
 *
 * The session is per-ticker/per-preset. ChartActivity holds one session.
 */
public class IndicatorSession {

    // Ordered by insertion (display order)
    private final LinkedHashMap<String, Indicator> instances = new LinkedHashMap<>();

    // ── CRUD ──────────────────────────────────────────────────────────

    /** Add a new default instance of typeId. Returns the new instance. */
    public Indicator addInstance(String typeId) {
        Indicator type = IndicatorRegistry.getInstance().getType(typeId);
        if (type == null) throw new IllegalArgumentException("Unknown type: " + typeId);
        Indicator inst = type.newInstance();
        instances.put(inst.getInstanceId(), inst);
        return inst;
    }

    /** Restore a specific instance from a snapshot (used for presets / auto-save). */
    public void restoreInstance(Indicator.IndicatorSnapshot snap) {
        Indicator type = IndicatorRegistry.getInstance().getType(snap.typeId);
        if (type == null) return;
        Indicator inst = type.newInstance();
        inst.setInstanceId(snap.instanceId);
        inst.setColor(snap.color);
        inst.restoreParams(snap.params);
        instances.put(inst.getInstanceId(), inst);
    }

    /** Remove instance by its UUID */
    public void removeInstance(String instanceId) {
        instances.remove(instanceId);
    }

    /** Remove all instances */
    public void clearAll() {
        instances.clear();
    }

    // ── Queries ───────────────────────────────────────────────────────

    public List<Indicator> getAll() {
        return new ArrayList<>(instances.values());
    }

    public List<Indicator> getOverlays() {
        List<Indicator> r = new ArrayList<>();
        for (Indicator i : instances.values()) if (!i.isSubChart()) r.add(i);
        return r;
    }

    public List<Indicator> getSubCharts() {
        List<Indicator> r = new ArrayList<>();
        for (Indicator i : instances.values()) if (i.isSubChart()) r.add(i);
        return r;
    }

    public Indicator getInstance(String instanceId) {
        return instances.get(instanceId);
    }

    public boolean isEmpty() {
        return instances.isEmpty();
    }

    public int size() {
        return instances.size();
    }

    // ── Preset serialization ──────────────────────────────────────────

    /** Save all active instances to a list of snapshots */
    public List<Indicator.IndicatorSnapshot> savePreset() {
        List<Indicator.IndicatorSnapshot> snaps = new ArrayList<>();
        for (Indicator inst : instances.values()) snaps.add(inst.snapshot());
        return snaps;
    }

    /** Restore all instances from a preset snapshot list (clears current state first) */
    public void loadPreset(List<Indicator.IndicatorSnapshot> snaps) {
        clearAll();
        for (Indicator.IndicatorSnapshot s : snaps) restoreInstance(s);
    }
}