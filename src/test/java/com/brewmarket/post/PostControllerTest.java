package com.brewmarket.post;

import com.brewmarket.auth.AuthenticatedUser;
import com.brewmarket.auth.BrewOidcUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.brewmarket.config.SecurityConfig;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 게시글 컨트롤러를 대상으로 웹 계층 테스트
@WebMvcTest(PostController.class)
// 컨트롤러와 우리 보안 설정을 명시적으로 사용
@ContextConfiguration(classes = {
        PostController.class,
        SecurityConfig.class
})
class PostControllerTest {

    // 서버 포트를 열지 않고 웹 요청을 테스트
    @Autowired
    private MockMvc mockMvc;

    // 실제 저장 대신 호출 내용과 반환값을 제어할 서비스 대역
    @MockitoBean
    private PostService postService;

    @Test
    void createsPostUsingAuthenticatedUserId() throws Exception {
        // 공통 준비 메서드로 회원 번호 42인 로그인 사용자 생성
        BrewOidcUser user = authenticatedUser(42L);

        // 지정한 값으로 서비스가 호출되면 게시글 번호 101을 반환
        when(postService.create(
                42L,
                "머그컵",
                "사용하지 않는 컵입니다.",
                3000L
        )).thenReturn(101L);

        // 로그인 상태와 CSRF 토큰을 포함해 실제 컨트롤러에 요청
        mockMvc.perform(post("/posts")
                        .with(oidcLogin().oidcUser(user))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "머그컵",
                                    "description": "사용하지 않는 컵입니다.",
                                    "price": 3000
                                }
                                """))
                // 생성 성공 상태와 서비스가 반환한 게시글 번호 확인
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(101));

        // 로그인한 회원 번호와 게시글 내용이 전달됐는지 확인
        verify(postService).create(
                42L,
                "머그컵",
                "사용하지 않는 컵입니다.",
                3000L
        );
    }

    @Test
    void rejectsPostCreationWithoutAuthentication() throws Exception {
        // CSRF 토큰은 정상으로 넣고 로그인 정보만 생략
        mockMvc.perform(post("/posts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "머그컵",
                                    "description": "사용하지 않는 컵입니다.",
                                    "price": 3000
                                }
                                """))
                // 로그인하지 않은 요청은 401로 거부
                .andExpect(status().isUnauthorized());

        // 거부된 요청이 등록 서비스까지 도달하지 않았는지 확인
        verifyNoInteractions(postService);
    }

    // 테스트에 필요한 로그인 사용자 객체 생성
    private BrewOidcUser authenticatedUser(Long userId) {
        // 실제 구글 토큰이 아닌 테스트용 정보
        OidcIdToken idToken = OidcIdToken.withTokenValue("test-id-token")
                .claim("sub", "test-google-account")
                .build();

        DefaultOidcUser oidcUser = new DefaultOidcUser(
                AuthorityUtils.createAuthorityList("ROLE_USER"),
                idToken
        );

        // 호출한 테스트가 지정한 브루마켓 회원 번호 사용
        return new BrewOidcUser(
                oidcUser,
                new AuthenticatedUser(userId)
        );
    }

    @Test
    void rejectsPostCreationWithoutCsrfToken() throws Exception {
        // 로그인 조건은 정상으로 준비
        BrewOidcUser user = authenticatedUser(42L);

        mockMvc.perform(post("/posts")
                        .with(oidcLogin().oidcUser(user))
                        // CSRF 토큰은 의도적으로 넣지 않음
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "title": "머그컵",
                                "description": "사용하지 않는 컵입니다.",
                                "price": 3000
                            }
                            """))
                // 로그인되어 있어도 CSRF 조건을 충족하지 못하면 거부
                .andExpect(status().isForbidden());

        // 거부된 요청은 등록 서비스까지 도달하지 않아야 함
        verifyNoInteractions(postService);
    }
}