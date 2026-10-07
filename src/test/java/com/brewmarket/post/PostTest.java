package com.brewmarket.post;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 스프링이나 DB를 시작하는 어노테이션이 없습니다.
// Post 객체의 생성 규칙만 검증하기 때문입니다.
class PostTest {

    @Test
    void rejectsNegativePrice() {
        // 준비: 판매자 ID·제목·설명은 유효한 값입니다.
        // 실행: 가격만 음수로 넣어 객체 생성을 시도합니다.
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Post(
                        1L,
                        "원목 의자 판매합니다",
                        "상태가 양호한 의자입니다.",
                        -1L
                )
        );

        // 검증: 가격 규칙 때문에 거부된 것이 맞는지 확인합니다.
        assertEquals(
                "가격은 0 이상이어야 합니다.",
                exception.getMessage()
        );
    }

    @Test
    void rejectsBlankTitle() {
        // 실행: 가격 등 다른 값은 유효하지만 제목은 공백뿐입니다.
        // 빈 문자열뿐 아니라 공백만 있는 제목도 거부해야 합니다.
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Post(
                        1L,
                        "   ",
                        "상태가 양호한 의자입니다.",
                        15000L
                )
        );

        // 검증: 제목 규칙 때문에 거부되었는지 확인합니다.
        assertEquals(
                "제목은 공백이 아닌 100자 이하여야 합니다.",
                exception.getMessage()
        );
    }
}