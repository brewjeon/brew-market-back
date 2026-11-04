package com.brewmarket.post;

import com.brewmarket.auth.BrewOidcUser;
import jakarta.validation.Valid;
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
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        requireLogin(user);
        Long id = service.create(user.getUserId(), request, image);

        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatePostResponse(id));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> update(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal BrewOidcUser user,
            @Valid @RequestPart("post") UpdatePostRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "removeImage", defaultValue = "false") boolean removeImage
    ) {
        requireLogin(user);
        service.update(id, user.getUserId(), request, image, removeImage);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable("id") Long id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .body(service.read(id));
    }

    private void requireLogin(BrewOidcUser user) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
}
