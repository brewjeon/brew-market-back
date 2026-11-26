package com.brewmarket.post;

import com.brewmarket.BrewMarketBackApplication;
import com.brewmarket.auth.AuthenticatedUser;
import com.brewmarket.auth.BrewOidcUser;
import com.brewmarket.inquiry.InquiryService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.IntStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @Autowired
    private PostService postService;
    @Autowired
    private InquiryService inquiries;
    @Autowired
    private JdbcTemplate jdbc;

    private User seller;
    private Post original;

    private static final String VALID_BODY = """
            {"title":"수정한 제목","description":"수정한 설명","price":0}
            """;

    @BeforeEach
    void preparePost() {
        seller = userRepository.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "작성자"));
        Long neighborhoodId = jdbc.queryForObject("""
                INSERT INTO brew_market.neighborhoods (code, name) VALUES (?, '테스트 동네') RETURNING id
                """, Long.class, UUID.randomUUID().toString().replace("-", ""));
        jdbc.update("""
                INSERT INTO brew_market.user_neighborhood_verifications (user_id, neighborhood_id, expires_at)
                VALUES (?, ?, now() + interval '1 day')
                """, seller.getId(), neighborhoodId);
        Post post = new Post(seller.getId(), "원래 제목", "원래 설명", 3000L);
        post.selectNeighborhood(neighborhoodId);
        Post saved = postRepository.saveAndFlush(post);
        original = postRepository.findById(saved.getId()).orElseThrow();
        assertNotNull(original.getCreatedAt());
    }

    @Test
    void sellerCannotEditCompletedPostThroughJsonOrMultipart() throws Exception {
        postService.changeStatus(original.getId(), seller.getId(), PostStatus.COMPLETED);
        var login = oidcLogin().oidcUser(authenticatedUser(seller.getId()));
        mockMvc.perform(patch("/posts/{id}", original.getId()).with(login).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POST_STATUS_CONFLICT"));
        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PATCH, "/posts/{id}", original.getId())
                        .file(new MockMultipartFile("post", "", "application/json",
                                VALID_BODY.getBytes(StandardCharsets.UTF_8)))
                        .param("removeImage", "true").with(login).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POST_STATUS_CONFLICT"));
        Post saved = postRepository.findById(original.getId()).orElseThrow();
        assertEquals(PostStatus.COMPLETED, saved.getStatus());
        assertEquals(original.getTitle(), saved.getTitle());
        assertEquals(original.getDescription(), saved.getDescription());
        assertEquals(original.getPrice(), saved.getPrice());
        assertEquals(original.getNeighborhoodId(), saved.getNeighborhoodId());
    }

    @Test
    void oneRoomWinsConcurrentReservationAndStaleActionsCannotChangeIt() throws Exception {
        var buyers = IntStream.range(0, 2)
                .mapToObj(i -> userRepository.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "구매자")))
                .toList();
        long first = inquiries.inquire(original.getId(), buyers.getFirst().getId(), "거래할 수 있나요?");
        long second = inquiries.inquire(original.getId(), buyers.getLast().getId(), "구매하고 싶어요.");
        long version = inquiries.reservation(first, seller.getId()).version();

        mockMvc.perform(reservationChange(first, buyers.getFirst().getId(), "CONFIRM", version))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/inquiries/{id}/reservation", first)
                        .with(oidcLogin().oidcUser(authenticatedUser(buyers.getLast().getId()))))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/posts/{id}/status", original.getId())
                        .with(oidcLogin().oidcUser(authenticatedUser(seller.getId())))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESERVED\"}"))
                .andExpect(status().isConflict());

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        int firstStatus;
        int secondStatus;
        try {
            var a = executor.submit(() -> tryConfirm(first, version, ready, start));
            var b = executor.submit(() -> tryConfirm(second, version, ready, start));
            assertEquals(true, ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            firstStatus = a.get(15, TimeUnit.SECONDS);
            secondStatus = b.get(15, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
        assertEquals(java.util.List.of(204, 409), IntStream.of(firstStatus, secondStatus).sorted().boxed().toList());
        long winner = firstStatus == 204 ? first : second;
        long loser = winner == first ? second : first;
        Post reserved = postRepository.findById(original.getId()).orElseThrow();
        assertEquals(PostStatus.RESERVED, reserved.getStatus());
        assertEquals(winner, reserved.getReservationRoomId());
        assertEquals(InquiryService.ReservationState.RESERVED_ELSEWHERE,
                inquiries.reservation(loser, seller.getId()).state());
        assertEquals("성수역 2번 출구", inquiries.reservation(winner, seller.getId()).meetingPlace());
        assertEquals(null, inquiries.reservation(loser, seller.getId()).meetingPlace());
        assertEquals(null, PostDetailResponse.from(reserved, buyers.getFirst().getId()).reservationRoomId());

        long beforeReschedule = inquiries.reservation(winner, seller.getId()).version();
        mockMvc.perform(reservationChange(loser, seller.getId(), "RESCHEDULE", beforeReschedule))
                .andExpect(status().isConflict());
        mockMvc.perform(reservationChange(winner, seller.getId(), "RESCHEDULE", beforeReschedule))
                .andExpect(status().isNoContent());
        long winningBuyer = winner == first ? buyers.getFirst().getId() : buyers.getLast().getId();
        var changed = inquiries.reservation(winner, winningBuyer);
        assertEquals(InquiryService.ReservationState.RESERVED_HERE, changed.state());
        assertEquals("성수역 3번 출구", changed.meetingPlace());
        mockMvc.perform(reservationChange(winner, seller.getId(), "RESCHEDULE", beforeReschedule))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/posts/{id}", original.getId())
                        .with(oidcLogin().oidcUser(authenticatedUser(seller.getId())))
                        .with(csrf()))
                .andExpect(status().isConflict());
        long oldReservationVersion = inquiries.reservation(winner, seller.getId()).version();
        mockMvc.perform(reservationChange(winner, seller.getId(), "CANCEL", oldReservationVersion))
                .andExpect(status().isNoContent());
        assertEquals(null, inquiries.reservation(winner, seller.getId()).meetingAt());
        long available = inquiries.reservation(winner, seller.getId()).version();
        mockMvc.perform(reservationChange(winner, seller.getId(), "CONFIRM", available))
                .andExpect(status().isNoContent());
        mockMvc.perform(reservationChange(winner, seller.getId(), "CANCEL", oldReservationVersion))
                .andExpect(status().isConflict());
        long current = inquiries.reservation(winner, seller.getId()).version();
        mockMvc.perform(reservationChange(winner, seller.getId(), "COMPLETE", current))
                .andExpect(status().isNoContent());
        assertEquals(InquiryService.ReservationState.COMPLETED_HERE,
                inquiries.reservation(winner, seller.getId()).state());
        assertEquals(null, inquiries.reservation(winner, seller.getId()).meetingPlace());

        mockMvc.perform(delete("/posts/{id}", original.getId())
                        .with(oidcLogin().oidcUser(authenticatedUser(seller.getId())))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        assertEquals(InquiryService.ReservationState.UNAVAILABLE,
                inquiries.reservation(winner, seller.getId()).state());
    }

    private int tryConfirm(long roomId, long version, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("예약 테스트 시작 신호가 없습니다.");
        return mockMvc.perform(reservationChange(roomId, seller.getId(), "CONFIRM", version))
                .andReturn().getResponse().getStatus();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder reservationChange(
            long roomId, long userId, String action, long version) {
        String body = switch (action) {
            case "CONFIRM", "RESCHEDULE" -> """
                    {"action":"%s","version":%d,"meetingAt":"%s","meetingPlace":" 성수역 %d번 출구 "}
                    """.formatted(action, version, Instant.now().plus(1, ChronoUnit.DAYS),
                    "RESCHEDULE".equals(action) ? 3 : 2);
            default -> "{\"action\":\"%s\",\"version\":%d}".formatted(action, version);
        };
        return patch("/inquiries/{id}/reservation", roomId)
                .with(oidcLogin().oidcUser(authenticatedUser(userId)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    @Test
    void publicListCombinesNeighborhoodSearchAndPagination() throws Exception {
        Long neighborhoodId = original.getNeighborhoodId();
        String keyword = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO brew_market.posts (seller_id, title, description, price, neighborhood_id)
                SELECT ?, ?, '설명', 0, ? FROM generate_series(1, 21)
                """, seller.getId(), keyword, neighborhoodId);
        jdbc.update("""
                INSERT INTO brew_market.posts (seller_id, title, description, price)
                VALUES (?, ?, '설명', 0)
                """, seller.getId(), keyword);

        mockMvc.perform(get("/neighborhoods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(neighborhoodId)).exists());
        mockMvc.perform(get("/posts").param("paged", "true")
                        .param("keyword", keyword).param("neighborhoodId", neighborhoodId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.hasNext").value(true));
        PostPageResponse first = postService.findPage(null, null, keyword, neighborhoodId);
        mockMvc.perform(get("/posts").param("paged", "true")
                        .param("keyword", keyword).param("neighborhoodId", neighborhoodId.toString())
                        .param("beforeTime", first.nextCreatedAt().toString())
                        .param("beforeId", first.nextId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
        PostPageResponse all = postService.findPage(null, null, keyword);
        assertEquals(2, postService.findPage(all.nextCreatedAt(), all.nextId(), keyword).items().size());
    }

    @Test
    void ownerCanReleaseVerificationSlotWhilePublishedPostRemainsVisible() throws Exception {
        long originalNeighborhood = original.getNeighborhoodId();
        User other = userRepository.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "다른 회원"));
        var ownerLogin = oidcLogin().oidcUser(authenticatedUser(seller.getId()));
        var otherLogin = oidcLogin().oidcUser(authenticatedUser(other.getId()));
        for (double longitude : new double[] {128.0, 129.0}) {
            jdbc.update("""
                    INSERT INTO brew_market.neighborhoods (code, name, boundary)
                    VALUES (?, '추가 인증 테스트 동네', ST_Multi(ST_Transform(
                        ST_MakeEnvelope(?, 35.95, ?, 36.05, 4326), 5179)))
                    """, UUID.randomUUID().toString().replace("-", ""), longitude - 0.05, longitude + 0.05);
        }
        String firstLocation = """
                {"latitude":36,"longitude":128,"accuracy":20}
                """;
        String secondLocation = """
                {"latitude":36,"longitude":129,"accuracy":20}
                """;
        mockMvc.perform(delete("/neighborhoods/verified/{id}", originalNeighborhood).with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/neighborhoods/verified/{id}", originalNeighborhood).with(otherLogin).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/neighborhoods/verified").with(ownerLogin))
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(originalNeighborhood)).exists());
        mockMvc.perform(post("/neighborhoods/verify").with(ownerLogin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(firstLocation))
                .andExpect(status().isOk());
        mockMvc.perform(post("/neighborhoods/verify").with(ownerLogin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(secondLocation))
                .andExpect(status().isConflict());

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(delete("/neighborhoods/verified/{id}", originalNeighborhood).with(ownerLogin).with(csrf()))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(get("/neighborhoods/verified").with(ownerLogin))
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(originalNeighborhood)).doesNotExist());
        mockMvc.perform(get("/posts/{id}", original.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.neighborhoodName").value("테스트 동네"));
        mockMvc.perform(patch("/posts/{id}", original.getId()).with(ownerLogin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/neighborhoods/verify").with(ownerLogin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(secondLocation))
                .andExpect(status().isOk());
    }

    @Test
    void currentLocationEnablesPublicationUntilVerificationExpires() throws Exception {
        Long candidate = jdbc.queryForObject("""
                INSERT INTO brew_market.neighborhoods (code, name, boundary)
                VALUES (?, '인증 테스트 동네', ST_Multi(ST_Transform(ST_MakeEnvelope(126.9, 37.5, 127.1, 37.6, 4326), 5179)))
                RETURNING id
                """, Long.class, UUID.randomUUID().toString().replace("-", ""));
        String postBody = """
                {"title":"새 글","description":"설명","price":0,"neighborhoodId":%d}
                """.formatted(candidate);
        var login = oidcLogin().oidcUser(authenticatedUser(seller.getId()));
        try {
            mockMvc.perform(post("/posts").with(login).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(postBody))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/neighborhoods/verify").with(login).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"latitude":37.55,"longitude":127.0,"accuracy":20}
                                    """))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(candidate));
            mockMvc.perform(get("/neighborhoods/verified").with(login))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == %d)]".formatted(candidate)).exists());
            mockMvc.perform(post("/posts").with(login).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(postBody))
                    .andExpect(status().isCreated());
            jdbc.update("""
                    UPDATE brew_market.user_neighborhood_verifications SET expires_at = now() - interval '1 minute'
                    WHERE user_id = ? AND neighborhood_id = ?
                    """, seller.getId(), candidate);
            mockMvc.perform(post("/posts").with(login).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(postBody))
                    .andExpect(status().isForbidden());
        } finally {
            jdbc.update("UPDATE brew_market.neighborhoods SET boundary = NULL WHERE id = ?", candidate);
        }
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
                .andExpect(jsonPath("$.neighborhoodName").value("테스트 동네"))
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
