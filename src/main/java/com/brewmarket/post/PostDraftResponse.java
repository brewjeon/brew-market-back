package com.brewmarket.post;

import java.time.Instant;
import java.util.List;

public record PostDraftResponse(Long id, String title, String description, Long price,
                                Instant updatedAt, Instant expiresAt, List<String> imageUrls) {
    public static PostDraftResponse from(PostDraft draft, List<String> urls) {
        return new PostDraftResponse(draft.getId(), draft.getTitle(), draft.getDescription(),
                draft.getPrice(), draft.getUpdatedAt(), draft.getExpiresAt(), urls);
    }
}
