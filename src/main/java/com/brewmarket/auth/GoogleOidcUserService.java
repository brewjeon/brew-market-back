package com.brewmarket.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleOidcUserService extends OidcUserService {

    private final GoogleAccountService googleAccountService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest)
            throws OAuth2AuthenticationException {

        // 현재 이 서비스는 구글 로그인만 처리
        String registrationId = userRequest
                .getClientRegistration()
                .getRegistrationId();

        if (!"google".equals(registrationId)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("unsupported_provider"),
                    "지원하지 않는 로그인 제공자입니다."
            );
        }

        // 스프링의 기본 사용자 정보 조회·처리 로직 실행
        // 토큰 검증 등 기존 OIDC 인증 흐름을 그대로 사용
        OidcUser oidcUser = super.loadUser(userRequest);

        try {
            // 구글 인증 결과로 내부 회원 번호 조회 또는 신규 가입
            // 구글 이름 대신 공개 가능한 기본 닉네임 사용
            Long userId = googleAccountService.getOrCreateUserId(
                    oidcUser.getSubject(),
                    oidcUser.getEmail(),
                    Boolean.TRUE.equals(oidcUser.getEmailVerified()),
                    "브루회원"
            );

            // 내부 회원 번호를 포함한 인증 사용자 반환
            return new BrewOidcUser(
                    oidcUser,
                    new AuthenticatedUser(userId)
            );

        } catch (IllegalArgumentException | IllegalStateException | DataAccessException exception) {

            // 가입 거부나 DB 실패를 로그인 실패로 전달
            // 내부 예외 메시지를 브라우저에 그대로 노출하지 않음
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("brew_login_failed"),
                    "브루마켓 로그인 처리에 실패했습니다.",
                    exception
            );
        }
    }
}
