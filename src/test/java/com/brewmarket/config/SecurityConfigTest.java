package com.brewmarket.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;

// 필요한 웹 계층을 중심으로 테스트 환경 구성
@WebMvcTest(SecurityConfigTest.TestPostController.class)
// 테스트할 보안 설정과 요청 처리기를 명시적으로 등록
@Import({SecurityConfig.class, SecurityConfigTest.TestPostController.class})
class SecurityConfigTest {

    // 테스트 환경에서 준비한 웹 요청 도구를 받아 사용
    @Autowired
    private MockMvc mockMvc;

    // 보안 검사를 통과한 요청이 도착할 테스트용 처리기
    @RestController
    static class TestPostController {

        @PostMapping("/posts")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void create() {
            // 저장은 하지 않음
            // 요청이 이곳까지 도착하면 204 반환
        }
    }

    // 이 테스트에서 사용할 최소한의 부트 설정
    @SpringBootConfiguration
    static class TestConfiguration {

    }

    @Test
    void rejectsPostCreationWithoutAuthentication() throws Exception {
        // CSRF 조건은 충족하고 로그인 정보만 생략
        // 인증되지 않은 요청이 401로 거부되는지 확인
        mockMvc.perform(post("/posts")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void allowsAuthenticatedRequestToReachController() throws Exception {
        // 테스트용 인증 정보와 정상 CSRF 토큰을 함께 사용
        // 보안 검사를 통과하면 테스트용 처리기가 204 반환
        mockMvc.perform(post("/posts")
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void logsOutWithValidCsrfToken() throws Exception {
        // 인증 정보와 정상 CSRF 토큰을 포함해 로그아웃 요청
        mockMvc.perform(post("/logout")
                        .with(csrf()))
                // 본문 없이 정상 처리
                .andExpect(status().isNoContent())
                // 요청 처리 후 인증 정보가 제거되었는지 확인
                .andExpect(unauthenticated());
    }

    @Test
    @WithMockUser
    void rejectsLogoutWithoutCsrfToken() throws Exception {
        // 로그인되어 있어도 CSRF 토큰이 없으면 요청 거부
        mockMvc.perform(post("/logout"))
                .andExpect(status().isForbidden());
    }
}
