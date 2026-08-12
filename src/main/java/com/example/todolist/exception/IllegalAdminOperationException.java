package com.example.todolist.exception;

public class IllegalAdminOperationException extends RuntimeException {

    public IllegalAdminOperationException(String message) {
        super(message);
    }
}
