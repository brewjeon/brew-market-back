package com.brewmarket.post;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    @Query("""
            select p from Post p where p.deletedAt is null
            and locate(lower(:keyword), lower(p.title)) > 0
            order by p.createdAt desc, p.id desc
            """)
    List<Post> searchFirst(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            select p from Post p where p.deletedAt is null
            and locate(lower(:keyword), lower(p.title)) > 0
            and (p.createdAt < :beforeTime or (p.createdAt = :beforeTime and p.id < :beforeId))
            order by p.createdAt desc, p.id desc
            """)
    List<Post> searchNext(
            @Param("keyword") String keyword,
            @Param("beforeTime") Instant beforeTime,
            @Param("beforeId") Long beforeId,
            Pageable pageable
    );

    @Query("""
            select p from Post p where p.deletedAt is null
            and (p.createdAt < :beforeTime or (p.createdAt = :beforeTime and p.id < :beforeId))
            order by p.createdAt desc, p.id desc
            """)
    List<Post> findNextPage(
            @Param("beforeTime") Instant beforeTime,
            @Param("beforeId") Long beforeId,
            Pageable pageable
    );

    List<Post> findTop21ByDeletedAtIsNullOrderByCreatedAtDescIdDesc();

    List<Post> findBySellerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long sellerId, Pageable limit);

    @Query("""
            select p from Post p where p.sellerId = :sellerId and p.deletedAt is null
            and (p.createdAt < :beforeTime or (p.createdAt = :beforeTime and p.id < :beforeId))
            order by p.createdAt desc, p.id desc
            """)
    List<Post> findNextMine(
            @Param("sellerId") Long sellerId,
            @Param("beforeTime") Instant beforeTime,
            @Param("beforeId") Long beforeId,
            Pageable limit
    );

    // DB에서 최신 20개만 조회하고, 생성 시각이 같으면 번호가 큰 글을 먼저 가져옴
    List<Post> findTop20ByDeletedAtIsNullOrderByCreatedAtDescIdDesc();

    Optional<Post> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.id = :id")
    Optional<Post> findByIdForUpdate(@Param("id") long id);
}
