package com.jettra.driver.java;

import io.jettra.json.JettraJson;
import io.jettra.json.JsonObject;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Fluent aggregation and grouping pipeline engine for JettraDBDriverJava.
 * Supports:
 * - Grouping by single or multiple fields (including nested dot-notation paths)
 * - Accumulators: count(), sum(), avg(), min(), max()
 * - Pre-filtering (WHERE / $match) via filter()
 * - Post-filtering (HAVING) via having()
 * - Sorting by keys or aggregated aliases (sortBy, sortByDesc)
 * - Pagination via skip() and limit()
 * - Cross-engine reference enrichment via lookup()
 */
public class JettraAggregation {

    public enum AccumulatorType {
        COUNT, SUM, AVG, MIN, MAX
    }

    public record AccumulatorSpec(AccumulatorType type, String field, String alias) {
    }

    public record LookupSpec(String refField, String targetAlias) {
    }

    private final JettraClient client;
    private final String modelType;
    private final String collection;
    private final JettraJson jsonParser;

    private final List<String> groupFields = new ArrayList<>();
    private final List<AccumulatorSpec> accumulators = new ArrayList<>();
    private final List<LookupSpec> lookups = new ArrayList<>();
    private Predicate<JsonObject> filterPredicate;
    private Predicate<AggregationRow> havingPredicate;
    private Comparator<AggregationRow> customComparator;
    private int skip = 0;
    private int limit = -1;

    public JettraAggregation() {
        this(null, "DOCUMENT", null);
    }

    public JettraAggregation(JettraClient client, String collection) {
        this(client, "DOCUMENT", collection);
    }

    public JettraAggregation(JettraClient client, String modelType, String collection) {
        this.client = client;
        this.modelType = modelType != null ? modelType.toUpperCase() : "DOCUMENT";
        this.collection = collection;
        this.jsonParser = new JettraJson();
    }

    public static JettraAggregation from(List<String> rawJsonDocuments) {
        JettraAggregation agg = new JettraAggregation();
        return agg;
    }

    public static JettraAggregation from(Collection<?> items) {
        JettraAggregation agg = new JettraAggregation();
        return agg;
    }

    // --- Grouping ---

    public JettraAggregation groupBy(String... fields) {
        if (fields != null) {
            for (String f : fields) {
                if (f != null && !f.isBlank()) {
                    this.groupFields.add(f.trim());
                }
            }
        }
        return this;
    }

    public JettraAggregation groupBy(List<String> fields) {
        if (fields != null) {
            for (String f : fields) {
                if (f != null && !f.isBlank()) {
                    this.groupFields.add(f.trim());
                }
            }
        }
        return this;
    }

    // --- Accumulators ---

    public JettraAggregation count() {
        return count("count");
    }

    public JettraAggregation count(String alias) {
        this.accumulators.add(new AccumulatorSpec(AccumulatorType.COUNT, null, alias != null ? alias : "count"));
        return this;
    }

    public JettraAggregation sum(String field) {
        return sum(field, "sum_" + field.replace('.', '_'));
    }

    public JettraAggregation sum(String field, String alias) {
        this.accumulators.add(new AccumulatorSpec(AccumulatorType.SUM, field, alias));
        return this;
    }

    public JettraAggregation avg(String field) {
        return avg(field, "avg_" + field.replace('.', '_'));
    }

    public JettraAggregation avg(String field, String alias) {
        this.accumulators.add(new AccumulatorSpec(AccumulatorType.AVG, field, alias));
        return this;
    }

    public JettraAggregation min(String field) {
        return min(field, "min_" + field.replace('.', '_'));
    }

    public JettraAggregation min(String field, String alias) {
        this.accumulators.add(new AccumulatorSpec(AccumulatorType.MIN, field, alias));
        return this;
    }

    public JettraAggregation max(String field) {
        return max(field, "max_" + field.replace('.', '_'));
    }

    public JettraAggregation max(String field, String alias) {
        this.accumulators.add(new AccumulatorSpec(AccumulatorType.MAX, field, alias));
        return this;
    }

    // --- Pre-Aggregation Filtering (WHERE / $match) ---

    public JettraAggregation filter(Predicate<JsonObject> predicate) {
        if (this.filterPredicate == null) {
            this.filterPredicate = predicate;
        } else {
            this.filterPredicate = this.filterPredicate.and(predicate);
        }
        return this;
    }

    // --- Post-Aggregation Filtering (HAVING) ---

    public JettraAggregation having(Predicate<AggregationRow> predicate) {
        if (this.havingPredicate == null) {
            this.havingPredicate = predicate;
        } else {
            this.havingPredicate = this.havingPredicate.and(predicate);
        }
        return this;
    }

    // --- Sorting ---

    public JettraAggregation sortBy(String field) {
        return sortBy(field, true);
    }

    public JettraAggregation sortByDesc(String field) {
        return sortBy(field, false);
    }

    public JettraAggregation sortBy(String field, boolean ascending) {
        Comparator<AggregationRow> comp = (r1, r2) -> {
            Object v1 = r1.get(field);
            Object v2 = r2.get(field);
            int res = compareObjects(v1, v2);
            return ascending ? res : -res;
        };
        if (this.customComparator == null) {
            this.customComparator = comp;
        } else {
            this.customComparator = this.customComparator.thenComparing(comp);
        }
        return this;
    }

    public JettraAggregation sort(Comparator<AggregationRow> comparator) {
        this.customComparator = comparator;
        return this;
    }

    // --- Pagination / Slicing ---

    public JettraAggregation skip(int offset) {
        this.skip = Math.max(0, offset);
        return this;
    }

    public JettraAggregation limit(int max) {
        this.limit = max;
        return this;
    }

    // --- Cross-Engine Reference Enrichment ---

    public JettraAggregation lookup(String refField, String targetAlias) {
        if (refField != null && targetAlias != null) {
            this.lookups.add(new LookupSpec(refField.trim(), targetAlias.trim()));
        }
        return this;
    }

    // --- Execution Methods ---

    /**
     * Executes the aggregation pipeline against the remote collection configured via JettraClient.
     */
    public AggregationResult execute() throws Exception {
        if (client == null) {
            throw new IllegalStateException("JettraClient is required for execute() when documents are not pre-supplied.");
        }
        List<String> rawDocs = client.listDocuments(collection);
        return execute(rawDocs);
    }

    /**
     * Executes the aggregation pipeline over a list of raw JSON document strings.
     */
    public AggregationResult execute(List<String> rawJsonDocuments) {
        if (rawJsonDocuments == null || rawJsonDocuments.isEmpty()) {
            return new AggregationResult(Collections.emptyList());
        }
        List<JsonObject> objects = new ArrayList<>(rawJsonDocuments.size());
        for (String raw : rawJsonDocuments) {
            if (raw == null || raw.isBlank()) continue;
            try {
                JsonObject jo = jsonParser.fromJson(raw, JsonObject.class);
                if (jo != null) {
                    objects.add(jo);
                }
            } catch (Exception ignored) {
            }
        }
        return executeJsonObjects(objects);
    }

    /**
     * Executes the aggregation pipeline over a collection of Java objects / records.
     */
    public AggregationResult execute(Collection<?> items) {
        if (items == null || items.isEmpty()) {
            return new AggregationResult(Collections.emptyList());
        }
        List<JsonObject> objects = new ArrayList<>(items.size());
        for (Object item : items) {
            if (item == null) continue;
            if (item instanceof JsonObject jo) {
                objects.add(jo);
            } else if (item instanceof String s) {
                try {
                    JsonObject jo = jsonParser.fromJson(s, JsonObject.class);
                    if (jo != null) objects.add(jo);
                } catch (Exception ignored) {
                }
            } else {
                try {
                    String json = jsonParser.toJson(item);
                    JsonObject jo = jsonParser.fromJson(json, JsonObject.class);
                    if (jo != null) objects.add(jo);
                } catch (Exception ignored) {
                }
            }
        }
        return executeJsonObjects(objects);
    }

    /**
     * Core aggregation logic over a list of JsonObjects.
     */
    public AggregationResult executeJsonObjects(List<JsonObject> objects) {
        if (objects == null || objects.isEmpty()) {
            return new AggregationResult(Collections.emptyList());
        }

        // 1. Pre-filtering
        List<JsonObject> filtered = objects;
        if (filterPredicate != null) {
            filtered = objects.stream().filter(filterPredicate).collect(Collectors.toList());
        }

        // If no accumulators were specified, default to count()
        List<AccumulatorSpec> effectiveSpecs = new ArrayList<>(this.accumulators);
        if (effectiveSpecs.isEmpty()) {
            effectiveSpecs.add(new AccumulatorSpec(AccumulatorType.COUNT, null, "count"));
        }

        // 2. Grouping & Accumulation
        Map<Map<String, Object>, GroupAccumulatorState> groupMap = new LinkedHashMap<>();

        for (JsonObject doc : filtered) {
            Map<String, Object> groupKeyMap = new LinkedHashMap<>();
            for (String gf : groupFields) {
                Object val = extractValue(doc, gf);
                groupKeyMap.put(gf, val != null ? val : "");
            }

            GroupAccumulatorState state = groupMap.computeIfAbsent(groupKeyMap,
                    k -> new GroupAccumulatorState(k, effectiveSpecs.size()));

            for (int i = 0; i < effectiveSpecs.size(); i++) {
                AccumulatorSpec spec = effectiveSpecs.get(i);
                switch (spec.type()) {
                    case COUNT -> state.counts[i]++;
                    case SUM -> {
                        Double num = extractNumber(doc, spec.field());
                        if (num != null) {
                            state.sums[i] += num;
                        }
                    }
                    case AVG -> {
                        Double num = extractNumber(doc, spec.field());
                        if (num != null) {
                            state.sums[i] += num;
                            state.counts[i]++;
                        }
                    }
                    case MIN -> {
                        Double num = extractNumber(doc, spec.field());
                        if (num != null) {
                            state.mins[i] = (state.mins[i] == null) ? num : Math.min(state.mins[i], num);
                        }
                    }
                    case MAX -> {
                        Double num = extractNumber(doc, spec.field());
                        if (num != null) {
                            state.maxs[i] = (state.maxs[i] == null) ? num : Math.max(state.maxs[i], num);
                        }
                    }
                }
            }
        }

        // 3. Build AggregationRows
        List<AggregationRow> rows = new ArrayList<>(groupMap.size());
        for (GroupAccumulatorState state : groupMap.values()) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (int i = 0; i < effectiveSpecs.size(); i++) {
                AccumulatorSpec spec = effectiveSpecs.get(i);
                String alias = spec.alias();
                switch (spec.type()) {
                    case COUNT -> values.put(alias, state.counts[i]);
                    case SUM -> values.put(alias, round2(state.sums[i]));
                    case AVG -> {
                        double avg = state.counts[i] > 0 ? (state.sums[i] / state.counts[i]) : 0.0;
                        values.put(alias, round2(avg));
                    }
                    case MIN -> values.put(alias, state.mins[i] != null ? round2(state.mins[i]) : null);
                    case MAX -> values.put(alias, state.maxs[i] != null ? round2(state.maxs[i]) : null);
                }
            }
            rows.add(new AggregationRow(state.groupKeyMap, values));
        }

        // 4. Cross-Engine Reference Enrichment (lookups)
        if (!lookups.isEmpty() && client != null) {
            for (AggregationRow row : rows) {
                for (LookupSpec lookup : lookups) {
                    Object refObj = row.get(lookup.refField());
                    if (refObj != null) {
                        String refUri = refObj.toString();
                        if (JettraReference.isReference(refUri)) {
                            try {
                                String resolvedJson = client.resolveRef(refUri);
                                if (resolvedJson != null && !resolvedJson.isBlank()) {
                                    JsonObject jo = jsonParser.fromJson(resolvedJson, JsonObject.class);
                                    row.setEnriched(lookup.targetAlias(), jo != null ? jo : resolvedJson);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            }
        }

        // 5. Post-aggregation filtering (HAVING)
        if (havingPredicate != null) {
            rows.removeIf(row -> !havingPredicate.test(row));
        }

        // 6. Sorting
        if (customComparator != null) {
            rows.sort(customComparator);
        }

        // 7. Slicing (skip / limit)
        if (skip > 0 || limit >= 0) {
            Stream<AggregationRow> stream = rows.stream();
            if (skip > 0) {
                stream = stream.skip(skip);
            }
            if (limit >= 0) {
                stream = stream.limit(limit);
            }
            rows = stream.collect(Collectors.toList());
        }

        return new AggregationResult(rows);
    }

    // --- Helpers ---

    public static Object extractValue(JsonObject obj, String fieldPath) {
        if (obj == null || fieldPath == null || fieldPath.isBlank()) return null;
        if (!fieldPath.contains(".")) {
            return obj.get(fieldPath);
        }
        String[] parts = fieldPath.split("\\.");
        Object curr = obj;
        for (String part : parts) {
            if (curr instanceof JsonObject jo) {
                curr = jo.get(part);
            } else {
                return null;
            }
        }
        return curr;
    }

    public static Double extractNumber(JsonObject obj, String fieldPath) {
        Object val = extractValue(obj, fieldPath);
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        if (val != null) {
            try {
                return Double.parseDouble(val.toString().trim());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int compareObjects(Object o1, Object o2) {
        if (o1 == null && o2 == null) return 0;
        if (o1 == null) return -1;
        if (o2 == null) return 1;
        if (o1 instanceof Number n1 && o2 instanceof Number n2) {
            return Double.compare(n1.doubleValue(), n2.doubleValue());
        }
        if (o1 instanceof Comparable c1 && o2 instanceof Comparable c2 && o1.getClass().isAssignableFrom(o2.getClass())) {
            return c1.compareTo(c2);
        }
        return o1.toString().compareTo(o2.toString());
    }

    private static class GroupAccumulatorState {
        final Map<String, Object> groupKeyMap;
        final long[] counts;
        final double[] sums;
        final Double[] mins;
        final Double[] maxs;

        GroupAccumulatorState(Map<String, Object> groupKeyMap, int numSpecs) {
            this.groupKeyMap = groupKeyMap;
            this.counts = new long[numSpecs];
            this.sums = new double[numSpecs];
            this.mins = new Double[numSpecs];
            this.maxs = new Double[numSpecs];
        }
    }
}

