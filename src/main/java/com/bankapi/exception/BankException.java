package com.bankapi.exception;

import org.springframework.http.HttpStatus;

public class BankException extends RuntimeException {

    private final HttpStatus status;

    public BankException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static BankException notFound(String message) {
        return new BankException(HttpStatus.NOT_FOUND, message);
    }

    public static BankException conflict(String message) {
        return new BankException(HttpStatus.CONFLICT, message);
    }

    public static BankException unprocessable(String message) {
        return new BankException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public static BankException unauthorized(String message) {
        return new BankException(HttpStatus.UNAUTHORIZED, message);
    }
}
