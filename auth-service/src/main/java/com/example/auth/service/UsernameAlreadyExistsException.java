package com.example.auth.service;

public class UsernameAlreadyExistsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UsernameAlreadyExistsException() {
        super("Username is already taken");
    }
}
