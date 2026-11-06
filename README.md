# Brew Market Backend

## 게시글 사진과 임시저장

- 사진은 선택 사항이며 JPEG·PNG 최대 5장, 장당 5MB·1200만 화소 이하를 지원합니다.
- 기본 저장 폴더는 실행 위치의 uploads/post-images이며 Git에서 제외합니다. app.images.directory로 변경할 수 있습니다.
- 등록·수정은 multipart의 post(JSON), images(선택), order(JSON)를 받습니다. 기존 단일 image 요청과 JSON 요청도 유지합니다.
- 서버에서 PNG로 재인코딩한 파일의 식별자와 순서를 DB에 기록합니다. 상세 응답의 imageUrls로 사진을 조회하고 첫 사진을 대표 사진으로 씁니다.
- 미완성 글도 계정당 최대 5개까지 임시저장할 수 있습니다. 마지막 저장부터 3일이 지나면 글과 사진을 삭제합니다.
- 게시된 글을 삭제하면 DB 행을 영구 삭제하고, 커밋 후 사진 파일도 삭제합니다.
- 교체·삭제는 DB 커밋 후 기존 파일을 정리하고 롤백 시 새 파일을 정리합니다.
- 파일 삭제가 실패하면 DB에 남긴 작업을 주기적으로 다시 처리합니다.
- 파일 누락은 404와 서버 로그, 화면의 사진 없음 표시로 처리합니다.
- DB와 파일 시스템은 하나의 원자적 트랜잭션이 아닙니다. 저장 직후 프로세스가 강제 종료되면 새 파일이 남을 수 있습니다.
- 요청 형식과 파일별 목적은 [사진·임시저장 계약](docs/post-media-drafts.md)에 정리했습니다.

지역 기반 거래 서비스 **Brew Market**의 Spring Boot API 서버입니다. 사용자 등록부터 게시글, 위치 검색, 거래 흐름까지 단계적으로 구현하며 데이터 무결성과 실패 조건을 검증합니다.

## 현재 구현

- 서버 상태 확인 API: `GET /health`
- 사용자 등록 API: `POST /users` — 이메일·닉네임 등록 및 DB 저장
- Bean Validation 기반 필수값·이메일 형식·길이 검증
- DB의 `uk_users_email` 유니크 제약조건을 이용한 이메일 중복 방지
- 입력값 검증 실패와 이메일 중복에 대한 공통 `ErrorResponse` 처리
- Flyway 기반 사용자 테이블 마이그레이션과 JPA 스키마 검증
- Testcontainers 기반 DB 통합 테스트와 GitHub Actions 빌드·테스트 워크플로

사용자 등록은 이메일과 닉네임을 저장하며, 웹 로그인에는 구글 OIDC를 사용합니다. 비밀번호·JWT 방식은 사용하지 않습니다. PostGIS 이미지를 사용하지만 위치 검색 API는 향후 구현 범위입니다.

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| 언어 / 프레임워크 | Java 21, Spring Boot 4.1.0 |
| API / 검증 | Spring MVC, Bean Validation |
| 영속성 | Spring Data JPA, PostgreSQL, Flyway |
| 로컬 DB | Docker Compose, `postgis/postgis:17-3.5` |
| 테스트 / 빌드 | JUnit 5, MockMvc, Testcontainers, Gradle Wrapper |
| CI | GitHub Actions |

## 로컬 실행

Java 21과 Docker Compose를 사용할 수 있는 환경에서 Docker를 실행합니다. 아래 명령은 저장소 루트에서 Windows Git Bash와 macOS 터미널에 공통으로 사용할 수 있습니다.

### 1. 환경변수 설정

`.env`가 없다면 예제 파일을 복사합니다.

```sh
cp .env.example .env
```

`.env`의 `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`를 로컬 개발용 값으로 수정합니다. `POSTGRES_PORT`의 기본 예제 값은 `5432`입니다. `.env`는 Git에 올리지 않습니다.

### 2. DB 시작

```sh
docker compose up -d
docker compose ps
```

DB가 준비되면 애플리케이션을 실행합니다.

```sh
./gradlew bootRun
```

애플리케이션은 `.env`를 읽어 DB에 연결합니다. Flyway는 `brew_market` 스키마에 마이그레이션을 적용하고, JPA는 `ddl-auto: validate`로 매핑을 검증합니다.

DB를 중지할 때는 `docker compose down`을 실행합니다. 데이터는 named volume에 유지됩니다.

## API

| 메서드 | 경로 | 동작 | 응답 |
| --- | --- | --- | --- |
| GET | `/health` | 서버 상태 확인 | `200`, `{"status":"UP"}` |
| POST | `/users` | 이메일·닉네임 등록 | `201`, `id`·`email`·`nickname` |
| PATCH | `/posts/{postId}` | 작성자 본인의 제목·본문·가격 수정 | `204`, 응답 본문 없음 |
| DELETE | `/posts/{postId}` | 작성자 본인의 게시글 영구 삭제 | `204`, 응답 본문 없음 |
| GET/POST | `/drafts` | 본인 임시저장 목록·새 임시저장 | `200`/`201` |
| GET/PATCH/DELETE | `/drafts/{id}` | 본인 임시저장 조회·다시 저장·삭제 | `200`/`204` |
| POST | `/drafts/{id}/publish` | 필수 입력 확인 후 게시 | `201` |

게시글 수정에는 로그인 세션과 CSRF 토큰이 필요합니다. 요청·오류 응답·검증 범위는 [게시글 수정 API](docs/post-update.md)를 참고하세요.

삭제도 로그인 세션과 CSRF 토큰이 필요하며 DB 행은 제거합니다. 삭제 후 상세 조회·수정은 404로 처리합니다. [사진·임시저장 계약](docs/post-media-drafts.md)을 참고하세요.

### 상태 확인

```powershell
curl.exe -i http://localhost:8080/health
```

### 사용자 등록

```powershell
$registrationBody = @{
    email = "learner@example.com"
    nickname = "brewer"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri http://localhost:8080/users `
    -ContentType "application/json" -Body $registrationBody
```

이메일은 필수이며 이메일 형식·최대 255자를 검증합니다. 닉네임은 필수이며 최대 50자입니다.

| 실패 조건 | HTTP 상태 | 오류 코드 |
| --- | --- | --- |
| 입력값 검증 실패 | `400` | `INVALID_REQUEST` |
| 이미 등록된 이메일 | `409` | `EMAIL_ALREADY_EXISTS` |

위 오류 응답은 `code`, `message`, `fieldErrors`를 포함합니다. 입력값 오류에는 필드별 메시지가 있고, 이메일 중복 응답의 `fieldErrors`는 빈 배열입니다. 모든 예외가 공통 응답으로 처리되는 것은 아니며, 다른 DB 무결성 오류는 다시 던집니다.

## 테스트와 CI

Docker가 실행 중인 상태에서 테스트합니다. 사용자 등록 통합 테스트는 Testcontainers로 별도 PostGIS 컨테이너를 생성하므로 로컬 개발 DB를 실행할 필요는 없습니다. 최초 실행에는 컨테이너 이미지 다운로드가 필요할 수 있습니다.

```powershell
./gradlew.bat test
```

현재 테스트 코드는 다음을 검증합니다.

- 상태 확인 API 응답
- 사용자 등록 성공 응답과 실제 DB 저장
- 중복 이메일의 `409` 응답·오류 형식과 추가 저장 방지
- 잘못된 이메일의 `400` 응답·필드 오류와 저장 방지

전체 빌드는 `./gradlew.bat build`로 실행합니다. macOS/Linux에서는 `./gradlew`를 사용합니다. 테스트 HTML 보고서는 `build/reports/tests/test/index.html`에 생성됩니다.

GitHub Actions는 `main` push와 `main` 대상 PR에서 `./gradlew build --no-daemon`을 실행하고 테스트 보고서를 artifact로 보관합니다. 최신 실행 결과는 [Actions](https://github.com/brewjeon/brew-market-back/actions)에서 확인할 수 있습니다.

## 다음 구현 범위

- 로그인 / JWT 인증과 권한
- 게시글 CRUD와 찜
- PostgreSQL / PostGIS 기반 반경 검색과 거리순 정렬
- 채팅, 거래 상태, 거래 완료와 후기
- 테스트 확장, 성능 비교와 배포

## 관련 저장소

- [Frontend](https://github.com/brewjeon/brew-market-front)
- [Dev Dashboard](https://github.com/brewjeon/brew-market-dashboard): 브라우저에 개발 진행 상태를 수동 기록하는 대시보드이며, 이 서버의 구현·테스트 결과와 자동 동기화되지 않습니다.
