package com.jettra.driver.java;

/**
 * Fluent API for JettraStoreEngine queries.
 */
public class JettraFluentQuery {
    
    private final JettraClient client;
    private final String modelType;
    private String collection;
    
    public JettraFluentQuery(JettraClient client, String modelType) {
        this.client = client;
        this.modelType = modelType;
    }
    
    public JettraFluentQuery collection(String collectionName) {
        this.collection = collectionName;
        return this;
    }
    
    public boolean insert(String id, String jsonDocument) {
        try {
            return client.insertModel(modelType, collection, id, jsonDocument);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public String get(String id) {
        try {
            return client.getModel(modelType, collection, id);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
