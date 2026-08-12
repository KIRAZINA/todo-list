package com.example.todolist.exception;

/**
 * Thrown when attempting to create a resource that already exists
 * (e.g., duplicate username or email during registration).
 */
public class ResourceAlreadyExistsException extends RuntimeException {
    public ResourceAlreadyExistsException(String message) {
        super(message);
    }
}
