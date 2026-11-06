package com.brewmarket.post;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final PostImageRepository images;
    private final PostDraftImageRepository draftImages;
    private final PostImageStorage storage;
    private final ImageDeletionQueue deletionQueue;

    @Transactional
    public Long create(Long userId, CreatePostRequest request, MultipartFile image) {
        return create(userId, request, image == null ? List.of() : List.of(image),
                image == null ? List.of() : List.of("n:0"));
    }

    @Transactional
    public void update(Long id, Long userId, UpdatePostRequest request, MultipartFile image, boolean removeImage) {
        if (image != null && removeImage) badOrder();
        List<MultipartFile> files = image == null ? List.of() : List.of(image);
        List<String> order = image != null ? List.of("n:0")
                : removeImage ? List.of()
                : java.util.stream.IntStream.range(0, images.findByPostIdOrderByPositionAsc(id).size())
                    .mapToObj(i -> "e:" + i).toList();
        update(id, userId, request, files, order);
    }

    @Transactional
    public Long create(Long userId, CreatePostRequest request, List<MultipartFile> files, List<String> order) {
        Long id = posts.create(userId, request.title(), request.description(), request.price());
        replacePost(id, files, order);
        return id;
    }

    @Transactional
    public void update(Long id, Long userId, UpdatePostRequest request,
                       List<MultipartFile> files, List<String> order) {
        posts.update(id, userId, request.title(), request.description(), request.price());
        replacePost(id, files, order);
    }

    @Transactional
    public void replacePost(Long id, List<MultipartFile> files, List<String> order) {
        Post post = repository.findByIdAndDeletedAtIsNull(id).orElseThrow(PostNotFoundException::new);
        List<PostImage> before = images.findByPostIdOrderByPositionAsc(id);
        List<String> oldKeys = before.stream().map(PostImage::getImageKey).toList();
        List<String> next = compose(oldKeys, files, order);
        if (next.equals(oldKeys)) return;
        images.deleteByPostId(id);
        images.flush();
        for (int i = 0; i < next.size(); i++) images.save(new PostImage(id, next.get(i), i));
        post.changeImage(next.isEmpty() ? null : next.get(0));
        cleanupRemoved(oldKeys, next);
    }

    @Transactional
    public void replaceDraft(Long draftId, List<MultipartFile> files, List<String> order) {
        List<String> oldKeys = draftImages.findByDraftIdOrderByPositionAsc(draftId).stream()
                .map(PostDraftImage::getImageKey).toList();
        List<String> next = compose(oldKeys, files, order);
        if (next.equals(oldKeys)) return;
        draftImages.deleteByDraftId(draftId);
        draftImages.flush();
        for (int i = 0; i < next.size(); i++) draftImages.save(new PostDraftImage(draftId, next.get(i), i));
        cleanupRemoved(oldKeys, next);
    }

    @Transactional
    public void transferDraft(Long draftId, Long postId) {
        List<String> keys = draftImages.findByDraftIdOrderByPositionAsc(draftId).stream()
                .map(PostDraftImage::getImageKey).toList();
        draftImages.deleteByDraftId(draftId);
        draftImages.flush();
        for (int i = 0; i < keys.size(); i++) images.save(new PostImage(postId, keys.get(i), i));
        repository.findById(postId).orElseThrow(PostNotFoundException::new)
                .changeImage(keys.isEmpty() ? null : keys.get(0));
    }

    @Transactional
    public void deleteDraftImages(Long draftId) {
        List<String> keys = draftImages.findByDraftIdOrderByPositionAsc(draftId).stream()
                .map(PostDraftImage::getImageKey).toList();
        draftImages.deleteByDraftId(draftId);
        deletionQueue.schedule(keys);
    }

    public List<String> draftUrls(Long draftId) {
        List<PostDraftImage> found = draftImages.findByDraftIdOrderByPositionAsc(draftId);
        return java.util.stream.IntStream.range(0, found.size())
                .mapToObj(i -> "/drafts/" + draftId + "/images/" + i).toList();
    }

    @Transactional(readOnly = true)
    public FileSystemResource read(Long id, int index) {
        repository.findByIdAndDeletedAtIsNull(id).orElseThrow(PostNotFoundException::new);
        return file(imageKey(images.findByPostIdOrderByPositionAsc(id), index));
    }

    @Transactional(readOnly = true)
    public FileSystemResource read(Long id) { return read(id, 0); }

    @Transactional(readOnly = true)
    public FileSystemResource readDraft(Long draftId, int index) {
        return file(imageKey(draftImages.findByDraftIdOrderByPositionAsc(draftId), index));
    }

    private String imageKey(List<? extends Object> entries, int index) {
        if (index < 0 || index >= entries.size()) throw new PostNotFoundException();
        Object entry = entries.get(index);
        return entry instanceof PostImage image ? image.getImageKey() : ((PostDraftImage) entry).getImageKey();
    }

    private FileSystemResource file(String key) {
        FileSystemResource resource = new FileSystemResource(storage.resolve(key));
        if (!resource.exists() || !resource.isReadable()) throw new PostNotFoundException();
        return resource;
    }

    private List<String> compose(List<String> oldKeys, List<MultipartFile> files, List<String> order) {
        List<MultipartFile> incoming = files == null ? List.of() : files;
        List<String> requested = order == null ? List.of() : order;
        if (requested.size() > 5 || incoming.size() > 5) badOrder();
        Set<String> seen = new HashSet<>();
        Set<Integer> newIndexes = new HashSet<>();
        for (String token : requested) {
            if (!seen.add(token) || token == null || !token.matches("[en]:[0-4]")) badOrder();
            int index = token.charAt(2) - '0';
            if (token.charAt(0) == 'e' && index >= oldKeys.size()) badOrder();
            if (token.charAt(0) == 'n') {
                if (index >= incoming.size()) badOrder();
                newIndexes.add(index);
            }
        }
        if (newIndexes.size() != incoming.size()) badOrder();
        List<String> newKeys = new ArrayList<>();
        for (MultipartFile file : incoming) {
            String key = storage.save(file);
            newKeys.add(key);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) storage.delete(key);
                }
            });
        }
        List<String> result = new ArrayList<>();
        for (String token : requested) {
            int index = token.charAt(2) - '0';
            result.add(token.charAt(0) == 'e' ? oldKeys.get(index) : newKeys.get(index));
        }
        return result;
    }

    private void cleanupRemoved(List<String> oldKeys, List<String> next) {
        deletionQueue.schedule(oldKeys.stream().filter(key -> !next.contains(key)).toList());
    }

    private void badOrder() {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진 순서가 올바르지 않습니다. 최대 5장까지 가능합니다.");
    }
}
