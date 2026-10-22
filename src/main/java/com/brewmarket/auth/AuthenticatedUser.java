package com.brewmarket.auth;

// 인증된 사용자를 브루마켓 회원 ID로 표현
public record AuthenticatedUser(Long userId) {

    // 생성할 때 회원 ID의 기본 형식 검사
    public AuthenticatedUser {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "회원 ID는 양수여야 합니다."
            );
        }
    }
}