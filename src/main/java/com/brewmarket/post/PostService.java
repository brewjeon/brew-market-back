package com.brewmarket.post;

import java.time.Instant;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;

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
    private final PostImageRepository postImageRepository;
    private final ImageDeletionQueue imageDeletionQueue;

    @Transactional(readOnly = true)
    public PostPageResponse findPage(Instant beforeTime, Long beforeId) {
        return findPage(beforeTime, beforeId, "");
    }

    @Transactional(readOnly = true)
    public PostPageResponse findPage(Instant beforeTime, Long beforeId, String keyword) {
        String query = keyword == null ? "" : keyword.strip();

        if (query.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }

        validateCursor(beforeTime, beforeId);

        // 한 건을 더 읽어 다음 목록 존재 여부를 판단한다.
        PageRequest pageRequest = PageRequest.of(0, 21);
        List<Post> rows;

        if (!query.isEmpty()) {
            if (beforeTime == null) {
                rows = postRepository.searchFirst(query, pageRequest);
            } else {
                rows = postRepository.searchNext(query, beforeTime, beforeId, pageRequest);
            }
        } else {
            if (beforeTime == null) {
                rows = postRepository.findTop21ByDeletedAtIsNullOrderByCreatedAtDescIdDesc();
            } else {
                rows = postRepository.findNextPage(beforeTime, beforeId, pageRequest);
            }
        }

        return toPage(rows);
    }

    @Transactional(readOnly = true)
    public PostPageResponse findMine(Long sellerId, Instant beforeTime, Long beforeId) {
        validateCursor(beforeTime, beforeId);
        var limit = PageRequest.of(0, 21);
        var rows = beforeTime == null
                ? postRepository.findBySellerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(sellerId, limit)
                : postRepository.findNextMine(sellerId, beforeTime, beforeId, limit);
        return toPage(rows);
    }

    private void validateCursor(Instant beforeTime, Long beforeId) {
        if ((beforeTime == null) != (beforeId == null) || (beforeId != null && beforeId <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    private PostPageResponse toPage(List<Post> rows) {
        boolean hasNext = rows.size() > 20;

        List<Post> visible = rows.subList(0, Math.min(20, rows.size()));
        Post last = hasNext ? visible.get(visible.size() - 1) : null;

        return new PostPageResponse(
                visible.stream().map(PostSummaryResponse::from).toList(),
                hasNext,
                last == null ? null : last.getCreatedAt(),
                last == null ? null : last.getId()
        );
    }

    @Transactional
    public void changeStatus(Long postId, Long userId, PostStatus status) {
        Post post = findActivePost(postId);

        if (!post.getSellerId().equals(userId)) {
            throw new PostAccessDeniedException();
        }

        post.changeStatus(status);
    }

    @Transactional
    public void delete(Long postId, Long userId) {
        Post post = findActivePost(postId);

        if (!post.getSellerId().equals(userId)) {
            throw new PostAccessDeniedException();
        }

        var keys = postImageRepository.findByPostIdOrderByPositionAsc(postId).stream()
                .map(PostImage::getImageKey).toList();
        postImageRepository.deleteByPostId(postId);
        postImageRepository.flush();
        postRepository.delete(post);
        postRepository.flush();
        imageDeletionQueue.schedule(keys);
    }

    private Post findActivePost(Long postId) {
        return postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(PostNotFoundException::new);
    }

    @Transactional
    public void update(Long postId, Long userId, String title, String description, Long price) {
        Post post = findActivePost(postId);

        if (!post.getSellerId().equals(userId)) {
            throw new PostAccessDeniedException();
        }

        // 조회한 엔티티의 변경은 트랜잭션이 커밋될 때 DB에 반영됩니다.
        post.updateContent(title, description, price);
    }

    @Transactional(readOnly = true)
    public PostDetailResponse findDetail(Long postId, Long viewerId) {
        // 저장소의 기본 단건 조회 사용. 해당 게시글이 없으면 예외로 중단
        Post post = findActivePost(postId);

        return PostDetailResponse.from(post, viewerId, postImageRepository.findByPostIdOrderByPositionAsc(postId));
    }

    // 조회한 엔티티를 그대로 노출하지 않고 공개할 목록 정보로 변환
    @Transactional(readOnly = true)
    public List<PostSummaryResponse> findLatest() {
        return postRepository.findTop20ByDeletedAtIsNullOrderByCreatedAtDescIdDesc()
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
