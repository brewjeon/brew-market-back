package com.brewmarket.post;

public class PostReservationConflictException extends RuntimeException {
    public PostReservationConflictException(String message) {
        super(message);
    }
}
