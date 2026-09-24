package com.jettra.driver.java;

import io.jettra.json.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a single aggregated row resulting from a JettraAggregation execution.
 * Contains grouped keys, accumulator values (count, sum, avg, min, max),
 * and any dereferenced or looked-up entity data.
 */
public class AggregationRow {

    private final Map<String, Object> groupKeys;
    private final Map<String, Object> values;
    private final Map<String, Object> enriched;

    public AggregationRow(Map<String, Object> groupKeys, Map<String, Object> values) {
        this(groupKeys, values, new LinkedHashMap<>());
    }

    public AggregationRow(Map<String, Object> groupKeys, Map<String, Object> values, Map<String, Object> enriched) {
        this.groupKeys = groupKeys != null ? new LinkedHashMap<>(groupKeys) : new LinkedHashMap<>();
        this.values = values != null ? new LinkedHashMap<>(values) : new LinkedHashMap<>();
        this.enriched = enriched != null ? new LinkedHashMap<>(enriched) : new LinkedHashMap<>();
    }

    public Map<String, Object> getGroupKeys() {
        return Collections.unmodifiableMap(groupKeys);
    }

    public Object getGroupKey(String field) {
        return groupKeys.get(field);
    }

    public String getGroupKeyAsString(String field) {
        Object val = groupKeys.get(field);
        return val != null ? val.toString() : null;
    }

    public Map<String, Object> getValues() {
        return Collections.unmodifiableMap(values);
    }

    public Map<String, Object> getEnriched() {
        return Collections.unmodifiableMap(enriched);
    }

    public void setEnriched(String field, Object entity) {
        this.enriched.put(field, entity);
    }

    public Object get(String field) {
        if (values.containsKey(field)) return values.get(field);
        if (groupKeys.containsKey(field)) return groupKeys.get(field);
        if (enriched.containsKey(field)) return enriched.get(field);
        return null;
    }

    public String getString(String field) {
        Object val = get(field);
        return val != null ? val.toString() : null;
    }

    public Number getNumber(String field) {
        Object val = get(field);
        if (val instanceof Number n) {
            return n;
        }
        if (val != null) {
            try {
                return Double.parseDouble(val.toString().trim());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public double getDouble(String field) {
        Number n = getNumber(field);
        return n != null ? n.doubleValue() : 0.0;
    }

    public long getLong(String field) {
        Number n = getNumber(field);
        return n != null ? n.longValue() : 0L;
    }

    public int getInt(String field) {
        Number n = getNumber(field);
        return n != null ? n.intValue() : 0;
    }

    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        JsonObject keys = new JsonObject();
        for (var e : groupKeys.entrySet()) {
            if (e.getValue() instanceof Number num) {
                keys.addProperty(e.getKey(), num);
            } else if (e.getValue() instanceof Boolean b) {
                keys.addProperty(e.getKey(), b);
            } else {
                keys.addProperty(e.getKey(), e.getValue() != null ? e.getValue().toString() : null);
            }
        }
        json.add("_id", keys);

        for (var e : values.entrySet()) {
            if (e.getValue() instanceof Number num) {
                json.addProperty(e.getKey(), num);
            } else if (e.getValue() instanceof Boolean b) {
                json.addProperty(e.getKey(), b);
            } else {
                json.addProperty(e.getKey(), e.getValue() != null ? e.getValue().toString() : null);
            }
        }

        for (var e : enriched.entrySet()) {
            if (e.getValue() instanceof JsonObject jo) {
                json.add(e.getKey(), jo);
            } else if (e.getValue() instanceof Number num) {
                json.addProperty(e.getKey(), num);
            } else if (e.getValue() instanceof Boolean b) {
                json.addProperty(e.getKey(), b);
            } else {
                json.addProperty(e.getKey(), e.getValue() != null ? e.getValue().toString() : null);
            }
        }
        return json;
    }

    public <R extends Record> R toRecord(Class<R> recordClass) {
        return JettraRecordMapper.toRecord(this, recordClass);
    }

    @Override
    public String toString() {
        return toJsonObject().toString();
    }
}

