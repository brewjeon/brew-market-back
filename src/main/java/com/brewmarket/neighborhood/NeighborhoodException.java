package com.brewmarket.neighborhood;

import org.springframework.http.HttpStatus;

public class NeighborhoodException extends RuntimeException {
    private final HttpStatus status;

    NeighborhoodException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
