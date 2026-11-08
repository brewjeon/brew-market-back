package com.brewmarket.post;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface PostDraftRepository extends JpaRepository<PostDraft, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PostDraft d where d.id = :id and d.sellerId = :sellerId and d.expiresAt > :now")
    Optional<PostDraft> findActiveForUpdate(@Param("id") Long id, @Param("sellerId") Long sellerId,
                                             @Param("now") Instant now);
    List<PostDraft> findBySellerIdAndExpiresAtAfterOrderByUpdatedAtDesc(Long sellerId, Instant now);
    Optional<PostDraft> findByIdAndSellerIdAndExpiresAtAfter(Long id, Long sellerId, Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PostDraft> findTop100ByExpiresAtBeforeOrderByExpiresAtAsc(Instant now);
}
