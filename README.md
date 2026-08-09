# brew-market-back

지역 기반 거래 서비스 `brew-market`의 Spring Boot API 서버입니다.

## Current status

- Java 21과 Spring Boot 기반 프로젝트 초기화
- 서버 상태 확인 API 구현
- Controller 테스트와 실제 HTTP 호출 검증

## Tech stack 

- Java 21
- Spring Boot 4.1.0
- Gradle

## Run tests
```powershell
./gradle.bat bootRun
```

## Health check
```powersehll
curl.exe -i http://localhost:8080/health
```

Expected response:
```json
{
  "status": "UP"
}
```

## Planned capabilities

- 인증과 권한
- 게시글과 찜
- PostgreSQL과 PostGIS 기반 위치 검색
- 채팅과 거래 상태
- 후기
- 테스트, 성능 검증과 운영 개선