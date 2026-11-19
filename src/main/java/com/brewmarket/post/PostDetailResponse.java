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
        java.util.List<String> imageUrls,
        Long neighborhoodId,
        String neighborhoodName,
        Long reservationRoomId
) {
    public PostDetailResponse(Long id, String title, String description, Long price,
                              PostStatus status, Instant createdAt, boolean canManage) {
        this(id, title, description, price, status, createdAt, canManage, null, java.util.List.of(), null, null, null);
    }

    public PostDetailResponse(Long id, String title, String description, Long price,
                              PostStatus status, Instant createdAt, boolean canManage, String imageUrl) {
        this(id, title, description, price, status, createdAt, canManage, imageUrl,
                imageUrl == null ? java.util.List.of() : java.util.List.of(imageUrl), null, null, null);
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
                post.getImageKey() == null ? java.util.List.of() : java.util.List.of("/posts/" + post.getId() + "/image"),
                post.getNeighborhoodId(),
                null,
                sellerReservationRoom(post, viewerId)
        );
    }

    public static PostDetailResponse from(Post post, Long viewerId, java.util.List<PostImage> images,
                                          String neighborhoodName) {
        var urls = java.util.stream.IntStream.range(0, images.size())
                .mapToObj(i -> "/posts/" + post.getId() + "/images/" + i).toList();
        return new PostDetailResponse(post.getId(), post.getTitle(), post.getDescription(), post.getPrice(),
                post.getStatus(), post.getCreatedAt(), viewerId != null && post.getSellerId().equals(viewerId),
                urls.isEmpty() ? null : urls.get(0), urls, post.getNeighborhoodId(), neighborhoodName,
                sellerReservationRoom(post, viewerId));
    }

    private static Long sellerReservationRoom(Post post, Long viewerId) {
        return post.getSellerId().equals(viewerId) ? post.getReservationRoomId() : null;
    }
}
