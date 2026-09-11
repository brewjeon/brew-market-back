package com.brewmarket.error;

import com.sun.net.httpserver.HttpsServer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.ResponseEntity;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        List<FieldErrorResponse> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldErrorResponse(
                        error.getField(),
                        error.getDefaultMessage()
                ))
                .toList();

        ErrorResponse response = new ErrorResponse(
                "INVALID_REQUEST",
                "입력값을 확인해 주세요.",
                fieldErrors
        );

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception
    ) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation &&
            "uk_users_email". equals(violation.getConstraintName())) {
                ErrorResponse response = new ErrorResponse(
                        "EMAIL_ALREADY_EXISTS",
                        "이미 등록된 이메일입니다.",
                        List.of()
                );

                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(response);
            }

            cause = cause.getCause();
        }

        throw exception;
    }
}
