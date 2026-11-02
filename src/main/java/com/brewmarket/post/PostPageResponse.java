package com.brewmarket.post;

import java.time.Instant;
import java.util.List;

public record PostPageResponse(List<PostSummaryResponse> items, boolean hasNext,
                               Instant nextCreatedAt, Long nextId) {
}
