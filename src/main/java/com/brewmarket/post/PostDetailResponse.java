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
        String imageUrl,
        java.util.List<String> imageUrls
) {
    public PostDetailResponse(Long id, String title, String description, Long price,
                              PostStatus status, Instant createdAt, boolean canManage) {
        this(id, title, description, price, status, createdAt, canManage, null, java.util.List.of());
    }

    public PostDetailResponse(Long id, String title, String description, Long price,
                              PostStatus status, Instant createdAt, boolean canManage, String imageUrl) {
        this(id, title, description, price, status, createdAt, canManage, imageUrl,
                imageUrl == null ? java.util.List.of() : java.util.List.of(imageUrl));
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
                post.getImageKey() == null ? null : "/posts/" + post.getId() + "/image",
                post.getImageKey() == null ? java.util.List.of() : java.util.List.of("/posts/" + post.getId() + "/image")
        );
    }

    public static PostDetailResponse from(Post post, Long viewerId, java.util.List<PostImage> images) {
        var urls = java.util.stream.IntStream.range(0, images.size())
                .mapToObj(i -> "/posts/" + post.getId() + "/images/" + i).toList();
        return new PostDetailResponse(post.getId(), post.getTitle(), post.getDescription(), post.getPrice(),
                post.getStatus(), post.getCreatedAt(), viewerId != null && post.getSellerId().equals(viewerId),
                urls.isEmpty() ? null : urls.get(0), urls);
    }
}
