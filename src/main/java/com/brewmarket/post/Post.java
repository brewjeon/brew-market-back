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

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // 동시에 읽은 예전 게시글로 수정·삭제 상태를 덮어쓰지 못하게 합니다.
    @Version
    @Column(nullable = false)
    private Long version;

    public void delete() {
        if (deletedAt == null) {
            deletedAt = Instant.now();
        }
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

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

        validateContent(title, description, price);
        this.sellerId = sellerId;
        this.title = title;
        this.description = description;
        this.price = price;
        this.status = PostStatus.FOR_SALE;
    }

    public void updateContent(String title, String description, Long price) {
        if (deletedAt != null) {
            throw new PostNotFoundException();
        }
        // 모든 값을 검증한 뒤 변경해 잘못된 입력으로 일부 필드만 바뀌지 않게 합니다.
        validateContent(title, description, price);
        this.title = title;
        this.description = description;
        this.price = price;
    }

    private static void validateContent(String title, String description, Long price) {
        if (title == null || title.isBlank() || title.length() > 100) {
            throw new IllegalArgumentException("제목은 공백이 아닌 100자 이하여야 합니다.");
        }

        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("설명은 필수입니다.");
        }

        if (price == null || price < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        }
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
