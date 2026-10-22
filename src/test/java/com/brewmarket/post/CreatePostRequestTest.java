package com.brewmarket.post;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatePostRequestTest {

    // 검증기 생성과 관련 자원 관리를 담당
    // 이 클래스의 테스트에서 함께 사용
    private static ValidatorFactory factory;

    // 요청 객체의 검증 어노테이션을 실제로 검사하는 객체
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        // 전체 테스트 시작 전에 한 번만 준비
        // Spring을 실행하지 않고 검증기를 직접 생성
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        // 전체 테스트 종료 후 사용한 자원 정리
        // 생성에 실패했을 경우를 고려해 null 여부 확인
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    void acceptsZeroPrice() {
        // 0원은 가격 누락이 아니라 나눔을 의미
        // 제목과 설명도 정상 값으로 설정
        CreatePostRequest request =
                new CreatePostRequest("책 나눔", "깨끗한 책입니다.", 0L);

        // 객체 생성만으로는 검증되지 않으므로 검증기를 직접 호출
        // 규칙 위반이 있으면 결과 모음에 담겨 반환됨
        var violations = validator.validate(request);

        // 나눔은 허용하므로 위반 결과가 없어야 함
        assertTrue(violations.isEmpty());
    }

    @Test
    void rejectsMissingPrice() {
        // 가격만 누락한 상황
        // Long 타입이라 0원과 null을 구분할 수 있음
        CreatePostRequest request =
                new CreatePostRequest("책 판매", "깨끗한 책입니다.", null);

        var violations = validator.validate(request);

        // @PositiveOrZero는 null을 허용
        // 따라서 @NotNull 위반 하나만 나와야 함
        assertEquals(1, violations.size());

        // 결과가 한 개임을 확인한 뒤 해당 항목을 꺼냄
        var violation = violations.iterator().next();

        // 가격 필드에서 예상한 오류가 발생했는지 확인
        assertEquals("price", violation.getPropertyPath().toString());
        assertEquals("가격은 필수입니다.", violation.getMessage());
    }

    @Test
    void rejectsNegativePrice() {
        // 가격만 음수로 설정. 나머지는 정상 값
        CreatePostRequest request =
                new CreatePostRequest("책 판매", "깨끗한 책입니다.", -1L);

        var violations = validator.validate(request);

        // 값은 있지만 음수이므로 @PositiveOrZero 위반
        assertEquals(1, violations.size());

        var violation = violations.iterator().next();

        assertEquals("price", violation.getPropertyPath().toString());
        assertEquals("가격은 0 이상이어야 합니다.", violation.getMessage());
    }

    @Test
    void rejectsBlankTitle() {
        // 제목만 공백으로 설정
        // 다른 값은 정상으로 두어 제목 검증만 따로 확인
        CreatePostRequest request =
                new CreatePostRequest("   ", "깨끗한 책입니다.", 1000L);

        var violations = validator.validate(request);

        // 길이 제한은 넘지 않지만 공백이므로 @NotBlank 위반
        assertEquals(1, violations.size());

        var violation = violations.iterator().next();

        assertEquals("title", violation.getPropertyPath().toString());
        assertEquals("제목은 필수입니다.", violation.getMessage());
    }

    @Test
    void rejectsTitleLongerThan100Characters() {
        // repeat(101): 같은 문자열을 101번 반복
        // 허용 길이인 100자를 한 글자 초과한 값으로 검사
        String title = "가".repeat(101);

        CreatePostRequest request =
                new CreatePostRequest(title, "깨끗한 책입니다.", 1000L);

        var violations = validator.validate(request);

        // 공백은 아니지만 길이를 초과했으므로 @Size 위반
        assertEquals(1, violations.size());

        var violation = violations.iterator().next();

        assertEquals("title", violation.getPropertyPath().toString());
        assertEquals("제목은 100자 이하여야 합니다.", violation.getMessage());
    }

    @Test
    void rejectsBlankDescription() {
        // 설명만 공백으로 설정
        CreatePostRequest request =
                new CreatePostRequest("책 판매", "   ", 1000L);

        var violations = validator.validate(request);

        // 설명의 @NotBlank 위반 하나만 나와야 함
        assertEquals(1, violations.size());

        var violation = violations.iterator().next();

        assertEquals("description", violation.getPropertyPath().toString());
        assertEquals("설명은 필수입니다.", violation.getMessage());
    }
}