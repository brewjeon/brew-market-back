package com.brewmarket.post;

import com.brewmarket.auth.BrewOidcUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// 게시글 관련 웹 요청을 받고 데이터를 응답
@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    // 게시글 조회와 등록에 사용할 서비스 객체의 참조 보관
    private final PostService postService;

    @PatchMapping("/{postId}")
    public ResponseEntity<Void> update(
            @PathVariable("postId") Long postId,
            @AuthenticationPrincipal BrewOidcUser user,
            @Valid @RequestBody UpdatePostRequest request
    ) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        postService.update(postId, user.getUserId(),
                request.title(), request.description(), request.price());
        return ResponseEntity.noContent().build();
    }

    // GET /posts 요청에는 최신 게시글 목록을 반환
    @GetMapping
    public List<PostSummaryResponse> findLatest() {
        return postService.findLatest();
    }

    // 주소에 들어온 게시글 번호로 상세 정보 한 건 조회
    @GetMapping("/{postId}")
    public PostDetailResponse findDetail(@PathVariable("postId") Long postId) {
        return postService.findDetail(postId);
    }

    // POST /posts 요청을 게시글 등록 처리와 연결
    @PostMapping
    public ResponseEntity<CreatePostResponse> create(
            // 서버가 관리하는 현재 로그인 사용자 정보
            @AuthenticationPrincipal BrewOidcUser user,

            // 화면에서 받은 게시글 내용과 입력값 검증
            @Valid @RequestBody CreatePostRequest request
    ) {
        // 예상한 로그인 사용자 정보가 없으면 등록 중단
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        // 작성자는 로그인 정보에서, 게시글 내용은 요청 본문에서 가져옴
        Long postId = postService.create(
                user.getUserId(),
                request.title(),
                request.description(),
                request.price()
        );

        // 생성된 게시글 번호를 응답 본문에 담아 반환
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreatePostResponse(postId));
    }
}
