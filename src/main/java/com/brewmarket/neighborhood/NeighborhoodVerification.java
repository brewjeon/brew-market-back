package com.brewmarket.neighborhood;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_neighborhood_verifications")
class NeighborhoodVerification {
    @Embeddable
    record Key(@Column(name = "user_id") Long userId,
               @Column(name = "neighborhood_id") Long neighborhoodId) {}

    @EmbeddedId
    Key id;
    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    protected NeighborhoodVerification() {}

    NeighborhoodVerification(Key id, Instant expiresAt) {
        this.id = id;
        this.expiresAt = expiresAt;
    }
}
