package com.brewmarket.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class SocialAccountServiceTest {

    //실제 DB에 연결하지 않는 테스트용 저장소
    private SocialAccountRepository socialAccountRepository;

    // 검증 대상인 서비스는 실제 객체 사용
    private SocialAccountService socialAccountService;

    @BeforeEach
    void setUp() {
        // 테스트마드 새로운 저장소 대역 생성
        socialAccountRepository = mock(SocialAccountRepository.class);

        // Lombok이 만든 생성자에 저장소 대역을 직접 전달
        socialAccountService = new SocialAccountService(socialAccountRepository);
    }

    @Test
    void returnUserIdWhenAccountExists() {
        // 준비: 10번 회원과 연결된 소셜 계정 객체 생성
        // DB에 저장하는 것은 아님
        SocialAccount account = new SocialAccount(
                10L,
                AuthProvider.GOOGLE,
                "google-account-A"
        );

        // 이 조건으로 조회하면 위 연결 정보를 반환하도록 대역 설정
        when(socialAccountRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                "google-account-A"
        )).thenReturn(Optional.of(account));

        // 실행: 실제 서비스의 조회 메서드 호출
        Optional<Long> result = socialAccountService.findUserId(
                AuthProvider.GOOGLE,
                "google-account-A"
        );

        // 검증: 소셜 계정의 ID가 아닌 브루마켓 회원 번호를 반환해야 함
        assertEquals(Optional.of(10L), result);

        // 제공자와 외부 식별자를 정확히 전달해 조회했는지 확인
        verify(socialAccountRepository)
                .findByProviderAndProviderSubject(
                        AuthProvider.GOOGLE,
                        "google-account-A"
                );
    }

    @Test
    void returnsEmptyWhenAccountDoesNotExist() {
        // 준비: 연결 정보가 없는 상황 설정
        when(socialAccountRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE,
                "unknown-account"
        )).thenReturn(Optional.empty());

        // 실행: 연결되지 않은 계정으로 회원 번호 조회
        Optional<Long> result = socialAccountService.findUserId(
                AuthProvider.GOOGLE,
                "unknown-account"
        );

        // 검증: 임의의 회원 번호를 만들지 않고 결과 없음 반환
        assertTrue(result.isEmpty());

        // 결과가 비어 있어도 실제로 조회를 요청했는지 확인
        verify(socialAccountRepository)
                .findByProviderAndProviderSubject(
                        AuthProvider.GOOGLE,
                        "unknown-account"
                );
    }
}





















