package com.jettra.driver.java;

import io.jettra.json.JsonArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Result of executing a JettraAggregation query pipeline.
 * Contains aggregated rows, metadata, and JSON serialization helpers.
 */
public class AggregationResult implements Iterable<AggregationRow> {

    private final List<AggregationRow> rows;

    public AggregationResult(List<AggregationRow> rows) {
        this.rows = rows != null ? new ArrayList<>(rows) : new ArrayList<>();
    }

    public List<AggregationRow> getRows() {
        return Collections.unmodifiableList(rows);
    }

    public int size() {
        return rows.size();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public AggregationRow get(int index) {
        return rows.get(index);
    }

    public Optional<AggregationRow> getFirst() {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public <R extends Record> Optional<R> getFirstAsRecord(Class<R> recordClass) {
        return getFirst().map(row -> row.toRecord(recordClass));
    }

    public <R extends Record> List<R> toRecordList(Class<R> recordClass) {
        return JettraRecordMapper.toRecordList(rows, recordClass);
    }

    public Stream<AggregationRow> stream() {
        return rows.stream();
    }

    @Override
    public Iterator<AggregationRow> iterator() {
        return rows.iterator();
    }

    public JsonArray toJsonArray() {
        JsonArray array = new JsonArray();
        for (AggregationRow row : rows) {
            array.add(row.toJsonObject());
        }
        return array;
    }

    public String toJson() {
        return toJsonArray().toString();
    }

    @Override
    public String toString() {
        return toJson();
    }
}
