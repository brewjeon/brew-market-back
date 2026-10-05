package com.brewmarket.auth;

import com.brewmarket.BrewMarketBackApplication;
import com.brewmarket.user.User;
import com.brewmarket.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.junit.jupiter.api.Assertions.assertFalse;

@Testcontainers
@SpringBootTest(classes = BrewMarketBackApplication.class)
@Transactional
class SocialAccountRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer database = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:17-3.5")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void savesAndFindsGoogleAccountWithCreatedAt() {
        User user = userRepository.saveAndFlush(
                new User("social-test@example.com", "테스트회원")
        );

        Long userId = user.getId();

        String providerSubject = "test-google-sub-001";

        SocialAccount account = new SocialAccount(
                userId,
                AuthProvider.GOOGLE,
                providerSubject
        );

        SocialAccount saved = socialAccountRepository.saveAndFlush(account);

        Long accountId = saved.getId();
        assertNotNull(accountId);

        entityManager.clear();

        SocialAccount found = socialAccountRepository
                .findByProviderAndProviderSubject(
                        AuthProvider.GOOGLE,
                        providerSubject
                )
                .orElseThrow();

        assertEquals(accountId, found.getId());
        assertEquals(userId, found.getUserId());
        assertEquals(AuthProvider.GOOGLE, found.getProvider());
        assertEquals(providerSubject, found.getProviderSubject());

        assertNotNull(found.getCreatedAt());
    }

    @Test
    void returnsEmptyWhenGoogleAccountIsNotLinked() {
        User user = userRepository.saveAndFlush(
                new User("existing@example.com", "기존회원")
        );

        socialAccountRepository.saveAndFlush(
                new SocialAccount(
                        user.getId(),
                        AuthProvider.GOOGLE,
                        "existing-google-sub"
                )
        );

        entityManager.clear();

        Optional<SocialAccount> result = socialAccountRepository
                .findByProviderAndProviderSubject(
                        AuthProvider.GOOGLE,
                        "unknowns-google-sub"
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void rejectsSameGoogleAccountLinkedToDifferentUsers() {
        User firstUser = userRepository.saveAndFlush(
                new User("first@example.com", "첫번째회원")
        );

        User secondUser = userRepository.saveAndFlush(
                new User("second@example.com", "두번째회원")
        );

        String providerSubject = "same-google-sub";

        socialAccountRepository.saveAndFlush(
                new SocialAccount(
                        firstUser.getId(),
                        AuthProvider.GOOGLE,
                        providerSubject
                )
        );

        SocialAccount duplicateAccount = new SocialAccount(
                secondUser.getId(),
                AuthProvider.GOOGLE,
                providerSubject
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> socialAccountRepository.saveAndFlush(duplicateAccount)
        );
    }

    @Test
    void rejectsMultipleGoogleAccountsForSameUser() {
        User user = userRepository.saveAndFlush(
                new User("single-user@example.com", "한명의회원")
        );

        socialAccountRepository.saveAndFlush(
                new SocialAccount(
                        user.getId(),
                        AuthProvider.GOOGLE,
                        "first-google-sub"
                )
        );

        SocialAccount anotherGoogleAccount = new SocialAccount(
                user.getId(),
                AuthProvider.GOOGLE,
                "second-google-sub"
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> socialAccountRepository.saveAndFlush(anotherGoogleAccount)
        );
    }

    @Test
    void rejectsSocialAccountForNonexistentUser() {
        Long nonexistentUserId = 999999L;

        assertFalse(userRepository.existsById(nonexistentUserId));

        // 회원 id는 양수이고 나머지 값도 유효하므로 객체 생성은 가능합니다.
        // 생성자는 DB에서 회원의 존재 여부를 조회하지 않는다.
        SocialAccount account = new SocialAccount(
                nonexistentUserId,
                AuthProvider.GOOGLE,
                "google-sub-without-user"
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> socialAccountRepository.saveAndFlush(account)
        );
    }
}
