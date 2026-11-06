package com.brewmarket.post;

import com.brewmarket.auth.BrewOidcUser;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/drafts")
@RequiredArgsConstructor
public class PostDraftController {
    private final PostDraftService service;
    private final PostImageService images;

    @GetMapping
    public List<PostDraftResponse> list(@AuthenticationPrincipal BrewOidcUser user) {
        return service.list(userId(user));
    }

    @GetMapping("/{id}")
    public PostDraftResponse detail(@PathVariable("id") Long id, @AuthenticationPrincipal BrewOidcUser user) {
        return service.detail(userId(user), id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostDraftResponse> create(
            @AuthenticationPrincipal BrewOidcUser user,
            @RequestPart("draft") DraftSaveRequest request,
            @RequestPart("order") List<String> order,
            @RequestPart(value = "images", required = false) List<MultipartFile> files) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(userId(user), null, request, files, order));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PostDraftResponse update(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal BrewOidcUser user,
            @RequestPart("draft") DraftSaveRequest request,
            @RequestPart("order") List<String> order,
            @RequestPart(value = "images", required = false) List<MultipartFile> files) {
        return service.save(userId(user), id, request, files, order);
    }

    @PostMapping(value = "/{id}/publish", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CreatePostResponse> publish(
            @PathVariable("id") Long id, @AuthenticationPrincipal BrewOidcUser user,
            @Valid @RequestPart("post") CreatePostRequest request,
            @RequestPart("order") List<String> order,
            @RequestPart(value = "images", required = false) List<MultipartFile> files) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreatePostResponse(service.publish(userId(user), id, request, files, order)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id, @AuthenticationPrincipal BrewOidcUser user) {
        service.delete(userId(user), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/images/{index}")
    public ResponseEntity<Resource> image(@PathVariable("id") Long id, @PathVariable("index") int index,
                                          @AuthenticationPrincipal BrewOidcUser user) {
        service.detail(userId(user), id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG).body(images.readDraft(id, index));
    }

    private Long userId(BrewOidcUser user) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return user.getUserId();
    }
}
