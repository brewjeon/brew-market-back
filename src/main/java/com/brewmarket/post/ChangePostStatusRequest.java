package com.brewmarket.post;

import jakarta.validation.constraints.NotNull;

public record ChangePostStatusRequest(
        @NotNull(message = "거래 상태는 필수입니다.") PostStatus status
) {
}
