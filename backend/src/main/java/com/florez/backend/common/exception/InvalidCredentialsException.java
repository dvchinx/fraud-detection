package com.florez.backend.common.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Credenciales inválidas");
    }
}
