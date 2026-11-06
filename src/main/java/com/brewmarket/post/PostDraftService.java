package com.brewmarket.post;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PostDraftService {
    private final PostDraftRepository drafts;
    private final PostImageService images;
    private final PostService posts;

    @Transactional
    public PostDraftResponse save(Long sellerId, Long id, DraftSaveRequest request,
                                  List<MultipartFile> files, List<String> order) {
        if ((request.title() != null && request.title().length() > 100)
                || (request.price() != null && request.price() < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목은 100자 이하, 가격은 0 이상이어야 합니다.");
        }
        Instant now = Instant.now();
        PostDraft draft;
        if (id == null) {
            // 한 사용자가 동시에 여러 탭에서 저장해도 최대 5개 제한을 지킨다.
            if (drafts.lockSeller(sellerId) == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            if (drafts.findBySellerIdAndExpiresAtAfterOrderByUpdatedAtDesc(sellerId, now).size() >= 5) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "임시저장은 최대 5개입니다.");
            }
            draft = new PostDraft(sellerId);
        } else {
            draft = locked(id, sellerId, now);
        }
        draft.save(request.title(), request.description(), request.price(), now);
        drafts.saveAndFlush(draft);
        images.replaceDraft(draft.getId(), files, order);
        return PostDraftResponse.from(draft, images.draftUrls(draft.getId()));
    }

    @Transactional(readOnly = true)
    public List<PostDraftResponse> list(Long sellerId) {
        return drafts.findBySellerIdAndExpiresAtAfterOrderByUpdatedAtDesc(sellerId, Instant.now()).stream()
                .map(draft -> PostDraftResponse.from(draft, images.draftUrls(draft.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public PostDraftResponse detail(Long sellerId, Long id) {
        PostDraft draft = active(id, sellerId, Instant.now());
        return PostDraftResponse.from(draft, images.draftUrls(id));
    }

    @Transactional
    public Long publish(Long sellerId, Long id, CreatePostRequest request,
                        List<MultipartFile> files, List<String> order) {
        PostDraft draft = locked(id, sellerId, Instant.now());
        Long postId = posts.create(sellerId, request.title(), request.description(), request.price());
        images.replaceDraft(id, files, order);
        images.transferDraft(id, postId);
        drafts.delete(draft);
        return postId;
    }

    @Transactional
    public void delete(Long sellerId, Long id) {
        PostDraft draft = locked(id, sellerId, Instant.now());
        images.deleteDraftImages(id);
        drafts.delete(draft);
    }

    @Transactional
    public void deleteExpired() {
        List<PostDraft> expired;
        do {
            expired = drafts.findTop100ByExpiresAtBeforeOrderByExpiresAtAsc(Instant.now());
            for (PostDraft draft : expired) {
                images.deleteDraftImages(draft.getId());
                drafts.delete(draft);
            }
            drafts.flush();
        } while (expired.size() == 100);
    }

    private PostDraft active(Long id, Long sellerId, Instant now) {
        return drafts.findByIdAndSellerIdAndExpiresAtAfter(id, sellerId, now)
                .orElseThrow(PostNotFoundException::new);
    }

    private PostDraft locked(Long id, Long sellerId, Instant now) {
        PostDraft draft = drafts.findActiveForUpdate(id, sellerId, now)
                .orElseThrow(PostNotFoundException::new);
        if (!draft.getExpiresAt().isAfter(Instant.now())) throw new PostNotFoundException();
        return draft;
    }
}
