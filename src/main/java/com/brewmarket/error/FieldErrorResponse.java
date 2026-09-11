package com.brewmarket.error;

public record FieldErrorResponse(
        String field,
        String message
) {

}
