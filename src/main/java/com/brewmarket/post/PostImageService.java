package com.brewmarket.post;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PostImageService {

    private final PostService posts;
    private final PostRepository repository;
    private final PostImageStorage storage;

    @Transactional
    public Long create(Long userId, CreatePostRequest request, MultipartFile image) {
        Long id = posts.create(userId, request.title(), request.description(), request.price());
        Post post = repository.findById(id).orElseThrow(PostNotFoundException::new);

        if (image != null) {
            replace(post, image, false);
        }

        return id;
    }

    @Transactional
    public void update(Long id, Long userId, UpdatePostRequest request, MultipartFile image, boolean removeImage) {
        if (image != null && removeImage) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진 교체와 삭제를 함께 요청할 수 없습니다.");
        }

        // 기존 서비스에서 작성자 권한과 본문 입력값을 먼저 검사한다.
        posts.update(id, userId, request.title(), request.description(), request.price());
        Post post = repository.findById(id).orElseThrow(PostNotFoundException::new);
        replace(post, image, removeImage);
    }

    private void replace(Post post, MultipartFile image, boolean removeImage) {
        if (image == null && !removeImage) return;

        String oldKey = post.getImageKey();
        String newKey = image == null ? null : storage.save(image);

        // DB 롤백 때는 새 파일을, 커밋 이후에는 기존 파일을 정리한다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    storage.delete(oldKey);
                } else {
                    storage.delete(newKey);
                }
            }
        });

        post.changeImage(newKey);
    }

    @Transactional(readOnly = true)
    public FileSystemResource read(Long id) {
        Post post = repository.findByIdAndDeletedAtIsNull(id).orElseThrow(PostNotFoundException::new);
        if (post.getImageKey() == null) throw new PostNotFoundException();

        FileSystemResource resource = new FileSystemResource(storage.resolve(post.getImageKey()));
        if (!resource.exists() || !resource.isReadable()) {
            org.slf4j.LoggerFactory.getLogger(PostImageService.class).warn("게시글 사진 파일 없음: postId={}", id);
            throw new PostNotFoundException();
        }

        return resource;
    }
}
