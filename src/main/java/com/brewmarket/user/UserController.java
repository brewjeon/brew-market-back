package com.brewmarket.user;

import jakarta.validation.Valid;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody CreateUserRequest request
    ) {
        UserResponse response = userService.create(
                request.email(),
                request.nickname()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(
            DataIntegrityViolationException exception
    ) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && "uk_users_email".equals(violation.getConstraintName())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of(
                                "code", "EMAIL_ALREADY_EXISTS",
                                "message", "이미 등록된 이메일입니다."
                        ));
            }

            cause = cause.getCause();
        }

        throw exception;
    }
}