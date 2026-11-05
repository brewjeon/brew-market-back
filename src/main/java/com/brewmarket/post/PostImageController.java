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
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostImageController {
    private final PostImageService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CreatePostResponse> create(
            @AuthenticationPrincipal BrewOidcUser user,
            @Valid @RequestPart("post") CreatePostRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @RequestPart(value = "order", required = false) List<String> order,
            @RequestPart(value = "image", required = false) MultipartFile legacyImage
    ) {
        requireLogin(user);
        Long id = order == null
                ? service.create(user.getUserId(), request, legacyImage)
                : service.create(user.getUserId(), request, images, order);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatePostResponse(id));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> update(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal BrewOidcUser user,
            @Valid @RequestPart("post") UpdatePostRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @RequestPart(value = "order", required = false) List<String> order,
            @RequestPart(value = "image", required = false) MultipartFile legacyImage,
            @RequestParam(value = "removeImage", defaultValue = "false") boolean removeImage
    ) {
        requireLogin(user);
        if (order == null) service.update(id, user.getUserId(), request, legacyImage, removeImage);
        else service.update(id, user.getUserId(), request, images, order);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable("id") Long id) {
        return image(id, 0);
    }

    @GetMapping("/{id}/images/{index}")
    public ResponseEntity<Resource> image(@PathVariable("id") Long id, @PathVariable("index") int index) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG).body(service.read(id, index));
    }

    private void requireLogin(BrewOidcUser user) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
}
