package com.brewmarket.post;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {
    List<PostImage> findByPostIdOrderByPositionAsc(Long postId);
    void deleteByPostId(Long postId);
}
