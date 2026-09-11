package com.user;

import com.brewmarket.BrewMarketBackApplication;
import com.brewmarket.user.User;
import com.brewmarket.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = BrewMarketBackApplication.class)
@AutoConfigureMockMvc
class UserRegistrationTest {

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

    @BeforeEach
    void setUp() throws SQLException {
        // 별도의 테스트 DB에서만 실행된다.
        // 각 테스트가 사용자 0명인 상태에서 시작하도록 정리한다.
        userRepository.deleteAll();
    }

    @Test
    void createsUserAndRejectsDuplicateEmail() throws Exception {
        // Given(준비): 정상적인 사용자 등록 요청
        String body = """
                {
                    "email": "learner@example.com",
                    "nickname": "brewer"
                }
                """;

        // When(실행) / Then(검증): 등록 요청이 201로 성공한다.
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("learner@example.com"))
                .andExpect(jsonPath("$.nickname").value("brewer"));

        // Then: DB에 실제로 저장됐는지 확인한다.
        User saved = userRepository.findByEmail("learner@example.com").orElseThrow();

        assertNotNull(saved.getId());
        assertEquals("brewer", saved.getNickname());
        assertNotNull(saved.getCreateAt());

        // When / Then: 같은 이메일로 재등록하면 409로 거부한다.
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        // Then: 중복 요청 이후에도 사용자는 한 명이다.
        assertEquals(1L, userRepository.count());
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        // Given: 이메일 형식이 잘못된 요청
        String body = """
                {
                    "email": "invalid",
                    "nickname": "brewer"
                }
                """;

        // When / Then: 요청이 400으로 거부된다.
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
                .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty());

        // Then: 잘못된 요청은 DB에 저장되지 않는다.
        assertEquals(0L, userRepository.count());
    }
}
