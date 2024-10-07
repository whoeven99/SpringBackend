package com.bogdatech.model;

// TODO add lombok
public class JdbcTestModel {
    public JdbcTestModel(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    private int id;
    private String name;
}
