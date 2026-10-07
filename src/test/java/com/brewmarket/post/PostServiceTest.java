package com.brewmarket.post;

import com.brewmarket.BrewMarketBackApplication;
import com.brewmarket.user.User;
import com.brewmarket.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 이전 테스트처럼 별도의 테스트용 DB를 자동으로 관리합니다.
@Testcontainers

// 실제 스프링 설정을 사용해 서비스와 저장소를 연결합니다.
@SpringBootTest(classes = BrewMarketBackApplication.class)

// 각 테스트가 끝나면 테스트에서 변경한 데이터를 되돌립니다.
@Transactional
class PostServiceTest {

    // 이 테스트 클래스에서 사용할 PostgreSQL 컨테이너입니다.
    @Container

    // 컨테이너의 DB 접속 정보를 스프링에 연결합니다.
    @ServiceConnection
    static PostgreSQLContainer database = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:17-3.5")
                    .asCompatibleSubstituteFor("postgres")
    );

    // 준비 단계에서 판매자를 저장할 때 사용합니다.
    @Autowired
    private UserRepository userRepository;

    // 이번 테스트에서 실제로 실행할 대상입니다.
    @Autowired
    private PostService postService;

    // 서비스가 저장한 결과를 다시 읽어 확인할 때 사용합니다.
    @Autowired
    private PostRepository postRepository;

    // 메모리에서 관리하던 객체를 분리하고 DB를 재조회하기 위해 사용합니다.
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void createsPostForExistingSeller() {
        // 준비: 실제로 존재하는 판매자를 만듭니다.
        User seller = userRepository.saveAndFlush(
                new User("seller@example.com", "판매자")
        );

        Long sellerId = seller.getId();

        // 실행: 저장소가 아니라 서비스를 호출합니다.
        // 회원 확인과 게시글 생성·저장을 서비스가 수행하게 합니다.
        Long postId = postService.create(
                sellerId,
                "원목 의자 판매합니다",
                "직접 사용하던 의자입니다. 동네에서 거래하고 싶습니다.",
                15000L
        );

        // 서비스가 저장된 게시글의 ID를 반환했는지 확인합니다.
        assertNotNull(postId);

        // 기존 객체의 관리 상태를 비웁니다. DB 데이터를 삭제하지 않습니다.
        // 서비스 안의 saveAndFlush로 저장 SQL은 이미 반영된 상태입니다.
        entityManager.clear();

        // 반환받은 ID로 실제 저장 결과를 읽습니다.
        // 결과가 없다면 예외가 발생하여 테스트가 실패합니다.
        Post found = postRepository.findById(postId).orElseThrow();

        // 검증: 기대한 판매자와 게시글 정보가 저장되었는지 확인합니다.
        assertEquals(sellerId, found.getSellerId());
        assertEquals("원목 의자 판매합니다", found.getTitle());
        assertEquals(
                "직접 사용하던 의자입니다. 동네에서 거래하고 싶습니다.",
                found.getDescription()
        );
        assertEquals(Long.valueOf(15000L), found.getPrice());

        // 새 게시글은 예약·완료 상태가 아니라 판매 중이어야 합니다.
        assertEquals(PostStatus.FOR_SALE, found.getStatus());

        // DB가 만든 생성 시각도 재조회되는지 확인합니다.
        assertNotNull(found.getCreatedAt());
    }

    @Test
    void rejectsPostWhenSellerDoesNotExist() {
        // 준비: 양수이지만 실제로는 존재하지 않는 회원 ID를 사용합니다.
        Long nonexistentSellerId = 999999L;

        // "큰 숫자이니 없을 것"이라고 추측하지 않고 준비 조건을 확인합니다.
        assertFalse(userRepository.existsById(nonexistentSellerId));

        // 실행: 나머지 입력은 정상으로 맞춰 판매자 존재 여부만 검증합니다.
        // assertThrows는 예상한 예외가 발생하면 그 예외 객체를 반환합니다.
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> postService.create(
                        nonexistentSellerId,
                        "원목 의자 판매합니다",
                        "상태가 양호한 의자입니다.",
                        15000L
                )
        );

        // 검증: 다른 입력 오류가 아니라, 없는 판매자 때문에 거부됐는지 확인합니다.
        assertEquals(
                "존재하지 않는 판매자입니다.",
                exception.getMessage()
        );
    }

    @Test
    void createsPostWithZeroPrice() {
        User seller = userRepository.saveAndFlush(
                new User("sharing@example.com", "나눔회원")
        );

        Long sellerId = seller.getId();

        Long postId = postService.create(
                sellerId,
                "원목 의자 나눔합니다",
                "직접 가져가실 이웃에게 무료로 드립니다.",
                0L
        );

        assertNotNull(postId);

        entityManager.clear();

        Post found = postRepository.findById(postId).orElseThrow();

        assertEquals(sellerId, found.getSellerId());
        assertEquals(Long.valueOf(0L), found.getPrice());

        assertEquals(PostStatus.FOR_SALE, found.getStatus());
    }
}