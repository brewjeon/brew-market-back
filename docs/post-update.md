# 게시글 수정 API

## 개발 목적과 파일별 역할

작성자가 등록한 글의 제목·본문·가격을 고칠 수 있게 하되, 다른 회원은 수정하지 못하게 합니다. 로그인 확인과 게시글 소유권 확인을 분리하고, 입력 오류나 권한 거절 시 기존 내용을 보존합니다.

아래 경로는 백엔드 저장소 루트 기준입니다.

| 구분 | 파일 | 생성·변경 목적 |
|---|---|---|
| 신규 | `src/main/java/com/brewmarket/post/UpdatePostRequest.java` | 수정 요청의 제목·본문·가격을 정의하고 필수값·길이·가격 범위를 검증합니다. 등록 요청과 분리해 수정 계약을 독립적으로 관리합니다. |
| 신규 | `src/main/java/com/brewmarket/post/PostAccessDeniedException.java` | 다른 회원의 글을 수정하려는 상황을 명시적으로 표현합니다. |
| 변경 | `src/main/java/com/brewmarket/post/PostController.java` | PATCH 요청을 받고 로그인 정보의 회원 번호와 수정 내용을 서비스에 전달합니다. 성공하면 204를 반환합니다. |
| 변경 | `src/main/java/com/brewmarket/config/SecurityConfig.java` | 수정 경로에 로그인 조건을 적용합니다. 기존 CSRF 보호를 유지합니다. |
| 변경 | `src/main/java/com/brewmarket/post/PostService.java` | 게시글 조회·작성자 확인·내용 변경을 하나의 트랜잭션으로 처리합니다. |
| 변경 | `src/main/java/com/brewmarket/post/Post.java` | `updateContent()`로 변경 가능한 필드를 제한합니다. 생성·수정의 검증을 공유하고 모든 검증 후 값을 바꿉니다. |
| 변경 | `src/main/java/com/brewmarket/error/GlobalExceptionHandler.java` | 작성자 권한 예외를 403과 `POST_ACCESS_DENIED` 응답으로 변환합니다. |
| 신규 | `src/test/java/com/brewmarket/post/PostUpdateTest.java` | 실제 웹·서비스·DB를 연결해 수정 결과, 권한 거절, 입력 오류, 인증·CSRF 조건과 기존 데이터 보존을 검사하도록 작성했습니다. |
| 변경 | `src/test/java/com/brewmarket/post/PostControllerTest.java` | 인증된 회원 번호 전달과 수정 권한 오류의 HTTP 응답을 검사합니다. |
| 변경 | `src/test/java/com/brewmarket/post/PostTest.java` | 잘못된 수정 입력으로 객체 일부만 변경되지 않는지 검사합니다. |
| 변경 | `README.md` | 수정 API를 소개하고 이 문서로 연결합니다. |
| 신규 | `docs/post-update.md` | 수정 계약·구현 책임·검증 범위·남은 제한을 기록합니다. |

읽는 순서: 요청 DTO → 컨트롤러 → 서비스 → 엔티티 → 예외 처리입니다. 보안 설정은 컨트롤러 진입 전의 조건을, 테스트는 각 계층이 지켜야 할 동작을 설명합니다.

## 요청과 처리 계약

`PATCH /posts/{postId}`는 작성자가 제목·본문·가격을 변경합니다. 이 API는 세 항목을 모두 받는 내용 수정 명령이며, 누락한 필드를 유지하는 JSON Merge Patch 방식은 아닙니다.

```json
{
  "title": "머그컵 나눔",
  "description": "직접 가져가실 분께 드립니다.",
  "price": 0
}
```

- 로그인 세션과 CSRF 토큰이 필요합니다. 로그인 후 `GET /auth/csrf`가 반환하는 `headerName`을 헤더 이름으로, `token`을 헤더 값으로 전송합니다.
- 작성자는 요청 본문이 아닌 로그인 정보에서 결정합니다. 서비스에서 게시글의 `sellerId`와 대조합니다.
- 제목은 공백이 아닌 최대 100자, 본문은 공백 불가, 가격은 필수이며 0 이상입니다.
- 작성자·거래 상태·생성 시각은 변경하지 않습니다. 현재는 거래 상태별 내용 수정 제한을 두지 않습니다.
- 성공은 본문 없는 `204`입니다. 변경된 내용은 공개 상세 조회 `GET /posts/{postId}`로 확인합니다.

| 조건 | 상태 | 오류 코드 |
|---|---|---|
| 유효하지 않거나 누락된 내용 | 400 | INVALID_REQUEST |
| 로그인 없음, 유효한 CSRF 포함 | 401 | 보안 필터 응답 |
| CSRF 누락·불일치 | 403 | 보안 필터 응답 |
| 다른 회원의 게시글 | 403 | POST_ACCESS_DENIED |
| 존재하지 않는 게시글 | 404 | POST_NOT_FOUND |

본문 검증과 보안 검사를 통과한 요청에 대해 게시글 조회 → 작성자 확인 → 엔티티 내용 검증·변경 순서로 처리합니다. 트랜잭션 커밋 시 JPA 변경 감지로 저장합니다. 실패 시 기존 내용을 유지합니다.

`PostUpdateTest`는 실제 컨트롤러·보안 설정·서비스와 별도 PostgreSQL 컨테이너를 연결합니다. 테스트용 로그인 정보를 사용하므로 구글 인증 서버와의 왕복은 검증하지 않습니다. 테스트 전체를 트랜잭션으로 감싸지 않고 요청 완료 후 DB를 재조회합니다.

동시 수정의 버전 충돌 감지는 아직 없습니다. 같은 작성자의 동시 요청에서는 나중에 반영된 수정이 앞선 내용을 덮어쓸 수 있습니다. 거래 상태 전환·삭제 정책은 별도 범위입니다.
