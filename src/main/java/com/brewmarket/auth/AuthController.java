package com.brewmarket.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @GetMapping("/me")
    public AuthenticatedUser me(
            @AuthenticationPrincipal BrewOidcUser user
    ) {
        // 예상한 인증 사용자 정보가 없으면 거부
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        // 회원 번호만 응답하고 인증 토큰 등은 노출하지 않음
        return new AuthenticatedUser(user.getUserId());
    }
}
