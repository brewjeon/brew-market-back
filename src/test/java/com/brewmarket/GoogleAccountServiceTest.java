package com.brewmarket;

import com.brewmarket.auth.AuthProvider;
import com.brewmarket.auth.GoogleAccountService;
import com.brewmarket.auth.SocialAccount;
import com.brewmarket.auth.SocialAccountRepository;
import com.brewmarket.user.User;
import com.brewmarket.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

// 테스트용 DB 컨테이너 시작과 종료 관리
@Testcontainers
// 실제 애플리케이션 설정으로 서비스와 저장소 준비
@SpringBootTest(classes = BrewMarketBackApplication.class)
class GoogleAccountServiceTest {

    // 이 필드를 테스트용 컨테이너로 관리
    @Container
    // 컨테이너 접속 정보를 스프링의 DB 설정에 연결
    @ServiceConnection
    static PostgreSQLContainer database = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:17-3.5")
                    .asCompatibleSubstituteFor("postgres")
    );

    // 직접 new 하지 않고 트랜잭션이 적용되는 서비스 객체 사용
    @Autowired
    private GoogleAccountService googleAccountService;

    // 서비스 실행 후 저장된 회원을 확인할 때 사용
    @Autowired
    private UserRepository userRepository;

    // 기본적으로 실제 저장소 사용
    // 필요한 테스트에서 특정 메서드의 동작만 변경
    @MockitoSpyBean
    private SocialAccountRepository socialAccountRepository;

    @Test
    void createsUserAndGoogleAccount() {
        // 준비: 다른 테스트 데이터와 겹치지 않을 식별자 생성
        String suffix = UUID.randomUUID().toString();
        String subject = "google-" + suffix;
        String email = suffix + "@example.com";

        // 실행: 처음 방문한 구글 계정으로 신규 가입
        Long userId = googleAccountService.getOrCreateUserId(
                subject,
                email,
                true,
                "브루회원"
        );

        // 검증: 가입 결과로 회원 번호가 반환되어야 함
        assertNotNull(userId);

        // 서비스 트랜잭션이 끝난 뒤 DB에서 회원을 다시 조회
        // 반환된 번호에 해당하는 회원이 없으면 테스트 실패
        User savedUser = userRepository.findById(userId).orElseThrow();

        // 같은 구글 계정으로 연결 정보를 다시 조회
        SocialAccount savedAccount = socialAccountRepository.findByProviderAndProviderSubject(
                        AuthProvider.GOOGLE,
                        subject
                )
                .orElseThrow();

        // 회원 정보가 전달한 값대로 저장되었는지 확인
        assertEquals(email, savedUser.getEmail());
        assertEquals("브루회원", savedUser.getNickname());

        // 구글 계정 연결이 방금 생성된 회원을 가리키는지 확인
        assertEquals(userId, savedAccount.getUserId());
        assertEquals(AuthProvider.GOOGLE, savedAccount.getProvider());
        assertEquals(subject, savedAccount.getProviderSubject());

        // DB 기본값으로 생성 시각이 기록되었는지 확인
        assertNotNull(savedAccount.getCreatedAt());
    }

    @Test
    void returnsExistingUserWithoutCreatingAnotherAccount() {
        // 준비: 다른 테스트와 겹치지 않는 계정 정보 생성
        String suffix = UUID.randomUUID().toString();
        String subject = "google-" + suffix;
        String originalEmail = "first-" + suffix + "@example.com";

        // 첫 가입을 완료해서 기존 회원이 있는 상황 준비
        Long firstUserId = googleAccountService.getOrCreateUserId(
                subject,
                originalEmail,
                true,
                "처음닉네임"
        );

        // 다른 테스트의 데이터가 있을 수 있으므로 현재 개수를 기준으로 비교
        long userCountBefore = userRepository.count();
        long accountCountBefore = socialAccountRepository.count();

        // 실행: 이메일과 이름이 바뀌어도 같은 구글 계정으로 재조회
        Long secondUserId = googleAccountService.getOrCreateUserId(
                subject,
                "changed-" + suffix + "@example.com",
                true,
                "변경된구글이름"
        );

        // 기존 회원 번호를 반환하고 회원이나 연결 정보를 추가하지 않아야 함
        assertEquals(firstUserId, secondUserId);
        assertEquals(userCountBefore, userRepository.count());
        assertEquals(accountCountBefore, socialAccountRepository.count());

        // 기존 회원의 이메일과 닉네임을 임의로 덮어쓰지 않아야 함
        User savedUser = userRepository.findById(firstUserId)
                .orElseThrow();
        assertEquals(originalEmail, savedUser.getEmail());
        assertEquals("처음닉네임", savedUser.getNickname());

        // 구글 계정 연결도 기존 회원을 계속 가리키는지 확인
        SocialAccount savedAccount = socialAccountRepository
                .findByProviderAndProviderSubject(AuthProvider.GOOGLE, subject)
                .orElseThrow();

        assertEquals(firstUserId, savedAccount.getUserId());
    }

    @Test
    void rejectsAutomaticLinkWhenEmailAlreadyExists() {
        // 준비: 구글 계정 연결이 없는 기존 회원 생성
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.com";
        String subject = "unlinked-google-" + suffix;

        User existingUser = userRepository.saveAndFlush(
                new User(email, "기존회원")
        );

        Long existingUserId = existingUser.getId();

        // 기존 회원을 준비한 이후의 개수를 기준으로 비교
        long userCountBefore = userRepository.count();
        long accountCountBefore = socialAccountRepository.count();

        // 실행·검증: 같은 이메일이라도 기존 계정에 자동 연결하지 않음
        assertThrows(
                IllegalStateException.class,
                () -> googleAccountService.getOrCreateUserId(
                        subject,
                        email,
                        true,
                        "새구글이름"
                )
        );

        // 거부된 요청으로 회원이나 연결 정보가 추가되지 않아야 함
        assertEquals(userCountBefore, userRepository.count());
        assertEquals(accountCountBefore, socialAccountRepository.count());

        // 요청에 사용한 구글 계정이 연결되지 않았는지 직접 확인
        assertTrue(
                socialAccountRepository
                        .findByProviderAndProviderSubject(
                                AuthProvider.GOOGLE,
                                subject
                        )
                        .isEmpty()
        );

        // 기존 회원의 정보도 그대로 유지되어야 함
        User savedUser = userRepository.findById(existingUserId)
                .orElseThrow();

        assertEquals(email, savedUser.getEmail());
        assertEquals("기존회원", savedUser.getNickname());
    }

    @Test
    void rollsBackUserWhenSocialAccountSaveFails() {
        // 준비: 다른 테스트와 겹치지 않는 신규 계정 정보
        String suffix = UUID.randomUUID().toString();
        String subject = "rollback-google-" + suffix;
        String email = "rollback-" + suffix + "@example.com";

        // 실제 DB 오류 대신 테스트에서 발생시킬 저장 실패
        DataIntegrityViolationException saveFailure =
                new DataIntegrityViolationException("테스트용 계정 연결 저장 실패");

        // 연결 저장이 호출되는 순간 실행할 동작 지정
        doAnswer(invocation -> {
            // saveAndFlush에 전달된 첫 번째 인자 꺼내기
            SocialAccount account = invocation.getArgument(0);

            // 연결 저장 직전에는 회원이 실제로 조회되어야 함
            // 아직 서비스 트랜잭션 안이므로 최종 커밋 전 상태
            assertTrue(userRepository.existsById(account.getUserId()));

            // 회원 저장 이후, 연결 저장 단계에서 의도적으로 실패
            throw saveFailure;
        }).when(socialAccountRepository)
                .saveAndFlush(any(SocialAccount.class));

        // 실행: 서비스가 저장 실패를 밖으로 전달하는지 확인
        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> googleAccountService.getOrCreateUserId(
                        subject,
                        email,
                        true,
                        "롤백확인회원"
                )
        );

        // 다른 오류가 아니라 위에서 의도한 실패가 발생했는지 확인
        assertSame(saveFailure, thrown);

        // 서비스 트랜잭션이 끝난 뒤에는 회원이 남아 있으면 안 됨
        assertTrue(userRepository.findByEmail(email).isEmpty());

        // 실패한 계정 연결도 남아 있으면 안 됨
        assertTrue(
                socialAccountRepository
                        .findByProviderAndProviderSubject(
                                AuthProvider.GOOGLE,
                                subject
                        )
                        .isEmpty()
        );
    }
}





























