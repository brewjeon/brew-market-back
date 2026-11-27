package com.brewmarket.inquiry;

import com.brewmarket.auth.AuthenticatedUser;
import com.brewmarket.auth.BrewOidcUser;
import com.brewmarket.post.PostService;
import com.brewmarket.user.User;
import com.brewmarket.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @Test
    void postStatusChangeUpdatesOnlyRoomsForThatPost() throws Exception {
        long seller = createUser();
        long buyer = createUser();
        long post = posts.create(seller, "머그컵", "판매합니다", 1000L);
        long otherPost = posts.create(seller, "책장", "판매합니다", 1000L);
        long room = inquiries.inquire(post, buyer, "머그컵 문의");
        long otherRoom = inquiries.inquire(otherPost, buyer, "책장 문의");

        var subscription = mockMvc.perform(get("/inquiries/{id}/events", room)
                        .with(oidcLogin().oidcUser(member(buyer))))
                .andExpect(request().asyncStarted()).andReturn();
        var otherSubscription = mockMvc.perform(get("/inquiries/{id}/events", otherRoom)
                        .with(oidcLogin().oidcUser(member(buyer))))
                .andExpect(request().asyncStarted()).andReturn();
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                mockMvc.perform(patch("/posts/{id}/status", post)
                                .with(oidcLogin().oidcUser(member(seller))).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"COMPLETED\"}"))
                        .andExpect(status().isNoContent());
            }

            assertEquals(1, changeCount(subscription.getResponse().getContentAsString()));
            assertEquals(0, changeCount(otherSubscription.getResponse().getContentAsString()));
            mockMvc.perform(get("/inquiries/{id}/reservation", room)
                            .with(oidcLogin().oidcUser(member(buyer))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("COMPLETED"));
        } finally {
            subscription.getRequest().getAsyncContext().complete();
            otherSubscription.getRequest().getAsyncContext().complete();
        }
    }

    @Test
    void countsUnreadMessagesAndFiltersRoomsBeforePagination() throws Exception {
        long seller = createUser();
        long buyer = createUser();
        long stranger = createUser();
        long mug = posts.create(seller, "머그컵", "판매합니다", 1000L);
        long shelf = posts.create(seller, "책장", "판매합니다", 1000L);
        long mugRoom = inquiries.inquire(mug, buyer, "첫 문의");
        long shelfRoom = inquiries.inquire(shelf, buyer, "책장 문의");

        var sellerUpdates = subscribeToList(seller);
        var strangerUpdates = subscribeToList(stranger);
        try {
            inquiries.send(mugRoom, buyer, "추가 문의");
            var firstPage = inquiries.rooms(seller, Long.MAX_VALUE, true);
            var first = firstPage.rooms().getFirst();
            assertEquals(mugRoom, first.id());
            assertEquals(2L, first.unreadCount());
            assertEquals(2L, firstPage.unreadRoomCount());
            assertEquals(0L, inquiries.rooms(buyer, Long.MAX_VALUE, true).unreadRoomCount());

            var older = inquiries.rooms(seller, first.lastMessageId(), true);
            assertEquals(shelfRoom, older.rooms().getFirst().id());
            assertEquals(1L, older.rooms().getFirst().unreadCount());
            assertEquals(2L, older.unreadRoomCount());

            inquiries.markRead(mugRoom, seller, first.lastMessageId());
            var unread = inquiries.rooms(seller, Long.MAX_VALUE, true);
            assertEquals(1L, unread.unreadRoomCount());
            assertEquals(1, unread.rooms().size());
            assertEquals(shelfRoom, unread.rooms().getFirst().id());
            assertEquals(2, changeCount(sellerUpdates.getResponse().getContentAsString()));
            assertEquals(0, changeCount(strangerUpdates.getResponse().getContentAsString()));
        } finally {
            sellerUpdates.getRequest().getAsyncContext().complete();
            strangerUpdates.getRequest().getAsyncContext().complete();
        }
    }

    @Test
    void reservationHistoryIsUnreadOnlyInTheSelectedRoom() throws Exception {
        long seller = createUser();
        long buyer = createUser();
        long otherBuyer = createUser();
        long post = posts.create(seller, "머그컵", "판매합니다", 1000L);
        long room = inquiries.inquire(post, buyer, "머그컵 문의");
        long otherRoom = inquiries.inquire(post, otherBuyer, "구매 문의");
        Instant time = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.DAYS);
        Instant newTime = time.plus(1, ChronoUnit.HOURS);

        changeReservation(room, seller, InquiryService.ReservationAction.CONFIRM, time, "역 2번 출구");
        changeReservation(room, seller, InquiryService.ReservationAction.RESCHEDULE, newTime, "역 3번 출구");
        changeReservation(room, seller, InquiryService.ReservationAction.RESCHEDULE, newTime, "역 3번 출구");
        changeReservation(room, seller, InquiryService.ReservationAction.CANCEL, null, null);
        changeReservation(room, seller, InquiryService.ReservationAction.CONFIRM, newTime, "역 3번 출구");
        changeReservation(room, seller, InquiryService.ReservationAction.COMPLETE, null, null);

        var history = inquiries.messages(room, buyer);
        assertEquals(InquiryMessage.Kind.TEXT, history.getFirst().kind());
        assertEquals(List.of(
                "판매자가 예약을 확정했습니다.",
                "판매자가 약속 시간 또는 장소를 변경했습니다.",
                "판매자가 예약을 취소했습니다.",
                "판매자가 예약을 확정했습니다.",
                "판매자가 거래를 완료했습니다."
        ), history.stream().filter(message -> message.kind() == InquiryMessage.Kind.SYSTEM)
                .map(InquiryService.Message::content).toList());
        assertEquals(1, inquiries.messages(otherRoom, otherBuyer).size());
        assertEquals(0L, inquiries.rooms(otherBuyer, Long.MAX_VALUE, true).unreadRoomCount());

        var unread = inquiries.rooms(buyer, Long.MAX_VALUE, true);
        assertEquals(1L, unread.unreadRoomCount());
        assertEquals(5L, unread.rooms().getFirst().unreadCount());
        inquiries.markRead(room, buyer, history.getLast().id());
        assertEquals(0L, inquiries.rooms(buyer, Long.MAX_VALUE, true).unreadRoomCount());
    }

    private void changeReservation(long room, long seller, InquiryService.ReservationAction action,
                                   Instant time, String place) {
        long version = inquiries.reservation(room, seller).version();
        inquiries.changeReservation(room, seller, action, version, time, place);
    }

    private org.springframework.test.web.servlet.MvcResult subscribeToList(long userId) throws Exception {
        return mockMvc.perform(get("/inquiries/events").with(oidcLogin().oidcUser(member(userId))))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();
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
