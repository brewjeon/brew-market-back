package com.brewmarket.post;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostFavoriteRepository extends JpaRepository<PostFavorite, Long> {
    boolean existsByUserIdAndPost_Id(long userId, long postId);

    @Modifying
    @Query("delete from PostFavorite f where f.userId = :userId and f.post.id = :postId")
    int remove(@Param("userId") long userId, @Param("postId") long postId);

    @Query("""
            select f from PostFavorite f join fetch f.post p
            where f.userId = :userId and p.deletedAt is null and f.id < :beforeId
            order by f.id desc
            """)
    List<PostFavorite> findPage(@Param("userId") long userId,
                                @Param("beforeId") long beforeId, Pageable limit);

    // JPA의 일반 save는 동시 중복 요청에서 트랜잭션이 실패한다.
    // 이 한 쿼리만 PostgreSQL의 충돌 무시 기능으로 멱등 저장한다.
    @Modifying
    @Query(value = """
            INSERT INTO brew_market.post_favorites (user_id, post_id)
            SELECT :userId, id FROM brew_market.posts WHERE id = :postId AND deleted_at IS NULL
            ON CONFLICT (user_id, post_id) DO NOTHING
            """, nativeQuery = true)
    int addIfAbsent(@Param("userId") long userId, @Param("postId") long postId);
}
