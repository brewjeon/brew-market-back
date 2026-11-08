package com.brewmarket.inquiry;

import com.brewmarket.auth.AuthenticatedUser;
import com.brewmarket.auth.BrewOidcUser;
import com.brewmarket.post.PostService;
import com.brewmarket.user.User;
import com.brewmarket.user.UserRepository;
import java.util.UUID;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class InquiryUpdatesTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer database = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres")
    );

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private PostService posts;
    @Autowired private InquiryService inquiries;

    @Test
    void sendsUpdatesForNewMessagesAndReadProgressOnlyToRoomParticipants() throws Exception {
        long sellerId = createUser();
        long buyerId = createUser();
        long strangerId = createUser();
        long postId = posts.create(sellerId, "머그컵", "판매합니다", 1000L);
        long roomId = inquiries.inquire(postId, buyerId, "판매 중인가요?");
        long firstId = inquiries.messages(roomId, buyerId).getFirst().id();

        mockMvc.perform(get("/inquiries/{id}/events", roomId).with(oidcLogin().oidcUser(member(strangerId))))
                .andExpect(status().isNotFound());
        var subscription = mockMvc.perform(
                        get("/inquiries/{id}/events", roomId).with(oidcLogin().oidcUser(member(buyerId))))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        try {
            inquiries.send(roomId, sellerId, "네, 판매 중입니다.");
            long replyId = inquiries.messages(roomId, buyerId).getLast().id();
            assertEquals(1, changeCount(subscription.getResponse().getContentAsString()));

            inquiries.markRead(roomId, buyerId, replyId);
            inquiries.markRead(roomId, buyerId, firstId);
            assertEquals(2, changeCount(subscription.getResponse().getContentAsString()));
        } finally {
            subscription.getRequest().getAsyncContext().complete();
        }
    }

    private long createUser() {
        return users.save(new User(UUID.randomUUID() + "@example.com", "테스트 회원")).getId();
    }

    private BrewOidcUser member(long userId) {
        var token = OidcIdToken.withTokenValue("test-token").subject(Long.toString(userId)).build();
        var user = new DefaultOidcUser(AuthorityUtils.createAuthorityList("ROLE_USER"), token);
        return new BrewOidcUser(user, new AuthenticatedUser(userId));
    }

    private long changeCount(String response) {
        return response.lines().filter("data:changed"::equals).count();
    }
}
