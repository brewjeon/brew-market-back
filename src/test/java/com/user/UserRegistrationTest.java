package com.user;

import com.brewmarket.BrewMarketBackApplication;
import com.brewmarket.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void setUp() {
        // 별도의 테스트 DB에서만 실행된다.
        // 각 테스트가 사용자 0명인 상태에서 시작하도록 정리한다.
        userRepository.deleteAll();
    }

    @Test
    void rejectsDirectRegistrationWithoutAuthentication() throws Exception {
        // 입력값 오류가 아닌 접근 제한으로 거부되는지 확인하기 위해 정상 입력 사용
        String body = """
                {
                    "email": "learner@example.com",
                    "nickname": "brewer"
                }
                """;

        // CSRF 검사를 통과시켜 직접 가입 차단 규칙을 확인
        mockMvc.perform(post("/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        // 요청이 거부된 뒤 회원이 저장되지 않았는지도 확인
        assertEquals(0L, userRepository.count());
    }

    @Test
    @WithMockUser
    void rejectsDirectRegistrationEvenWhenAuthenticated() throws Exception {
        // 테스트용 로그인 상태에서도 직접 가입은 허용하지 않음
        String body = """
                {
                    "email": "learner@example.com",
                    "nickname": "brewer"
                }
                """;

        // 유효한 CSRF 토큰이 있어도 가입 권한이 없으므로 403 반환
        mockMvc.perform(post("/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        // 로그인한 요청도 회원을 생성하지 못했는지 확인
        assertEquals(0L, userRepository.count());
    }
}
