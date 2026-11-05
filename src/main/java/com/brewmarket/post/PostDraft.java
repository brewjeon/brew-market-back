package com.brewmarket.post;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "post_drafts")
public class PostDraft {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "seller_id", nullable = false, updatable = false)
    private Long sellerId;
    @Column(length = 100)
    private String title;
    @Column(columnDefinition = "text")
    private String description;
    private Long price;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected PostDraft() {}
    public PostDraft(Long sellerId) { this.sellerId = sellerId; }
    public void save(String title, String description, Long price, Instant now) {
        if (title != null && title.length() > 100) throw new IllegalArgumentException("제목은 100자 이하여야 합니다.");
        if (price != null && price < 0) throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        this.title = title;
        this.description = description;
        this.price = price;
        this.updatedAt = now;
        this.expiresAt = now.plusSeconds(3 * 24 * 60 * 60);
    }
    public Long getId() { return id; }
    public Long getSellerId() { return sellerId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Long getPrice() { return price; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
