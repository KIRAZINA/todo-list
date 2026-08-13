package com.example.todolist.service;

import java.util.Arrays;

/**
 * Maps the API-facing {@code sortBy} query values to Task entity property
 * names, so the {@code Sort} object is always built from a whitelist rather
 * than from arbitrary client-supplied property names.
 */
public enum TaskSortField {
    createdAt("createdAt"),
    dueDate("dueDate"),
    priority("priority"),
    title("title");

    private final String entityProperty;

    TaskSortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    public String getEntityProperty() {
        return entityProperty;
    }

    public static TaskSortField fromApiValue(String value) {
        for (TaskSortField field : values()) {
            if (field.name().equalsIgnoreCase(value)) {
                return field;
            }
        }
        throw new IllegalArgumentException(
                "Invalid sortBy value: '" + value + "'. Allowed values: " + Arrays.toString(values()));
    }
}
