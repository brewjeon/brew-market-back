package com.brewmarket.neighborhood;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface NeighborhoodVerificationRepository
        extends JpaRepository<NeighborhoodVerification, NeighborhoodVerification.Key> {
    long countByIdUserIdAndExpiresAtAfter(Long userId, Instant now);

    @Query("""
            select new com.brewmarket.neighborhood.NeighborhoodOption(n.id, n.name, v.expiresAt)
            from NeighborhoodVerification v join Neighborhood n on n.id = v.id.neighborhoodId
            where v.id.userId = :userId and v.expiresAt > :now
            order by n.name, n.id
            """)
    List<NeighborhoodOption> findActive(Long userId, Instant now);
}
