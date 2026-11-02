package com.brewmarket.post;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select p from Post p where p.deletedAt is null
            and (p.createdAt < :beforeTime or (p.createdAt = :beforeTime and p.id < :beforeId))
            order by p.createdAt desc, p.id desc
            """)
    List<Post> findNextPage(@org.springframework.data.repository.query.Param("beforeTime") java.time.Instant beforeTime,
                            @org.springframework.data.repository.query.Param("beforeId") Long beforeId,
                            org.springframework.data.domain.Pageable pageable);

    List<Post> findTop21ByDeletedAtIsNullOrderByCreatedAtDescIdDesc();
    // DB에서 최신 20개만 조회하고, 생성 시각이 같으면 번호가 큰 글을 먼저 가져옴
    List<Post> findTop20ByDeletedAtIsNullOrderByCreatedAtDescIdDesc();

    Optional<Post> findByIdAndDeletedAtIsNull(Long id);
}
