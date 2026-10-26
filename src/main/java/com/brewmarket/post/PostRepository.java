package com.brewmarket.post;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    // DB에서 최신 20개만 조회하고, 생성 시각이 같으면 번호가 큰 글을 먼저 가져옴
    List<Post> findTop20ByOrderByCreatedAtDescIdDesc();
}
