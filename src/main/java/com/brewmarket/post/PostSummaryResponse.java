package com.brewmarket.post;

// 목록에서 공개할 정보만 응답하고 판매자 정보와 본문은 제외
public record PostSummaryResponse(
        Long id,
        String title,
        Long price,
        PostStatus status
) {
    public static PostSummaryResponse from(Post post) {
        return new PostSummaryResponse(
                post.getId(),
                post.getTitle(),
                post.getPrice(),
                post.getStatus()
        );
    }
}
