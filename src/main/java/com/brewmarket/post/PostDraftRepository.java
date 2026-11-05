package com.brewmarket.post;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostDraftRepository extends JpaRepository<PostDraft, Long> {
    @Query(value = "SELECT id FROM brew_market.users WHERE id = :sellerId FOR UPDATE", nativeQuery = true)
    Long lockSeller(@Param("sellerId") Long sellerId);
    List<PostDraft> findBySellerIdAndExpiresAtAfterOrderByUpdatedAtDesc(Long sellerId, Instant now);
    Optional<PostDraft> findByIdAndSellerIdAndExpiresAtAfter(Long id, Long sellerId, Instant now);
    List<PostDraft> findTop100ByExpiresAtBeforeOrderByExpiresAtAsc(Instant now);
}
