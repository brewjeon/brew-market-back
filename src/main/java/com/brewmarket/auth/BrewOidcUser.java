package com.brewmarket.auth;

import lombok.Getter;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class BrewOidcUser extends DefaultOidcUser {

    @Getter
    private final Long userId;

    public BrewOidcUser (
             OidcUser oidcUser,
             AuthenticatedUser authenticatedUser
    ) {
        // 기존 인증 번호는 유지
        super(
                oidcUser.getAuthorities(),
                oidcUser.getIdToken(),
                oidcUser.getUserInfo()
        );

        if (authenticatedUser == null) {
            throw new IllegalArgumentException(
                    "브루마켓 회원 정보는 필수입니다."
            );
        }

        // 기존 AuthenticatedUser에서 검증한 회원 번호 보관
        this.userId = authenticatedUser.userId();
    }
}
