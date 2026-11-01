package com.brewmarket.post;

import com.brewmarket.BrewMarketBackApplication;
import com.brewmarket.auth.AuthenticatedUser;
import com.brewmarket.auth.BrewOidcUser;
import com.brewmarket.user.User;
import com.brewmarket.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 테스트 전체를 트랜잭션으로 감싸지 않아 HTTP 처리 후 커밋된 DB 값을 확인합니다.
@Testcontainers
@SpringBootTest(classes = BrewMarketBackApplication.class)
@AutoConfigureMockMvc
class PostUpdateTest {
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

    private User seller;
    private Post original;

    private static final String VALID_BODY = """
            {"title":"수정한 제목","description":"수정한 설명","price":0}
            """;

    @BeforeEach
    void preparePost() {
        seller = userRepository.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "작성자"));
        Post saved = postRepository.saveAndFlush(new Post(seller.getId(), "원래 제목", "원래 설명", 3000L));
        original = postRepository.findById(saved.getId()).orElseThrow();
        assertNotNull(original.getCreatedAt());
    }

    @Test
    void ownerUpdatesContentAndPublicDetailReflectsCommittedChanges() throws Exception {
        mockMvc.perform(patch("/posts/{id}", original.getId())
                        .with(oidcLogin().oidcUser(authenticatedUser(seller.getId())))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNoContent());

        Post updated = postRepository.findById(original.getId()).orElseThrow();
        assertEquals("수정한 제목", updated.getTitle());
        assertEquals("수정한 설명", updated.getDescription());
        assertEquals(0L, updated.getPrice());
        assertEquals(original.getSellerId(), updated.getSellerId());
        assertEquals(original.getStatus(), updated.getStatus());
        assertEquals(original.getCreatedAt(), updated.getCreatedAt());

        mockMvc.perform(get("/posts/{id}", original.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정한 제목"))
                .andExpect(jsonPath("$.description").value("수정한 설명"))
                .andExpect(jsonPath("$.price").value(0));
    }

    @Test
    void anotherUserCannotUpdateEvenWhenBodyClaimsOwnership() throws Exception {
        User other = userRepository.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "다른 회원"));
        String spoofedBody = """
                {"title":"수정한 제목","description":"수정한 설명","price":0,"sellerId":%d}
                """.formatted(seller.getId());

        mockMvc.perform(patch("/posts/{id}", original.getId())
                        .with(oidcLogin().oidcUser(authenticatedUser(other.getId())))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(spoofedBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("POST_ACCESS_DENIED"));
        assertPostUnchanged();
    }

    @Test
    void missingPostReturnsNotFound() throws Exception {
        assertFalse(postRepository.existsById(Long.MAX_VALUE));
        long countBefore = postRepository.count();
        mockMvc.perform(patch("/posts/{id}", Long.MAX_VALUE)
                        .with(oidcLogin().oidcUser(authenticatedUser(seller.getId())))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        assertEquals(countBefore, postRepository.count());
        assertPostUnchanged();
    }

    @Test
    void unauthenticatedRequestCannotUpdate() throws Exception {
        mockMvc.perform(patch("/posts/{id}", original.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        assertPostUnchanged();
    }

    @Test
    void ownerWithoutCsrfCannotUpdate() throws Exception {
        mockMvc.perform(patch("/posts/{id}", original.getId())
                        .with(oidcLogin().oidcUser(authenticatedUser(seller.getId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
        assertPostUnchanged();
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void invalidContentReturnsBadRequestAndPreservesPost(String body) throws Exception {
        mockMvc.perform(patch("/posts/{id}", original.getId())
                        .with(oidcLogin().oidcUser(authenticatedUser(seller.getId())))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertPostUnchanged();
    }

    static Stream<String> invalidBodies() {
        return Stream.of(
                """
                {"title":" ","description":"설명","price":1000}
                """,
                """
                {"title":"%s","description":"설명","price":1000}
                """.formatted("가".repeat(101)),
                """
                {"title":"제목","description":" ","price":1000}
                """,
                """
                {"title":"제목","description":"설명","price":-1}
                """,
                """
                {"title":"제목","description":"설명"}
                """,
                """
                {"description":"설명","price":1000}
                """,
                """
                {"title":"제목","price":1000}
                """
        );
    }

    private void assertPostUnchanged() {
        Post found = postRepository.findById(original.getId()).orElseThrow();
        assertEquals(original.getTitle(), found.getTitle());
        assertEquals(original.getDescription(), found.getDescription());
        assertEquals(original.getPrice(), found.getPrice());
        assertEquals(original.getSellerId(), found.getSellerId());
        assertEquals(original.getStatus(), found.getStatus());
        assertEquals(original.getCreatedAt(), found.getCreatedAt());
    }

    private BrewOidcUser authenticatedUser(Long userId) {
        OidcIdToken idToken = OidcIdToken.withTokenValue("test-id-token")
                .claim("sub", "test-google-" + userId).build();
        DefaultOidcUser oidcUser = new DefaultOidcUser(
                AuthorityUtils.createAuthorityList("ROLE_USER"), idToken);
        return new BrewOidcUser(oidcUser, new AuthenticatedUser(userId));
    }
}
