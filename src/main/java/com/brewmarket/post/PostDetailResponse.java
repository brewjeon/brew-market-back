package com.brewmarket.post;

import java.time.Instant;

// 상세 화면에 필요한 정보만 전달하고 판매자의 계정 정보는 제외
public record PostDetailResponse(
        Long id,
        String title,
        String description,
        Long price,
        PostStatus status,
        Instant createdAt,
        boolean canManage,
        String imageUrl
) {
    public PostDetailResponse(Long id, String title, String description, Long price,
                              PostStatus status, Instant createdAt, boolean canManage) {
        this(id, title, description, price, status, createdAt, canManage, null);
    }

    public static PostDetailResponse from(Post post, Long viewerId) {
        return new PostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getDescription(),
                post.getPrice(),
                post.getStatus(),
                post.getCreatedAt(),
                viewerId != null && post.getSellerId().equals(viewerId),
                post.getImageKey() == null ? null : "/posts/" + post.getId() + "/image"
        );
    }
}
