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

    @Transactional
    public void update(Long postId, Long userId, String title, String description, Long price) {
        Post post = postRepository.findById(postId)
                .orElseThrow(PostNotFoundException::new);

        if (!post.getSellerId().equals(userId)) {
            throw new PostAccessDeniedException();
        }

        // 조회한 엔티티의 변경은 트랜잭션이 커밋될 때 DB에 반영됩니다.
        post.updateContent(title, description, price);
    }

    @Transactional(readOnly = true)
    public PostDetailResponse findDetail(Long postId) {
        // 저장소의 기본 단건 조회 사용. 해당 게시글이 없으면 예외로 중단
        Post post = postRepository.findById(postId)
                .orElseThrow(PostNotFoundException::new);

        return PostDetailResponse.from(post);
    }

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
