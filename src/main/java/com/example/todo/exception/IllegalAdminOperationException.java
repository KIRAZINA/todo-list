package com.example.todo.exception;

public class IllegalAdminOperationException extends RuntimeException {

    public IllegalAdminOperationException(String message) {
        super(message);
    }
}
