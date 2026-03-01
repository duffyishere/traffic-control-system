# traffic-control-system

트래픽 제어(레이트 리미트 + 대기열) 기반의 콘서트 좌석 예약 데모 프로젝트입니다.  
요청 급증 상황에서 Gateway, Queue, Token 기반 진입 제어가 어떻게 동작하는지 검증하기 위해 제작하였습니다.

## 프로젝트 개요

- Gateway에서 요청을 수용하고 레이트 리미트를 적용합니다.
- 임계치 조건을 만족하지 못한 요청은 Turnstile 대기열로 우회합니다.
- 대기열에서 순번 상태를 SSE로 전달하고, 통과 시 JWT를 발급합니다.
- JWT를 가진 요청만 예약 API로 진입해 좌석 예약을 수행합니다.

## 아키텍처

```mermaid
flowchart LR
    Client["Client"] --> Gateway["API Gateway"]
    Gateway -->|Bearer JWT 유효| App1["Consumer API #1"]
    Gateway -->|Bearer JWT 유효| App2["Consumer API #2"]
    Gateway -->|대기열 리다이렉트| Turnstile["Turnstile"]
    Turnstile -->|SSE 상태| Client
    Turnstile -->|JWT 발급| Client
    App1 --> MySQL[("MySQL")]
    App2 --> MySQL
    Gateway --> Redis[("Redis")]
    Turnstile --> Redis
```

## 서비스 구성

- `api-gateway`
  - Spring Cloud Gateway(WebFlux) 기반 진입점
  - JWT 검증 및 리다이렉트 처리
  - `app1`, `app2` 로드밸런싱
- `turnstile`
  - Redis ZSET 대기열 관리
  - SSE 이벤트 스트림(`/turnstile/queue/events`)
  - JWK 노출 및 JWT 발급
- `consumer-api` (2개 인스턴스)
  - 좌석 조회/예약 API
  - `PESSIMISTIC_WRITE` 기반 예약 동시성 제어
- `redis`
  - Bucket4j 상태 저장
  - 대기열 데이터 저장
- `mysql`
  - 좌석/예약 데이터 저장
- `locust`
  - 조회 + 예약 부하 테스트

## 요청 흐름

1. 클라이언트가 `GET /api/v1/concerts/seats`, `POST /api/v1/reservation` 호출
2. Gateway 필터에서 `Authorization` 헤더(JWT) 확인
3. JWT 유효 시 `consumer-api`로 전달
4. JWT 없음/검증 실패 시 `303`으로 Turnstile SSE 엔드포인트로 리다이렉트
5. Turnstile이 요청을 Redis ZSET에 등록하고 `WAITING`/`ALLOWED` 상태를 SSE로 전달
6. `ALLOWED` 시 토큰을 획득한 클라이언트가 재요청
7. 예약 API에서 좌석 락 획득 후 예약 처리

## 기술 스택

- Java 21
- Spring Boot 4.0.3
- Spring Cloud Gateway
- Spring WebFlux / Spring MVC
- Spring Data JPA / MySQL
- Spring Data Redis Reactive
- Bucket4j (Redis backend)
- Docker / Docker Compose
- Locust

## 실행 방법

### 사전 준비

- Docker, Docker Compose

### 전체 실행

```bash
docker compose up --build
```

실행 후 브라우저에서 `http://localhost:5173` 으로 접속하면 대기열/좌석 조회 페이지를 확인할 수 있습니다.

### 주요 포트

- Web Client: `http://localhost:5173`
- Gateway: `http://localhost:8080`
- Turnstile: `http://localhost:8083`
- Consumer API #1: `http://localhost:8081`
- Consumer API #2: `http://localhost:8082`
- MySQL: `localhost:3306`
- Redis: `localhost:6379`
- Locust UI: `http://localhost:8089`

### 종료

```bash
docker compose down
```

## API 요약

- `GET /api/v1/concerts/seats`
- `POST /api/v1/reservation`
  - 요청 본문
  ```json
  {
    "userId": 123,
    "seatId": 10
  }
  ```
- `GET /turnstile/queue/events?requestId={uuid}` (SSE)
- `GET /.well-known/openid-configuration` (JWK)

## 부하 테스트

Locust 시나리오 파일: [`locust/rate_limit_test.py`](./locust/rate_limit_test.py)

- 좌석 조회 후 랜덤 좌석 예약 요청
- 성공 응답: `200`
- 중복 예약 등 실패 응답: `400`

실행 후 Locust UI(`http://localhost:8089`)에서 사용자 수와 증가율을 조절해 테스트할 수 있습니다.

## 디렉토리 구조

```text
.
├── api-gateway
├── consumer-api
├── turnstile
├── locust
├── redis
├── data
├── docker-compose.yml
└── settings.gradle
```
