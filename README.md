# Brew Market Backend

지역 기반 거래 서비스 **Brew Market**의 Spring Boot API 서버입니다. 사용자 등록부터 게시글, 위치 검색, 거래 흐름까지 단계적으로 구현하며 데이터 무결성과 실패 조건을 검증합니다.

## 현재 구현

- 서버 상태 확인 API: `GET /health`
- 사용자 등록 API: `POST /users` — 이메일·닉네임 등록 및 DB 저장
- Bean Validation 기반 필수값·이메일 형식·길이 검증
- DB의 `uk_users_email` 유니크 제약조건을 이용한 이메일 중복 방지
- 입력값 검증 실패와 이메일 중복에 대한 공통 `ErrorResponse` 처리
- Flyway 기반 사용자 테이블 마이그레이션과 JPA 스키마 검증
- Testcontainers 기반 DB 통합 테스트와 GitHub Actions 빌드·테스트 워크플로

현재 사용자 등록은 이메일과 닉네임만 저장합니다. 비밀번호, 로그인, JWT 인증·권한 처리는 아직 구현하지 않았습니다. PostGIS 이미지를 사용하지만 위치 검색 API는 향후 구현 범위입니다.

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

Java 21과 Docker Compose를 사용할 수 있는 환경에서 Docker를 실행합니다. 아래 명령은 저장소 루트에서 PowerShell로 실행합니다.

### 1. 환경변수 설정

`.env`가 없다면 예제 파일을 복사합니다.

```powershell
Copy-Item .env.example .env
```

`.env`의 `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`를 로컬 개발용 값으로 수정합니다. `POSTGRES_PORT`의 기본 예제 값은 `5432`입니다. `.env`는 Git에 올리지 않습니다.

### 2. DB 시작

```powershell
docker compose up -d
docker compose ps
```

DB가 준비되면 애플리케이션을 실행합니다.

```powershell
./gradlew.bat bootRun
```

애플리케이션은 `.env`를 읽어 DB에 연결합니다. Flyway는 `brew_market` 스키마에 마이그레이션을 적용하고, JPA는 `ddl-auto: validate`로 매핑을 검증합니다.

DB를 중지할 때는 `docker compose down`을 실행합니다. 데이터는 named volume에 유지됩니다.

## API

| 메서드 | 경로 | 동작 | 응답 |
| --- | --- | --- | --- |
| GET | `/health` | 서버 상태 확인 | `200`, `{"status":"UP"}` |
| POST | `/users` | 이메일·닉네임 등록 | `201`, `id`·`email`·`nickname` |

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
