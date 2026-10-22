package com.brewmarket.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticatedUserTest {

    @Test
    void keepsPositiveUserId() {
        // 정상 ID로 객체 생성
        AuthenticatedUser user = new AuthenticatedUser(1L);

        // 전달한 ID가 그대로 보관되는지 확인
        assertEquals(Long.valueOf(1L), user.userId());
    }

    @Test
    void rejectsNullUserId() {
        // 회원 ID가 없으면 생성 거부
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthenticatedUser(null)
        );
    }

    @Test
    void rejectsZeroUserId() {
        // 0은 양수가 아니므로 생성 거부
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthenticatedUser(0L)
        );
    }

    @Test
    void rejectsNegativeUserId() {
        // 음수 ID도 생성 거부
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthenticatedUser(-1L)
        );
    }
}
