package com.jettra.driver.java;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Fluent API for JettraStoreEngine queries and operations across all 9 storage
 * engines.
 */
public class JettraFluentQuery {

    private final JettraClient client;
    private final String modelType;
    private String collection;
    private List<String> projectedFields;

    public JettraFluentQuery(JettraClient client, String modelType) {
        this.client = client;
        this.modelType = modelType != null ? modelType.toUpperCase() : "DOCUMENT";
        this.projectedFields = new ArrayList<>();
    }

    public JettraFluentQuery collection(String collectionName) {
        this.collection = collectionName;
        return this;
    }

    public JettraFluentQuery fields(String... fields) {
        if (fields != null) {
            this.projectedFields.addAll(Arrays.asList(fields));
        }
        return this;
    }

    public JettraFluentQuery project(List<String> fields) {
        if (fields != null) {
            this.projectedFields.addAll(fields);
        }
        return this;
    }

    public boolean insert(String id, String jsonDocument) {
        try {
            if ("DOCUMENT".equalsIgnoreCase(modelType)) {
                return client.insertDocument(collection, id, jsonDocument);
            }
            return client.insertModel(modelType, collection, id, jsonDocument);
        } catch (Exception e) {
            return false;
        }
    }

    public String insertAuto(String jsonDocument, JettraClient.IdMode idMode) {
        try {
            return client.insertDocumentAuto(collection, jsonDocument,
                    idMode != null ? idMode : JettraClient.IdMode.UUID);
        } catch (Exception e) {
            return null;
        }
    }

    public String get(String id) {
        try {
            if ("DOCUMENT".equalsIgnoreCase(modelType)) {
                return client.getDocument(collection, id);
            }
            if ("RECORDS".equalsIgnoreCase(modelType) && projectedFields != null && !projectedFields.isEmpty()) {
                return client.getRecordFields(collection, id, projectedFields)
                        .map(Object::toString)
                        .orElse(null);
            }
            return client.getModel(modelType, collection, id);
        } catch (Exception e) {
            return null;
        }
    }

    public <R extends Record> Optional<R> get(String id, Class<R> recordClass) {
        try {
            if ("RECORDS".equalsIgnoreCase(modelType)) {
                return client.getRecord(collection, id, projectedFields, recordClass);
            }
            String json = get(id);
            if (json != null && !json.isBlank()) {
                io.jettra.json.JettraJson parser = new io.jettra.json.JettraJson();
                return Optional.ofNullable(parser.fromJson(json, recordClass));
                return Optional.ofNullable(JettraRecordMapper.toRecord(json, recordClass));
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    public boolean delete(String id) {
        try {
            if ("DOCUMENT".equalsIgnoreCase(modelType)) {
                return client.deleteDocument(collection, id);
            }
            return client.deleteModel(modelType, collection, id);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean exists(String id) {
        String val = get(id);
        return val != null && !val.isBlank();
    }

    public List<String> list() {
        try {
            if ("DOCUMENT".equalsIgnoreCase(modelType)) {
                return client.listDocuments(collection);
            }
        } catch (Exception ignored) {
        }
        return new ArrayList<>();
    }

    public <R extends Record> List<R> list(Class<R> recordClass) {
        List<String> rawList = list();
        return JettraRecordMapper.toRecordList(rawList, recordClass);
    }

    public <R extends Record> List<R> filter(java.util.function.Predicate<R> predicate, Class<R> recordClass) {
        List<R> records = list(recordClass);
        if (predicate == null) return records;
        return records.stream().filter(predicate).toList();
    }

    public String resolveRef(String refUri) {
        try {
            return client.resolveRef(refUri);
        } catch (Exception e) {
            return null;
        }
    }

    public String resolveRef(JettraReference ref) {
        try {
            return client.resolveRef(ref);
        } catch (Exception e) {
            return null;
        }
    }

    public <R extends Record> Optional<R> resolveRef(String refUri, Class<R> recordClass) {
        try {
            return client.resolveRef(refUri, recordClass);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public <R extends Record> Optional<R> resolveRef(JettraReference ref, Class<R> recordClass) {
        try {
            return client.resolveRef(ref, recordClass);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public JettraAggregation aggregate() {
        return new JettraAggregation(client, modelType, collection);
    }
}
