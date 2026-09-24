package com.jettra.driver.java;

import io.jettra.json.JettraJson;
import io.jettra.json.JsonObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository pattern implementation for JettraStoreEngine.
 * Allows mapping a Java class or Record directly to a Model Collection.
 */
public class JettraRepository<T> {

    private final JettraClient client;
    private final Class<T> entityClass;
    private final String modelType;
    private final String collection;
    private final JettraJson gson;

    public JettraRepository(JettraClient client, Class<T> entityClass, String modelType, String collection) {
        this.client = client;
        this.entityClass = entityClass;
        this.modelType = modelType != null ? modelType.toUpperCase() : "DOCUMENT";
        this.collection = collection;
        this.gson = new JettraJson();
    }

    public boolean save(T entity) {
        if (entity == null)
            return false;
        String id = extractEntityId(entity);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        return save(id, entity);
    }

    public boolean save(String id, T entity) {
        try {
            if ("RECORDS".equalsIgnoreCase(modelType) && entity instanceof Record r) {
                return client.saveRecord(collection, id, r);
            }
            if ("DOCUMENT".equalsIgnoreCase(modelType)) {
                String json = gson.toJson(entity);
                return client.insertDocument(collection, id, json);
            }
            String json = gson.toJson(entity);
            return client.insertModel(modelType, collection, id, json);
        } catch (Exception e) {
            return false;
        }
    }

    public Optional<T> findById(String id) {
        return findById(id, null);
    }

    public Optional<T> findById(String id, List<String> fields) {
        try {
            if ("RECORDS".equalsIgnoreCase(modelType) && Record.class.isAssignableFrom(entityClass)) {
                @SuppressWarnings("unchecked")
                Class<? extends Record> recClass = (Class<? extends Record>) entityClass;
                Optional<? extends Record> rec = client.getRecord(collection, id, fields, recClass);
                if (rec.isPresent()) {
                    @SuppressWarnings("unchecked")
                    T casted = (T) rec.get();
                    return Optional.of(casted);
                }
                return Optional.empty();
            }

            String json = "DOCUMENT".equalsIgnoreCase(modelType)
                    ? client.getDocument(collection, id)
                    : client.getModel(modelType, collection, id);

            if (json != null && !json.isBlank()) {
                if (entityClass.isRecord()) {
                    JsonObject root = gson.fromJson(json, JsonObject.class);
                    if (root != null && root.has("components")) {
                        Object comps = root.get("components");
                        String compJson = comps instanceof JsonObject ? comps.toString() : gson.toJson(comps);
                        return Optional.of(gson.fromJson(compJson, entityClass));
                    }
                    @SuppressWarnings("unchecked")
                    Class<? extends Record> recType = (Class<? extends Record>) entityClass;
                    return Optional.ofNullable((T) JettraRecordMapper.toRecord(json, recType));
                }
                return Optional.of(gson.fromJson(json, entityClass));
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    public boolean existsById(String id) {
        return findById(id).isPresent();
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

    public List<T> findAll() {
        List<T> list = new ArrayList<>();
        try {
            if ("DOCUMENT".equalsIgnoreCase(modelType)) {
                List<String> rawDocs = client.listDocuments(collection);
                for (String raw : rawDocs) {
                    try {
                        T item = gson.fromJson(raw, entityClass);
                        if (item != null)
                            list.add(item);
                        if (entityClass.isRecord()) {
                            @SuppressWarnings("unchecked")
                            Class<? extends Record> recType = (Class<? extends Record>) entityClass;
                            T item = (T) JettraRecordMapper.toRecord(raw, recType);
                            if (item != null) list.add(item);
                        } else {
                            T item = gson.fromJson(raw, entityClass);
                            if (item != null) list.add(item);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public List<T> find(java.util.function.Predicate<T> filter) {
        List<T> all = findAll();
        if (filter == null) return all;
        return all.stream().filter(filter).toList();
    }

    public Optional<T> findFirst(java.util.function.Predicate<T> filter) {
        return find(filter).stream().findFirst();
    }

    public long count() {
        return findAll().size();
    }

    public JettraAggregation aggregate() {
        return new JettraAggregation(client, modelType, collection);
    }

    private String extractEntityId(T entity) {
        if (entity instanceof Record r) {
            try {
                for (java.lang.reflect.RecordComponent rc : r.getClass().getRecordComponents()) {
                    String name = rc.getName();
                    if ("id".equalsIgnoreCase(name) || "code".equalsIgnoreCase(name) || "key".equalsIgnoreCase(name)) {
                        Object val = rc.getAccessor().invoke(r);
                        if (val != null)
                            return val.toString();
                    }
                }
            } catch (Exception ignored) {
            }
        }

        try {
            Method m = entityClass.getMethod("getId");
            Object val = m.invoke(entity);
            if (val != null)
                return val.toString();
        } catch (Exception ignored) {
        }

        try {
            Method m = entityClass.getMethod("id");
            Object val = m.invoke(entity);
            if (val != null)
                return val.toString();
        } catch (Exception ignored) {
        }

        return null;
    }
}
