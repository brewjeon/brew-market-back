package com.brewmarket.post;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false, updatable = false)
    private Long sellerId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private Instant createdAt;

    protected Post() {
    }

    public Post(
            Long sellerId,
            String title,
            String description,
            Long price
    ) {
        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException("판매자 ID는 양수여야 합니다.");
        }

        if (title == null || title.isBlank() || title.length() > 100) {
            throw new IllegalArgumentException("제목은 공백이 아닌 100자 이하여야 합니다.");
        }

        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("설명은 필수입니다.");
        }

        if (price == null || price < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        }

        this.sellerId = sellerId;
        this.title = title;
        this.description = description;
        this.price = price;
        this.status = PostStatus.FOR_SALE;
    }

    public Long getId() {
        return id;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Long getPrice() {
        return price;
    }

    public PostStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
