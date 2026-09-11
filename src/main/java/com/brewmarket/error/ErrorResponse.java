package com.brewmarket.error;

import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        List<FieldErrorResponse> fieldErrors
) {
}