package com.brewmarket.post;

import com.brewmarket.BrewMarketBackApplication;
import com.brewmarket.auth.AuthenticatedUser;
import com.brewmarket.auth.BrewOidcUser;
import com.brewmarket.user.User;
import com.brewmarket.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 개발 DB가 아닌 테스트 전용 DB 사용. 요청이 끝난 뒤 커밋된 값을 재조회
@Testcontainers
@SpringBootTest(classes = BrewMarketBackApplication.class)
@AutoConfigureMockMvc
class PostDeleteTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer database = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:17-3.5")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PostRepository postRepository;

    @Test
    void ownerDeletesPostPermanently() throws Exception {
        User seller = userRepository.saveAndFlush(
                new User(UUID.randomUUID() + "@example.com", "작성자")
        );
        Post saved = postRepository.saveAndFlush(
                new Post(seller.getId(), "삭제할 머그컵", "사용하지 않는 컵", 3000L)
        );
        Post original = postRepository.findById(saved.getId()).orElseThrow();
        long countBefore = postRepository.count();

        // 삭제 전에는 공개 목록과 상세에서 찾을 수 있어야 함
        String targetInList = "$[?(@.id == " + original.getId() + ")]";
        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(targetInList).isNotEmpty());
        mockMvc.perform(get("/posts/{id}", original.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/posts/{id}", original.getId())
                        .with(oidcLogin().oidcUser(authenticatedUser(seller.getId())))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // 데이터베이스에서도 게시글 행이 사라져야 한다.
        assertFalse(postRepository.existsById(original.getId()));
        assertEquals(countBefore - 1, postRepository.count());

        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(targetInList).isEmpty());
        mockMvc.perform(get("/posts/{id}", original.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    void anotherUserCannotDeleteAndPostRemainsVisible() throws Exception {
        User seller = userRepository.saveAndFlush(
                new User(UUID.randomUUID() + "@example.com", "작성자")
        );
        User other = userRepository.saveAndFlush(
                new User(UUID.randomUUID() + "@example.com", "다른 회원")
        );
        Post saved = postRepository.saveAndFlush(
                new Post(seller.getId(), "보존할 머그컵", "원래 본문", 3000L)
        );
        Post original = postRepository.findById(saved.getId()).orElseThrow();
        long countBefore = postRepository.count();

        // 로그인과 CSRF 조건은 통과시키고 작성자 권한만 다르게 준비
        mockMvc.perform(delete("/posts/{id}", original.getId())
                        .with(oidcLogin().oidcUser(authenticatedUser(other.getId())))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("POST_ACCESS_DENIED"));

        Post preserved = postRepository.findById(original.getId()).orElseThrow();
        assertNull(preserved.getDeletedAt());
        assertEquals(countBefore, postRepository.count());
        assertEquals(original.getSellerId(), preserved.getSellerId());
        assertEquals(original.getTitle(), preserved.getTitle());
        assertEquals(original.getDescription(), preserved.getDescription());
        assertEquals(original.getPrice(), preserved.getPrice());
        assertEquals(original.getStatus(), preserved.getStatus());
        assertEquals(original.getCreatedAt(), preserved.getCreatedAt());

        mockMvc.perform(get("/posts/{id}", original.getId()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + original.getId() + ")]").isNotEmpty());
    }

    private BrewOidcUser authenticatedUser(Long userId) {
        OidcIdToken idToken = OidcIdToken.withTokenValue("test-id-token")
                .claim("sub", "test-google-" + userId).build();
        DefaultOidcUser oidcUser = new DefaultOidcUser(
                AuthorityUtils.createAuthorityList("ROLE_USER"), idToken);
        return new BrewOidcUser(oidcUser, new AuthenticatedUser(userId));
    }
}
