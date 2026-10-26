package com.brewmarket.post;

import com.brewmarket.user.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;

    // 조회한 엔티티를 그대로 노출하지 않고 공개할 목록 정보로 변환
    @Transactional(readOnly = true)
    public List<PostSummaryResponse> findLatest() {
        return postRepository.findTop20ByOrderByCreatedAtDescIdDesc()
                .stream()
                .map(PostSummaryResponse::from)
                .toList();
    }

    @Transactional
    public Long create(
            Long sellerId,
            String title,
            String description,
            Long price
    ) {
        Post post = new Post(sellerId, title, description, price);

        if (!userRepository.existsById(sellerId)) {
            throw new IllegalArgumentException("존재하지 않는 판매자입니다.");
        }

        Post savedPost = postRepository.saveAndFlush(post);

        return savedPost.getId();
    }
}
