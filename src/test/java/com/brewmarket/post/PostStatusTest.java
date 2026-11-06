package com.brewmarket.post;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PostStatusTest {
    @Test
    void reservesCancelsAndCompletes() {
        Post post = new Post(1L, "머그컵", "설명", 1000L);
        post.changeStatus(PostStatus.RESERVED);
        assertEquals(PostStatus.RESERVED, post.getStatus());
        post.changeStatus(PostStatus.FOR_SALE);
        assertEquals(PostStatus.FOR_SALE, post.getStatus());
        post.changeStatus(PostStatus.COMPLETED);
        assertEquals(PostStatus.COMPLETED, post.getStatus());
        assertDoesNotThrow(() -> post.changeStatus(PostStatus.COMPLETED));
        assertThrows(PostStatusConflictException.class, () -> post.changeStatus(PostStatus.FOR_SALE));
        assertThrows(PostStatusConflictException.class, () -> post.changeStatus(PostStatus.RESERVED));
    }

    @Test
    void rejectsNullStatus() {
        Post post = new Post(1L, "머그컵", "설명", 1000L);
        assertThrows(IllegalArgumentException.class, () -> post.changeStatus(null));
    }
}
